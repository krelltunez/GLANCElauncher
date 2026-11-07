package app.glance.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.system.OpenSourceNotices
import app.glance.launcher.ui.theme.GlanceTheme

/** The list of what the APK carries; a tap shows that one's licence in full. */
@Composable
internal fun OpenSourceNoticesDialog(onDismiss: () -> Unit) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    var open by remember { mutableStateOf<OpenSourceNotices.Notice?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open source licences") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (notice in OpenSourceNotices.all) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { open = if (open == notice) null else notice }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(notice.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(notice.usedFor, color = c.textSecondary, fontSize = 12.sp)
                    }
                    if (open == notice) {
                        val licence = remember(notice) {
                            runCatching { context.assets.open(notice.asset).bufferedReader().use { it.readText() } }
                                .getOrDefault("")
                        }
                        Text(licence, color = c.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
