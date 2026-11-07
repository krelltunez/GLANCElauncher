package app.glance.launcher.ui.planning

import app.glance.launcher.data.Goal
import app.glance.launcher.data.GoalStatus
import app.glance.launcher.data.Project
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskColors
import app.glance.launcher.data.TaskSource
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The SCHED rules, checked against the behaviour described in dayGLANCE's sched sources. */
class SchedAgendaTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val now = LocalDateTime.of(2026, 10, 17, 10, 20)
    private var n = 0
    private fun task(
        title: String, date: LocalDate? = today, start: Int? = 9 * 60, dur: Int = 30, allDay: Boolean = false,
        done: Boolean = false, color: String = TaskColors.BLUE, project: String? = null, deadline: LocalDate? = null,
        source: TaskSource = TaskSource.DAYGLANCE,
    ) = Task("t${n++}", title, date, start, dur, isAllDay = allDay, completed = done, colorHex = color, projectId = project, deadline = deadline, source = source)

    @Test
    fun `window runs from the selected day, inclusive`() {
        val w = SchedAgenda.window(today, 14)
        assertEquals(14, w.size)
        assertEquals(today, w.first())
        assertEquals(today.plusDays(13), w.last())
        assertEquals(1, SchedAgenda.window(today, 0).size)
    }

    @Test
    fun `a day lists all-day rows first, then by start time, and never inbox rows`() {
        val tasks = listOf(
            task("Lunch", start = 12 * 60), task("Pay bill", start = null, allDay = true), task("Standup", start = 9 * 60),
            task("Inbox thing", date = null, start = null),
        )
        assertEquals(listOf("Pay bill", "Standup", "Lunch"), SchedAgenda.tasksOn(tasks, today).map { it.title })
    }

    @Test
    fun `deadline rows are inbox tasks due that day, a past deadline only once done`() {
        val yesterday = today.minusDays(1)
        val tasks = listOf(
            task("Due today", date = null, start = null, deadline = today),
            task("Was due", date = null, start = null, deadline = yesterday),
            task("Was due, done", date = null, start = null, deadline = yesterday, done = true),
            task("Scheduled with deadline", date = today, deadline = today),
        )
        assertEquals(listOf("Due today"), SchedAgenda.deadlinesOn(tasks, today, today).map { it.title })
        assertEquals(listOf("Was due, done"), SchedAgenda.deadlinesOn(tasks, yesterday, today).map { it.title })
    }

    @Test
    fun `deadlines by date hold every day's deadlinesOn, for the month grid's flags`() {
        val yesterday = today.minusDays(1)
        val tasks = listOf(
            task("Due today", date = null, start = null, deadline = today),
            task("Due in a week", date = null, start = null, deadline = today.plusDays(7)),
            task("Was due, open", date = null, start = null, deadline = yesterday),
            task("Was due, done", date = null, start = null, deadline = yesterday, done = true),
            task("Scheduled with deadline", date = today, deadline = today),
            task("No deadline", date = null, start = null),
        )
        val byDate = SchedAgenda.deadlinesByDate(tasks, today)
        for (d in listOf(yesterday, today, today.plusDays(7), today.plusDays(1))) {
            assertEquals(SchedAgenda.deadlinesOn(tasks, d, today), byDate[d].orEmpty(), "$d")
        }
        assertEquals(setOf(yesterday, today, today.plusDays(7)), byDate.keys)
    }

    @Test
    fun `overdue holds late scheduled tasks before the window and late inbox deadlines, events excluded`() {
        val tasks = listOf(
            task("Old task", date = today.minusDays(3)),
            task("Older task", date = today.minusDays(5), start = 15 * 60),
            task("Old event", date = today.minusDays(3), source = TaskSource.CALENDAR),
            task("Old done", date = today.minusDays(3), done = true),
            task("Late inbox", date = null, start = null, deadline = today.minusDays(1)),
            task("In window", date = today.minusDays(1)),
        )
        val rows = SchedAgenda.overdue(tasks, today, windowStart = today.minusDays(1))
        assertEquals(listOf("Older task", "Old task", "Late inbox"), rows.map { it.task.title })
        assertEquals(listOf(false, false, true), rows.map { it.deadlineKind })
    }

    @Test
    fun `empty days hide unless asked, today stays while routines are pending`() {
        val days = SchedAgenda.days(listOf(task("Tomorrow", date = today.plusDays(1))), SchedAgenda.window(today, 3), today)
        assertEquals(listOf(today.plusDays(1)), SchedAgenda.visible(days, showEmptyDays = false, today, todayHasRoutines = false).map { it.date })
        assertEquals(listOf(today, today.plusDays(1)), SchedAgenda.visible(days, showEmptyDays = false, today, todayHasRoutines = true).map { it.date })
        assertEquals(3, SchedAgenda.visible(days, showEmptyDays = true, today, todayHasRoutines = false).size)
    }

    @Test
    fun `past-due is a red label only for an incomplete task whose window has ended`() {
        val late = SchedAgenda.cardState(task("Late", start = 9 * 60), now, use24 = false)
        assertTrue(late.pastDue); assertFalse(late.inProgress); assertFalse(late.finishedEvent)
        assertEquals("9:00 AM · 30m", late.timeLabel)

        val running = SchedAgenda.cardState(task("Running", start = 10 * 60, dur = 60), now, use24 = true)
        assertTrue(running.inProgress); assertFalse(running.pastDue)
        assertEquals("10:00 · 60m", running.timeLabel)

        // A task's red label starts when its START passes, not its end: 10:00 for 15 minutes at 10:20.
        val justOver = SchedAgenda.cardState(task("Just over", start = 10 * 60, dur = 15), now, use24 = true)
        assertTrue(justOver.pastDue)

        val done = SchedAgenda.cardState(task("Done", start = 9 * 60, done = true), now, use24 = true)
        assertFalse(done.pastDue)

        val allDay = SchedAgenda.cardState(task("All day", date = today.minusDays(1), start = null, allDay = true), now, use24 = true)
        assertFalse(allDay.pastDue)
        assertEquals("All day", allDay.timeLabel)
    }

    @Test
    fun `an event fades once it has ended, and only then`() {
        val ended = SchedAgenda.cardState(task("Ended", start = 9 * 60, dur = 60, source = TaskSource.CALENDAR), now, true)
        assertTrue(ended.finishedEvent); assertFalse(ended.pastDue)
        val running = SchedAgenda.cardState(task("Running", start = 10 * 60, dur = 60, source = TaskSource.CALENDAR), now, true)
        assertFalse(running.finishedEvent); assertTrue(running.inProgress)
        assertTrue(SchedAgenda.isPastEvent(task("Ended", start = 9 * 60, dur = 60, source = TaskSource.CALENDAR), now))
        assertFalse(SchedAgenda.isPastEvent(task("Late task", start = 9 * 60), now))
        assertFalse(SchedAgenda.isPastEvent(task("All-day event", start = null, allDay = true, source = TaskSource.CALENDAR), now))
    }

    @Test
    fun `filters match any within a dimension and combine across`() {
        val a = task("Run #training", color = TaskColors.GREEN, project = "p1")
        val b = task("Read #books", color = TaskColors.INDIGO)
        val c = task("Plan #training #work", color = TaskColors.BLUE)
        val byColor = SchedFilters(colors = setOf(TaskColors.GREEN, TaskColors.INDIGO))
        assertEquals(listOf(a, b), listOf(a, b, c).filter(byColor::matches))
        val byTag = SchedFilters(tags = setOf("training"))
        assertEquals(listOf(a, c), listOf(a, b, c).filter(byTag::matches))
        val combined = SchedFilters(colors = setOf(TaskColors.GREEN, TaskColors.INDIGO), tags = setOf("training"))
        assertEquals(listOf(a), listOf(a, b, c).filter(combined::matches))
        val noProject = SchedFilters(projectIds = setOf(SchedFilters.NO_PROJECT))
        assertEquals(listOf(b, c), listOf(a, b, c).filter(noProject::matches))
        assertEquals(3, combined.count)
        assertFalse(SchedFilters.NONE.active)
        assertEquals(SchedFilters.NONE, SchedFilters.NONE.toggleTag("x").toggleTag("x"))
    }

    @Test
    fun `hide completed and hide past events are view preferences over a day`() {
        val tasks = listOf(
            task("Done", done = true), task("Ended event", start = 8 * 60, dur = 30, source = TaskSource.CALENDAR), task("Open"),
        )
        val plain = SchedAgenda.days(tasks, listOf(today), today)
        assertEquals(3, plain.single().tasks.size)
        val trimmed = SchedAgenda.days(tasks, listOf(today), today, hideCompleted = true, hidePastEvents = true, now = now)
        assertEquals(listOf("Open"), trimmed.single().tasks.map { it.title })
    }

    @Test
    fun `next slot is the quarter hour after now`() {
        assertEquals(615, SchedAgenda.nextSlot(600))
        assertEquals(615, SchedAgenda.nextSlot(614))
        assertEquals(630, SchedAgenda.nextSlot(615))
        assertEquals(600, SchedAgenda.nextSlot(599))
    }

    @Test
    fun `projects group by goal, orphans under Standalone, inactive ones dropped`() {
        val goals = listOf(Goal("g1", "Fitness"), Goal("g2", "Archived", status = GoalStatus.ARCHIVED), Goal("g3", "Empty"))
        val projects = listOf(
            Project("p1", "Base", goalId = "g1"), Project("p2", "Orphan", goalId = "g2"), Project("p3", "Loose"),
            Project("p4", "Old", goalId = "g1", status = GoalStatus.COMPLETED),
        )
        val groups = SchedAgenda.projectGroups(projects, goals)
        assertEquals(listOf("Fitness", "Standalone"), groups.map { it.label })
        assertEquals(listOf("Base"), groups[0].projects.map { it.title })
        assertEquals(listOf("Orphan", "Loose"), groups[1].projects.map { it.title })
        assertTrue(groups[1].standalone)
    }

    @Test
    fun `the Planning Projects section lists only active projects linked to no goal`() {
        val projects = listOf(
            Project("p1", "Base", goalId = "g1"),
            Project("p2", "Loose"),
            Project("p3", "Blank goal", goalId = ""),
            Project("p4", "Shelved", status = GoalStatus.ARCHIVED),
        )
        assertEquals(listOf("Loose", "Blank goal"), SchedAgenda.standaloneProjects(projects).map { it.title })
    }

    @Test
    fun `goals run soonest target date first, then undated goals most complete first`() {
        val goals = listOf(
            Goal("g1", "Undated, half done"),
            Goal("g2", "December", targetDate = LocalDate.of(2026, 12, 1)),
            Goal("g3", "Undated, untouched"),
            Goal("g4", "Overdue", targetDate = LocalDate.of(2026, 10, 1)),
            Goal("g5", "Undated, all done"),
            Goal("g6", "Done", targetDate = LocalDate.of(2026, 9, 1), status = GoalStatus.COMPLETED),
            Goal("g7", "November", targetDate = LocalDate.of(2026, 11, 1)),
        )
        val projects = listOf(Project("p1", "Half", goalId = "g1"), Project("p5", "All", goalId = "g5"))
        val tasks = listOf(
            task("a", project = "p1", done = true), task("b", project = "p1"),
            task("c", project = "p5", done = true),
        )
        assertEquals(
            listOf("Overdue", "November", "December", "Undated, all done", "Undated, half done", "Undated, untouched"),
            SchedAgenda.goalsInOrder(goals, projects, tasks).map { it.title },
        )
    }

    @Test
    fun `goals that tie keep the order dayGLANCE sent`() {
        val date = LocalDate.of(2026, 11, 1)
        val goals = listOf(Goal("g1", "B", targetDate = date), Goal("g2", "A", targetDate = date), Goal("g3", "D"), Goal("g4", "C"))
        assertEquals(listOf("B", "A", "D", "C"), SchedAgenda.goalsInOrder(goals, emptyList(), emptyList()).map { it.title })
    }

    @Test
    fun `a remembered project that no longer exists is ignored, No project is kept`() {
        val saved = SchedFilters(colors = setOf(TaskColors.GREEN), projectIds = setOf("p1", "gone", SchedFilters.NO_PROJECT))
        assertEquals(setOf("p1", SchedFilters.NO_PROJECT), saved.live(setOf("p1", "p2")).projectIds)
        assertEquals(setOf(TaskColors.GREEN), saved.live(setOf("p1")).colors)
        // Only a deleted project selected: no project filter at all, not an empty agenda.
        assertFalse(SchedFilters(projectIds = setOf("gone")).live(setOf("p1")).active)
        // Nothing stale: the same filters back.
        val intact = SchedFilters(projectIds = setOf("p1"))
        assertTrue(intact.live(setOf("p1")) === intact)
    }

    // ── Tasks that run past midnight (dayGLANCE #2278): judged by their real end ────────────

    private val yesterday = today.minusDays(1)
    private val halfPastMidnight = LocalDateTime.of(today, java.time.LocalTime.of(0, 30))
    private val oneAm = LocalDateTime.of(today, java.time.LocalTime.of(1, 0))

    @Test
    fun `last night's task is not overdue while it still runs, and is once it ends`() {
        val night = task("Night shift", date = yesterday, start = 23 * 60, dur = 120)
        val ended = task("Ended last night", date = yesterday, start = 21 * 60, dur = 60)
        val tasks = listOf(night, ended)
        assertEquals(listOf("Ended last night"), SchedAgenda.overdue(tasks, today, today, now = halfPastMidnight).map { it.task.title })
        // At 01:00 the night shift has ended (its end is not after now): overdue.
        assertEquals(listOf("Ended last night", "Night shift"), SchedAgenda.overdue(tasks, today, today, now = oneAm).map { it.task.title })
        // Without a clock the rule is the old one: every task dated before today.
        assertEquals(2, SchedAgenda.overdue(tasks, today, today).size)
    }

    @Test
    fun `a task running past midnight is in progress, not past due, until its end`() {
        val night = task("Night shift", date = yesterday, start = 23 * 60, dur = 120)
        val running = SchedAgenda.cardState(night, halfPastMidnight, use24 = true)
        assertTrue(running.inProgress)
        assertFalse(running.pastDue)
        val over = SchedAgenda.cardState(night, LocalDateTime.of(today, java.time.LocalTime.of(1, 5)), use24 = true)
        assertFalse(over.inProgress)
        assertTrue(over.pastDue)
        // A task starting tonight that runs into tomorrow is in progress tonight too.
        val late = task("Late", start = 23 * 60, dur = 120)
        assertTrue(SchedAgenda.cardState(late, LocalDateTime.of(today, java.time.LocalTime.of(23, 30)), use24 = true).inProgress)
    }

    @Test
    fun `an event from 11 pm to 1 am has not passed at midnight`() {
        val event = task("Late film", date = yesterday, start = 23 * 60, dur = 120, source = TaskSource.CALENDAR)
        assertFalse(SchedAgenda.isPastEvent(event, halfPastMidnight))
        assertFalse(SchedAgenda.cardState(event, halfPastMidnight, true).finishedEvent)
        assertTrue(SchedAgenda.isPastEvent(event, oneAm))
        assertTrue(SchedAgenda.cardState(event, LocalDateTime.of(today, java.time.LocalTime.of(1, 1)), true).finishedEvent)
        // All-day events still last until their day ends; a moment with no duration passes at its time.
        assertTrue(SchedAgenda.isPastEvent(task("Yesterday all day", date = yesterday, start = null, allDay = true, source = TaskSource.CALENDAR), halfPastMidnight))
        assertFalse(SchedAgenda.isPastEvent(task("Today all day", start = null, allDay = true, source = TaskSource.CALENDAR), oneAm))
        assertTrue(SchedAgenda.isPastEvent(task("Moment", date = yesterday, start = 23 * 60, dur = 0, source = TaskSource.CALENDAR), halfPastMidnight))
    }
}
