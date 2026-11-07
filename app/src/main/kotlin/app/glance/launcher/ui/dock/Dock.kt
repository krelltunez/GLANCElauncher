@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.glance.launcher.ui.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.apps.AppEntry
import app.glance.launcher.apps.Pin
import app.glance.launcher.apps.DockRecents
import app.glance.launcher.ui.GlanceIcons
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.common.AppIcon
import app.glance.launcher.ui.theme.GlanceTheme
import kotlinx.coroutines.delay

/**
 * The GLANCE taskbar: pinned apps on the left, recents on the right, visible under Home and
 * the planning screen. Tap launches; a long-press drag upward launches adjacent (split
 * screen). Recents are the apps last opened from the launcher, and with usage access (an
 * opt-in in Settings, never asked for here) the apps last used anywhere ([DockRecents]).
 */
@Composable
fun Dock(controller: LauncherController) {
    val c = GlanceTheme.colors
    val prefs = controller.graph.prefs
    val apps = controller.graph.apps
    val pins by prefs.dockPins.collectAsState()
    val showRecents by prefs.dockShowsRecents.collectAsState()
    val showLabels by prefs.dockShowsLabels.collectAsState()
    val allApps by apps.apps.collectAsState()
    val history by prefs.launchHistory.collectAsState()
    val hidden by prefs.hiddenApps.collectAsState()
    val pinned = pins.mapNotNull { key -> allApps.firstOrNull { it.key == key } }

    // A launch from here updates the history at once; use elsewhere is polled while it can be read.
    var usage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(showRecents) {
        while (showRecents) {
            usage = apps.usageLastUsed()
            delay(15_000)
        }
    }
    val recents = remember(showRecents, history, usage, allApps, pins, hidden) {
        if (showRecents) DockRecents.pick(history, usage, allApps, pins.toSet() + hidden, 6) else emptyList<AppEntry>()
    }

    // The dock sits above the system navigation gesture zone (navigationBarsPadding): a swipe
    // that starts in that zone belongs to Android's home gesture and never reaches us.
    var dragUp by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card.copy(alpha = 0.92f))
            .border(1.dp, c.border, RoundedCornerShape(0.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragUp = 0f },
                    onVerticalDrag = { change, dy -> change.consume(); dragUp += dy },
                    onDragEnd = { if (dragUp < -48f) controller.openDrawer(); dragUp = 0f },
                    onDragCancel = { dragUp = 0f },
                )
            }
            .navigationBarsPadding(),
    ) {
        // Drag handle: the affordance for "swipe up for all apps"; tapping it opens the drawer too.
        Box(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { controller.openDrawer() })
                .padding(top = 6.dp, bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).background(c.textMuted.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (entry in pinned) DockItem(entry, controller, showLabels)
                DockAddButton { controller.openDrawer(LauncherController.PickMode.DOCK) }
            }
            if (showRecents) {
                Box(Modifier.width(1.dp).height(40.dp).background(c.border))
                Spacer(Modifier.width(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (entry in recents) DockItem(entry, controller, showLabels, recent = true)
                    if (recents.isEmpty()) Text("No recent apps yet", color = c.textMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DockItem(entry: AppEntry, controller: LauncherController, showLabel: Boolean, recent: Boolean = false) {
    val apps = controller.graph.apps
    // As Home's pinned row (docs/decisions.md, decision 53): a long-press opens the app's menu, and carrying
    // on into a drag upward closes it and opens the app in split screen. The long-press is the
    // click handler's own, so releasing a held press does not also launch the app.
    var dragUp by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .width(if (showLabel) 56.dp else 60.dp)
            .height(64.dp)
            .combinedClickable(onClick = { apps.launch(entry) }, onLongClick = { controller.appMenuFor = Pin.App(entry) })
            .pointerInput(entry.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragUp = 0f },
                    onDrag = { change, amount -> change.consume(); dragUp += amount.y },
                    onDragEnd = {
                        if (dragUp < -80f) {
                            controller.appMenuFor = null
                            apps.launch(entry, adjacent = true)
                        }
                        dragUp = 0f
                    },
                    onDragCancel = { dragUp = 0f },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The bar keeps its height either way; without labels the icon takes the room.
        AppIcon(entry, apps, size = if (showLabel) 40.dp else 54.dp, notificationDot = true)
        if (showLabel) {
            Text(
                entry.label,
                color = GlanceTheme.colors.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DockAddButton(onClick: () -> Unit) {
    val c = GlanceTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(GlanceIcons.Pin, contentDescription = "Pin an app to the dock", tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}
