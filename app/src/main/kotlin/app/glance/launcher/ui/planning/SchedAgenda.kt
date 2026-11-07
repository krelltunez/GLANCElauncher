package app.glance.launcher.ui.planning

import app.glance.daydial.formatClock
import app.glance.launcher.data.Goal
import app.glance.launcher.data.GoalStatus
import app.glance.launcher.data.Overnight
import app.glance.launcher.data.Project
import app.glance.launcher.data.ProgressMath
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskSource
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The SCHED agenda model, ported from dayGLANCE's `useSchedAgendaState.js`, `schedAgenda.js`
 * and the time rules at the top of `SchedTaskCard.jsx`. Pure functions over the task list so
 * the rules are testable without Compose; `SchedView.kt` draws what these return.
 *
 * dayGLANCE's vocabulary maps as follows: an "imported" task is a [TaskSource.CALENDAR] row,
 * an "unscheduled" (Inbox) task is a row with no date, and `"HH:MM"` strings are minutes.
 */
object SchedAgenda {
    /** `INITIAL_DAYS` / `LOAD_MORE_DAYS` in `useSchedAgendaState.js`. */
    const val INITIAL_DAYS = 14
    const val LOAD_MORE_DAYS = 14

    /** The dates the rolling agenda covers: `schedRollingWindow`, inclusive, from the selected day. */
    fun window(start: LocalDate, daysShown: Int): List<LocalDate> =
        List(maxOf(1, daysShown)) { start.plusDays(it.toLong()) }

    /** All-day first, then by start time: the sort `rawDays` applies. Unscheduled rows never appear here. */
    fun tasksOn(tasks: List<Task>, date: LocalDate): List<Task> =
        tasks.filter { it.date == date }
            .sortedWith(compareBy<Task> { if (it.isAllDay) 0 else 1 }.thenBy { it.startMin ?: 0 })

    /**
     * `getDeadlineTasksForDate`: Inbox tasks whose deadline is this day. A past deadline is only
     * shown once the task is done; while it is open it lives in the Overdue section instead.
     */
    fun deadlinesOn(tasks: List<Task>, date: LocalDate, today: LocalDate): List<Task> =
        tasks.filter { it.date == null && it.deadline == date && (date >= today || it.completed) }

    /** [deadlinesOn] for every date at once, for a grid of day cells. */
    fun deadlinesByDate(tasks: List<Task>, today: LocalDate): Map<LocalDate, List<Task>> =
        tasks.filter { t -> t.date == null && t.deadline != null && (t.deadline >= today || t.completed) }.groupBy { it.deadline!! }

    fun days(
        tasks: List<Task>,
        window: List<LocalDate>,
        today: LocalDate,
        filters: SchedFilters = SchedFilters.NONE,
        hideCompleted: Boolean = false,
        hidePastEvents: Boolean = false,
        now: LocalDateTime? = null,
    ): List<SchedDay> = window.map { date ->
        SchedDay(
            date = date,
            tasks = tasksOn(tasks, date).filter { t ->
                filters.matches(t) && (!hideCompleted || !t.completed) && (!hidePastEvents || now == null || !isPastEvent(t, now))
            },
            deadlineTasks = deadlinesOn(tasks, date, today).filter { t -> filters.matches(t) && (!hideCompleted || !t.completed) },
        )
    }

    /** `visibleDays`: empty days are hidden unless asked for; today never counts as empty while routines are pending. */
    fun visible(days: List<SchedDay>, showEmptyDays: Boolean, today: LocalDate, todayHasRoutines: Boolean): List<SchedDay> =
        days.filter { d -> showEmptyDays || d.tasks.isNotEmpty() || d.deadlineTasks.isNotEmpty() || (d.date == today && todayHasRoutines) }

    /**
     * The Overdue section: incomplete scheduled tasks from before today AND before the visible
     * window (so they never duplicate a rendered day), plain tasks only (events just passed,
     * nothing actionable), then Inbox tasks whose deadline has passed. Each row says which kind
     * it is because the card reads it.
     *
     * A task is over at its real end, not at the end of its date (dayGLANCE #2278): with [now]
     * given, last night's 23:00 task of two hours is not overdue at 00:30, and is at 01:00.
     */
    fun overdue(
        tasks: List<Task>, today: LocalDate, windowStart: LocalDate, filters: SchedFilters = SchedFilters.NONE,
        now: LocalDateTime? = null,
    ): List<OverdueRow> {
        val scheduled = tasks.filter { t ->
            val d = t.date
            d != null && d < today && d < windowStart && !t.completed && t.source != TaskSource.CALENDAR && filters.matches(t) &&
                !stillRunning(t, now)
        }.sortedWith(compareBy<Task> { it.date }.thenBy { it.startMin ?: 0 })
            .map { OverdueRow(it, deadlineKind = false) }
        val deadlines = tasks.filter { t ->
            t.date == null && t.deadline != null && t.deadline < today && !t.completed && filters.matches(t)
        }.sortedBy { it.deadline }
            .map { OverdueRow(it, deadlineKind = true) }
        return scheduled + deadlines
    }

    /**
     * `isPastEvent`: a calendar event that has ENDED relative to [now]. Tasks are never "past"
     * here, even past-due ones; all-day events last until their day ends.
     */
    fun isPastEvent(task: Task, now: LocalDateTime): Boolean {
        if (task.source != TaskSource.CALENDAR) return false
        val date = task.date ?: return false
        if (task.isAllDay) return date < now.toLocalDate()
        // Judged at its real end (#2278): an event from 23:00 to 01:00 is not over at midnight.
        val start = task.startMin ?: return date < now.toLocalDate()
        return !eventEnd(date, start, task.durationMin).isAfter(now)
    }

    /** A dated, timed task whose real end is still ahead of [now] (it runs past its own date). */
    private fun stillRunning(task: Task, now: LocalDateTime?): Boolean {
        if (now == null) return false
        val end = Overnight.end(task) ?: return false
        return end.isAfter(now)
    }

    /** An event's real end: its date and start plus its own duration (0 is a moment, not 30 minutes). */
    private fun eventEnd(date: LocalDate, start: Int, durationMin: Int): LocalDateTime =
        date.atStartOfDay().plusMinutes((start + maxOf(0, durationMin)).toLong())

    /**
     * The card's time treatment (Todoist model): a task whose start has passed keeps its place
     * with a red time label; an event that has ended fades like a completed task; a card whose
     * window contains the current minute shows "Now" instead. All-day items never go stale
     * before their day ends.
     */
    fun cardState(task: Task, now: LocalDateTime, use24: Boolean): CardState {
        val isEvent = task.source == TaskSource.CALENDAR
        val timeLabel = when {
            task.isAllDay -> "All day"
            task.startMin != null -> formatClock(task.startMin, use24) + if (task.durationMin > 0) " · ${task.durationMin}m" else ""
            task.durationMin > 0 -> "${task.durationMin}m"
            else -> ""
        }
        val start = task.startMin
        val date = task.date
        // Judged on real date-times, not "is it today" (#2278): a task that started last night
        // and runs past midnight is still in progress at 00:30, and an event from 23:00 to 01:00
        // has not passed at midnight.
        val startAt = if (date != null && start != null) date.atStartOfDay().plusMinutes(start.toLong()) else null
        val timePassed = !task.isAllDay && startAt != null && (
            if (isEvent) eventEnd(date!!, start!!, task.durationMin).isBefore(now) else startAt.isBefore(now)
            )
        // Tasks default to 30 min (the scheduler's default); events with no duration are instantaneous.
        val duration = if (task.durationMin > 0) task.durationMin else if (isEvent) 0 else 30
        val inProgress = !task.isAllDay && !task.completed && startAt != null &&
            !startAt.isAfter(now) && now.isBefore(startAt.plusMinutes(duration.toLong()))
        return CardState(
            timeLabel = timeLabel,
            inProgress = inProgress,
            pastDue = timePassed && !isEvent && !task.completed && !inProgress,
            finishedEvent = timePassed && isEvent,
        )
    }

    /** `scheduleTaskAtNextSlot`: the next quarter hour after now, as minutes past midnight (may run past the day). */
    fun nextSlot(nowMin: Int): Int = ((nowMin + 1 + 14) / 15) * 15

    /**
     * The Planning screen's Projects section: active projects linked to no goal, as
     * dayGLANCE's Goals dashboard lists them (`standaloneProjects`). A goal's projects
     * show as its row's bars in the Goals section instead.
     */
    fun standaloneProjects(projects: List<Project>): List<Project> =
        projects.filter { it.status == GoalStatus.ACTIVE && it.goalId.isNullOrEmpty() }

    /**
     * The Planning screen's Goals section: active goals by target date, soonest (so overdue)
     * first, as dayGLANCE's Goals screen orders them (`sortGoalsForCarousel`). Goals with no
     * target date follow, most complete first: the app keeps those in creation order, which
     * the user asked the launcher not to (docs/decisions.md, decision 67). Ties keep the snapshot's order.
     */
    fun goalsInOrder(goals: List<Goal>, projects: List<Project>, tasks: List<Task>): List<Goal> {
        val active = goals.filter { it.status == GoalStatus.ACTIVE }
        val (dated, undated) = active.partition { it.targetDate != null }
        val progress = undated.associate { it.id to ProgressMath.goal(it.id, projects, tasks) }
        return dated.sortedBy { it.targetDate } + undated.sortedByDescending { progress.getValue(it.id) }
    }

    /**
     * `groupProjectsForFilter`: one group per goal (goal order, goals with active projects only),
     * then a "Standalone" group for goal-less or orphaned projects.
     */
    fun projectGroups(projects: List<Project>, goals: List<Goal>): List<ProjectGroup> {
        val active = projects.filter { it.status == GoalStatus.ACTIVE }
        val visibleGoals = goals.filter { it.status != GoalStatus.ARCHIVED }
        val groups = visibleGoals.map { g -> ProjectGroup(g.title, active.filter { it.goalId == g.id }) }.filter { it.projects.isNotEmpty() }
        val goalIds = visibleGoals.map { it.id }.toSet()
        val standalone = active.filter { it.goalId == null || it.goalId !in goalIds }
        return if (standalone.isEmpty()) groups else groups + ProjectGroup("Standalone", standalone, standalone = true)
    }
}

data class SchedDay(val date: LocalDate, val tasks: List<Task>, val deadlineTasks: List<Task>)

/** An Overdue row: a scheduled task whose day is behind us, or an Inbox task whose deadline has passed. */
data class OverdueRow(val task: Task, val deadlineKind: Boolean)

data class CardState(val timeLabel: String, val inProgress: Boolean, val pastDue: Boolean, val finishedEvent: Boolean)

data class ProjectGroup(val label: String, val projects: List<Project>, val standalone: Boolean = false)

/**
 * SCHED's filter combo: selections within a dimension match any; dimensions combine. The
 * project id `"none"` stands for tasks without a project, as in the app.
 */
data class SchedFilters(
    val colors: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val projectIds: Set<String> = emptySet(),
) {
    val active: Boolean get() = colors.isNotEmpty() || tags.isNotEmpty() || projectIds.isNotEmpty()
    val count: Int get() = colors.size + tags.size + projectIds.size

    fun matches(task: Task): Boolean {
        if (!active) return true
        if (colors.isNotEmpty() && task.colorHex.lowercase() !in colors) return false
        if (tags.isNotEmpty() && task.tags.none { it in tags }) return false
        if (projectIds.isNotEmpty() && (task.projectId ?: NO_PROJECT) !in projectIds) return false
        return true
    }

    /** These filters without project ids missing from [existing] ("No project" always stays). */
    fun live(existing: Set<String>): SchedFilters {
        val kept = projectIds.filterTo(LinkedHashSet()) { it == NO_PROJECT || it in existing }
        return if (kept.size == projectIds.size) this else copy(projectIds = kept)
    }

    fun toggleColor(hex: String) = copy(colors = colors.toggled(hex.lowercase()))
    fun toggleTag(tag: String) = copy(tags = tags.toggled(tag))
    fun toggleProject(id: String) = copy(projectIds = projectIds.toggled(id))

    private fun Set<String>.toggled(v: String) = if (v in this) this - v else this + v

    companion object {
        const val NO_PROJECT = "none"
        val NONE = SchedFilters()
    }
}
