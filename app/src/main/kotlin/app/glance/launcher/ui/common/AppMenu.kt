package app.glance.launcher.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.apps.AppShortcut
import app.glance.launcher.apps.AppsRepository
import app.glance.launcher.apps.Pin
import app.glance.launcher.apps.Pins
import app.glance.launcher.apps.ShortcutMenu
import app.glance.launcher.ui.GlanceIcons
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.theme.GlanceTheme

/**
 * The rows of an app's long-press menu below its own shortcuts, for a pinned app or one in the
 * drawer, and for a pinned shortcut. Pure, tested on the JVM; [AppMenu] draws them.
 */
object AppMenuRows {
    enum class Row { OPEN_APP, APP_INFO, TOGGLE_HIDDEN, TOGGLE_HOME, TOGGLE_DOCK, REMOVE_FROM_HOME, UNINSTALL }

    /**
     * An app gets the drawer's full menu, wherever it was long-pressed, so the same press always
     * offers the same things. A pinned shortcut is not an app: hiding, the dock and uninstalling
     * would act on an app the user did not press, so it offers its app, the app's info and its
     * own removal.
     */
    fun forPin(shortcut: Boolean): List<Row> =
        if (shortcut) listOf(Row.OPEN_APP, Row.APP_INFO, Row.REMOVE_FROM_HOME)
        else listOf(Row.APP_INFO, Row.TOGGLE_HIDDEN, Row.TOGGLE_HOME, Row.TOGGLE_DOCK, Row.UNINSTALL)

    fun label(row: Row, appLabel: String, hidden: Boolean, homePinned: Boolean, dockPinned: Boolean): String = when (row) {
        Row.OPEN_APP -> "Open $appLabel"
        Row.APP_INFO -> "App info"
        Row.TOGGLE_HIDDEN -> if (hidden) "Show in drawer" else "Hide from drawer"
        Row.TOGGLE_HOME -> if (homePinned) "Unpin from Home" else "Pin to Home"
        Row.TOGGLE_DOCK -> if (dockPinned) "Unpin from dock" else "Pin to dock"
        Row.REMOVE_FROM_HOME -> "Remove from Home"
        Row.UNINSTALL -> "Uninstall"
    }
}

/**
 * The long-press menu for [pin], with every action wired: the drawer's tiles and Home's pinned
 * row both open it. An app lists its own shortcuts first (tap to start one, the pin beside it
 * to put it on Home), then [AppMenuRows]. Any action closes it, as does a tap outside.
 */
@Composable
fun AppMenu(controller: LauncherController, pin: Pin, onDismiss: () -> Unit) {
    val apps = controller.graph.apps
    val prefs = controller.graph.prefs
    val context = LocalContext.current
    val all by apps.apps.collectAsState()
    val hidden by prefs.hiddenApps.collectAsState()
    val homePins by prefs.homePins.collectAsState()
    val dockPins by prefs.dockPins.collectAsState()
    val allShortcuts by apps.shortcuts.collectAsState()

    val entry = when (pin) {
        is Pin.App -> pin.entry
        is Pin.Shortcut -> all.firstOrNull { it.packageName == pin.shortcut.packageName }
    }
    val packageName = when (pin) {
        is Pin.App -> pin.entry.packageName
        is Pin.Shortcut -> pin.shortcut.packageName
    }
    val shortcuts = remember(allShortcuts, pin) {
        if (pin is Pin.App) ShortcutMenu.forApp(allShortcuts, packageName) else emptyList()
    }
    val appKey = entry?.key
    val rows = AppMenuRows.forPin(shortcut = pin is Pin.Shortcut)
        // A shortcut whose app is gone (or not loaded yet) has nothing to open.
        .filter { it != AppMenuRows.Row.OPEN_APP || entry != null }

    fun act(block: () -> Unit) {
        block()
        onDismiss()
    }

    val c = GlanceTheme.colors
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.8f)
                .widthIn(max = 360.dp)
                .background(c.card, RoundedCornerShape(16.dp))
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(8.dp),
        ) {
            Text(pin.label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (shortcuts.isNotEmpty()) {
                for (s in shortcuts) {
                    ShortcutMenuRow(
                        s, Pins.keyOf(s.packageName, s.id) in homePins, apps,
                        onClick = { act { apps.launchShortcut(s) } },
                        onTogglePin = { prefs.toggleHomePin(Pins.keyOf(s.packageName, s.id)) },
                    )
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(1.dp).background(c.border))
            }
            for (row in rows) {
                val label = AppMenuRows.label(
                    row, entry?.label ?: packageName,
                    hidden = appKey != null && appKey in hidden,
                    homePinned = appKey != null && appKey in homePins,
                    dockPinned = appKey != null && appKey in dockPins,
                )
                MenuRow(label, danger = row == AppMenuRows.Row.UNINSTALL) {
                    act {
                        when (row) {
                            AppMenuRows.Row.OPEN_APP -> entry?.let { apps.launch(it) }
                            AppMenuRows.Row.APP_INFO -> openPackageIntent(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName)
                            AppMenuRows.Row.TOGGLE_HIDDEN -> appKey?.let { prefs.setHidden(it, it !in hidden) }
                            AppMenuRows.Row.TOGGLE_HOME -> appKey?.let { prefs.toggleHomePin(it) }
                            AppMenuRows.Row.TOGGLE_DOCK -> appKey?.let { prefs.toggleDockPin(it) }
                            AppMenuRows.Row.REMOVE_FROM_HOME -> prefs.toggleHomePin(pin.key)
                            AppMenuRows.Row.UNINSTALL -> openPackageIntent(context, Intent.ACTION_DELETE, packageName)
                        }
                    }
                }
            }
        }
    }
}

private fun openPackageIntent(context: Context, action: String, packageName: String) {
    val intent = Intent(action).setData(Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** One of the app's shortcuts: its icon and label start it; the pin on the right puts it on Home or takes it off. */
@Composable
private fun ShortcutMenuRow(
    shortcut: AppShortcut, pinned: Boolean, apps: AppsRepository,
    onClick: () -> Unit, onTogglePin: () -> Unit,
) {
    val c = GlanceTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortcutIcon(shortcut, apps, size = 24.dp)
            Spacer(Modifier.width(12.dp))
            Text(shortcut.label, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            GlanceIcons.Pin,
            contentDescription = if (pinned) "Unpin ${shortcut.label} from Home" else "Pin ${shortcut.label} to Home",
            tint = if (pinned) c.accent else c.textMuted,
            modifier = Modifier.size(40.dp).clickable(onClick = onTogglePin).padding(11.dp),
        )
    }
}

@Composable
private fun MenuRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = GlanceTheme.colors
    Text(
        label,
        color = if (danger) c.danger else c.text,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
    )
}
