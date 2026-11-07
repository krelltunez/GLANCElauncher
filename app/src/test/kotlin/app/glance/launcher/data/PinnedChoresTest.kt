package app.glance.launcher.data

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** lastGLANCE's Pinned row order (`collectPinned` in its utils/pinned.ts). */
class PinnedChoresTest {
    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private fun daysAgo(d: Long) = now.minus(Duration.ofDays(d))

    @Test
    fun `pins run most overdue first, chores without a ratio last, ties by name, ignoring case`() {
        val chores = listOf(
            Chore("c1", "Fridge", "Kitchen", targetCadenceDays = 7, lastDoneAt = daysAgo(7), pinned = true), // 1.0
            Chore("c2", "Never done", "Garden", targetCadenceDays = 7, pinned = true), // no ratio
            Chore("c3", "Gutters", "Garden", targetCadenceDays = 10, lastDoneAt = daysAgo(20), pinned = true), // 2.0
            Chore("c4", "Not pinned", "Kitchen", targetCadenceDays = 1, lastDoneAt = daysAgo(30)),
            Chore("c5", "dishes", "Kitchen", targetCadenceDays = 2, lastDoneAt = daysAgo(2), pinned = true), // 1.0
            Chore("c6", "Alarm battery", "Home", lastDoneAt = daysAgo(3), pinned = true), // no cadence
            Chore("c7", "Plants", "Home", targetCadenceDays = 4, lastDoneAt = daysAgo(1), pinned = true), // 0.25
            Chore("c8", "Sweep porch", "Home", targetCadenceDays = 7, lastDoneAt = now, pinned = true), // 0.0, still ahead of no ratio
        )
        assertEquals(
            listOf("Gutters", "dishes", "Fridge", "Plants", "Sweep porch", "Alarm battery", "Never done"),
            Cadence.pinned(chores, now).map { it.name },
        )
    }

    @Test
    fun `nothing pinned, nothing listed`() {
        assertEquals(emptyList(), Cadence.pinned(listOf(Chore("c1", "Vacuum", "Kitchen", targetCadenceDays = 7)), now))
    }
}
