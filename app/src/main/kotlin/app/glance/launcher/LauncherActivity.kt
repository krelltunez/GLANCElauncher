package app.glance.launcher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.glance.launcher.dev.DevActions
import app.glance.launcher.ui.LauncherRoot

/**
 * The HOME activity. It is `singleTask` and `stateNotNeeded`, so every Home press lands on
 * the same instance (via [onNewIntent]) and the system may drop and recreate it freely.
 * Pressing Home while on the planning screen returns to the Home page.
 */
class LauncherActivity : ComponentActivity() {
    /** Bumped on every Home press so the root can snap back to the Home page. */
    private var homePressCount by mutableIntStateOf(0)
    /** Bumped each time the launcher comes back on screen, so the root can catch a date that turned meanwhile. */
    private var foregroundCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones stay upright, as the stock launchers do: Home has no landscape job there, and a
        // 336 dp-tall column beside the dial has no room for one. Tablets keep both orientations.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        val graph = (application as LauncherApp).graph
        setContent {
            LauncherRoot(graph = graph, homePressCount = homePressCount, foregroundCount = foregroundCount)
        }
        DevActions.handle(this, intent, graph.providerRepository, graph.scope)
    }

    override fun onStart() {
        super.onStart()
        foregroundCount++
        // Back on screen: re-read both providers and re-send any write still pending.
        (application as LauncherApp).graph.providerRepository.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePressCount++
        val graph = (application as LauncherApp).graph
        DevActions.handle(this, intent, graph.providerRepository, graph.scope)
    }
}
