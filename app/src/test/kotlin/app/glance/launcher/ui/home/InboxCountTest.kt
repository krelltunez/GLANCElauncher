package app.glance.launcher.ui.home

import app.glance.launcher.data.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InboxCountTest {
    private val rows = listOf(
        Task("i1", "Call dentist", null, null),
        Task("i2", "Project task", null, null, projectId = "p1"),
        Task("i3", "Done", null, null, completed = true),
        Task("t1", "Run", LocalDate.of(2026, 10, 23), 9 * 60),
    )

    @Test fun `the app's own count wins over the rows, which carry no Inbox filters`() {
        assertEquals(1, InboxCount.of(appCount = 1, tasks = rows))
        assertEquals(0, InboxCount.of(appCount = 0, tasks = rows))
    }

    @Test fun `without one, open dateless rows are counted`() {
        assertEquals(2, InboxCount.of(appCount = null, tasks = rows))
    }
}
