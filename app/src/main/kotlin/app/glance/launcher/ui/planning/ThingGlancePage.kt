package app.glance.launcher.ui.planning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.data.ThingEvent
import app.glance.launcher.data.ThingGlance
import app.glance.launcher.data.ThingGlanceData
import app.glance.launcher.data.ThingItem
import app.glance.launcher.data.ThingMonth
import app.glance.launcher.data.ThingTone
import app.glance.launcher.data.provider.GlanceApp
import app.glance.launcher.intents.GlanceIntents
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.common.ThingGlanceSky
import app.glance.launcher.ui.common.ThingGlanceSkyDark
import app.glance.launcher.ui.theme.GlanceTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * thingGLANCE's planning page (D81): the app's own Glance screen, section for section. The
 * headline panel with its four figures and the year strip, what needs a decision, the fleet, and
 * plan charges month by month. Its parts are ports of the app's (`GlanceView.tsx`, `parts.tsx`,
 * `ui.tsx`): the T-minus badge, the life ring and bar, the tones. Surfaces and text stay the
 * launcher's, so the page sits with the other two; the accent and the tones are thingGLANCE's.
 *
 * Every word on a date is the app's (`title`, `detail`); the headings are the app's English.
 * A tap opens the app at that date, thing or view. "Done with this" and "Send to dayGLANCE" are
 * the two writes, and a cleared date can be put back from "Recently cleared".
 */
@Composable
internal fun ThingGlancePage(controller: LauncherController, modifier: Modifier) {
    val data by controller.repository.thingGlance.collectAsState()
    val today = controller.today
    PageFrame(GlanceApp.THINGGLANCE, controller, modifier) { landscape, compact ->
        val d = data
        when {
            // The page is on its way out (the app was removed): nothing to draw for a frame or two.
            d == null -> OneColumn(GlanceApp.THINGGLANCE, controller, compact, rememberScrollState()) {}
            !d.hasData -> OneColumn(GlanceApp.THINGGLANCE, controller, compact, rememberScrollState()) {
                EmptyPanel("Nothing shared yet", "Open thingGLANCE once and its dates show up here.", "Open thingGLANCE") { GlanceIntents.launchThingGlance(it) }
            }
            d.counts.things == 0 && d.counts.plans == 0 && d.attention.isEmpty() -> OneColumn(GlanceApp.THINGGLANCE, controller, compact, rememberScrollState()) {
                EmptyPanel(
                    "Nothing on the radar yet",
                    "Add your first thing, a laptop, a phone, anything with a warranty, a plan or a shelf life, and thingGLANCE starts watching its dates for you.",
                    "Add a thing",
                ) { GlanceIntents.addThing(it) }
            }
            landscape -> TwoColumns(
                GlanceApp.THINGGLANCE, controller, rememberScrollState(),
                left = {
                    HeadlinePanel(d, today, compact)
                    Spacer(Modifier.height(16.dp))
                    QueuePanel(d, today, controller, compact)
                },
                right = {
                    FleetPanel(d, today, compact)
                    Spacer(Modifier.height(16.dp))
                    ChargesPanel(d, compact)
                },
            )
            else -> OneColumn(GlanceApp.THINGGLANCE, controller, compact, rememberScrollState()) {
                HeadlinePanel(d, today, compact)
                Spacer(Modifier.height(16.dp))
                QueuePanel(d, today, controller, compact)
                Spacer(Modifier.height(16.dp))
                FleetPanel(d, today, compact)
                Spacer(Modifier.height(16.dp))
                ChargesPanel(d, compact)
            }
        }
    }
}

// region thingGLANCE's colours

/** thingGLANCE's accent and tones (its `index.css`), light and dark. */
@Immutable
data class ThingColors(val accent: Color, val accentInk: Color, val ok: Color, val warn: Color, val bad: Color, val sub: Color, val line: Color) {
    fun of(tone: ThingTone, dim: Color): Color = when (tone) {
        ThingTone.OK -> ok
        ThingTone.ACCENT -> accent
        ThingTone.WARN -> warn
        ThingTone.BAD -> bad
        ThingTone.SUB -> sub
        ThingTone.DIM -> dim
    }
}

private val LightThing = ThingColors(ThingGlanceSky, Color.White, Color(0xFF059669), Color(0xFFCA6C04), Color(0xFFE11D48), Color(0xFF7C3AED), Color(0xFF14325F))
private val DarkThing = ThingColors(ThingGlanceSkyDark, Color(0xFF031020), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFFB7185), Color(0xFFA78BFA), Color(0xFF94C5FF))

@Composable
@ReadOnlyComposable
fun thingColors(): ThingColors = if (GlanceTheme.colors.isDark) DarkThing else LightThing

/** A tone's colour; the dim tone is the launcher's secondary text. */
@Composable
@ReadOnlyComposable
private fun toneColor(tone: ThingTone): Color = thingColors().of(tone, GlanceTheme.colors.textSecondary)

/** The app's "soft" treatment of a tone: a tinted fill, a tinted hairline, the tone as text. */
@Composable
private fun Modifier.soft(tone: ThingTone, shape: RoundedCornerShape): Modifier {
    val t = thingColors()
    val color = toneColor(tone)
    return if (tone == ThingTone.DIM) background(t.line.copy(alpha = 0.05f), shape).border(1.dp, t.line.copy(alpha = 0.15f), shape)
    else background(color.copy(alpha = 0.10f), shape).border(1.dp, color.copy(alpha = if (tone == ThingTone.BAD) 0.30f else 0.25f), shape)
}

// endregion

// region the app's small parts

/** The app's `.panel`: a 16 dp card, and with [hud] its corner brackets in the accent. */
@Composable
private fun Panel(modifier: Modifier = Modifier, hud: Boolean = false, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = GlanceTheme.colors
    val accent = thingColors().accent.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(c.card, shape)
            .border(1.dp, c.border, shape)
            .then(if (hud) Modifier.drawBehind {
                // Top left and bottom right, following the card's own corners.
                val d = 32.dp.toPx()
                val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(accent, 180f, 90f, false, Offset.Zero, Size(d, d), style = stroke)
                drawArc(accent, 0f, 90f, false, Offset(size.width - d, size.height - d), Size(d, d), style = stroke)
            } else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The app's mono micro-label: its instrument-panel voice. */
@Composable
private fun MicroLabel(text: String, modifier: Modifier = Modifier, size: Float = 10f) {
    Text(
        text.uppercase(), color = GlanceTheme.colors.textMuted, fontFamily = FontFamily.Monospace, fontSize = size.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp, maxLines = 1, softWrap = false, modifier = modifier,
    )
}

/** The countdown badge: T-12d before a date, T+3d after it. */
@Composable
private fun TBadge(days: Int, tone: ThingTone) {
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.height(24.dp).widthIn(min = 58.dp).soft(tone, shape).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(ThingGlance.tMinus(days), color = toneColor(tone), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Chip(text: String, tone: ThingTone, dot: Boolean = false, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(50)
    val color = toneColor(tone)
    Row(Modifier.height(24.dp).soft(tone, shape).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dot) Box(Modifier.size(6.dp).background(if (tone == ThingTone.DIM) GlanceTheme.colors.textMuted else color, CircleShape))
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The life-left ring: [value] is 0..1, or null for "no dates yet" (a dashed track). */
@Composable
private fun Ring(value: Float?, tone: ThingTone, size: Dp = 40.dp, stroke: Dp = 3.5.dp, content: @Composable () -> Unit) {
    val track = thingColors().line.copy(alpha = 0.10f)
    val color = if (tone == ThingTone.DIM) GlanceTheme.colors.textMuted else toneColor(tone)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = Offset(w / 2, w / 2)
            val box = Size(this.size.width - w, this.size.height - w)
            drawArc(
                track, 0f, 360f, false, inset, box,
                style = Stroke(w, pathEffect = if (value == null) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())) else null),
            )
            if (value != null) drawArc(color, -90f, 360f * value.coerceIn(0.02f, 1f), false, inset, box, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/**
 * A thing's life in one line: purchase on the left, the end of its window on the right, filled
 * up to today, with ticks where coverage and support end.
 */
@Composable
private fun LifeBar(thing: ThingItem, today: LocalDate, tone: ThingTone) {
    val c = GlanceTheme.colors
    val t = thingColors()
    val track = t.line.copy(alpha = 0.10f)
    val color = if (tone == ThingTone.DIM) c.textMuted else toneColor(tone)
    val now = ThingGlance.lifePosition(thing, today)
    val start = thing.lifeStart
    val end = thing.lifeEnd
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val h = 4.dp.toPx()
        val y = (size.height - h) / 2
        val round = androidx.compose.ui.geometry.CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), round)
        if (now == null || start == null || end == null) return@Canvas
        drawRoundRect(color.copy(alpha = 0.7f), Offset(0f, y), Size(size.width * now, h), round)
        val total = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1).toFloat()
        fun pos(d: LocalDate) = (ChronoUnit.DAYS.between(start, d) / total).coerceIn(0f, 1f) * size.width
        fun tick(at: LocalDate, tickColor: Color) = drawRoundRect(tickColor, Offset(pos(at) - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
        thing.coverageEnd?.takeIf { it < end }?.let { tick(it, t.ok) }
        thing.supportEnd?.takeIf { thing.lifeEndReason == "plan" && it > start }?.let { tick(it, t.accent) }
        // Today: a dot ringed in the card's colour, so it reads over the fill.
        val x = (size.width * now).coerceIn(4.dp.toPx(), size.width - 4.dp.toPx())
        drawCircle(c.card, 6.dp.toPx(), Offset(x, size.height / 2))
        drawCircle(color, 4.dp.toPx(), Offset(x, size.height / 2))
    }
}

/** The app's square icon button: 32 dp, a hairline, the icon in the secondary text colour. */
@Composable
private fun IconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = GlanceTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(32.dp).clip(shape).background(c.cardSubtle, shape).border(1.dp, c.border, shape).clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = c.textSecondary, modifier = Modifier.size(15.dp))
    }
}

/** A panel's title, the line under it, and what sits at its right. */
@Composable
private fun PanelHeader(title: String, hint: String, trailing: @Composable () -> Unit = {}) {
    val c = GlanceTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** "All things →": the app's link from a panel into one of its views. */
@Composable
private fun ViewLink(label: String, view: String) {
    val context = LocalContext.current
    val accent = thingColors().accent
    Row(Modifier.clickable(role = Role.Button) { GlanceIntents.openThingsView(context, view) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = accent, modifier = Modifier.size(12.dp))
    }
}

/** The app's `Empty`, as a whole panel with one thing to do. */
@Composable
private fun EmptyPanel(title: String, body: String, action: String, onAction: (android.content.Context) -> Unit) {
    val c = GlanceTheme.colors
    val t = thingColors()
    val context = LocalContext.current
    Panel(hud = true, padding = 24.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).widthIn(max = 380.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                action, color = t.accentInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(t.accent).clickable(role = Role.Button) { onAction(context) }.padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

// endregion

// region the headline panel

@Composable
private fun HeadlinePanel(d: ThingGlanceData, today: LocalDate, compact: Boolean) {
    val c = GlanceTheme.colors
    val t = thingColors()
    val waiting = d.counts.attention
    // Counted from the dates in view, so it follows the calendar past midnight and a tick made here.
    val urgent = d.attention.count { ThingGlance.severityTone(ThingGlance.severity(it, ThingGlance.daysUntil(it.date, today))) == ThingTone.BAD }
    Panel(hud = true, padding = if (compact) 18.dp else 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (urgent > 0) t.bad else if (waiting > 0) t.warn else t.ok, CircleShape))
            Spacer(Modifier.width(8.dp))
            MicroLabel("${today.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))} · ${ThingGlance.statusWord(urgent, waiting)}")
        }
        Text(
            ThingGlance.headline(waiting), color = c.text, fontSize = if (compact) 26.sp else 30.sp, lineHeight = if (compact) 30.sp else 35.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 10.dp),
        )
        Text(ThingGlance.nextUp(d.attention.firstOrNull(), today, d.counts), color = c.textSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(18.dp))
        val stats = listOf(
            Triple("In service", d.counts.things.toString(), "${d.counts.covered} under coverage"),
            Triple("Plans / month", money(d, d.monthlyPlans), "${ThingGlance.money(d.monthlyPlans * 12, d.currency, d.locale, compact = true)} a year"),
            Triple("Next ${d.chargesDays} days", money(d, d.chargesTotal), if (d.chargesCount == 1) "1 charge coming" else "${d.chargesCount} charges coming"),
            Triple("Upgrade fund", money(d, d.fundMonthly), "monthly target"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in stats.chunked(if (compact) 2 else 4)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((label, value, sub) in row) Stat(label, value, sub, Modifier.weight(1f))
                }
            }
        }
        YearStrip(d, today, compact)
    }
}

private fun money(d: ThingGlanceData, amount: Double): String = ThingGlance.money(amount, d.currency, d.locale)

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    val c = GlanceTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.background(c.cardSubtle, shape).border(1.dp, c.border, shape).padding(horizontal = 14.dp, vertical = 12.dp)) {
        MicroLabel(label)
        Text(value, color = c.text, fontFamily = FontFamily.Monospace, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(sub, color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The kinds named under the strip, in the order their lanes run. */
private val STRIP_LEGEND = listOf("coverage-end", "support-end", "replace", "renewal", "trial-end")

/** Twelve months at a glance: one pip per date, in a lane per kind, and a line for today. */
@Composable
private fun YearStrip(d: ThingGlanceData, today: LocalDate, compact: Boolean) {
    val c = GlanceTheme.colors
    val t = thingColors()
    val context = LocalContext.current
    val start = today.withDayOfMonth(1)
    val end = start.plusMonths(12)
    val total = ChronoUnit.DAYS.between(start, end).toFloat()
    fun pos(date: LocalDate) = ChronoUnit.DAYS.between(start, date) / total
    val pips = d.allEvents.filter { !it.date.isBefore(start) && it.date.isBefore(end) }
    val waiting = d.attention.mapTo(HashSet()) { it.key }
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MicroLabel("Next 12 months", Modifier.weight(1f))
        ViewLink("Open horizon", "horizon")
    }
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(68.dp).clip(shape).background(c.cardSubtle, shape).border(1.dp, c.border, shape)
            .clickable(onClickLabel = "Open horizon", role = Role.Button) { GlanceIntents.openThingsView(context, "horizon") },
    ) {
        val width = maxWidth
        val grid = t.line.copy(alpha = 0.08f)
        for (i in 0 until 12) {
            val month = start.plusMonths(i.toLong())
            // The year where it changes, and on the first month when there is room for it.
            val pattern = if ((month.monthValue == 1 || i == 0) && !compact) "MMM yy" else "MMM"
            Box(Modifier.offset(x = width * pos(month)).fillMaxHeight().drawBehind { if (i > 0) drawLine(grid, Offset.Zero, Offset(0f, size.height), 1.dp.toPx()) }) {
                MicroLabel(month.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())), Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 4.dp), size = if (compact) 7.5f else 9f)
            }
        }
        // Today.
        Box(Modifier.offset(x = width * pos(today)).width(1.dp).fillMaxHeight().background(t.accent.copy(alpha = 0.7f)))
        Box(Modifier.offset(x = width * pos(today) - 3.dp).size(7.dp).background(t.accent, CircleShape))
        for (e in pips) {
            val color = toneColor(ThingGlance.eventTone(e.type))
            Box(
                Modifier.offset(x = width * pos(e.date) - 6.dp, y = (7 + ThingGlance.eventLane(e.type) * 13).dp)
                    // Ringed in the strip's own colour; a date that is waiting on a decision is ringed in its tone too.
                    .size(12.dp).background(if (e.key in waiting) color.copy(alpha = 0.35f) else c.card, CircleShape).padding(2.dp).background(color, CircleShape),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
        for (kind in STRIP_LEGEND) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(toneColor(ThingGlance.eventTone(kind)), CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(ThingGlance.eventKind(kind), color = c.textMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

// endregion

// region needs a decision

@Composable
private fun QueuePanel(d: ThingGlanceData, today: LocalDate, controller: LauncherController, compact: Boolean) {
    val c = GlanceTheme.colors
    // Only what is still cleared: a date put back from the app itself drops off this list.
    val shown = d.attention.mapTo(HashSet()) { it.key }
    val cleared = controller.thingCleared.filterNot { it.key in shown }
    Panel {
        PanelHeader("Needs a decision", if (d.canSend) "Clear each one when you have decided, or hand it to dayGLANCE." else "Clear each one when you have decided.") {
            if (d.counts.attention > 0) {
                val shape = RoundedCornerShape(6.dp)
                Text(
                    d.counts.attention.toString(), color = c.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.background(c.cardSubtle, shape).border(1.dp, c.border, shape).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (d.attention.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Queue is empty", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "When a warranty is about to lapse, a plan is about to renew or a device nears the end of its support, it shows up here.",
                    color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).widthIn(max = 360.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (e in d.attention) EventRow(e, d, today, controller, compact)
            }
            // The app lists more than the launcher is given: say so rather than end short.
            if (d.counts.attention > d.attention.size) {
                Spacer(Modifier.height(8.dp))
                Text("+${d.counts.attention - d.attention.size} more in thingGLANCE", color = c.textSecondary, fontSize = 12.sp)
            }
        }
        if (cleared.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            MicroLabel("Recently cleared", Modifier.padding(top = 12.dp, bottom = 4.dp))
            for (e in cleared) ClearedRow(e, controller)
        }
    }
}

/** The app's `EventRow`: countdown, the date in its own words, and what can be done about it. */
@Composable
private fun EventRow(e: ThingEvent, d: ThingGlanceData, today: LocalDate, controller: LauncherController, compact: Boolean) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val days = ThingGlance.daysUntil(e.date, today)
    val tone = ThingGlance.severityTone(ThingGlance.severity(e, days))
    val shape = RoundedCornerShape(12.dp)
    val whenText = e.date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    val amount = e.amount?.takeIf { e.type != "replace" }?.let { " · ${money(d, it)}" }.orEmpty()
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.cardSubtle, shape).border(1.dp, c.border, shape)
            .clickable(onClickLabel = "Open in thingGLANCE", role = Role.Button) { GlanceIntents.openThingGlance(context, e.link.ifEmpty { "thingglance://queue" }) }
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TBadge(days, tone)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.detail} · $whenText$amount", color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (e.sent) {
            // As in the app: the chip where there is room, and no second "send" either way.
            if (!compact) { Spacer(Modifier.width(8.dp)); Chip("In dayGLANCE", ThingTone.ACCENT, icon = Icons.AutoMirrored.Filled.Send) }
        } else if (d.canSend) {
            Spacer(Modifier.width(6.dp))
            IconButton(Icons.AutoMirrored.Filled.Send, "Send to dayGLANCE as a task") { controller.thingSend(e) }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(Icons.Filled.Check, "Done with this") { controller.thingDone(e) }
    }
}

/** The app's `DecidedRow`: struck through, with the way back. */
@Composable
private fun ClearedRow(e: ThingEvent, controller: LauncherController) {
    val c = GlanceTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, null, tint = thingColors().ok, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "${e.title} · ${e.detail}", color = c.textMuted, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text("Restore", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.clickable(role = Role.Button) { controller.thingRestore(e) }.padding(horizontal = 8.dp, vertical = 8.dp))
    }
}

// endregion

// region the fleet

/** As the app's Fleet card: the things closest to the end of their window, and how many more there are. */
private const val FLEET_SHOWN = 7

@Composable
private fun FleetPanel(d: ThingGlanceData, today: LocalDate, compact: Boolean) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val fleet = d.fleet
    Panel {
        PanelHeader("Fleet", "Closest to the end of its window first.") { ViewLink("All things", "things") }
        if (fleet.isEmpty()) Text("Nothing in service yet.", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (thing in fleet.take(FLEET_SHOWN)) {
                val tone = ThingGlance.verdictTone(thing.verdict)
                val remaining = ThingGlance.remaining(thing, today, d.builtFor)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Open in thingGLANCE", role = Role.Button) { GlanceIntents.openThing(context, thing.id) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ring(remaining, tone) {
                        Text(
                            remaining?.let { (it * 100).roundToInt().toString() } ?: "–",
                            color = if (tone == ThingTone.DIM) c.textSecondary else toneColor(tone), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(thing.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        LifeBar(thing, today, tone)
                    }
                    // The verdict in words where there is room, as the app keeps it for wider screens.
                    if (!compact) { Spacer(Modifier.width(12.dp)); Chip(ThingGlance.verdictLabel(thing.verdict, d.verdictLabels), tone, dot = true) }
                    thing.lifeEnd?.let { Spacer(Modifier.width(12.dp)); TBadge(ThingGlance.daysUntil(it, today), tone) }
                }
            }
        }
        if (fleet.size > FLEET_SHOWN) {
            Text(
                "+${fleet.size - FLEET_SHOWN} more in Things", color = c.textSecondary, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { GlanceIntents.openThingsView(context, "things") }.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

// endregion

// region plan charges

@Composable
private fun ChargesPanel(d: ThingGlanceData, compact: Boolean) {
    Panel {
        PanelHeader("Plan charges, month by month", "What will actually leave your account. Annual renewals are the spikes.") { ViewLink("Plans", "plans") }
        if (d.months.isEmpty()) {
            Text("Add a plan to see what it will charge.", color = GlanceTheme.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            ChargesChart(d, d.months, compact)
        }
    }
}

/**
 * The app's `ChargesChart`: one bar per month with what leaves the account written above it. On
 * a phone there is no room to label twelve bars, so only the spikes (annual renewals standing
 * clear of a normal month) keep theirs, or this month's when the year is flat.
 */
@Composable
private fun ChargesChart(d: ThingGlanceData, months: List<ThingMonth>, compact: Boolean) {
    val c = GlanceTheme.colors
    val sub = thingColors().sub
    val max = months.maxOf { it.total }.coerceAtLeast(1.0)
    val usual = months.map { it.total }.sorted().let { it.getOrElse(it.size / 2) { 0.0 } }
    fun spike(total: Double) = total > 0 && total >= usual * 1.4 && total > usual
    val flat = months.none { spike(it.total) }
    Row(Modifier.fillMaxWidth().height(176.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        months.forEachIndexed { i, m ->
            val keep = spike(m.total) || (flat && i == 0)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                // The bar scales into what is left once the figure above it and the month below have
                // their room, whatever the font size makes of those.
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (m.total > 0 && (keep || !compact)) {
                        Text(
                            ThingGlance.money(Math.round(m.total).toDouble(), d.currency, d.locale), color = if (spike(m.total)) c.textSecondary else c.textMuted,
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                        )
                        Spacer(Modifier.height(6.dp))
                    } else {
                        // The room a figure would take, so every bar is measured against the same height.
                        Text("", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    val share = (m.total / max).toFloat().coerceIn(0f, 1f)
                    val bar = Modifier.fillMaxWidth().background(if (i == 0) sub else sub.copy(alpha = 0.35f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    if (share > 0.03f) {
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) { Box(bar.fillMaxHeight(share)) }
                    } else {
                        Spacer(Modifier.weight(1f))
                        Box(bar.height(3.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                MicroLabel(m.month.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())), size = if (compact) 7.5f else 9f)
            }
        }
    }
}

// endregion
