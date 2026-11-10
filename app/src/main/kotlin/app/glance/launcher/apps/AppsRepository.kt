package app.glance.launcher.apps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.glance.launcher.prefs.LauncherPrefs
import app.glance.launcher.system.LaunchOptions
import app.glance.launcher.ui.common.drawableToBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One launchable activity. [key] is "package/class" and is what the prefs store. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
    /** Null means the current user (tests build entries without a UserHandle). */
    val user: UserHandle? = null,
) {
    val key: String get() = "$packageName/$className"
    val component: ComponentName get() = ComponentName(packageName, className)
}

/**
 * Installed apps from [LauncherApps], kept current through its package-change callbacks. Icons are loaded lazily and cached by component key.
 * Also every app's shortcuts ([shortcuts]), which search offers as actions.
 */
class AppsRepository(private val context: Context, private val prefs: LauncherPrefs) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    /** Every launchable activity, alphabetical, including hidden ones. */
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /**
     * Rendered icon bitmaps by cache key ([app.glance.launcher.ui.theme.ThemedIcons.cacheKey], or
     * [SHORTCUT_BITMAP_PREFIX] and the shortcut's key), so a scrolling grid never re-decodes. Read
     * it directly for a first frame; [iconBitmap] fills it.
     */
    val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
    /** One lock per cache key: an icon on screen twice at once (the drawer's recent row and its list) is rendered once. */
    private val renderLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    private val _iconEpoch = MutableStateFlow(0)
    /**
     * Moves on whenever [forgetIcons] drops an app's bitmaps, so icons on screen look again: the
     * dropped ones redraw from the app's current icon, the rest find theirs still cached.
     */
    val iconEpoch: StateFlow<Int> = _iconEpoch.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = changed(*packageNames.orEmpty())
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = changed(*packageNames.orEmpty())
        // An app published, changed or removed shortcuts (a messaging app's conversations change often).
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = refreshShortcuts()
    }

    /** An app was installed, updated or removed: its icon may have changed with it. */
    private fun changed(vararg packageNames: String?) {
        forgetIcons(packageNames.filterNotNull())
        refresh()
    }

    /**
     * Drops the rendered icons of [packageNames]' apps (plain and themed). An update can change an
     * app's icon; without this the launcher kept the old one, or a bad render of it, until restarted.
     */
    fun forgetIcons(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val removed = bitmapCache.keys.removeAll { key -> packageNames.any { IconKeys.isOfPackage(key, it) } }
        if (removed) _iconEpoch.value += 1
    }

    fun refresh() {
        val me = Process.myUserHandle()
        val list = launcherApps.getActivityList(null, me)
            .filter { it.applicationInfo.packageName != context.packageName }
            .map { AppEntry(it.label.toString(), it.applicationInfo.packageName, it.name, it.user) }
            .sortedBy { it.label.lowercase() }
        _apps.value = list
        // Shortcuts carry their app's label, and an app that went away takes its shortcuts with it.
        refreshShortcuts()
    }

    /**
     * The app's icon, loaded fresh on each call: a new Drawable for this caller alone. Not cached,
     * because a Drawable is not safe to draw from two threads, and an AdaptiveIconDrawable shows
     * it: it fills a bitmap it keeps with black, draws its two layers into it, and paints the icon
     * from that bitmap, so a second thread drawing the same instance caught it half done (an
     * all-black or garbled icon, which [bitmapCache] then kept). [iconBitmap] renders each app once.
     */
    fun icon(entry: AppEntry): Drawable? {
        val density = context.resources.displayMetrics.densityDpi
        val info = launcherApps.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component),
            entry.user ?: Process.myUserHandle(),
        ) ?: return null
        return info.getIcon(density)
    }

    /**
     * The bitmap cached under [cacheKey], or [load]'s Drawable rendered at [sizePx] and cached.
     * Blocking: call it off the main thread. Renders of one key take turns, so the second finds
     * the first's bitmap; null when [load] has no icon, which is not cached and so tried again.
     */
    fun iconBitmap(cacheKey: String, sizePx: Int, load: () -> Drawable?): ImageBitmap? {
        bitmapCache[cacheKey]?.let { return it }
        synchronized(renderLocks.getOrPut(cacheKey) { Any() }) {
            bitmapCache[cacheKey]?.let { return it }
            val drawable = load() ?: return null
            return drawableToBitmap(drawable, sizePx).asImageBitmap().also { bitmapCache[cacheKey] = it }
        }
    }

    /**
     * The app's monochrome layer tinted [glyph] on a [tile] background, in the system's icon
     * shape: a themed icon ([app.glance.launcher.ui.theme.ThemedIcons]). Null before Android 13,
     * or when the app ships no monochrome layer, which then keeps its own colours.
     */
    fun themedIcon(entry: AppEntry, tile: Int, glyph: Int): Drawable? {
        if (Build.VERSION.SDK_INT < 33) return null
        val adaptive = icon(entry) as? AdaptiveIconDrawable ?: return null
        // A copy: the cached drawable is shared, and tinting it would theme the plain icon too.
        val mono = adaptive.monochrome?.constantState?.newDrawable()?.mutate() ?: return null
        mono.setTint(glyph)
        return AdaptiveIconDrawable(ColorDrawable(tile), mono)
    }

    fun byKey(key: String): AppEntry? = _apps.value.firstOrNull { it.key == key }

    /** Starts the activity. With [adjacent] the system is asked to open it beside the current app (split screen). */
    fun launch(entry: AppEntry, adjacent: Boolean = false) {
        prefs.recordLaunch(entry.key)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (adjacent) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        if (adjacent) {
            runCatching { context.startActivity(intent, LaunchOptions.iconSplash()) }
            return
        }
        try {
            launcherApps.startMainActivity(entry.component, entry.user ?: Process.myUserHandle(), null, LaunchOptions.iconSplash())
        } catch (e: Exception) {
            runCatching { context.startActivity(intent, LaunchOptions.iconSplash()) }
        }
    }

    // region shortcuts

    private val _shortcuts = MutableStateFlow<List<AppShortcut>>(emptyList())
    /**
     * Every enabled shortcut of a launchable app, in the apps' own order: what each app offers on
     * a long-press of its icon (manifest and dynamic), the conversations messaging apps keep for
     * the launcher (cached, Android 11+), and any pinned here. Empty unless GLANCElauncher is the
     * default home app: only that app may read other apps' shortcuts.
     */
    val shortcuts: StateFlow<List<AppShortcut>> = _shortcuts.asStateFlow()

    /** The platform objects behind [shortcuts], for their icons and the launch; replaced whole on each load. */
    @Volatile private var shortcutInfos: Map<String, ShortcutInfo> = emptyMap()
    /** One load at a time, off the main thread: the query is a binder call across every app. */
    private val shortcutLoader = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val shortcutLoadQueued = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Reloads [shortcuts] in the background; calls while one is waiting to run fold into it. */
    fun refreshShortcuts() {
        if (!shortcutLoadQueued.compareAndSet(false, true)) return
        shortcutLoader.execute {
            shortcutLoadQueued.set(false)
            loadShortcuts()
        }
    }

    private fun loadShortcuts() {
        val infos = try {
            if (!launcherApps.hasShortcutHostPermission()) emptyList()
            else launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(SHORTCUT_QUERY_FLAGS), Process.myUserHandle()).orEmpty()
        } catch (e: Exception) {
            // SecurityException: no longer the home app; IllegalStateException: the user is still locked.
            emptyList()
        }
        val appLabels = HashMap<String, String>()
        for (a in _apps.value) appLabels.putIfAbsent(a.packageName, a.label)
        val byKey = LinkedHashMap<String, ShortcutInfo>()
        val list = ArrayList<AppShortcut>()
        for (info in infos) {
            if (!info.isEnabled) continue
            if (Build.VERSION.SDK_INT >= 33 && info.isExcludedFromSurfaces(ShortcutInfo.SURFACE_LAUNCHER)) continue
            // A shortcut of an app with no launcher activity has no app to show it under.
            val appLabel = appLabels[info.`package`] ?: continue
            val short = info.shortLabel?.toString()?.trim().orEmpty()
            val long = info.longLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val label = short.ifEmpty { long } ?: continue
            val shortcut = AppShortcut(
                packageName = info.`package`,
                id = info.id,
                shortLabel = label,
                longLabel = long,
                appLabel = appLabel,
                rank = info.rank,
                // Cached: kept for the launcher after the app dropped it (Android 11+), which only conversations are.
                conversation = ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION in info.categories.orEmpty() ||
                    (Build.VERSION.SDK_INT >= 30 && info.isCached),
                pinned = info.isPinned,
                listed = info.isDeclaredInManifest || info.isDynamic,
                user = info.userHandle,
            )
            // One query can list a shortcut twice (dynamic and pinned): keep the first.
            if (byKey.putIfAbsent(shortcut.key, info) == null) list += shortcut
        }
        shortcutInfos = byKey
        // A shortcut keeps its id when its icon changes (a contact's new photo): drop the cached
        // ones, which the rows reload as they draw.
        bitmapCache.keys.removeAll { it.startsWith(SHORTCUT_BITMAP_PREFIX) }
        _shortcuts.value = list
    }

    /**
     * Keeps Android's list of shortcuts pinned by this launcher equal to the Home pins
     * ([Pins.pinChanges]): a shortcut unpinned from Home is released, so its app may remove it.
     * Runs on the loader after any load already queued, so it compares with a current list.
     */
    fun syncPinnedShortcuts(homeKeys: List<String>) {
        shortcutLoader.execute {
            val pinnedNow = _shortcuts.value.filter { it.pinned }.groupBy({ it.packageName }, { it.id }).mapValues { it.value.toSet() }
            val changes = Pins.pinChanges(homeKeys, pinnedNow)
            if (changes.isEmpty()) return@execute
            for ((pkg, ids) in changes) {
                // A pin Android refuses (an id the app has since removed, no longer the home app)
                // leaves that package as it was; the next change tries again.
                runCatching { launcherApps.pinShortcuts(pkg, ids.toList(), Process.myUserHandle()) }
            }
            loadShortcuts()
        }
    }

    /** The shortcut's own icon, loaded fresh on each call, as [icon] is and for the same reason. */
    fun shortcutIcon(shortcut: AppShortcut): Drawable? {
        val info = shortcutInfos[shortcut.key] ?: return null
        return runCatching { launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi) }.getOrNull()
    }

    /**
     * Starts the shortcut. One that has gone since the list loaded (an app update, a conversation
     * deleted) or cannot start opens its app instead, which is where the user was going.
     */
    fun launchShortcut(shortcut: AppShortcut) {
        try {
            val info = shortcutInfos[shortcut.key]
            if (info != null) launcherApps.startShortcut(info, null, LaunchOptions.iconSplash())
            else launcherApps.startShortcut(shortcut.packageName, shortcut.id, null, LaunchOptions.iconSplash(), shortcut.user ?: Process.myUserHandle())
        } catch (e: Exception) {
            _apps.value.firstOrNull { it.packageName == shortcut.packageName }?.let { launch(it) }
            refreshShortcuts()
        }
    }

    /** Opens a Home pin: the app, or the shortcut an app asked to put on Home. */
    fun launch(pin: Pin) = when (pin) {
        is Pin.App -> launch(pin.entry)
        is Pin.Shortcut -> launchShortcut(pin.shortcut)
    }

    // endregion

    fun isPackageInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    // region recents

    /** Whether the user has granted usage access, which lets the dock's recents count apps opened elsewhere. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * When each package was last used over the past two days, by package name: the part of the
     * dock's recents ([DockRecents]) the launcher cannot see itself. Empty without usage access.
     */
    fun usageLastUsed(): Map<String, Long> {
        if (!hasUsageAccess()) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 2L * 24 * 60 * 60 * 1000, now) ?: return emptyMap()
        val lastUsed = HashMap<String, Long>()
        for (s in stats) {
            if (s.lastTimeUsed <= 0) continue
            lastUsed[s.packageName] = maxOf(lastUsed[s.packageName] ?: 0L, s.lastTimeUsed)
        }
        return lastUsed
    }

    // endregion

    // Last, after every property above: Kotlin runs initializers in source order, and the first
    // refresh reaches the shortcut loader.
    init {
        refresh()
        // LauncherApps reports installs, removals and changes; no package broadcast receiver needed.
        launcherApps.registerCallback(callback)
    }

    companion object {
        /** [bitmapCache] keys of shortcut icons ([app.glance.launcher.ui.common.ShortcutIcon]). */
        const val SHORTCUT_BITMAP_PREFIX = IconKeys.SHORTCUT_PREFIX
        private val SHORTCUT_QUERY_FLAGS: Int = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
            (if (Build.VERSION.SDK_INT >= 30) LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED else 0)
    }
}
