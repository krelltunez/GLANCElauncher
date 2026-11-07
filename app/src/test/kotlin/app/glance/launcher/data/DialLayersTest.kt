package app.glance.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DialLayersTest {
    private val day = LocalDate.of(2026, 10, 23)
    private val tasks = listOf(
        Task("t1", "Run", day, 7 * 60),
        Task("e1", "Standup", day, 9 * 60, source = TaskSource.CALENDAR),
        Task("o1", "From Obsidian", day, 10 * 60),
    )

    @Test fun `with calendar events on, every task draws`() {
        assertEquals(listOf("t1", "e1", "o1"), DialLayers().dialTasks(tasks).map { it.id })
    }

    @Test fun `with them off, only imported events go and the user's own tasks stay`() {
        assertEquals(listOf("t1", "o1"), DialLayers(calendars = false).dialTasks(tasks).map { it.id })
    }
}
