package app.glance.launcher.ui.common

import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskColors
import app.glance.launcher.data.TaskSource
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The month cell's all-day marks, in the order dayGLANCE's MonthDayCell stacks them. */
class DayCellMarksTest {
    private val day = LocalDate.of(2026, 11, 7)
    private fun task(id: String, allDay: Boolean = true, done: Boolean = false, source: TaskSource = TaskSource.DAYGLANCE, deadline: LocalDate? = null) =
        Task(id, id, if (deadline != null) null else day, if (allDay) null else 9 * 60, 30, isAllDay = allDay, completed = done, colorHex = TaskColors.BLUE, deadline = deadline, source = source)

    @Test
    fun `deadlines come first, then events, then tasks, each open before done`() {
        val items = listOf(
            task("task-done", done = true),
            task("task"),
            task("timed", allDay = false),
            task("event", source = TaskSource.CALENDAR),
        )
        val deadlines = listOf(task("due-done", done = true, deadline = day), task("due", deadline = day))
        assertEquals(listOf("due", "due-done", "event", "task", "task-done"), allDayMarks(items, deadlines).map { it.second.id })
    }

    @Test
    fun `a day with only deadlines still shows them`() {
        assertEquals(listOf("due"), allDayMarks(emptyList(), listOf(task("due", deadline = day))).map { it.second.id })
    }
}
