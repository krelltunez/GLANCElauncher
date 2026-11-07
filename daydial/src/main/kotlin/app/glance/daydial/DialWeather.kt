package app.glance.daydial

import androidx.compose.runtime.Immutable

/** One forecast hour as the dial's weather ring reads it: temperature in the user's unit, WMO code, UV index. */
@Immutable
data class HourWeather(val temp: Int?, val code: Int?, val uv: Double?)

enum class PrecipKind { RAIN, SNOW }

/** A spell of precipitation, in minutes of day, built from whole forecast hours. */
@Immutable
data class PrecipRun(val kind: PrecipKind, val startMin: Int, val endMin: Int)

/**
 * The weather ring's rules, a port of dayGLANCE's `classifyPrecip`, `precipRuns`,
 * `precipArcSegments` and `dialPeakUv` (`src/utils/dayDial.js`). Hour temperatures sit as quiet
 * numerals at the 3-hour stations; precipitation is the one condition that earns ink, as a thin
 * arc broken at its midpoint for one glyph per spell.
 */
object DialWeather {
    const val INSET_MIN = 4
    const val GAP_MIN = 14
    const val MIN_STUB_MIN = 6

    /** WMO weather codes: drizzle, rain, showers and storms are rain; snow, grains and snow showers are snow. */
    fun classifyPrecip(code: Int?): PrecipKind? = when {
        code == null -> null
        code in 71..77 || code == 85 || code == 86 -> PrecipKind.SNOW
        code in 51..67 || code in 80..82 || code >= 95 -> PrecipKind.RAIN
        else -> null
    }

    /** Contiguous spells, adjacent hours of one kind merged, a kind change splitting. */
    fun precipRuns(hourly: Map<Int, HourWeather>?): List<PrecipRun> {
        if (hourly == null) return emptyList()
        val runs = ArrayList<PrecipRun>()
        var cur: PrecipRun? = null
        for (h in 0 until 24) {
            val kind = classifyPrecip(hourly[h]?.code)
            if (kind != null && cur != null && cur.kind == kind && cur.endMin == h * 60) {
                cur = cur.copy(endMin = (h + 1) * 60)
                runs[runs.size - 1] = cur
            } else if (kind != null) {
                cur = PrecipRun(kind, h * 60, (h + 1) * 60)
                runs.add(cur)
            } else {
                cur = null
            }
        }
        return runs
    }

    /** The run's arc as two segments around the glyph at its midpoint; none when a spell is too short to show both stubs. */
    fun precipArcSegments(run: PrecipRun): List<Pair<Float, Float>> {
        val from = (run.startMin + INSET_MIN).toFloat()
        val to = (run.endMin - INSET_MIN).toFloat()
        val mid = (run.startMin + run.endMin) / 2f
        val segments = listOf(from to mid - GAP_MIN / 2f, mid + GAP_MIN / 2f to to)
        return if (segments.all { (s, e) -> e - s >= MIN_STUB_MIN }) segments else emptyList()
    }

    /** The day's peak UV index, or null without a forecast. */
    fun peakUv(hourly: Map<Int, HourWeather>?): Double? =
        hourly?.values?.mapNotNull { it.uv }?.maxOrNull()
}
