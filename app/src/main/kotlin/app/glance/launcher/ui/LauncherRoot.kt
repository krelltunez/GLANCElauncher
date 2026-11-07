package app.glance.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.di.AppGraph
import app.glance.launcher.ui.common.AppMenu
import app.glance.launcher.ui.common.NotificationDotsProvider
import app.glance.launcher.ui.dock.Dock
import app.glance.launcher.ui.drawer.AppDrawer
import app.glance.launcher.ui.home.HomeScreen
import app.glance.launcher.ui.home.PinnedEditor
import app.glance.launcher.ui.home.SearchOverlay
import app.glance.launcher.ui.planning.PlanningScreen
import app.glance.launcher.ui.planning.SchedFilterSheet
import app.glance.launcher.ui.settings.SettingsSheet
import app.glance.launcher.ui.diagnostics.DiagnosticsSheet
import app.glance.launcher.ui.theme.GlanceLauncherTheme
import app.glance.launcher.ui.theme.GlanceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_PLANNING = 0
private const val PAGE_HOME = 1

@Composable
fun LauncherRoot(graph: AppGraph, homePressCount: Int, foregroundCount: Int = 0) {
    val themeMode by graph.prefs.themeMode.collectAsState()
    val themedIcons by graph.prefs.themedIcons.collectAsState()
    GlanceLauncherTheme(themeMode, themedIcons) {
        NotificationDotsProvider(graph.prefs) {
            val scope = rememberCoroutineScope()
            val controller = remember { LauncherController(graph, scope) }
            val dockEnabled by graph.prefs.dockEnabled.collectAsState()
            val pager = rememberPagerState(initialPage = PAGE_HOME) { 2 }
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
                pager.animateScrollToPage(PAGE_HOME)
            }
            LaunchedEffect(controller.planningRequests) {
                if (controller.planningRequests > 0) pager.animateScrollToPage(PAGE_PLANNING)
            }
            // Back with an overlay closes it; back on the planning page returns Home. On Home it does
            // nothing: the launcher is the bottom of the back stack, and handing the gesture to the
            // system played its back-to-home animation over Home itself.
            BackHandler {
                when {
                    controller.drawerOpen || controller.settingsOpen || controller.diagnosticsOpen || controller.searchActive || controller.pinnedEditorOpen || controller.appMenuFor != null || controller.schedFiltersOpen -> controller.closeOverlays()
                    pager.currentPage != PAGE_HOME -> scope.launch { pager.animateScrollToPage(PAGE_HOME) }
                }
            }

            Box(Modifier.fillMaxSize().background(c.background)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        // Home is the star on every size; planning is a swipe to the left.
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                            if (page == PAGE_PLANNING) PlanningScreen(controller, Modifier.fillMaxSize())
                            else HomeScreen(controller, Modifier.fillMaxSize())
                        }
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
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (dockEnabled) 96.dp else 24.dp)
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
