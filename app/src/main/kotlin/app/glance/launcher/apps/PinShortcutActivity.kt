package app.glance.launcher.apps

import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.LauncherApp
import app.glance.launcher.ui.common.drawableToBitmap
import app.glance.launcher.ui.theme.GlanceLauncherTheme
import app.glance.launcher.ui.theme.GlanceTheme

/**
 * An app's "Add to Home screen" (Chrome's page shortcut, a contact, a Maps route). Android sends
 * the request to the default launcher as `CONFIRM_PIN_SHORTCUT`; without an activity for it the
 * request went nowhere. This asks once, and on Add accepts the request (Android pins the
 * shortcut for this launcher) and puts it at the end of Home's pinned row ([Pins]).
 *
 * Widgets (`CONFIRM_PIN_APPWIDGET`) are not handled: the launcher hosts none.
 */
class PinShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        // Null for an intent that did not come from Android's pin request (another app cannot forge one).
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        val info = request
            ?.takeIf { it.isValid && it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT }
            ?.shortcutInfo
        if (request == null || info == null) {
            finish()
            return
        }
        val graph = (application as LauncherApp).graph
        val appLabel = graph.apps.apps.value.firstOrNull { it.packageName == info.`package` }?.label
            ?: runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.`package`, 0)).toString() }.getOrDefault(info.`package`)
        val label = (info.longLabel?.toString()?.takeIf { it.isNotBlank() } ?: info.shortLabel?.toString()).orEmpty()
        val icon = runCatching { launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi) }.getOrNull()
            ?.let { drawableToBitmap(it, 144).asImageBitmap() }

        setContent {
            val mode by graph.prefs.themeMode.collectAsState()
            GlanceLauncherTheme(mode) {
                PinShortcutSheet(
                    label = label,
                    appLabel = appLabel,
                    icon = icon,
                    onAdd = {
                        // False when the request went stale (the app withdrew it); then nothing is pinned.
                        if (runCatching { request.accept() }.getOrDefault(false)) {
                            graph.prefs.addHomePin(Pins.keyOf(info.`package`, info.id))
                            graph.apps.refreshShortcuts()
                        }
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun PinShortcutSheet(label: String, appLabel: String, icon: ImageBitmap?, onAdd: () -> Unit, onCancel: () -> Unit) {
    val c = GlanceTheme.colors
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .background(c.card, RoundedCornerShape(20.dp))
                .border(1.dp, c.border, RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Add to Home", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(56.dp))
            else Box(Modifier.size(56.dp).background(c.track, RoundedCornerShape(14.dp)))
            Spacer(Modifier.height(10.dp))
            Text(label, color = c.text, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(appLabel, color = c.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text("It goes at the end of your pinned apps.", color = c.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton("Cancel", c.track, c.text, Modifier.weight(1f), onCancel)
                SheetButton("Add", c.accent, Color.White, Modifier.weight(1f), onAdd)
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, background: Color, textColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
