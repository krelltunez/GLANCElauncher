package app.glance.launcher.data

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Who is due a hello: Reach out, Home and the circles' lists all go by these two (D82). */
class ReachOutTest {
    private val now = Instant.parse("2026-11-15T19:00:00Z")
    private fun ago(days: Long) = now.minus(Duration.ofDays(days))

    @Test
    fun `someone never contacted is overdue as soon as they have a cadence`() {
        val never = Person("p1", "Mom", targetCadenceDays = 14)
        assertEquals(Cadence.NEVER_CONTACTED_DAYS / 14, Cadence.reachRatio(never, now))
        assertTrue(Cadence.reachOut(never, now))
    }

    @Test
    fun `no cadence means no ratio and no reaching out, contacted or not`() {
        assertNull(Cadence.reachRatio(Person("p1", "Priya"), now))
        assertFalse(Cadence.reachOut(Person("p1", "Priya"), now))
        assertFalse(Cadence.reachOut(Person("p2", "Lee", lastInteractionAt = ago(400)), now))
    }

    @Test
    fun `due from three quarters of the cadence, or from half when they reached out last`() {
        assertFalse(Cadence.reachOut(Person("p1", "Ana", targetCadenceDays = 20, lastInteractionAt = ago(14)), now))
        assertTrue(Cadence.reachOut(Person("p1", "Ana", targetCadenceDays = 20, lastInteractionAt = ago(15)), now))
        assertTrue(Cadence.reachOut(Person("p2", "Ben", targetCadenceDays = 20, lastInteractionAt = ago(10), unreturned = true), now))
        assertFalse(Cadence.reachOut(Person("p2", "Ben", targetCadenceDays = 20, lastInteractionAt = ago(9), unreturned = true), now))
    }

    @Test
    fun `a snoozed person is not due, however long it has been`() {
        val snoozed = Person("p1", "Dee", targetCadenceDays = 7, lastInteractionAt = ago(60), snoozedUntil = now.plus(Duration.ofDays(2)))
        assertFalse(Cadence.reachOut(snoozed, now))
        assertTrue(Cadence.reachOut(snoozed.copy(snoozedUntil = ago(1)), now))
    }
}
