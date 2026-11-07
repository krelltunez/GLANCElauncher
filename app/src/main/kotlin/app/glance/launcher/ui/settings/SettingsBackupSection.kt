package app.glance.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.glance.launcher.BuildConfig
import app.glance.launcher.prefs.SettingsBackup
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.theme.GlanceTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Settings to a file and back ([SettingsBackup]): they survive a reinstall, which a build signed
 * with another key needs, and move to another device. An import asks first, since it replaces.
 */
@Composable
internal fun SettingsBackupSection(controller: LauncherController) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val prefs = controller.graph.prefs
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<SettingsBackup.Decoded.Ok?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = SettingsBackup.encode(prefs.exportValues(), Instant.now(), BuildConfig.VERSION_NAME)
        status = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        }.fold({ "Settings exported" }, { "Could not write the file" })
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                .takeIf { it.size <= MAX_FILE_BYTES }?.decodeToString()
        }.getOrNull()
        when (val d = text?.let(SettingsBackup::decode)) {
            null -> status = "Could not read the file"
            is SettingsBackup.Decoded.Invalid -> status = d.reason
            is SettingsBackup.Decoded.Ok -> pending = d
        }
    }

    Text("Backup", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Save these settings to a file, then import it after a reinstall or on another device. " +
            "Permissions and the Home-app choice are not included.",
        color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BackupButton("Export settings") { exporter.launch("glance-launcher-settings-${LocalDate.now()}.json") }
        // Some file managers label a .json file as text or as plain bytes.
        BackupButton("Import settings") { importer.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
    }
    status?.let { Text(it, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }

    pending?.let { d ->
        val made = listOfNotNull(
            d.exportedAt?.let {
                "exported " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .format(it.atZone(ZoneId.systemDefault()))
            },
            d.appVersion?.let { "by version $it" },
        ).joinToString(" ")
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Import settings?") },
            text = {
                Text(
                    "Your launcher settings (pinned apps, hidden apps, theme, toggles, agenda filters and history) " +
                        "are replaced with the ones in this file" + (if (made.isNotEmpty()) ", $made" else "") + ". " +
                        "Permissions stay as they are on this device.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.importValues(d.values)
                    controller.reloadSchedView()
                    status = if (d.skipped.isEmpty()) "Settings imported"
                    else "Settings imported; ${d.skipped.size} could not be read and are back to their defaults"
                    pending = null
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BackupButton(label: String, onClick: () -> Unit) {
    val c = GlanceTheme.colors
    Text(
        label,
        color = c.text, fontSize = 13.sp,
        modifier = Modifier
            .background(c.cardSubtle, RoundedCornerShape(8.dp))
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A settings file is a few kilobytes; anything far larger is not one. */
private const val MAX_FILE_BYTES = 1_000_000
