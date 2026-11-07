@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.glance.launcher.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.daydial.LaneInput
import app.glance.daydial.assignLanes
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskSource
import app.glance.launcher.ui.theme.GlanceTheme
import app.glance.launcher.ui.theme.hexColor
import java.time.LocalDate

private const val CELL_START_MIN = 7 * 60
private const val CELL_END_MIN = 21 * 60

/** dayGLANCE's month cell proportions (`constants/monthView.js`), resolved for a cell size in dp. */
internal data class CellMetrics(val header: Float, val inset: Float, val padY: Float, val marker: Float, val point: Float, val radius: Float, val cap: Float) {
    companion object {
        private fun rule(base: Float, ratio: Float, min: Float, max: Float) = (base * ratio).coerceIn(min, max)
        fun of(width: Float, height: Float): CellMetrics {
            val header = rule(height, 0.14f, 18f, 26f)
            return CellMetrics(
                header = header,
                inset = rule(width, 0.045f, 3f, 8f),
                padY = rule(height, 0.02f, 2f, 5f),
                marker = rule(header, 0.4f, 6f, 10f),
                point = rule(width, 0.03f, 3f, 5f),
                radius = rule(width, 0.015f, 2f, 3.5f),
                cap = rule(width, 0.018f, 2f, 3.5f),
            )
        }
    }
}

/**
 * A day cell as dayGLANCE draws it: a header row with the date number (and the all-day marks
 * beside it), then a mini vertical timeline 07:00 to 21:00 across the usable width. Items
 * that overlap in time split into up to three lanes; anything past that joins a "+N" badge.
 */
@Composable
fun DayCell(
    date: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, items: List<Task>, routineStarts: List<Int>,
    modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit,
    /** Width over height. 1 is the month grid's square; null fills whatever size the caller gives. */
    aspect: Float? = 1f,
) {
    val c = GlanceTheme.colors
    val timed = items.filter { it.startMin != null && !it.isAllDay }.sortedBy { it.startMin }
    val allDay = items.filter { it.isAllDay || it.startMin == null }
    val laneSlots = remember(timed) {
        assignLanes(timed.mapIndexed { i, t -> LaneInput(i, t.startMin!!, t.startMin!! + maxOf(t.durationMin, 15)) })
    }
    val hiddenCount = laneSlots.count { it.lane >= 3 }
    BoxWithConstraints(
        modifier
            .then(if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize())
            .padding(1.dp)
            .background(if (isToday) c.todayTint.compositeOverCard(c.card) else c.card)
            .border(1.dp, c.border)
            .then(if (isSelected) Modifier.border(2.dp, c.brand) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (inMonth || isSelected) 1f else 0.4f),
    ) {
        val m = CellMetrics.of(maxWidth.value, maxHeight.value)
        val badgeText = if (hiddenCount > 0) "+$hiddenCount" else null
        Canvas(Modifier.fillMaxSize().padding(start = m.inset.dp, end = m.inset.dp, top = (m.header + m.padY).dp, bottom = m.padY.dp)) {
            val h = size.height
            val gap = 1.dp.toPx()
            fun yOf(min: Int) = (min.coerceIn(CELL_START_MIN, CELL_END_MIN) - CELL_START_MIN).toFloat() / (CELL_END_MIN - CELL_START_MIN) * h
            // Routines: a thin teal rule at the start time, behind the lanes.
            for (rm in routineStarts) {
                if (rm < CELL_START_MIN || rm > CELL_END_MIN) continue
                val y = yOf(rm)
                drawLine(Color(0xFF14B8A6), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
            for ((i, t) in timed.withIndex()) {
                val slot = laneSlots[i]
                val lanes = minOf(slot.laneCount, 3)
                if (slot.lane >= lanes) continue
                val laneW = (size.width - (lanes - 1) * gap) / lanes
                val x = slot.lane * (laneW + gap)
                val minBand = 4.dp.toPx()
                // A band never leaves the timeline: late items are pulled up so the minimum height still fits.
                val y0 = minOf(yOf(t.startMin!!), h - minBand)
                val color = hexColor(t.colorHex).copy(alpha = if (t.completed) 0.5f else 1f)
                if (t.durationMin <= 0) {
                    // A moment with no length: a hollow rounded diamond, never a short band.
                    val p = m.point.dp.toPx()
                    val cx = x + laneW / 2f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, y0 - p); lineTo(cx + p, y0); lineTo(cx, y0 + p); lineTo(cx - p, y0); close()
                    }
                    drawPath(path, color, style = Stroke(1.5.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    continue
                }
                val y1 = minOf(h, maxOf(y0 + minBand, yOf(t.startMin!! + t.durationMin)))
                drawRoundRect(color, Offset(x, y0), Size(laneW, y1 - y0), androidx.compose.ui.geometry.CornerRadius(m.radius.dp.toPx()))
                if (t.source == TaskSource.CALENDAR) {
                    // Calendar events carry a darker left cap, flush with the band's edge.
                    drawRect(if (c.isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.25f), Offset(x, y0), Size(m.cap.dp.toPx(), y1 - y0))
                }
            }
        }
        if (badgeText != null) {
            Text(
                badgeText,
                color = if (c.isDark) Color(0xFF111827) else Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = m.inset.dp, bottom = m.padY.dp)
                    .background(if (c.isDark) Color(0xFFD1D5DB) else Color(0xFF57534E), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        // Header row: the date number, then the all-day marks beside it.
        Row(
            Modifier.height(m.header.dp).padding(start = m.inset.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = if (isToday) c.todayChipText else c.text,
                fontSize = (m.header * 0.5f).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .then(if (isToday) Modifier.background(c.todayChip, RoundedCornerShape(4.dp)) else Modifier)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
            for (t in allDay.take(4)) {
                Box(
                    Modifier
                        .size(m.marker.dp)
                        .alpha(if (t.completed) 0.5f else 1f)
                        .background(hexColor(t.colorHex), RoundedCornerShape(m.radius.dp)),
                )
            }
            if (allDay.size > 4) Text("+${allDay.size - 4}", fontSize = 8.sp, color = c.textSecondary)
        }
    }
}

internal fun Color.compositeOverCard(card: Color): Color {
    val a = alpha
    return Color(red * a + card.red * (1 - a), green * a + card.green * (1 - a), blue * a + card.blue * (1 - a))
}

