package app.glance.daydial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

/**
 * A corner readout on the dial face. Four slots: top-left, top-right, bottom-left,
 * bottom-right, in that order; a null slot stays empty (the next one does not slide in).
 */
@Immutable
sealed class DialComplication {
    abstract val label: String
    abstract val onClick: (() -> Unit)?

    /** A count dressed as a chronograph subdial: a recessed disc with an icon and the number. */
    data class Count(
        override val label: String,
        val count: Int,
        val icon: ImageVector = DialGlyphs.Inbox,
        /** Tint for the count when it needs attention (overdue); null keeps the default white. */
        val accent: Color? = null,
        override val onClick: (() -> Unit)? = null,
    ) : DialComplication()

    /** A progress ring with the value in the middle; turns green with a check mark at 100 %. */
    data class Ring(
        override val label: String,
        val fraction: Float,
        val value: String,
        override val onClick: (() -> Unit)? = null,
    ) : DialComplication()
}

/** Size tier by the dial's measured diameter in px, as in the reference. Null below 520 px. */
data class ComplicationTier(val key: String, val minDialPx: Int, val dot: Dp, val icon: Dp, val countSp: Float, val labelSp: Float)

val COMPLICATION_TIERS = listOf(
    ComplicationTier("sm", 520, 46.dp, 15.dp, 18f, 10f),
    ComplicationTier("md", 700, 58.dp, 18.dp, 24f, 11f),
    ComplicationTier("lg", 860, 74.dp, 22.dp, 30f, 12f),
)

fun complicationTier(dialPx: Float): ComplicationTier? {
    var found: ComplicationTier? = null
    for (t in COMPLICATION_TIERS) if (dialPx >= t.minDialPx) found = t
    return found
}

/** Slot centres as a fraction of the dial radius: 1.2 along each diagonal, clearing the hour labels at 0.96. */
private const val SLOT_R = 1.2f
private val SLOT_DIAGONAL = (1.0 / sqrt(2.0)).toFloat()

@Composable
internal fun DialComplicationsLayer(
    complications: List<DialComplication?>,
    dialPx: Float,
    sans: FontFamily,
    modifier: Modifier = Modifier,
) {
    val tier = complicationTier(dialPx) ?: return
    val density = LocalDensity.current
    val radiusDp = with(density) { (dialPx / 2f).toDp() }
    val d = radiusDp * SLOT_R * SLOT_DIAGONAL
    val slots = listOf(Pair(-d, -d), Pair(d, -d), Pair(-d, d), Pair(d, d))
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        for ((i, c) in complications.take(4).withIndex()) {
            if (c == null) continue
            val (dx, dy) = slots[i]
            // Each slot is centred on its diagonal point; the column's own size is unknown
            // up front, so offset by the dot radius and let the label hang below.
            Column(
                Modifier
                    .offset(x = dx, y = dy)
                    .widthIn(max = 136.dp)
                    .then(if (c.onClick != null) Modifier.clickable { c.onClick?.invoke() } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (c) {
                    is DialComplication.Count -> CountSlot(c, tier, sans)
                    is DialComplication.Ring -> RingSlot(c, tier, sans)
                }
                Text(
                    c.label.uppercase(),
                    color = DialColors.Ink.copy(alpha = 0.35f),
                    fontSize = tier.labelSp.sp,
                    letterSpacing = (tier.labelSp * 0.12f).sp,
                    fontFamily = sans,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun CountSlot(c: DialComplication.Count, tier: ComplicationTier, sans: FontFamily) {
    Column(
        Modifier
            .size(tier.dot)
            .border(1.dp, DialColors.Ink.copy(alpha = 0.1f), CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(c.icon, contentDescription = null, tint = DialColors.Ink.copy(alpha = 0.45f), modifier = Modifier.size(tier.icon))
        Text(
            c.count.toString(),
            color = c.accent ?: DialColors.Ink.copy(alpha = 0.85f),
            fontSize = (tier.countSp * 0.8f).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = sans,
            lineHeight = (tier.countSp * 0.8f).sp,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun RingSlot(c: DialComplication.Ring, tier: ComplicationTier, sans: FontFamily) {
    val met = c.fraction >= 1f
    Box(Modifier.size(tier.dot), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.38f
            val stroke = 3.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            drawArc(DialColors.Ink.copy(alpha = 0.1f), 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(stroke))
            drawArc(
                if (met) Color(0xFF22C55E).copy(alpha = 0.75f) else DialColors.Ink.copy(alpha = 0.55f),
                -90f, 360f * c.fraction.coerceIn(0f, 1f), false, tl, Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (met) {
            Icon(DialGlyphs.Check, contentDescription = "Complete", tint = Color(0xFF22C55E), modifier = Modifier.size(tier.dot * 0.34f))
        } else {
            Text(
                c.value,
                color = DialColors.Ink.copy(alpha = 0.85f),
                fontSize = (tier.countSp * 0.7f).sp,
                fontWeight = FontWeight.Medium,
                fontFamily = sans,
            )
        }
    }
}
