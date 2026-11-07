package app.glance.daydial

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** One step of the daylight band, at this opacity. Minutes may run past 1440 when the lit span wraps midnight. */
@Immutable
data class DaylightStep(val startMin: Int, val endMin: Int, val opacity: Float)

/** One step of the moon band: the moon is up and the sun is down, at this opacity. */
@Immutable
data class MoonBandStep(val startMin: Int, val endMin: Int, val altitude: Double, val opacity: Float)

/**
 * The moon band for one local day: the sun-down minutes the moon is above the horizon, each
 * step's opacity set by how high it rides and how much of its disc is lit, and the minute the
 * phase glyph goes at (the moon's apex in the drawn stretch it climbs highest in), or null when
 * no stretch is long enough to hold one.
 */
@Immutable
data class MoonBand(val steps: List<MoonBandStep>, val glyphMin: Int?, val fraction: Float, val waxing: Boolean) {
    companion object {
        val NONE = MoonBand(emptyList(), null, 0f, true)
    }
}

/**
 * The moon on the dial, a port of dayGLANCE's `computeMoonBand` and `moonGlyphMinute`
 * (`src/utils/dayDial.js`). The moon shares the daylight band's track and takes over when
 * the sun leaves it: the band means "light this body is giving you", and a moon in a blue sky
 * is giving you none. Pure; the host supplies the observer's coordinates.
 */
object DialSky {
    /** Brightest the moon band gets: a full moon, high, on a clear night. */
    const val MOONLIGHT_PEAK = 0.20f
    /** Its quietest, at the horizon. Both are scaled by the lit fraction. */
    const val MOONLIGHT_FLOOR = 0.012f
    const val MOON_ALTITUDE_REFERENCE = 45.0
    const val STEP_MIN = 4
    /** A stretch shorter than this is left to speak for itself, unglyphed. */
    const val GLYPH_MIN_RUN = 60
    /** How far the glyph is kept off a stretch's ends. */
    const val GLYPH_EDGE_PAD_MIN = 12

    /** The daylight band's opacity at the horizon, and at an overhead summer noon under a clear sky. */
    const val DAYLIGHT_FLOOR = 0.05f
    const val DAYLIGHT_PEAK = 0.2f
    /** UV index taken as full strength: the bottom of the WHO's "very high" band. */
    const val UV_REFERENCE = 8.0
    /** However flat the light, a lit day still reads as lit. */
    const val UV_MIN_SCALE = 0.4

    /**
     * The daylight band from solar elevation, a port of dayGLANCE's `computeDaylightBand`: each
     * step's opacity follows the sun's elevation against the latitude's best-day peak, and the
     * whole band is scaled by the day's peak UV when the forecast has one (the one term that
     * knows about the sky rather than the geometry). Null [uvMax] leaves the band pure geometry.
     */
    fun daylightBand(date: LocalDate, lat: Double, lon: Double, sun: SunTimes, uvMax: Double?, zone: ZoneId): List<DaylightStep> {
        val start = sun.sunriseMin
        val end = if (sun.sunsetMin > sun.sunriseMin) sun.sunsetMin else sun.sunsetMin + MINUTES_PER_DAY
        if (end <= start) return emptyList()
        val peakSin = sin(Solar.peakElevation(lat) * PI / 180).takeIf { it != 0.0 } ?: 1.0
        val uvScale = if (uvMax == null) 1.0 else min(1.0, maxOf(UV_MIN_SCALE, uvMax / UV_REFERENCE))
        val steps = ArrayList<DaylightStep>()
        var m = start
        while (m < end) {
            val stop = min(m + STEP_MIN, end)
            val elev = Solar.elevation(date, lat, lon, (m + stop) / 2.0, zone)
            val lit = maxOf(0.0, sin(elev * PI / 180)) / peakSin
            val opacity = DAYLIGHT_FLOOR + (DAYLIGHT_PEAK - DAYLIGHT_FLOOR) * min(1.0, lit).toFloat() * uvScale.toFloat()
            steps.add(DaylightStep(m, stop, opacity))
            m = stop
        }
        return steps
    }

    fun moonBand(date: LocalDate, lat: Double, lon: Double, sun: SunTimes?, zone: ZoneId): MoonBand {
        val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val lit = Lunar.illumination(midnight + 12 * 3_600_000L)
        val sunUp: (Int) -> Boolean = when {
            sun == null -> { _ -> false }
            sun.sunsetMin > sun.sunriseMin -> { m -> m >= sun.sunriseMin && m < sun.sunsetMin }
            // Sunset before sunrise on the clock: the lit span wraps midnight.
            else -> { m -> m >= sun.sunriseMin || m < sun.sunsetMin }
        }
        val ref = sin(MOON_ALTITUDE_REFERENCE * PI / 180)
        val steps = ArrayList<MoonBandStep>()
        var m = 0
        while (m < MINUTES_PER_DAY) {
            val mid = m + STEP_MIN / 2
            if (!sunUp(mid)) {
                val alt = Lunar.altitude(midnight + mid * 60_000L, lat, lon)
                if (alt > 0) {
                    val strength = min(1.0, sin(alt * PI / 180) / ref)
                    val opacity = ((MOONLIGHT_FLOOR + (MOONLIGHT_PEAK - MOONLIGHT_FLOOR) * strength) * lit.fraction).toFloat()
                    steps.add(MoonBandStep(m, m + STEP_MIN, alt, opacity))
                }
            }
            m += STEP_MIN
        }
        return MoonBand(steps, glyphMinute(steps), lit.fraction.toFloat(), lit.waxing)
    }

    /** A stretch of consecutive steps, with the minute the moon rides highest within it. */
    data class Stretch(val startMin: Int, val endMin: Int, val peakMin: Int, val peakAlt: Double)

    fun stretches(steps: List<MoonBandStep>): List<Stretch> {
        val runs = ArrayList<Stretch>()
        for (step in steps) {
            val last = runs.lastOrNull()
            val mid = (step.startMin + step.endMin) / 2
            if (last != null && last.endMin == step.startMin) {
                runs[runs.size - 1] = if (step.altitude > last.peakAlt) last.copy(endMin = step.endMin, peakMin = mid, peakAlt = step.altitude)
                else last.copy(endMin = step.endMin)
            } else {
                runs.add(Stretch(step.startMin, step.endMin, mid, step.altitude))
            }
        }
        return runs
    }

    /** The moon's apex in the drawn stretch it climbs highest in, held off that stretch's ends. */
    fun glyphMinute(steps: List<MoonBandStep>): Int? {
        val best = stretches(steps).filter { it.endMin - it.startMin >= GLYPH_MIN_RUN }.maxByOrNull { it.peakAlt } ?: return null
        return min(maxOf(best.peakMin, best.startMin + GLYPH_EDGE_PAD_MIN), best.endMin - GLYPH_EDGE_PAD_MIN)
    }
}
