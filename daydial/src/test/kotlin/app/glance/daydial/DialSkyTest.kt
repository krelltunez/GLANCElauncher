package app.glance.daydial

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The moon band and the glyph's placement, held to dayGLANCE's computeMoonBand rules. */
class DialSkyTest {
    private val zone = ZoneId.of("America/Denver")
    private val denver = 39.74 to -104.99
    private val sun = SunTimes(7 * 60 + 15, 16 * 60 + 50)

    @Test
    fun `the band keeps to the hours the sun is down`() {
        val band = DialSky.moonBand(LocalDate.of(2026, 1, 3), denver.first, denver.second, sun, zone)
        assertTrue(band.steps.isNotEmpty())
        for (s in band.steps) {
            val mid = (s.startMin + s.endMin) / 2
            assertTrue(mid < sun.sunriseMin || mid >= sun.sunsetMin, "step at $mid is in daylight")
            assertTrue(s.altitude > 0)
            assertTrue(s.opacity in 0f..DialSky.MOONLIGHT_PEAK)
        }
        // A full moon: the band's brightest step is near the peak and the glyph is in a night stretch.
        assertTrue(band.fraction > 0.98f)
        assertTrue(band.steps.maxOf { it.opacity } > 0.15f)
        val g = assertNotNull(band.glyphMin)
        assertTrue(g < sun.sunriseMin || g >= sun.sunsetMin)
    }

    @Test
    fun `a new moon has a band near the floor and the opacity scales with the lit fraction`() {
        val band = DialSky.moonBand(LocalDate.of(2026, 1, 18), denver.first, denver.second, sun, zone)
        assertTrue(band.fraction < 0.02f)
        assertTrue(band.steps.all { it.opacity < 0.01f })
    }

    @Test
    fun `without sun times nothing is clipped, and polar wraps clip the lit span across midnight`() {
        val unclipped = DialSky.moonBand(LocalDate.of(2026, 1, 3), denver.first, denver.second, null, zone)
        val clipped = DialSky.moonBand(LocalDate.of(2026, 1, 3), denver.first, denver.second, sun, zone)
        assertTrue(unclipped.steps.size > clipped.steps.size)
        val wrapped = DialSky.moonBand(LocalDate.of(2026, 1, 3), denver.first, denver.second, SunTimes(23 * 60, 2 * 60), zone)
        assertTrue(wrapped.steps.none { val m = (it.startMin + it.endMin) / 2; m >= 23 * 60 || m < 2 * 60 })
    }

    @Test
    fun `stretches merge consecutive steps and carry the apex, and the glyph is padded off the ends`() {
        fun step(start: Int, alt: Double) = MoonBandStep(start, start + 4, alt, 0.1f)
        val steps = (0 until 30).map { step(it * 4, 10.0 + it) } +        // 00:00 to 02:00, climbing to 39 at the end
            (0 until 10).map { step(22 * 60 + it * 4, 5.0 + it) }          // 22:00 to 22:40, short
        val runs = DialSky.stretches(steps)
        assertEquals(2, runs.size)
        assertEquals(0 to 120, runs[0].startMin to runs[0].endMin)
        assertEquals(118, runs[0].peakMin)
        // The apex is flush against the end, so the glyph is held off it; the short run holds no glyph.
        assertEquals(120 - DialSky.GLYPH_EDGE_PAD_MIN, DialSky.glyphMinute(steps))
        assertNull(DialSky.glyphMinute(steps.drop(30)))
    }

    @Test
    fun `the daylight band follows the sun's elevation and scales with the day's UV`() {
        val day = LocalDate.of(2026, 6, 21)
        val juneSun = SunTimes(5 * 60 + 32, 20 * 60 + 31)
        val clear = DialSky.daylightBand(day, denver.first, denver.second, juneSun, null, zone)
        assertTrue(clear.isNotEmpty())
        assertEquals(juneSun.sunriseMin, clear.first().startMin)
        assertEquals(juneSun.sunsetMin, clear.last().endMin)
        for (step in clear) assertTrue(step.opacity in DialSky.DAYLIGHT_FLOOR..DialSky.DAYLIGHT_PEAK + 0.001f)
        // Noon is the brightest; the edges sit at the floor.
        val noon = clear.first { it.startMin <= 13 * 60 && 13 * 60 < it.endMin }
        assertTrue(noon.opacity > 0.19f, "noon ${noon.opacity}")
        assertTrue(clear.first().opacity < 0.07f)
        // A dim forecast scales the whole band down, never below the floor's share.
        val overcast = DialSky.daylightBand(day, denver.first, denver.second, juneSun, 2.0, zone)
        val overcastNoon = overcast.first { it.startMin <= 13 * 60 && 13 * 60 < it.endMin }
        assertTrue(overcastNoon.opacity < noon.opacity && overcastNoon.opacity > DialSky.DAYLIGHT_FLOOR)
        // Strong UV changes nothing: the geometry is already the ceiling.
        assertEquals(noon.opacity, DialSky.daylightBand(day, denver.first, denver.second, juneSun, 11.0, zone).first { it.startMin == noon.startMin }.opacity)
        // A December noon is well below June's at the same place.
        val december = DialSky.daylightBand(LocalDate.of(2026, 12, 21), denver.first, denver.second, SunTimes(7 * 60 + 17, 16 * 60 + 38), null, zone)
        assertTrue(december.maxOf { it.opacity } < noon.opacity * 0.75f)
    }
}
