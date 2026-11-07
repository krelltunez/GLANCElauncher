package app.glance.daydial

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/** The lunar series against almanac moments: a port of the checks in dayGLANCE's lunar tests. */
class LunarTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `full and new moons read as full and new`() {
        // Full moon 2026-01-03 10:03 UTC; new moon 2026-01-18 19:52 UTC.
        val full = Lunar.illumination(ms("2026-01-03T10:03:00Z"))
        assertTrue(full.fraction > 0.99, "full ${full.fraction}")
        val new = Lunar.illumination(ms("2026-01-18T19:52:00Z"))
        assertTrue(new.fraction < 0.01, "new ${new.fraction}")
    }

    @Test
    fun `the week after a new moon waxes, the week after a full moon wanes`() {
        val waxing = Lunar.illumination(ms("2026-01-25T12:00:00Z"))
        assertTrue(waxing.waxing && waxing.fraction in 0.3..0.7, "first quarter ${waxing.fraction} waxing=${waxing.waxing}")
        val waning = Lunar.illumination(ms("2026-01-10T12:00:00Z"))
        assertTrue(!waning.waxing && waning.fraction in 0.3..0.7, "last quarter ${waning.fraction} waxing=${waning.waxing}")
    }

    @Test
    fun `a full moon is high at local midnight and below the horizon at local noon`() {
        // Denver, the night of the January 2026 full moon: 07:00Z is midnight MST, 19:00Z is noon.
        val lat = 39.74
        val lon = -104.99
        assertTrue(Lunar.altitude(ms("2026-01-04T07:00:00Z"), lat, lon) > 40.0)
        assertTrue(Lunar.altitude(ms("2026-01-03T19:00:00Z"), lat, lon) < 0.0)
    }

    @Test
    fun `altitude is opposite at the antipode`() {
        val t = ms("2026-03-15T03:00:00Z")
        val a = Lunar.altitude(t, 39.74, -104.99)
        val b = Lunar.altitude(t, -39.74, 75.01)
        assertTrue(a * b < 0 || kotlin.math.abs(a) < 2.0 || kotlin.math.abs(b) < 2.0, "a=$a b=$b")
    }
}
