package app.glance.launcher.ui.settings

import android.os.Build
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
import androidx.compose.material3.Switch
import app.glance.launcher.system.LockScreenService
import app.glance.launcher.system.DoubleTapLock
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.BuildConfig
import app.glance.launcher.prefs.ThemeMode
import app.glance.launcher.system.NotificationDotsService
import app.glance.launcher.ui.common.appStatusLine
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.theme.GlanceTheme
import app.glance.launcher.ui.theme.ThemedIcons

/** Launcher settings: theme, dock, recents, clock, hidden apps, backup and the Home-app shortcut. */
@Composable
fun SettingsSheet(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val prefs = controller.graph.prefs
    val context = LocalContext.current
    val theme by prefs.themeMode.collectAsState()
    val dock by prefs.dockEnabled.collectAsState()
    val recents by prefs.dockShowsRecents.collectAsState()
    val clock by prefs.use24HourClock.collectAsState()
    val hidden by prefs.hiddenApps.collectAsState()
    val solidWallpaper by prefs.solidWallpaper.collectAsState()
    val notificationDots by prefs.notificationDots.collectAsState()
    val dotsConnected by NotificationDotsService.connected.collectAsState()
    val themedIcons by prefs.themedIcons.collectAsState()
    val doubleTapToLock by prefs.doubleTapToLock.collectAsState()
    val lockServiceOn by LockScreenService.connected.collectAsState()
    var lockDisclosure by remember { mutableStateOf(false) }
    var noticesOpen by remember { mutableStateOf(false) }
    val dockLabels by prefs.dockShowsLabels.collectAsState()
    val drawerLabels by prefs.drawerShowsLabels.collectAsState()
    val contactsInSearch by prefs.contactsInSearch.collectAsState()
    val hideStatusBar by prefs.hideStatusBar.collectAsState()
    val demoData by prefs.demoData.collectAsState()

    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { controller.settingsOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 480.dp)
                .background(c.card, RoundedCornerShape(16.dp))
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Launcher settings", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            if (demoData) {
                // The header no longer says "demo" while it is on, so say it here, first.
                Text(
                    "Screenshot demo is on: Home and Planning show demo data. Tap to turn it off.",
                    color = c.warning, fontSize = 13.sp,
                    modifier = Modifier.clickable { prefs.setDemoData(false) }.padding(bottom = 12.dp),
                )
            }

            Text(
                "Set as Home app",
                color = c.card, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(c.accent, RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel("Appearance")
            Text("Theme", color = c.text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in ThemeMode.values()) {
                    val selected = mode == theme
                    Text(
                        mode.name.lowercase().replaceFirstChar { it.titlecase() },
                        color = if (selected) c.card else c.text,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(if (selected) c.accent else c.cardSubtle, RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) c.accent else c.border, RoundedCornerShape(8.dp))
                            .clickable { prefs.setThemeMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                "Solid wallpaper",
                if (solidWallpaper) "The device wallpaper is kept the dayGLANCE background colour, so nothing else shows during system transitions"
                else "Off: your own wallpaper is left alone (it shows briefly during the home gesture)",
                solidWallpaper,
            ) {
                prefs.setSolidWallpaper(it)
                if (!it) prefs.appliedWallpaperArgb = null
            }
            if (Build.VERSION.SDK_INT >= ThemedIcons.MIN_SDK) {
                ToggleRow(
                    "Themed icons",
                    if (themedIcons) "Apps that offer a monochrome icon are drawn in the GLANCE blues; the rest keep their own colours"
                    else "Each app in its own colours",
                    themedIcons,
                ) { prefs.setThemedIcons(it) }
            }
            ToggleRow("Hide status bar", if (hideStatusBar) "Swipe down from the top edge to peek at it" else "Clock, battery and notifications stay visible", hideStatusBar) { prefs.setHideStatusBar(it) }
            ToggleRow("24-hour clock", if (clock == null) "Following dayGLANCE's setting" else "Launcher override", clock ?: false) {
                prefs.setUse24HourClock(it)
            }
            if (clock != null) {
                Text(
                    "Follow dayGLANCE again",
                    color = c.accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { prefs.setUse24HourClock(null) }.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("Home")
            if (Build.VERSION.SDK_INT >= DoubleTapLock.MIN_SDK) {
                ToggleRow(
                    "Double-tap to lock",
                    if (doubleTapToLock) "Double-tap an empty part of Home to lock the screen" else "Off",
                    doubleTapToLock,
                ) { on ->
                    // Turning it on goes through the disclosure first; off needs none.
                    if (on) lockDisclosure = true else prefs.setDoubleTapToLock(false)
                }
                if (doubleTapToLock) {
                    Text(
                        if (lockServiceOn) "The lock service is on in Accessibility settings"
                        else "Turn on \"GLANCElauncher: double-tap to lock\" in Accessibility settings",
                        color = if (lockServiceOn) c.textSecondary else c.accent, fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { LockScreenService.openAccessibilitySettings(context) }
                            .padding(vertical = 4.dp),
                    )
                    if (!lockServiceOn) RestrictedSettingsHint()
                }
            }
            if (lockDisclosure) {
                LockDisclosure(
                    onContinue = {
                        lockDisclosure = false
                        prefs.setDoubleTapToLock(true)
                        if (!lockServiceOn) LockScreenService.openAccessibilitySettings(context)
                    },
                    onCancel = { lockDisclosure = false },
                )
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("Taskbar")
            ToggleRow("GLANCE taskbar", "Pinned and recent apps under Home and planning", dock) { prefs.setDockEnabled(it) }
            if (dock) {
                ToggleRow("App names in the taskbar", if (dockLabels) "Labels under the icons" else "Icons only, drawn larger", dockLabels) { prefs.setDockShowsLabels(it) }
                ToggleRow(
                    "Show recent apps in the taskbar",
                    if (recents) "The apps you last opened from the launcher" else "Off",
                    recents,
                ) { prefs.setDockShowsRecents(it) }
                if (recents) {
                    // Optional: the launcher sees only what it opens. Re-read whenever the sheet recomposes,
                    // so returning from Settings shows the new state.
                    val usageAccess = controller.graph.apps.hasUsageAccess()
                    Text(
                        if (usageAccess) "Also counting apps opened elsewhere (usage access is on)"
                        else "Also count apps opened elsewhere: grant usage access",
                        color = if (usageAccess) c.textSecondary else c.accent, fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { context.startActivity(controller.graph.apps.usageAccessIntent()) }
                            .padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("Apps")
            ToggleRow(
                "Notification dots",
                if (notificationDots) "A dot on the icons of apps with notifications, in the drawer, the taskbar and the pinned row" else "Off",
                notificationDots,
            ) { prefs.setNotificationDots(it) }
            if (notificationDots) {
                // The listener connecting is what flips this, so returning from Settings shows it.
                val access = dotsConnected || NotificationDotsService.hasAccess(context)
                Text(
                    if (access) "Notification access is on. The launcher reads only which apps have notifications, never what they say"
                    else "Allow notification access to show dots",
                    color = if (access) c.textSecondary else c.accent, fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { NotificationDotsService.openAccessSettings(context) }
                        .padding(vertical = 4.dp),
                )
                if (!access) RestrictedSettingsHint()
            }
            ToggleRow("App names in the drawer", if (drawerLabels) "Labels under the icons" else "Icons only", drawerLabels) { prefs.setDrawerShowsLabels(it) }
            Text("Hidden apps: ${hidden.size}. Long-press an app in the drawer to hide or show it.", color = c.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))

            SectionLabel("Search")
            // Re-read whenever the sheet recomposes, so returning from the permission prompt shows it.
            val contactsGranted = app.glance.launcher.system.ContactsSource.hasPermission(context)
            val askContacts = { (context as? android.app.Activity)?.requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS) }
            ToggleRow(
                "Contacts in search",
                when {
                    !contactsInSearch -> "Off: search finds apps, shortcuts and your GLANCE data"
                    contactsGranted -> "Your phone's contacts by name; a tap opens the contact"
                    else -> "Needs access to contacts"
                },
                contactsInSearch,
            ) {
                prefs.setContactsInSearch(it)
                if (it && !contactsGranted) askContacts()
            }
            if (contactsInSearch && !contactsGranted) {
                Text(
                    "Allow access to contacts",
                    color = c.accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { askContacts() }.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            SettingsBackupSection(controller)
            Spacer(Modifier.height(20.dp))

            if (BuildConfig.DEBUG) {
                SectionLabel("Developer")
                ToggleRow(
                    "Screenshot demo",
                    if (demoData) "Home and Planning show demo data; your data and queued writes are untouched"
                    else "Show demo data on Home and Planning, without the header's demo badge",
                    demoData,
                ) { prefs.setDemoData(it) }
                Spacer(Modifier.height(20.dp))
            }

            // Data status in words: Home's header only badges an app's icon once its data is stale.
            SectionLabel("About")
            val availability by controller.repository.availability.collectAsState()
            val now = java.time.Instant.now()
            for ((name, f) in listOf("dayGLANCE" to availability.dayGlanceFreshness, "lastGLANCE" to availability.lastGlanceFreshness)) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(name, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(appStatusLine(f, now), color = c.textSecondary, fontSize = 13.sp)
                }
            }
            Text(
                "Diagnostics",
                color = c.accent, fontSize = 13.sp,
                modifier = Modifier
                    .clickable { controller.settingsOpen = false; controller.diagnosticsOpen = true }
                    .padding(vertical = 8.dp),
            )
            Text(
                "Open source licences",
                color = c.accent, fontSize = 13.sp,
                modifier = Modifier.clickable { noticesOpen = true }.padding(vertical = 8.dp),
            )
            if (noticesOpen) OpenSourceNoticesDialog(onDismiss = { noticesOpen = false })
            Spacer(Modifier.height(12.dp))

            // Which build is installed: the version, when it was built, and from which commit.
            val built = java.time.Instant.ofEpochMilli(BuildConfig.BUILD_TIME).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT))
            // Two lines: the version alone, then when and from which commit it was built.
            Text(
                "GLANCElauncher ${BuildConfig.VERSION_NAME}",
                color = c.textMuted, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                "built $built · ${BuildConfig.GIT_SHA}",
                color = c.textMuted, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/**
 * Under an access link that is still off. From Android 13 an app installed from a downloaded file
 * cannot be given notification or accessibility access until it is allowed in App info, and no app
 * can tell that it has been blocked, so this shows whenever the access is off. Android only offers
 * the allow after a first refused attempt, hence "try" first. A Play install is exempt.
 */
@Composable
private fun RestrictedSettingsHint() {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val fromPlay = remember {
        runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName == "com.android.vending" }
            .getOrDefault(false)
    }
    if (fromPlay) return
    Text(
        "If Android says this is restricted, open App info, tap ⋮, choose Allow restricted settings, then try again.",
        color = c.textSecondary, fontSize = 12.sp,
    )
    Text(
        "Open App info",
        color = c.accent, fontSize = 12.sp,
        modifier = Modifier
            .clickable {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
            .padding(vertical = 4.dp),
    )
}

/** A group's heading in the sheet, in the style of Backup's. */
@Composable
private fun SectionLabel(text: String) {
    Text(text, color = GlanceTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = GlanceTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 14.sp)
            Text(subtitle, color = c.textSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private const val REQUEST_CONTACTS = 4101

/**
 * The prominent disclosure before the accessibility service is turned on: what it is for, what
 * it does not do, and that Android's own page comes next. Play requires one for any
 * accessibility service that is not an accessibility tool.
 */
@Composable
private fun LockDisclosure(onContinue: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Double-tap to lock") },
        text = {
            Text(
                "Android lets a home app lock the screen, with fingerprint and face unlock still working, " +
                    "only through an accessibility service. GLANCElauncher's does one thing: lock the screen " +
                    "when you double-tap an empty part of Home. It does not read the screen, see what you type, " +
                    "or collect or share any data.\n\nNext, Android's Accessibility settings open: turn on " +
                    "\"GLANCElauncher: double-tap to lock\" there.",
            )
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
