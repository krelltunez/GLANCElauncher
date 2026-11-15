package app.glance.launcher.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.glance.launcher.data.provider.GlanceApp
import app.glance.launcher.ui.common.LastGlanceGreen
import app.glance.launcher.ui.theme.GlanceTheme

/** The colour that says whose page it is: the app's own, as on its wordmark, its dot and its "+". */
@Composable
@ReadOnlyComposable
fun planningColor(app: GlanceApp): Color = when (app) {
    GlanceApp.DAYGLANCE -> GlanceTheme.colors.brand
    GlanceApp.LASTGLANCE -> LastGlanceGreen
    GlanceApp.THINGGLANCE -> thingColors().accent
}

// The pill is one 24 dp cell per app (the tap target, with an 8 dp dot in it) and sits
// PageIndicatorBottom above the foot of the pager, so its top edge is 32 dp up.
private val DotCell = 24.dp
private val Dot = 8.dp

/** The gap the root leaves under the pill. */
val PageIndicatorBottom = 8.dp

/**
 * The blank foot each planning column ends with, so its last row scrolls clear of what floats
 * over the page and can be read and tapped: the "+" at the bottom end (52 dp, 16 dp in, so its
 * top edge is 68 dp up) and this pill (32 dp up). With the column's own 8 dp of bottom padding
 * that is 80 dp, 12 more than the "+".
 */
val PageFootReserve = 72.dp

/**
 * How far the root lifts the toast while a planning page is in front, on top of the navigation
 * bar's inset: the toast otherwise lands on the pill.
 */
val PageIndicatorToastLift = 32.dp

/**
 * Which planning page is in front (D80): one dot per app in the app's colour ([planningColor]),
 * in the order the pages sit on screen ([PlanningPages.leftToRight]), the one in front filled
 * and the others outlined. Tapping a dot goes to its page. `LauncherRoot` draws it over the
 * pager, fixed while the pages move under it, and fades it out towards Home, which has no dot:
 * Home is where the pill is not.
 */
@Composable
fun PageIndicator(apps: List<GlanceApp>, current: GlanceApp?, onPick: (GlanceApp) -> Unit, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(c.card.copy(alpha = 0.92f), shape).border(1.dp, c.border, shape).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (app in PlanningPages.leftToRight(apps)) {
            val color = planningColor(app)
            val name = PlanningPages.name(app)
            val front = app == current
            Box(
                Modifier
                    .size(DotCell)
                    .clickable(onClickLabel = "Show $name planning", role = Role.Tab) { onPick(app) }
                    .semantics { contentDescription = "$name planning"; selected = front },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(Dot).then(if (front) Modifier.background(color, CircleShape) else Modifier.border(1.5.dp, color, CircleShape)))
            }
        }
    }
}
