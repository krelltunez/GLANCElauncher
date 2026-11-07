package app.glance.launcher.data

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tasks that run past midnight (dayGLANCE #2278): the app's rule, ported.
 *
 * The contract is docs/launcher-integration.md, "Tasks that run past midnight", in the
 * dayGLANCE repository, and the app's code is packages/agenda-core/src/overnight.js. The
 * shared vectors (dayglance-ios/TestFixtures/overnight.vectors.json, copied into this
 * module's test resources) hold this port to the app's numbers, case by case.
 *
 * A task is one row on one date. When a timed row's start plus its duration passes 24:00,
 * the part after midnight lands on the next date: that date SHOWS the piece and counts it
 * busy, but the start date OWNS the task (its counts, its list and every write). And a
 * task is over at its real end, not at the end of its date: a 23:00 task of two hours is
 * still running at 00:30 the next day.
 *
 * The dial has always carried the piece (daydial's layoutDayBlocks, prevDayBlocks); this
 * is the same rule for everything else that reads a task's time.
 */
object Overnight {
    const val DAY_MINUTES = 24 * 60

    /** Minutes the scheduler gives a task with no duration of its own, as the app's end does. */
    private const val DEFAULT_DURATION_MIN = 30

    /** A row that can carry: timed (not all-day), with a date, a start and a length. */
    fun isTimedBlock(task: Task): Boolean =
        !task.isAllDay && task.date != null && task.startMin != null && task.durationMin > 0

    /**
     * The minutes of [task] that land on the date after its own: 0 for a row that ends by
     * 24:00 or does not carry, and at most a whole day (a longer imported event is clipped
     * at the next date's end).
     */
    fun spillMinutes(task: Task): Int {
        if (!isTimedBlock(task)) return 0
        return (task.startMin!! + task.durationMin - DAY_MINUTES).coerceIn(0, DAY_MINUTES)
    }

    /**
     * Where [task] really ends: its date, plus its start (midnight when it has none), plus
     * its duration (the scheduler's 30 minutes when it has none). Null for an all-day or
     * undated row, which ends with its day.
     */
    fun end(task: Task): LocalDateTime? {
        val date = task.date ?: return null
        if (task.isAllDay) return null
        val start = task.startMin ?: 0
        val length = if (task.durationMin > 0) task.durationMin else DEFAULT_DURATION_MIN
        return date.atStartOfDay().plusMinutes((start + length).toLong())
    }

    /** A date's piece of a task from the date before: drawn from 00:00 to [endMinutes]. */
    data class Continuation(val task: Task, val fromDate: LocalDate, val endMinutes: Int) {
        val startMinutes: Int get() = 0
        val minutes: Int get() = endMinutes
    }

    /** The pieces [prevDayTasks] carry onto the next date, in row order, each the task itself. */
    fun continuationsFromPreviousDay(prevDayTasks: List<Task>): List<Continuation> =
        prevDayTasks.mapNotNull { t ->
            val spill = spillMinutes(t)
            if (spill > 0) Continuation(t, t.date!!, spill) else null
        }

    /** The pieces that land on [date]: those of the tasks dated the day before. */
    fun continuationsFor(date: LocalDate, tasks: List<Task>): List<Continuation> {
        val prev = date.minusDays(1)
        return continuationsFromPreviousDay(tasks.filter { it.date == prev })
    }
}
