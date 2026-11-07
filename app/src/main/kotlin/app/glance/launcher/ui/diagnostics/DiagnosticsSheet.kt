package app.glance.launcher.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.data.DataSource
import app.glance.launcher.data.Freshness
import app.glance.launcher.data.RefreshRequest
import app.glance.launcher.data.provider.GlanceApp
import app.glance.launcher.data.provider.RefreshEntry
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.common.formatAgo
import app.glance.launcher.ui.common.formatHeld
import app.glance.launcher.ui.theme.GlanceTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The freshness diagnostics (long-press the header status): per app, where its data comes
 * from, when its snapshot last changed, when it last looked, when it last synced, whether its
 * renderer was alive at the last read, and the last REFRESH requests with their outcomes and
 * timing. "Refresh now" sends a REFRESH and the outcome shows here as the app logs it.
 */
@Composable
fun DiagnosticsSheet(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val repo = controller.repository
    val availability by repo.availability.collectAsState()
    val requests by repo.refreshRequests.collectAsState()
    val scope = rememberCoroutineScope()
    // Ages tick while the sheet is open.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); nowMs = System.currentTimeMillis() } }
    val now = Instant.ofEpochMilli(nowMs)

    Box(
        modifier.background(Color.Black.copy(alpha = 0.4f)).clickable { controller.diagnosticsOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .background(c.card, RoundedCornerShape(16.dp))
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Data freshness", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Staleness is measured from the last completed sync, else the last time the app looked. The log is what the app recorded for each refresh the launcher asked for.",
                color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            AppSection("dayGLANCE", availability.dayGlanceFreshness, requests[GlanceApp.DAYGLANCE], now) {
                scope.launch { repo.requestRefresh(GlanceApp.DAYGLANCE) }
            }
            Spacer(Modifier.height(14.dp))
            AppSection("lastGLANCE", availability.lastGlanceFreshness, requests[GlanceApp.LASTGLANCE], now) {
                scope.launch { repo.requestRefresh(GlanceApp.LASTGLANCE) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Close", color = c.accent, fontSize = 13.sp, modifier = Modifier.clickable { controller.diagnosticsOpen = false }.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun AppSection(name: String, f: Freshness, request: RefreshRequest?, now: Instant, onRefresh: () -> Unit) {
    val c = GlanceTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            when {
                f.source == DataSource.DEMO && !f.installed -> "not installed · demo"
                f.source == DataSource.DEMO -> "installed · demo (provider not readable)"
                else -> "live"
            },
            color = if (f.source == DataSource.LIVE) c.accent else c.warning, fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        if (f.source == DataSource.LIVE) {
            Text(
                "Refresh now", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.border(1.dp, c.border, RoundedCornerShape(8.dp)).clickable(onClick = onRefresh).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    StatRow("Snapshot changed", stamp(f.updatedAt, now))
    StatRow("App last looked", stamp(f.checkedAt, now))
    StatRow("Last sync completed", stamp(f.syncedAt, now) ?: "no sync configured")
    StatRow("Renderer at last read", when (f.rendererAlive) { true -> "alive"; false -> "not running"; null -> "unknown (older app build)" })
    f.headlessRunning?.let { StatRow("Headless refresh", if (it) "running now" else "not running") }
    request?.let { StatRow("Last request sent", "${formatAgo(Duration.between(it.sentAt, now))} · ${it.clientId.take(8)}") }
    Spacer(Modifier.height(6.dp))
    Text("Refresh log", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    if (f.refreshLog.isEmpty()) {
        Text("none recorded", color = c.textMuted, fontSize = 12.sp)
    } else {
        for (e in f.refreshLog.takeLast(8).asReversed()) LogRow(e, now)
    }
}

@Composable
private fun StatRow(label: String, value: String?) {
    val c = GlanceTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.textSecondary, fontSize = 12.sp)
        Text(value ?: "never", color = c.text, fontSize = 12.sp)
    }
}

@Composable
private fun LogRow(e: RefreshEntry, now: Instant) {
    val c = GlanceTheme.colors
    val color = when (RefreshLogView.tone(e, now)) {
        RefreshLogView.Tone.OK -> c.accent
        RefreshLogView.Tone.BAD -> c.danger
        RefreshLogView.Tone.WARN -> c.warning
        RefreshLogView.Tone.NEUTRAL -> c.text
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(e.requestedAt?.let { formatAgo(Duration.between(it, now)) } ?: "?", color = c.textMuted, fontSize = 11.sp, modifier = Modifier.widthIn(min = 52.dp))
        Spacer(Modifier.padding(horizontal = 4.dp))
        Column {
            Text(RefreshLogView.headline(e, now, ::formatHeld), color = color, fontSize = 12.sp)
            e.reason?.let { Text(it, color = c.textSecondary, fontSize = 11.sp) }
        }
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm:ss")

private fun stamp(at: Instant?, now: Instant): String? =
    at?.let { "${formatAgo(Duration.between(it, now))} · ${CLOCK.format(it.atZone(ZoneId.systemDefault()))}" }
