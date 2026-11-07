package app.glance.daydial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** The day's totals, as the in-app dial's legend pill reports them. Minutes unless noted. */
data class DialBreakdown(
    val effortMin: Int,
    val restoreMin: Int,
    /** Null when there is no day window. */
    val sleepMin: Int?,
    /** Awake time not covered by any block. */
    val unblockedMin: Int,
    val focusMin: Int,
    val routinesDone: Int,
    val routinesTotal: Int,
    /** Scheduled block minutes inside top-level frames over the frames' minutes, 0..1; null without frames. */
    val framesFraction: Float?,
    /** Completable blocks (timed and all-day) done over planned. */
    val blocksDone: Int = 0,
    val blocksPlanned: Int = 0,
)

/**
 * Computes the breakdown for one day. Blocks are clipped to the day; carried-in overruns
 * from the previous day are excluded, as in the reference's totals.
 */
fun computeBreakdown(
    blocks: List<DialBlock>,
    dayWindow: DayWindow?,
    focusSpans: List<FocusSpan> = emptyList(),
    routines: List<DialRoutine> = emptyList(),
    frames: List<DialFrame> = emptyList(),
): DialBreakdown {
    val timed = blocks.filter { !it.isAllDay && it.durationMin > 0 }
        .map { Triple(it, it.startMin.coerceIn(0, MINUTES_PER_DAY), min(MINUTES_PER_DAY, it.startMin + it.durationMin)) }
        .filter { it.third > it.second }
    val effort = timed.filter { it.first.kind == DialBlockKind.EFFORT }.sumOf { it.third - it.second }
    val restore = timed.filter { it.first.kind == DialBlockKind.RESTORE }.sumOf { it.third - it.second }
    val sleepSegs = sleepSegments(dayWindow)
    val sleep = if (sleepSegs.isEmpty()) null else sleepSegs.sumOf { it.second - it.first }
    val awake = Pair(dayWindow?.startMin ?: 0, dayWindow?.stopMin ?: MINUTES_PER_DAY)
    val covered = mergeSpans(timed.map { Pair(it.second, it.third) })
    val blockedAwake = covered.sumOf { (s, e) -> max(0, min(e, awake.second) - max(s, awake.first)) }
    val unblocked = max(0, (awake.second - awake.first) - blockedAwake)
    val focus = mergeFocusSpans(focusSpans).sumOf { it.endMin - it.startMin }
    val framesFraction = if (frames.isEmpty()) null else {
        val top = frames.filterIndexed { i, _ -> frameDepth(frames, i) == 0 }
        val frameMin = top.sumOf { it.endMin - it.startMin }
        val inside = top.sumOf { f -> covered.sumOf { (s, e) -> max(0, min(e, f.endMin) - max(s, f.startMin)) } }
        if (frameMin == 0) 0f else (inside.toFloat() / frameMin).coerceIn(0f, 1f)
    }
    return DialBreakdown(
        effortMin = effort,
        restoreMin = restore,
        sleepMin = sleep,
        unblockedMin = unblocked,
        focusMin = focus,
        routinesDone = routines.count { it.completed },
        routinesTotal = routines.size,
        framesFraction = framesFraction,
        blocksDone = blocks.count { it.completable && it.completed },
        blocksPlanned = blocks.count { it.completable },
    )
}

/** Union of half-open spans, sorted and merged. */
internal fun mergeSpans(spans: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    for (s in spans.sortedBy { it.first }) {
        val last = out.lastOrNull()
        if (last != null && s.first <= last.second) out[out.size - 1] = Pair(last.first, max(last.second, s.second))
        else out += s
    }
    return out
}

/**
 * The legend pill: Effort, Restore, Sleep (with a day window), Unblocked, Focus, Routines
 * and Frames. A wrapping row; [columns] controls how many entries sit per row.
 *
 * [ink] is the text colour of the surface the legend sits on: [DialColors.Ink] (white) on the
 * dark face, the host's text colour on a light one. A dark [ink] swaps the icon tints, pastels
 * made for the dark face, for deeper ones of the same hues ([LegendTints.onLight]).
 */
@Composable
fun DialLegend(
    breakdown: DialBreakdown,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    sans: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    /** Tighter padding and row spacing for a column short on height. */
    compact: Boolean = false,
    /** Entry labels to leave out (for example "Focus" and "Done" where the host shows them elsewhere). */
    hide: Set<String> = emptySet(),
    /**
     * Hidden labels that may stand in, in this order, for a shown entry with no data that day
     * ([LegendSlots]): each takes the empty entry's own place, so nothing else moves.
     */
    substitutes: List<String> = emptyList(),
    ink: Color = DialColors.Ink,
) {
    val tints = if (ink.luminance() < 0.5f) LegendTints.onLight(ink) else LegendTints.onDark
    // Fixed order and fixed slots, so the pill never reflows: top Effort, Restore, Sleep, Focus;
    // bottom Done, Routines, Frames, Unblocked. Missing data shows a dash rather than dropping out,
    // unless a hidden entry with data can take that slot ([substitutes]).
    val all = listOf(
        Triple(DialGlyphs.Zap, "Effort", formatDuration(breakdown.effortMin)) to tints.effort,
        Triple(DialGlyphs.Leaf, "Restore", formatDuration(breakdown.restoreMin)) to tints.restore,
        Triple(DialGlyphs.MoonStar, "Sleep", breakdown.sleepMin?.let { formatDuration(it) } ?: "–") to tints.sleep,
        Triple(DialGlyphs.Timer, "Focus", formatDuration(breakdown.focusMin)) to tints.focus,
        Triple(DialGlyphs.Check, "Done", "${breakdown.blocksDone}/${breakdown.blocksPlanned}") to tints.done,
        Triple(DialGlyphs.Sparkles, "Routines", if (breakdown.routinesTotal > 0) "${breakdown.routinesDone}/${breakdown.routinesTotal}" else "–") to tints.routine,
        Triple(DialGlyphs.LayoutGrid, "Frames", breakdown.framesFraction?.let { "${(it * 100).toInt()}%" } ?: "–") to tints.frames,
        Triple(DialGlyphs.CircleDashed, "Unblocked", formatDuration(breakdown.unblockedMin)) to tints.unblocked,
    )
    val byLabel = all.associateBy { it.first.second }
    val entries = LegendSlots.choose(all.map { it.first.second }, hide, substitutes) { LegendSlots.hasData(breakdown, it) }
        .mapNotNull { byLabel[it] }
    Column(
        modifier
            .background(ink.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        for (row in entries.chunked(max(1, columns))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((entry, tint) in row) LegendEntry(entry.first, entry.second, entry.third, tint, ink, sans, Modifier.weight(1f))
                repeat(max(0, columns - row.size)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LegendEntry(icon: ImageVector, label: String, value: String, tint: Color, ink: Color, sans: androidx.compose.ui.text.font.FontFamily, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, color = ink.copy(alpha = 0.45f), fontSize = 11.sp, fontFamily = sans, maxLines = 1)
            Text(value, color = ink.copy(alpha = 0.9f), fontSize = 14.sp, fontFamily = sans, maxLines = 1)
        }
    }
}

/** The legend's icon tints, one set per surface. */
data class LegendTints(
    val effort: Color, val restore: Color, val sleep: Color, val focus: Color,
    val done: Color, val routine: Color, val frames: Color, val unblocked: Color,
) {
    companion object {
        /** The dial's own pastels, for the dark face. */
        val onDark = LegendTints(
            effort = DialColors.Effort, restore = DialColors.Restore, sleep = DialColors.Sleep, focus = DialColors.Ink,
            done = Color(0xFF22C55E), routine = DialColors.Routine, frames = DialColors.FrameLegend, unblocked = DialColors.Unblocked,
        )

        /** The same hues a few steps deeper (Tailwind 500/600), legible on a light background. */
        fun onLight(ink: Color) = LegendTints(
            effort = Color(0xFF3B82F6), restore = Color(0xFF0D9488), sleep = Color(0xFF8B5CF6), focus = ink.copy(alpha = 0.7f),
            done = Color(0xFF16A34A), routine = Color(0xFF0D9488), frames = Color(0xFF78716C), unblocked = Color(0xFF6B7280),
        )
    }
}

/**
 * Which entry fills each of the legend's shown places. Pure, tested on the JVM.
 *
 * The legend's order is fixed so it never reflows. A shown entry with no data that day (a dash)
 * gives its place to the first hidden [substitutes] entry that has data, in place: the others
 * keep their positions and only that cell changes. An entry whose zero is information (Effort,
 * Restore, Unblocked) always has data and never gives way; a substitute with nothing to say
 * (no blocks planned, no focus time) does not take a place.
 */
object LegendSlots {
    fun choose(labels: List<String>, hide: Set<String>, substitutes: List<String>, hasData: (String) -> Boolean): List<String> {
        val pool = ArrayDeque(substitutes.filter { it in hide && it in labels && hasData(it) })
        return labels.filter { it !in hide }.map { label ->
            if (!hasData(label) && pool.isNotEmpty()) pool.removeFirst() else label
        }
    }

    /** Whether an entry has something to show for this day, rather than a dash or a meaningless zero. */
    fun hasData(b: DialBreakdown, label: String): Boolean = when (label) {
        "Sleep" -> b.sleepMin != null
        "Routines" -> b.routinesTotal > 0
        "Frames" -> b.framesFraction != null
        "Done" -> b.blocksPlanned > 0
        "Focus" -> b.focusMin > 0
        else -> true
    }
}
