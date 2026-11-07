package app.glance.daydial

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sun's elevation, a port of dayGLANCE's `getSunElevation` and `getPeakSunElevation`
 * (`src/utils/solar.js`): declination from the day of the year, the equation of time, the
 * zone's standard meridian, and the hour angle. Good to a degree, which is what a band on
 * the dial can show.
 */
object Solar {
    const val AXIAL_TILT_DEG = 23.44

    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI

    /** Elevation in degrees at [minOfDay] local clock minutes on [date] at ([lat], [lon]); negative below the horizon. */
    fun elevation(date: LocalDate, lat: Double, lon: Double, minOfDay: Double, zone: ZoneId): Double {
        val n = date.dayOfYear
        val dec = -AXIAL_TILT_DEG * cos(rad((360.0 / 365) * (n + 10)))
        val b = rad((360.0 / 365) * (n - 81))
        val eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        // The zone's offset on this date, so a DST day gets that day's meridian.
        val offsetHours = date.atStartOfDay(zone).offset.totalSeconds / 3600.0
        val standardMeridian = 15 * offsetHours
        val solarMin = minOfDay + 4 * (lon - standardMeridian) + eot
        val h = 15 * (solarMin / 60 - 12)
        val sinAlt = sin(rad(dec)) * sin(rad(lat)) + cos(rad(dec)) * cos(rad(lat)) * cos(rad(h))
        return deg(asin(sinAlt.coerceIn(-1.0, 1.0)))
    }

    /** The highest the sun gets at this latitude on its best day of the year; capped at overhead inside the tropics. */
    fun peakElevation(lat: Double): Double = minOf(90.0, 90 - kotlin.math.abs(lat) + AXIAL_TILT_DEG)
}
