package app.glance.daydial

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The moon's position, phase and altitude: a port of dayGLANCE's `src/utils/lunar.js`, the
 * truncated lunar series from Meeus (ch. 47) with the largest periodic terms. Good to a few
 * hundredths of a degree, which puts moonrise within a couple of minutes and the lit fraction
 * within a percent; far below what a band on the dial can show.
 *
 * Instants are epoch milliseconds; angles in degrees unless named otherwise.
 */
object Lunar {
    private const val OBLIQUITY_DEG = 23.4397
    /** 2000-01-01T12:00Z. */
    private const val J2000_MS = 946_728_000_000L
    private const val EARTH_RADIUS_KM = 6371.0
    private const val SUN_DISTANCE_KM = 149_598_000.0

    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI
    private fun daysSinceJ2000(ms: Long) = (ms - J2000_MS) / 86_400_000.0

    data class Equatorial(val ra: Double, val dec: Double, val dist: Double)
    data class Illumination(
        /** Lit portion of the disc, 0 (new) to 1 (full). */
        val fraction: Double,
        /** 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. */
        val phase: Double,
        val waxing: Boolean,
    )

    private fun toEquatorial(lonDeg: Double, latDeg: Double, dist: Double): Equatorial {
        val e = rad(OBLIQUITY_DEG)
        val l = rad(lonDeg)
        val b = rad(latDeg)
        return Equatorial(
            ra = deg(atan2(sin(l) * cos(e) - tan(b) * sin(e), cos(l))),
            dec = deg(asin(sin(b) * cos(e) + cos(b) * sin(e) * sin(l))),
            dist = dist,
        )
    }

    private fun localSiderealDeg(d: Double, lon: Double) = 280.16 + 360.985_623_5 * d + lon

    private fun sunPosition(d: Double): Equatorial {
        val m = rad(357.5291 + 0.985_600_28 * d)
        val c = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lon = deg(m) + c + 102.9372 + 180
        return toEquatorial(lon, 0.0, SUN_DISTANCE_KM)
    }

    /** The moon's geocentric equatorial position and distance (km) at an instant. */
    fun moonPosition(ms: Long): Equatorial {
        val d = daysSinceJ2000(ms)
        val l = rad(218.316 + 13.176_396 * d)
        val m = rad(134.963 + 13.064_993 * d)
        val f = rad(93.272 + 13.229_350 * d)
        val dd = rad(297.850 + 12.190_749 * d)
        val ms0 = rad(357.529 + 0.985_600_28 * d)
        val lon = deg(l) + 6.289 * sin(m) + 1.274 * sin(2 * dd - m) + 0.658 * sin(2 * dd) +
            0.214 * sin(2 * m) - 0.186 * sin(ms0) - 0.114 * sin(2 * f)
        val lat = 5.128 * sin(f) + 0.281 * sin(m + f) - 0.278 * sin(f - m)
        val dist = 385_001 - 20_905 * cos(m) - 3699 * cos(2 * dd - m) - 2956 * cos(2 * dd)
        return toEquatorial(lon, lat, dist)
    }

    /** How much of the disc is lit and which way it is heading. */
    fun illumination(ms: Long): Illumination {
        val d = daysSinceJ2000(ms)
        val s = sunPosition(d)
        val m = moonPosition(ms)
        val phi = acos(
            (sin(rad(s.dec)) * sin(rad(m.dec)) + cos(rad(s.dec)) * cos(rad(m.dec)) * cos(rad(s.ra - m.ra))).coerceIn(-1.0, 1.0),
        )
        val inc = atan2(s.dist * sin(phi), m.dist - s.dist * cos(phi))
        val angle = atan2(
            cos(rad(s.dec)) * sin(rad(s.ra - m.ra)),
            sin(rad(s.dec)) * cos(rad(m.dec)) - cos(rad(s.dec)) * sin(rad(m.dec)) * cos(rad(s.ra - m.ra)),
        )
        val phase = 0.5 + (0.5 * inc * (if (angle < 0) -1 else 1)) / PI
        return Illumination(fraction = (1 + cos(inc)) / 2, phase = phase, waxing = phase < 0.5)
    }

    /** The moon's altitude above the horizon in degrees, corrected for parallax; negative below. */
    fun altitude(ms: Long, lat: Double, lon: Double): Double {
        val m = moonPosition(ms)
        val h = rad(localSiderealDeg(daysSinceJ2000(ms), lon) - m.ra)
        val phi = rad(lat)
        val dec = rad(m.dec)
        val sinAlt = sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        return deg(alt - asin((EARTH_RADIUS_KM / m.dist) * cos(alt)))
    }
}
