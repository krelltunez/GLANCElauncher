package app.glance.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.data.provider.GlanceApp
import app.glance.launcher.di.AppGraph
import app.glance.launcher.ui.common.AppMenu
import app.glance.launcher.ui.common.NotificationDotsProvider
import app.glance.launcher.ui.dock.Dock
import app.glance.launcher.ui.drawer.AppDrawer
import app.glance.launcher.ui.home.HomeScreen
import app.glance.launcher.ui.home.PinnedEditor
import app.glance.launcher.ui.home.SearchOverlay
import app.glance.launcher.ui.planning.PageIndicator
import app.glance.launcher.ui.planning.PageIndicatorBottom
import app.glance.launcher.ui.planning.PageIndicatorToastLift
import app.glance.launcher.ui.planning.PlanningPage
import app.glance.launcher.ui.planning.PlanningPages
import app.glance.launcher.ui.planning.SchedFilterSheet
import app.glance.launcher.ui.settings.SettingsSheet
import app.glance.launcher.ui.diagnostics.DiagnosticsSheet
import app.glance.launcher.ui.theme.GlanceLauncherTheme
import app.glance.launcher.ui.theme.GlanceTheme
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(graph: AppGraph, homePressCount: Int, foregroundCount: Int = 0) {
    val themeMode by graph.prefs.themeMode.collectAsState()
    val themedIcons by graph.prefs.themedIcons.collectAsState()
    GlanceLauncherTheme(themeMode, themedIcons) {
        NotificationDotsProvider(graph.prefs) {
            val scope = rememberCoroutineScope()
            val controller = remember { LauncherController(graph, scope) }
            val dockEnabled by graph.prefs.dockEnabled.collectAsState()
            // Home, then a planning page per app (D80). The list is derived, so it is one instance
            // until it really changes: availability emits on every provider read, and a new (equal)
            // list each time would rebuild the pager's pages.
            val availability by controller.repository.availability.collectAsState()
            val apps by remember { derivedStateOf { PlanningPages.apps(availability) } }
            // The list can change while the launcher is up, and the pager must not move when it
            // does. Two things see to that. Indices count away from Home (PlanningPages; the pager
            // is laid out in reverse below), so an app joining the end of the list takes a new
            // index and Home is 0 throughout. And each page has a key, so if an app joins or leaves
            // nearer Home than the page in front, the pager finds that page's new index while it
            // measures, before anything is drawn.
            val pager = rememberPagerState(initialPage = PlanningPages.HOME) { PlanningPages.count(apps) }
            val c = GlanceTheme.colors
            SolidWallpaperEffect(graph.prefs)
            val hideStatusBar by graph.prefs.hideStatusBar.collectAsState()
            StatusBarEffect(hideStatusBar)

            // Past midnight, the dial and the Planning month move on to the new day. Checked each
            // minute, and on coming back on screen, since the minute's delay does not run while the
            // device sleeps.
            LaunchedEffect(controller) {
                while (true) {
                    controller.rollToday()
                    delay((60 - java.time.LocalTime.now().second) * 1000L + 50)
                }
            }
            LaunchedEffect(foregroundCount) { controller.rollToday() }

            // Home press: back to Home, overlays closed, search cleared.
            LaunchedEffect(homePressCount) {
                controller.closeOverlays()
                pager.animateScrollToPage(PlanningPages.HOME)
            }
            // A header icon on Home asked for its app's planning page (showPlanning).
            LaunchedEffect(controller.planningRequests) {
                if (controller.planningRequests > 0) PlanningPages.indexOf(controller.planningTarget, apps)?.let { pager.animateScrollToPage(it) }
            }
            // A planning page settled in front: its app may want asking for fresher data (planningShown).
            LaunchedEffect(pager.settledPage, apps, foregroundCount) {
                PlanningPages.appAt(pager.settledPage, apps)?.let { controller.repository.planningShown(it) }
            }
            // Back with an overlay closes it; back on a planning page returns Home. On Home it does
            // nothing: the launcher is the bottom of the back stack, and handing the gesture to the
            // system played its back-to-home animation over Home itself.
            BackHandler {
                when {
                    controller.drawerOpen || controller.settingsOpen || controller.diagnosticsOpen || controller.searchActive || controller.pinnedEditorOpen || controller.appMenuFor != null || controller.schedFiltersOpen || controller.quickAdd != null -> controller.closeOverlays()
                    pager.currentPage != PlanningPages.HOME -> scope.launch { pager.animateScrollToPage(PlanningPages.HOME) }
                }
            }

            Box(Modifier.fillMaxSize().background(c.background)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        // Home is the star on every size; each app's planning is a swipe further left.
                        // reverseLayout puts index 0, Home, on the right with the rest leftwards.
                        HorizontalPager(
                            state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, reverseLayout = true,
                            key = { page -> PlanningPages.key(page, apps) },
                        ) { page ->
                            val app = PlanningPages.appAt(page, apps)
                            if (app != null) PlanningPage(app, controller, Modifier.fillMaxSize())
                            else HomeScreen(controller, Modifier.fillMaxSize())
                        }
                        PlanningIndicator(
                            pager, apps, controller,
                            Modifier.align(Alignment.BottomCenter).then(if (dockEnabled) Modifier else Modifier.navigationBarsPadding()).padding(bottom = PageIndicatorBottom),
                        )
                    }
                    if (dockEnabled) Dock(controller)
                }

                if (controller.searchActive) SearchOverlay(controller, Modifier.fillMaxSize())
                if (controller.drawerOpen) AppDrawer(controller, Modifier.fillMaxSize())
                if (controller.pinnedEditorOpen) PinnedEditor(controller, Modifier.fillMaxSize())
                if (controller.settingsOpen) SettingsSheet(controller, Modifier.fillMaxSize())
                if (controller.diagnosticsOpen) DiagnosticsSheet(controller, Modifier.fillMaxSize())
                if (controller.schedFiltersOpen) SchedFilterSheet(controller, Modifier.fillMaxSize())
                controller.appMenuFor?.let { pin -> AppMenu(controller, pin, onDismiss = { controller.appMenuFor = null }) }

                val toast = controller.toast
                if (toast != null) {
                    LaunchedEffect(toast) {
                        delay(2500)
                        if (controller.toast == toast) controller.toast = null
                    }
                    // On a planning page the page indicator is where the toast would land, so the
                    // toast rides above it there.
                    val overPlanning by remember { derivedStateOf { pager.currentPage != PlanningPages.HOME } }
                    val lift = if (overPlanning) PageIndicatorToastLift + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = (if (dockEnabled) 96.dp else 24.dp) + lift)
                            .background(c.text.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(toast, color = c.background, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * The page indicator over the pager (D80): there while a planning page is in view, and fading
 * with the swipe so that it is gone on Home, which has no dot. It is not composed at all on
 * Home, where it would take taps meant for what is under it, nor under a quick-add sheet. The
 * fade is read in the draw phase, so a swipe redraws the pill without recomposing the root.
 */
@Composable
private fun PlanningIndicator(pager: PagerState, apps: List<GlanceApp>, controller: LauncherController, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    // How far the pager is from Home, in pages: 0 on Home, 1 and more on a planning page.
    fun fromHome(): Float = abs(pager.currentPage - PlanningPages.HOME + pager.currentPageOffsetFraction)
    val inView by remember(pager) { derivedStateOf { fromHome() > 0.02f } }
    if (!inView || controller.quickAdd != null || apps.isEmpty()) return
    PageIndicator(
        apps = apps,
        current = PlanningPages.appAt(pager.currentPage, apps),
        onPick = { app -> PlanningPages.indexOf(app, apps)?.let { scope.launch { pager.animateScrollToPage(it) } } },
        modifier = modifier.graphicsLayer { alpha = fromHome().coerceIn(0f, 1f) },
    )
}
