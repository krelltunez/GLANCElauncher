package app.glance.daydial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The weather ring's rules, held to dayGLANCE's dayDial tests. */
class DialWeatherTest {
    private fun hour(code: Int?, temp: Int = 50, uv: Double? = null) = HourWeather(temp, code, uv)

    @Test
    fun `WMO codes classify as rain, snow or nothing`() {
        assertEquals(PrecipKind.RAIN, DialWeather.classifyPrecip(61))
        assertEquals(PrecipKind.RAIN, DialWeather.classifyPrecip(51))
        assertEquals(PrecipKind.RAIN, DialWeather.classifyPrecip(81))
        assertEquals(PrecipKind.RAIN, DialWeather.classifyPrecip(95))
        assertEquals(PrecipKind.SNOW, DialWeather.classifyPrecip(73))
        assertEquals(PrecipKind.SNOW, DialWeather.classifyPrecip(86))
        assertNull(DialWeather.classifyPrecip(0))
        assertNull(DialWeather.classifyPrecip(3))
        assertNull(DialWeather.classifyPrecip(45))
        assertNull(DialWeather.classifyPrecip(null))
    }

    @Test
    fun `adjacent hours of one kind merge, a kind change splits, a gap ends the run`() {
        val hourly = mapOf(9 to hour(61), 10 to hour(63), 11 to hour(71), 14 to hour(80), 16 to hour(80))
        assertEquals(
            listOf(PrecipRun(PrecipKind.RAIN, 540, 660), PrecipRun(PrecipKind.SNOW, 660, 720), PrecipRun(PrecipKind.RAIN, 840, 900), PrecipRun(PrecipKind.RAIN, 960, 1020)),
            DialWeather.precipRuns(hourly),
        )
        assertTrue(DialWeather.precipRuns(null).isEmpty())
    }

    @Test
    fun `a run's arc is two stubs around the glyph, or nothing when an hour is too short to hold both`() {
        val two = DialWeather.precipArcSegments(PrecipRun(PrecipKind.RAIN, 540, 660))
        assertEquals(listOf(544f to 593f, 607f to 656f), two)
        // One hour: 60 minutes less insets and the gap leaves 19 per stub, enough.
        assertEquals(2, DialWeather.precipArcSegments(PrecipRun(PrecipKind.RAIN, 540, 600)).size)
        assertTrue(DialWeather.precipArcSegments(PrecipRun(PrecipKind.RAIN, 540, 560)).isEmpty())
    }

    @Test
    fun `peak UV is the day's maximum, null without a forecast`() {
        assertEquals(6.5, DialWeather.peakUv(mapOf(9 to hour(0, uv = 2.0), 12 to hour(0, uv = 6.5), 15 to hour(0, uv = null))))
        assertNull(DialWeather.peakUv(mapOf(9 to hour(0))))
        assertNull(DialWeather.peakUv(null))
    }
}
