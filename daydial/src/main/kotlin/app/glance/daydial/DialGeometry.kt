package app.glance.daydial

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geometry of the Day Dial, ported from dayGLANCE `src/utils/dayDial.js`.
 *
 * Everything is expressed in the reference's 1000-unit box with the centre at (500, 500);
 * the composable scales by `size / 1000` when it draws.
 */
object DialGeometry {
    const val BOX = 1000f
    const val CX = 500f
    const val CY = 500f

    // Radii (box units), inside out.
    const val R_TEMP = 250f
    const val R_NEEDLE_ROOT = 265f
    const val R_PRECIP = 274f
    const val R_DAYLIGHT_INNER = 282f
    const val R_DAYLIGHT_OUTER = 300f
    const val R_INNER = 300f
    const val R_EDGE = 385f
    const val R_FOCUS_INNER = 307f
    const val R_FOCUS_OUTER = 315f
    const val R_TICK_INNER = 400f
    const val R_TICK_OUTER = 436f
    const val R_ROUTINE_INNER = 404f
    const val R_ROUTINE_OUTER = 432f
    const val R_BEZEL = 424f
    const val R_SUN_GLYPH = 456f
    /** The daylight track's mid-line: the moon glyph sits IN the band. */
    const val R_MOON_GLYPH = 292f
    /** The moon disc's radius, a shade under the band's half-width. */
    const val MOON_GLYPH_RADIUS = 9f
    const val R_LABEL = 478f
    const val R_NEEDLE_TIP = R_BEZEL - 14f

    const val GAP_MIN = 3f
    const val LANE_GAP = 6f
    const val SUN_LABEL_CLEARANCE_MIN = 30
    const val TRAIL_STEPS = 15
    const val TRAIL_STEP_MIN = 4

    /** Angle in radians for a minute of the day: 0 at the top, clockwise. */
    fun angle(min: Float): Float = (min / MINUTES_PER_DAY) * 2f * PI.toFloat()

    /** Degrees for a minute of the day, 0 at the top, clockwise. */
    fun degrees(min: Float): Float = min / MINUTES_PER_DAY * 360f

    /** Point on the circle of radius [r] at minute [min]. */
    fun point(cx: Float, cy: Float, r: Float, min: Float): Pair<Float, Float> {
        val a = angle(min)
        return Pair(cx + r * sin(a), cy - r * cos(a))
    }

    /**
     * Minute of the day under a point, measured from the centre. Inverse of [point].
     * Returns a value in [0, 1440).
     */
    fun minuteAt(cx: Float, cy: Float, x: Float, y: Float): Float {
        val a = atan2(x - cx, -(y - cy)) // 0 at top, clockwise positive
        var m = a / (2f * PI.toFloat()) * MINUTES_PER_DAY
        if (m < 0f) m += MINUTES_PER_DAY
        return m % MINUTES_PER_DAY
    }

    fun radiusAt(cx: Float, cy: Float, x: Float, y: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Angular padding between adjacent arcs, in minutes. The pad yields so a sliver keeps
     * at least two thirds of its span: `pad = min(3, (end - start) / 6)`. The pad is skipped
     * on a side that crosses midnight.
     */
    fun padSpan(startMin: Float, endMin: Float, padStart: Boolean = true, padEnd: Boolean = true): Pair<Float, Float> {
        val pad = min(GAP_MIN, (endMin - startMin) / 6f)
        return Pair(startMin + (if (padStart) pad else 0f), endMin - (if (padEnd) pad else 0f))
    }

    /** Fill opacity, edge opacity and edge width grow with duration and saturate at 180 minutes. */
    data class Intensity(val fillOpacity: Float, val edgeOpacity: Float, val edgeWidth: Float)

    fun intensity(durationMin: Int): Intensity {
        val t = (durationMin / 180f).coerceIn(0f, 1f)
        return Intensity(
            fillOpacity = 0.05f + t * 0.11f,
            edgeOpacity = 0.45f + t * 0.55f,
            edgeWidth = 2f + t * 2f,
        )
    }

    /** Radial band for a lane inside [rInner, rOuter]. */
    data class Band(val rInner: Float, val rOuter: Float) {
        val depth: Float get() = rOuter - rInner
        val mid: Float get() = (rInner + rOuter) / 2f
    }

    fun laneBand(rInner: Float, rOuter: Float, lane: Int, laneCount: Int): Band {
        if (laneCount <= 1) return Band(rInner, rOuter)
        val span = rOuter - rInner
        val gap = min(LANE_GAP, span / (laneCount * 5f))
        val depth = (span - gap * (laneCount - 1)) / laneCount
        val base = rInner + lane * (depth + gap)
        return Band(base, base + depth)
    }

    /** Tick marks every 5 minutes: 24 hour ticks, 72 quarter ticks and 192 minor ticks. */
    enum class TickKind { HOUR, QUARTER, MINOR }

    data class Tick(val min: Int, val kind: TickKind)

    private val tickList: List<Tick> by lazy {
        (0 until MINUTES_PER_DAY step 5).map { m ->
            val kind = when {
                m % 60 == 0 -> TickKind.HOUR
                m % 15 == 0 -> TickKind.QUARTER
                else -> TickKind.MINOR
            }
            Tick(m, kind)
        }
    }

    fun ticks(): List<Tick> = tickList

    data class HourLabel(val hour: Int, val text: String, val cardinal: Boolean)

    /** One label every three hours. Cardinals (0, 6, 12, 18) are larger and brighter. */
    fun hourLabels(use24HourClock: Boolean, compact: Boolean = false): List<HourLabel> =
        (0 until 24 step 3).filter { h -> !(compact && (h == 6 || h == 18)) }.map { h ->
            val text = if (use24HourClock) {
                h.toString().padStart(2, '0')
            } else {
                val h12 = if (h % 12 == 0) 12 else h % 12
                "$h12 ${if (h < 12) "AM" else "PM"}"
            }
            HourLabel(h, text, h % 6 == 0)
        }

    /** Circular minute distance, so 23:50 and 00:10 are 20 minutes apart. */
    fun circularDistance(a: Int, b: Int): Int {
        val d = abs(a - b) % MINUTES_PER_DAY
        return min(d, MINUTES_PER_DAY - d)
    }

    /** The hub's long date fits when it takes at most this share of the dial (`dialDateFits`). */
    const val DATE_MAX_FRAC = 0.44f

    /**
     * Whether the hub spells [date]'s month out ("November 24") or abbreviates it ("Nov 24").
     * Decided once per month, from the widest long-form date in it, so a month never switches
     * form partway through ("November 1" fitting, then "Nov 24"). [longWidth] measures a date's
     * long form in pixels.
     */
    fun monthDateFits(date: LocalDate, dialPx: Float, longWidth: (LocalDate) -> Float): Boolean {
        val month = YearMonth.from(date)
        val widest = (1..month.lengthOfMonth()).maxOf { longWidth(month.atDay(it)) }
        return widest <= dialPx * DATE_MAX_FRAC
    }

    /** A label hides when a sun mark (or alarm) sits within 30 minutes of it, measured circularly. */
    fun labelYieldsToMark(labelMin: Int, marks: List<Int>): Boolean =
        marks.any { circularDistance(labelMin, it) < SUN_LABEL_CLEARANCE_MIN }

    /**
     * Mutes a task colour for the dark face: keeps the hue, caps saturation at 0.5 and pins
     * lightness at 0.73 (HSL).
     */
    fun muteColor(color: Color): Color = hslAdjust(color, maxSaturation = 0.5f, lightness = 0.73f)

    /**
     * The light-surface counterpart of [muteColor], for a task colour shown off the dial on a
     * light background: keeps the hue, caps saturation at 0.6 and pins lightness at 0.45 (HSL).
     */
    fun deepenColor(color: Color): Color = hslAdjust(color, maxSaturation = 0.6f, lightness = 0.45f)

    /** Frame outlines are muted harder: saturation cap 0.28, lightness 0.62. */
    fun muteFrameColor(color: Color): Color = hslAdjust(color, maxSaturation = 0.28f, lightness = 0.62f)

    private fun hslAdjust(color: Color, maxSaturation: Float, lightness: Float): Color {
        val r = color.red
        val g = color.green
        val b = color.blue
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d > 0.00001f) {
            s = d / (1f - abs(2f * l - 1f))
            h = when (mx) {
                r -> ((g - b) / d) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } * 60f
            if (h < 0f) h += 360f
        }
        val s2 = min(s, maxSaturation)
        return hslToColor(h, s2, lightness)
    }

    fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h % 360f + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
    }

    /** Snaps a minute to the nearest multiple of [step], clamped inside the day. */
    fun snap(min: Float, step: Int): Int {
        val s = (min / step).roundToInt() * step
        return s.coerceIn(0, MINUTES_PER_DAY)
    }

    /** Rounds down to a tick boundary. */
    fun floorTo(min: Float, step: Int): Int = (floor(min / step) * step).toInt().coerceIn(0, MINUTES_PER_DAY)
}

/**
 * A block as drawn on one day. Blocks that cross midnight are clipped and flagged so
 * readouts can still show the true times.
 */
data class DayBlock(
    val block: DialBlock,
    /** Clipped start on this day. */
    val startMin: Int,
    /** Clipped end on this day, at most 1440. */
    val endMin: Int,
    val startedPrevDay: Boolean,
    val endsNextDay: Boolean,
    val lane: Int = 0,
    val laneCount: Int = 1,
) {
    /** True start, which may be negative (yesterday) when the block started before midnight. */
    val trueStart: Int get() = if (startedPrevDay) block.startMin - MINUTES_PER_DAY else block.startMin
    val trueEnd: Int get() = trueStart + block.durationMin
}

/**
 * Projects today's blocks and yesterday's overruns onto the 0..1440 ring and packs
 * overlapping blocks into lanes.
 */
fun layoutDayBlocks(dayBlocks: List<DialBlock>, prevDayBlocks: List<DialBlock> = emptyList()): List<DayBlock> {
    val projected = ArrayList<DayBlock>()
    for (b in dayBlocks) {
        if (b.isAllDay || b.durationMin <= 0) continue
        val start = b.startMin.coerceIn(0, MINUTES_PER_DAY)
        val rawEnd = b.startMin + b.durationMin
        val end = min(MINUTES_PER_DAY, rawEnd)
        if (end <= start) continue
        projected += DayBlock(b, start, end, startedPrevDay = false, endsNextDay = rawEnd > MINUTES_PER_DAY)
    }
    for (b in prevDayBlocks) {
        if (b.isAllDay || b.durationMin <= 0) continue
        val rawEnd = b.startMin + b.durationMin
        if (rawEnd <= MINUTES_PER_DAY) continue
        val end = min(MINUTES_PER_DAY, rawEnd - MINUTES_PER_DAY)
        if (end <= 0) continue
        projected += DayBlock(b, 0, end, startedPrevDay = true, endsNextDay = false)
    }
    if (projected.isEmpty()) return projected
    val slots = assignLanes(projected.mapIndexed { i, d -> LaneInput(i, d.startMin, d.endMin) })
    return projected.mapIndexed { i, d -> d.copy(lane = slots[i].lane, laneCount = slots[i].laneCount) }
}

/** Sleep segments: the time outside the awake window, when both markers exist and stop > start. */
fun sleepSegments(window: DayWindow?): List<Pair<Int, Int>> {
    if (window == null || window.stopMin <= window.startMin) return emptyList()
    val out = ArrayList<Pair<Int, Int>>()
    if (window.startMin > 0) out += Pair(0, window.startMin)
    if (window.stopMin < MINUTES_PER_DAY) out += Pair(window.stopMin, MINUTES_PER_DAY)
    return out
}

/**
 * The block the hub narrates: the running block wins (the latest-starting one among
 * several, so a nested block beats its container); otherwise the next upcoming block;
 * otherwise null.
 */
fun findFocusBlock(blocks: List<DayBlock>, nowMin: Int?): DayBlock? {
    if (nowMin == null) return null
    val running = blocks.filter { it.trueStart <= nowMin && nowMin < it.trueEnd }
    if (running.isNotEmpty()) return running.maxByOrNull { it.trueStart }
    return blocks.filter { it.trueStart > nowMin }.minByOrNull { it.trueStart }
}

/** Merges focus spans that sit within [toleranceMin] of each other and clips them to the day. */
fun mergeFocusSpans(spans: List<FocusSpan>, toleranceMin: Int = 2): List<FocusSpan> {
    val sorted = spans
        .map { FocusSpan(it.startMin.coerceIn(0, MINUTES_PER_DAY), it.endMin.coerceIn(0, MINUTES_PER_DAY)) }
        .filter { it.endMin > it.startMin }
        .sortedBy { it.startMin }
    val out = ArrayList<FocusSpan>()
    for (s in sorted) {
        val last = out.lastOrNull()
        if (last != null && s.startMin <= last.endMin + toleranceMin) {
            out[out.size - 1] = FocusSpan(last.startMin, max(last.endMin, s.endMin))
        } else {
            out += s
        }
    }
    return out
}

/** Depth of a frame: how many other frames fully enclose it, capped at 1. Equal spans: earlier is outer. */
fun frameDepth(frames: List<DialFrame>, index: Int): Int {
    val f = frames[index]
    var depth = 0
    for ((i, other) in frames.withIndex()) {
        if (i == index) continue
        val encloses = other.startMin <= f.startMin && other.endMin >= f.endMin
        val identical = other.startMin == f.startMin && other.endMin == f.endMin
        if (encloses && (!identical || i < index)) depth++
    }
    return min(depth, 1)
}

/** Fill and edge mutes for a block by state. */
data class Tone(val fill: Float, val edge: Float)

fun blockTone(block: DayBlock, nowMin: Int?, dayIsPast: Boolean, viewingToday: Boolean): Tone {
    if (block.block.completed) return Tone(0.6f, 0.25f)
    val past = if (nowMin != null) block.trueEnd <= nowMin else dayIsPast
    if (!past) return Tone(1f, 1f)
    val owed = block.block.completable && viewingToday && !block.startedPrevDay
    return if (owed) Tone(0.35f, 1f) else Tone(0.6f, 0.6f)
}

/** Formats minutes past midnight as a clock reading. Values outside the day wrap. */
fun formatClock(min: Int, use24HourClock: Boolean): String {
    val m = ((min % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val h = m / 60
    val mm = (m % 60).toString().padStart(2, '0')
    return if (use24HourClock) {
        "${h.toString().padStart(2, '0')}:$mm"
    } else {
        val h12 = if (h % 12 == 0) 12 else h % 12
        "$h12:$mm ${if (h < 12) "AM" else "PM"}"
    }
}

/** "45m", "1h", "1h 30m", "2d 3h". */
fun formatDuration(min: Int): String {
    if (min < 60) return "${min}m"
    val h = min / 60
    val m = min % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}

/** Relative phrase for the hub: "{x} left" while running, "in {x}" before, "ended {x} ago" after. */
fun relativePhrase(block: DayBlock, nowMin: Int): String = when {
    block.trueStart <= nowMin && nowMin < block.trueEnd -> "${formatDuration(block.trueEnd - nowMin)} left"
    nowMin < block.trueStart -> "in ${formatDuration(block.trueStart - nowMin)}"
    else -> "ended ${formatDuration(nowMin - block.trueEnd)} ago"
}

/** Words that start with `#` in a title, lower-cased, as dayGLANCE stores tags inline. */
fun extractTags(title: String): List<String> =
    Regex("(?<=^|\\s)#([\\p{L}\\p{N}_/-]+)").findAll(title).map { it.groupValues[1].lowercase() }.toList()

/** Title without its inline tags. */
fun titleWithoutTags(title: String): String =
    title.replace(Regex("(?<=^|\\s)#[\\p{L}\\p{N}_/-]+"), "").replace(Regex("\\s{2,}"), " ").trim()
