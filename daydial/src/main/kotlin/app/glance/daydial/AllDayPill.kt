@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.glance.daydial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "All Day" pill beside the dial: one wrapping row of all-day items as chips (a 6dp dot in
 * the muted colour plus the title, incomplete first), and a second wrapping row for the
 * deadlines (a rose dot). Each chip is a button; rows wrap rather than truncate.
 *
 * With [singleLine] (a phone, where the pill sits under a full-width dial) each row is exactly
 * one line: the chips that fit, then "+N" for the rest, which calls [onMore]. A wrapping pill
 * grew to three or four lines and took that height from the dial.
 *
 * [ink] is the text colour of the surface the pill sits on: [DialColors.Ink] (white) on the dark
 * face, the host's text colour on a light one. A dark [ink] also deepens the all-day dots, which
 * are muted for the dark face and wash out on light.
 */
@Composable
fun AllDayPill(
    items: List<DialBlock>,
    modifier: Modifier = Modifier,
    deadlines: List<DialBlock> = emptyList(),
    title: String? = null,
    maxChips: Int = 8,
    sans: FontFamily = FontFamily.Default,
    /** Looser padding and row spacing when the host has height to spare. */
    roomy: Boolean = false,
    singleLine: Boolean = false,
    onMore: (() -> Unit)? = null,
    ink: Color = DialColors.Ink,
    onItemTap: (DialBlock) -> Unit = {},
) {
    val onLight = ink.luminance() < 0.5f
    val sorted = items.filter { it.isAllDay }.sortedBy { it.completed }
    Column(
        modifier
            .background(ink.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = if (roomy) 12.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (roomy) 8.dp else 4.dp),
    ) {
        if (title != null) {
            Text(
                title.uppercase(),
                color = ink.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontFamily = sans,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        ChipRow(
            glyph = DialGlyphs.CalendarDays, glyphLabel = "All day",
            chips = sorted.take(maxChips), overflow = sorted.size - minOf(sorted.size, maxChips),
            empty = "Nothing all day", dotFor = { if (onLight) DialGeometry.deepenColor(it.color) else DialGeometry.muteColor(it.color) },
            sans = sans, ink = ink, onItemTap = onItemTap, singleLine = singleLine, onMore = onMore,
        )
        ChipRow(
            glyph = DialGlyphs.CalendarClock, glyphLabel = "Deadlines",
            chips = deadlines.take(maxChips), overflow = deadlines.size - minOf(deadlines.size, maxChips),
            empty = "No deadlines this week", dotFor = { Color(0xFFFB7185) },
            sans = sans, ink = ink, onItemTap = onItemTap, singleLine = singleLine, onMore = onMore,
        )
    }
}

@Composable
private fun ChipRow(
    glyph: ImageVector,
    glyphLabel: String,
    chips: List<DialBlock>,
    overflow: Int,
    empty: String,
    dotFor: (DialBlock) -> Color,
    sans: FontFamily,
    ink: Color,
    onItemTap: (DialBlock) -> Unit,
    singleLine: Boolean = false,
    onMore: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            glyph, contentDescription = glyphLabel, tint = ink.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 3.dp).size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        if (chips.isEmpty()) {
            Text(empty, color = ink.copy(alpha = 0.35f), fontSize = 14.sp, fontFamily = sans, modifier = Modifier.padding(vertical = 2.dp))
            return@Row
        }
        if (singleLine) {
            OneLineChips(
                items = chips,
                extra = overflow,
                chip = { b -> Chip(b, dotFor, sans, ink, onItemTap) },
                more = { n -> MoreLabel(n, sans, ink, onMore) },
                modifier = Modifier.fillMaxWidth(),
            )
            return@Row
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (b in chips) {
                Row(
                    Modifier.clickable { onItemTap(b) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(dotFor(b), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        titleWithoutTags(b.title),
                        color = ink.copy(alpha = if (b.completed) 0.4f else 0.85f),
                        fontSize = 14.sp,
                        fontFamily = sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 260.dp),
                    )
                }
            }
            if (overflow > 0) {
                Text("+$overflow", color = ink.copy(alpha = 0.45f), fontSize = 12.sp, fontFamily = sans, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun Chip(b: DialBlock, dotFor: (DialBlock) -> Color, sans: FontFamily, ink: Color, onItemTap: (DialBlock) -> Unit) {
    Row(
        Modifier.clickable { onItemTap(b) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(dotFor(b), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            titleWithoutTags(b.title),
            color = ink.copy(alpha = if (b.completed) 0.4f else 0.85f),
            fontSize = 14.sp,
            fontFamily = sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
    }
}

@Composable
private fun MoreLabel(n: Int, sans: FontFamily, ink: Color, onMore: (() -> Unit)?) {
    Text(
        "+$n", color = ink.copy(alpha = 0.55f), fontSize = 12.sp, fontFamily = sans,
        modifier = (if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier).padding(vertical = 2.dp, horizontal = 2.dp),
    )
}

/**
 * Chips on exactly one line: as many as fit in order, then "+N" for every chip that did not
 * plus [extra] (those already past the pill's chip limit). The first chip always shows,
 * ellipsised by its own width cap, so a row is never only "+N".
 *
 * Each slot is handed its item, never an index into [items]: when a day with fewer chips
 * replaces one with more, the old slots past the new end are recomposed once, with the updated
 * [chip], before the layout drops them, and an index would read past the new list (the crash
 * paging the dial from a day with two all-day items to one with one).
 */
@Composable
private fun <T> OneLineChips(
    items: List<T>,
    extra: Int,
    chip: @Composable (T) -> Unit,
    more: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { constraints ->
        val gap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val count = items.size
        val placeables = items.mapIndexed { i, item -> subcompose("chip$i") { chip(item) }.first().measure(loose) }
        val widths = placeables.map { it.width }
        // Memoised: a slot may be subcomposed only once per measure pass.
        val moreCache = HashMap<Int, Int>()
        val moreWidths = { n: Int -> if (n <= 0) 0 else moreCache.getOrPut(n) { subcompose("more$n") { more(n) }.first().measure(loose).width } }
        val shown = OneLineFit.shown(widths, extra, width, gap, moreWidths)
        val hidden = count - shown + extra
        val morePlaceable = if (hidden > 0) subcompose("moreFinal") { more(hidden) }.first().measure(loose) else null
        val height = (placeables.take(shown).maxOfOrNull { it.height } ?: 0).coerceAtLeast(morePlaceable?.height ?: 0)
        layout(width, height) {
            var x = 0
            for (i in 0 until shown) {
                val p = placeables[i]
                p.place(x, (height - p.height) / 2)
                x += p.width + gap
            }
            morePlaceable?.place(x, (height - morePlaceable.height) / 2)
        }
    }
}

/** How many chips fit on one line beside the "+N" they leave. Pure, for [OneLineChips]. */
object OneLineFit {
    /**
     * The count of leading chips (of [widths]) that fit in [available] with [gap] between
     * pieces, leaving room for "+N" whenever anything is hidden ([moreWidth] of the hidden
     * count, 0 for none). At least one when there are any chips.
     */
    fun shown(widths: List<Int>, extra: Int, available: Int, gap: Int, moreWidth: (Int) -> Int): Int {
        if (widths.isEmpty()) return 0
        for (k in widths.size downTo 1) {
            val hidden = widths.size - k + extra
            val chipsWidth = widths.take(k).sum() + gap * (k - 1)
            val total = if (hidden > 0) chipsWidth + gap + moreWidth(hidden) else chipsWidth
            if (total <= available) return k
        }
        return 1
    }
}
