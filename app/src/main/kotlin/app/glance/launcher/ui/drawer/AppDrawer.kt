@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.glance.launcher.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.apps.AppEntry
import app.glance.launcher.ui.GlanceIcons
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.apps.Pin
import app.glance.launcher.ui.common.AppIcon
import app.glance.launcher.ui.common.AppMenu
import app.glance.launcher.ui.theme.GlanceTheme

/**
 * Alphabetical, searchable app drawer. Long-press an app to hide it; the "hidden" toggle
 * shows hidden apps dimmed so they can be brought back. In pick mode a tap pins instead of
 * launching.
 *
 * Four columns on a phone (an adaptive grid on a tablet), the four apps most recently opened
 * from the launcher in a row above the alphabetical list while nothing is typed, and a labels
 * switch in the header (also in Settings) for an icons-only grid.
 *
 * Swipe down on the header, or on the grid once it is at the top, to close it
 * ([DrawerPullToClose]): the drawer follows the finger part of the way and closes on release
 * past the threshold or on a flick down; short of that it springs back.
 */
@Composable
fun AppDrawer(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val apps = controller.graph.apps
    val prefs = controller.graph.prefs
    val all by apps.apps.collectAsState()
    val hidden by prefs.hiddenApps.collectAsState()
    val homePins by prefs.homePins.collectAsState()
    val dockPins by prefs.dockPins.collectAsState()
    val showLabels by prefs.drawerShowsLabels.collectAsState()
    val history by prefs.launchHistory.collectAsState()
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val pick = controller.drawerPickMode

    val visible = all.filter { (showHidden || it.key !in hidden) && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }
    val byKey = remember(all) { all.associateBy { it.key } }
    val recent = remember(history, byKey, hidden, query) {
        if (query.isNotBlank()) emptyList()
        else history.recent(RECENT_COUNT) { it in byKey && it !in hidden }.mapNotNull { byKey[it] }
    }
    val phone = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

    val close = { controller.drawerOpen = false; controller.drawerPickMode = null }
    val density = LocalDensity.current
    val pull = remember(density) { with(density) { DrawerPullToClose(PULL_DISTANCE.toPx(), PULL_VELOCITY.toPx()) } }
    var pulled by remember { mutableFloatStateOf(0f) }
    // The grid's own scrolling comes first; only the downward drag it cannot use, at its top, is a pull.
    val pullConnection = remember(pull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                return Offset(0f, pull.onPreScroll(available.y)).also { pulled = pull.pulled }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                return Offset(0f, pull.onPostScroll(available.y)).also { pulled = pull.pulled }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pull.pulling) return Velocity.Zero
                val closes = pull.release(available.y)
                pulled = 0f
                if (closes) close()
                return available
            }
        }
    }

    Column(
        modifier
            .graphicsLayer { translationY = pulled * PULL_FOLLOW }
            .nestedScroll(pullConnection)
            .background(c.background.copy(alpha = 0.98f))
            .clickable(enabled = false) {}
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // The header has nothing to scroll: a drag on it is a pull straight away. Its buttons still
        // take taps, since a drag starts only past the touch slop.
        Column(
            Modifier.fillMaxWidth().pointerInput(pull) {
                detectVerticalDragGestures(
                    onDragEnd = { if (pull.release(0f)) close(); pulled = 0f },
                    onDragCancel = { pull.release(0f); pulled = 0f },
                ) { change, dy ->
                    if (dy < 0f) pull.onPreScroll(dy) else pull.onPostScroll(dy)
                    pulled = pull.pulled
                    change.consume()
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (pick) {
                        LauncherController.PickMode.HOME -> "Pin to Home"
                        LauncherController.PickMode.DOCK -> "Pin to dock"
                        null -> "Apps"
                    },
                    color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier
                        .clickable { showHidden = !showHidden }
                        .border(1.dp, c.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (showHidden) GlanceIcons.Eye else GlanceIcons.EyeOff, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    // Shorter than "Show hidden (n)", so the labels switch fits beside it on a phone; the eye says which state.
                    Text("Hidden (${hidden.size})", color = c.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (showLabels) "Labels on" else "Labels off",
                    color = c.textSecondary, fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { prefs.setDrawerShowsLabels(!showLabels) }
                        .border(1.dp, c.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.Close, contentDescription = "Close", tint = c.textSecondary,
                    modifier = Modifier.size(28.dp).clickable { close() },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (pick != null) "Tap an app to pin it." else "Long-press an app for App info, hide, pin. Swipe down to close.",
                color = c.textMuted, fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyVerticalGrid(
            columns = if (phone) GridCells.Fixed(4) else GridCells.Adaptive(88.dp),
            verticalArrangement = Arrangement.spacedBy(if (showLabels) 14.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (recent.isNotEmpty()) {
                item(key = "recent-label", span = { GridItemSpan(maxLineSpan) }, contentType = "label") {
                    DrawerSectionLabel("Recent")
                }
                items(recent, key = { "recent:" + it.key }, contentType = { "app" }) { entry ->
                    DrawerTile(
                        entry = entry, dimmed = false, showLabel = showLabels,
                        pinned = when (pick) {
                            LauncherController.PickMode.HOME -> entry.key in homePins
                            LauncherController.PickMode.DOCK -> entry.key in dockPins
                            null -> false
                        },
                        onClick = {
                            when (pick) {
                                LauncherController.PickMode.HOME -> prefs.toggleHomePin(entry.key)
                                LauncherController.PickMode.DOCK -> prefs.toggleDockPin(entry.key)
                                null -> { apps.launch(entry); controller.drawerOpen = false }
                            }
                        },
                        onLongClick = { menuFor = entry },
                        controller = controller,
                    )
                }
                item(key = "all-label", span = { GridItemSpan(maxLineSpan) }, contentType = "label") {
                    DrawerSectionLabel("All apps")
                }
            }
            items(visible, key = { it.key }, contentType = { "app" }) { entry ->
                val isHidden = entry.key in hidden
                val pinnedHere = when (pick) {
                    LauncherController.PickMode.HOME -> entry.key in homePins
                    LauncherController.PickMode.DOCK -> entry.key in dockPins
                    null -> false
                }
                DrawerTile(
                    entry = entry,
                    dimmed = isHidden,
                    showLabel = showLabels,
                    pinned = pinnedHere,
                    onClick = {
                        when (pick) {
                            LauncherController.PickMode.HOME -> prefs.toggleHomePin(entry.key)
                            LauncherController.PickMode.DOCK -> prefs.toggleDockPin(entry.key)
                            null -> { apps.launch(entry); controller.drawerOpen = false }
                        }
                    },
                    onLongClick = { menuFor = entry },
                    controller = controller,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // Search at the bottom, where the thumbs are, and above the keyboard when it opens.
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.card, RoundedCornerShape(12.dp))
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search apps", color = c.textMuted, fontSize = 15.sp)
                    inner()
                },
            )
        }
    }

    val target = menuFor
    if (target != null) {
        AppMenu(controller, Pin.App(target), onDismiss = { menuFor = null })
    }
}

private const val RECENT_COUNT = 4

/**
 * How far a pull must go to close, and how fast a flick must be. Measured in the drawer's own
 * coordinates, which move with it: at [PULL_FOLLOW] the finger travels half as far again
 * (about 96 dp), which reads as a little resistance.
 */
private val PULL_DISTANCE = 64.dp
private val PULL_VELOCITY = 1000.dp
/** The share of the pull the drawer moves by. */
private const val PULL_FOLLOW = 0.5f

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text.uppercase(), color = GlanceTheme.colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp),
    )
}

@Composable
private fun DrawerTile(entry: AppEntry, dimmed: Boolean, showLabel: Boolean, pinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, controller: LauncherController) {
    val c = GlanceTheme.colors
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (pinned) Modifier.border(2.dp, c.brand, RoundedCornerShape(14.dp)) else Modifier)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            // A hidden app's corner holds its eye mark instead of a dot.
            AppIcon(entry, controller.graph.apps, size = 52.dp, modifier = Modifier.alpha(if (dimmed) 0.4f else 1f), notificationDot = !dimmed)
            if (dimmed) Icon(GlanceIcons.EyeOff, contentDescription = "Hidden", tint = c.textSecondary, modifier = Modifier.size(16.dp).align(Alignment.TopEnd))
        }
        if (showLabel) {
            Text(
                entry.label,
                color = if (dimmed) c.textMuted else c.text,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
