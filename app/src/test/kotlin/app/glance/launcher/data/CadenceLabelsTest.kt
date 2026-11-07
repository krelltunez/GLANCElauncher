package app.glance.launcher.data

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The day labels on a chore row, held to lastGLANCE's `formatElapsed` and `formatNextDue`. */
class CadenceLabelsTest {
    private val zone = ZoneOffset.UTC
    private fun at(s: String) = Instant.parse("2026-${s}Z")
    private fun due(last: String, cadence: Int, now: String) = Cadence.formatNextDue(at(last), cadence, at(now), zone)
    private fun ago(last: String, now: String) = Cadence.formatElapsed(at(last), at(now), zone)

    @Test
    fun `the next due day counts calendar days, not the fraction of a day left`() {
        // 2.4 days into a 3-day cadence: 0.6 of a day is left, but the due date is tomorrow.
        assertEquals("tomorrow", due("10-31T08:00:00", 3, "11-02T17:36:00"))
        // 1.1 days in: 1.9 are left, but the due date is two days off.
        assertEquals("in 2d", due("11-01T15:00:00", 3, "11-02T17:24:00"))
        // 1.5 days into 14: 12.5 are left, which rounds up, but the due date is 12 days off.
        assertEquals("in 12d", due("10-31T20:00:00", 14, "11-02T08:00:00"))
    }

    @Test
    fun `today is the due date before the cadence has fully elapsed`() {
        assertEquals("today", due("10-30T20:00:00", 3, "11-02T08:00:00"))
    }

    @Test
    fun `due now goes by time elapsed, even on the due date itself`() {
        assertEquals("due now", due("10-30T08:00:00", 3, "11-02T08:00:00"))
        // 2.96 days rounds to 3.0, as the app rounds it.
        assertEquals("due now", due("10-30T08:00:00", 3, "11-02T07:00:00"))
        assertEquals("due now", due("09-01T08:00:00", 3, "11-02T08:00:00"))
    }

    @Test
    fun `a chore without a cadence or a completion has no next due`() {
        assertNull(Cadence.formatNextDue(null, 3, at("11-02T08:00:00"), zone))
        assertNull(Cadence.formatNextDue(at("11-01T08:00:00"), null, at("11-02T08:00:00"), zone))
    }

    @Test
    fun `elapsed reads hours under a day, even across midnight`() {
        assertEquals("just now", ago("11-02T07:59:30", "11-02T08:00:00"))
        assertEquals("30m ago", ago("11-02T07:30:00", "11-02T08:00:00"))
        assertEquals("14h ago", ago("11-01T20:00:00", "11-02T10:00:00"))
    }

    @Test
    fun `elapsed counts calendar days from one day up`() {
        assertEquals("yesterday", ago("11-01T08:00:00", "11-02T20:00:00"))
        assertEquals("2d ago", ago("10-31T23:00:00", "11-02T08:00:00"))
    }

    @Test
    fun `elapsed never reads zero days`() {
        // 23 hours rounds to one day, but it is still the same date.
        assertEquals("23h ago", ago("11-02T00:30:00", "11-02T23:30:00"))
    }
}
