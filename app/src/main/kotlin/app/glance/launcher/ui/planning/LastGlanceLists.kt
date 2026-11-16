package app.glance.launcher.ui.planning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.ui.common.GlanceCard
import app.glance.launcher.ui.theme.GlanceTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The parts of lastGLANCE's planning page that are not rows (D82): the heatmap that heads it,
 * and the folding headers of its full lists. What can be worked out without drawing is here as
 * plain functions, tested on the JVM.
 */
object LastGlanceLists {
    /** The app's heatmap runs 52 weeks on a wide screen; the launcher draws as many as fit, up to that. */
    const val MAX_WEEKS = 52

    /** The app's cell and the gap between cells, in dp (9 px and 3 px there). */
    const val CELL = 9f
    const val GAP = 3f

    /** How many whole week columns fit [width] dp at the app's cell size, between 1 and [MAX_WEEKS]. */
    fun weeksThatFit(width: Float): Int = ((width + GAP) / (CELL + GAP)).toInt().coerceIn(1, MAX_WEEKS)

    /**
     * The heatmap's columns, oldest first: [weeks] weeks of seven days from [firstDay], the last
     * of them the week [today] is in (`buildHeaderHeatmap`). Days after today are in it, and are
     * drawn as nothing.
     */
    fun heatWeeks(today: LocalDate, firstDay: DayOfWeek, weeks: Int): List<List<LocalDate>> {
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(firstDay))
        return (weeks - 1 downTo 0).map { back -> (0L..6L).map { thisWeek.minusWeeks(back.toLong()).plusDays(it) } }
    }

    /** The app's five steps (`heatCellColor`): none, one, two, three or four, five and more. */
    fun heatLevel(count: Int): Int = when {
        count <= 0 -> 0
        count == 1 -> 1
        count == 2 -> 2
        count <= 4 -> 3
        else -> 4
    }

    /** [items] under each of their [key]s, in the order the keys first appear: the app's own order of categories and circles. */
    fun <T> grouped(items: List<T>, key: (T) -> String): List<Pair<String, List<T>>> {
        val out = LinkedHashMap<String, MutableList<T>>()
        for (item in items) out.getOrPut(key(item)) { ArrayList() }.add(item)
        return out.map { it.key to it.value }
    }

    /** What a folded category or circle is remembered under (`LauncherPrefs.lastGlanceFolded`). */
    fun choresKey(category: String): String = "chores:$category"
    fun peopleKey(circle: String): String = "people:$circle"
}

// The app's greens, least to most. On a dark page the brightest is the busiest day, as in the
// app; on a light one the order is turned round, so that the busiest day is still the strongest.
private val HeatDark = listOf(Color(0xFF166534), Color(0xFF16A34A), Color(0xFF22C55E), Color(0xFF4ADE80))
private val HeatLight = HeatDark.asReversed()

/**
 * lastGLANCE's header heatmap: a column a week, a cell a day, greener the more chores were
 * logged that day. As many of the latest weeks as the width holds, at the app's cell size and
 * then stretched to fill it. A tap opens lastGLANCE. Nothing is drawn when the app has sent no
 * counts (a lastGLANCE from before the launcher asked for them).
 */
@Composable
internal fun ChoreHeatmap(activity: Map<LocalDate, Int>, today: LocalDate, onOpen: () -> Unit) {
    val c = GlanceTheme.colors
    val ramp = if (c.isDark) HeatDark else HeatLight
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    GlanceCard(Modifier.fillMaxWidth().clickable(onClickLabel = "Open lastGLANCE", role = Role.Button, onClick = onOpen), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val count = LastGlanceLists.weeksThatFit(maxWidth.value)
            val weeks = LastGlanceLists.heatWeeks(today, firstDay, count)
            val gap = LastGlanceLists.GAP.dp
            val cell = (maxWidth - gap * (count - 1)) / count
            val done = weeks.sumOf { week -> week.sumOf { activity[it] ?: 0 } }
            Canvas(
                Modifier.fillMaxWidth().height(cell * 7 + gap * 6)
                    .semantics { contentDescription = "$done chores done in the last $count weeks" },
            ) {
                val side = cell.toPx()
                val step = side + gap.toPx()
                val round = CornerRadius(2.dp.toPx())
                weeks.forEachIndexed { w, week ->
                    week.forEachIndexed { d, day ->
                        if (day.isAfter(today)) return@forEachIndexed
                        val level = LastGlanceLists.heatLevel(activity[day] ?: 0)
                        drawRoundRect(if (level == 0) c.track else ramp[level - 1], Offset(w * step, d * step), Size(side, side), round)
                    }
                }
            }
        }
    }
}

/**
 * The header of a category or a circle in the full lists: its name and how many it holds, and a
 * tap folds it away or opens it. Folded, it shows a dot in [dot] when something inside is due,
 * as the app's collapsed category does, so what is hidden is not missed.
 */
@Composable
internal fun FoldHeader(title: String, count: Int, folded: Boolean, dot: Color?, onToggle: () -> Unit) {
    val c = GlanceTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = if (folded) "Show $title" else "Hide $title", role = Role.Button, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (folded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("${title.uppercase()} · $count", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (folded && dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
    }
}
