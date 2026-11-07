package app.glance.launcher.prefs

import android.content.Context
import android.content.SharedPreferences
import app.glance.launcher.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Launcher-only settings, in SharedPreferences. Each value is exposed as a StateFlow so
 * Compose can observe it; writes are synchronous so a setting survives the launcher being
 * killed the moment after it changed.
 */
class LauncherPrefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("glance_launcher", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dockEnabled = MutableStateFlow(sp.getBoolean(KEY_DOCK, true))
    val dockEnabled: StateFlow<Boolean> = _dockEnabled.asStateFlow()

    private val _dockShowsRecents = MutableStateFlow(sp.getBoolean(KEY_DOCK_RECENTS, false))
    val dockShowsRecents: StateFlow<Boolean> = _dockShowsRecents.asStateFlow()

    private val _hiddenApps = MutableStateFlow(sp.getStringSet(KEY_HIDDEN, emptySet())!!.toSet())
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()

    private val _homePins = MutableStateFlow(readList(KEY_HOME_PINS))
    /** Component keys ("package/activity") pinned to Home's middle area, in order. */
    val homePins: StateFlow<List<String>> = _homePins.asStateFlow()

    private val _dockPins = MutableStateFlow(readList(KEY_DOCK_PINS))
    val dockPins: StateFlow<List<String>> = _dockPins.asStateFlow()

    private val _use24HourClock = MutableStateFlow<Boolean?>(if (sp.contains(KEY_24H)) sp.getBoolean(KEY_24H, false) else null)
    /** Null means "follow dayGLANCE". */
    val use24HourClock: StateFlow<Boolean?> = _use24HourClock.asStateFlow()

    private val _dockShowsLabels = MutableStateFlow(sp.getBoolean(KEY_DOCK_LABELS, true))
    /** App names under the dock icons. Off makes the icons larger inside the same bar height. */
    val dockShowsLabels: StateFlow<Boolean> = _dockShowsLabels.asStateFlow()

    private val _hideStatusBar = MutableStateFlow(sp.getBoolean(KEY_HIDE_STATUS_BAR, false))
    val hideStatusBar: StateFlow<Boolean> = _hideStatusBar.asStateFlow()

    private val _dialLocked = MutableStateFlow(sp.getBoolean(KEY_DIAL_LOCKED, false))
    /** Locked: the dial shows and inspects but blocks cannot be moved, resized or created. */
    val dialLocked: StateFlow<Boolean> = _dialLocked.asStateFlow()

    private val _launchHistory = MutableStateFlow(LaunchHistory.parse(sp.getString(KEY_LAUNCH_HISTORY, null)))
    /** Apps opened from the launcher: the drawer's recent row and search's suggestions. */
    val launchHistory: StateFlow<LaunchHistory> = _launchHistory.asStateFlow()

    private val _recentSearches = MutableStateFlow(readList(KEY_RECENT_SEARCHES))
    /** Queries that led somewhere, newest first, offered when search opens empty. */
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _drawerShowsLabels = MutableStateFlow(sp.getBoolean(KEY_DRAWER_LABELS, true))
    /** App names under the drawer's icons. */
    val drawerShowsLabels: StateFlow<Boolean> = _drawerShowsLabels.asStateFlow()

    private val _contactsInSearch = MutableStateFlow(sp.getBoolean(KEY_CONTACTS_IN_SEARCH, false))
    /** Phone contacts in search results. Off by default: it needs the contacts permission. */
    val contactsInSearch: StateFlow<Boolean> = _contactsInSearch.asStateFlow()

    private val _solidWallpaper = MutableStateFlow(readSolidWallpaper())
    /**
     * Keep the device's home wallpaper a solid dayGLANCE background colour. Off by default from
     * 1.0.0, since turning it off cannot bring the user's wallpaper back (decision 15).
     */
    val solidWallpaper: StateFlow<Boolean> = _solidWallpaper.asStateFlow()

    private val _notificationDots = MutableStateFlow(sp.getBoolean(KEY_NOTIFICATION_DOTS, true))
    /** A dot on the icons of apps with notifications. Default on; it shows nothing until access is granted. */
    val notificationDots: StateFlow<Boolean> = _notificationDots.asStateFlow()

    private val _themedIcons = MutableStateFlow(sp.getBoolean(KEY_THEMED_ICONS, false))
    /** Apps with a monochrome icon drawn in the GLANCE palette (Android 13+). Default off, as Android's own. */
    val themedIcons: StateFlow<Boolean> = _themedIcons.asStateFlow()

    private val _doubleTapToLock = MutableStateFlow(sp.getBoolean(KEY_DOUBLE_TAP_LOCK, false))
    /** Double-tap Home's empty space to lock (Android 9+, through an accessibility service). Default off. */
    val doubleTapToLock: StateFlow<Boolean> = _doubleTapToLock.asStateFlow()

    private val _demoData = MutableStateFlow(BuildConfig.DEBUG && sp.getBoolean(KEY_DEMO_DATA, false))
    /**
     * Debug builds only: Home and Planning run on the seeded demo, for screenshots
     * ([app.glance.launcher.data.ProviderRepository]). A release build reads it as off whatever
     * is stored, and the settings file does not carry it.
     */
    val demoData: StateFlow<Boolean> = _demoData.asStateFlow()

    /** The ARGB colour last written as the wallpaper, so it is not rewritten on every launch. */
    var appliedWallpaperArgb: Int?
        get() = if (sp.contains(KEY_WALLPAPER_ARGB)) sp.getInt(KEY_WALLPAPER_ARGB, 0) else null
        set(value) {
            val e = sp.edit()
            if (value == null) e.remove(KEY_WALLPAPER_ARGB) else e.putInt(KEY_WALLPAPER_ARGB, value)
            e.commit()
        }

    fun setThemeMode(mode: ThemeMode) {
        sp.edit().putString(KEY_THEME, mode.name).commit()
        _themeMode.value = mode
    }

    fun setDockEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_DOCK, enabled).commit()
        _dockEnabled.value = enabled
    }

    fun setDockShowsRecents(show: Boolean) {
        sp.edit().putBoolean(KEY_DOCK_RECENTS, show).commit()
        _dockShowsRecents.value = show
    }

    fun setUse24HourClock(value: Boolean?) {
        val e = sp.edit()
        if (value == null) e.remove(KEY_24H) else e.putBoolean(KEY_24H, value)
        e.commit()
        _use24HourClock.value = value
    }

    fun setHidden(componentKey: String, hidden: Boolean) {
        val next = if (hidden) _hiddenApps.value + componentKey else _hiddenApps.value - componentKey
        sp.edit().putStringSet(KEY_HIDDEN, next).commit()
        _hiddenApps.value = next
    }

    fun setHomePins(keys: List<String>) {
        writeList(KEY_HOME_PINS, keys)
        _homePins.value = keys
    }

    /** Adds a pin at the end of the row unless it is there already (an "Add to Home screen" request). */
    fun addHomePin(key: String) {
        val cur = _homePins.value
        if (key !in cur) setHomePins(cur + key)
    }

    fun toggleHomePin(componentKey: String) {
        val cur = _homePins.value
        setHomePins(if (componentKey in cur) cur - componentKey else cur + componentKey)
    }

    fun setDockPins(keys: List<String>) {
        writeList(KEY_DOCK_PINS, keys)
        _dockPins.value = keys
    }

    fun toggleDockPin(componentKey: String) {
        val cur = _dockPins.value
        setDockPins(if (componentKey in cur) cur - componentKey else cur + componentKey)
    }

    fun setDockShowsLabels(show: Boolean) {
        sp.edit().putBoolean(KEY_DOCK_LABELS, show).commit()
        _dockShowsLabels.value = show
    }

    fun setHideStatusBar(hide: Boolean) {
        sp.edit().putBoolean(KEY_HIDE_STATUS_BAR, hide).commit()
        _hideStatusBar.value = hide
    }

    /** The SCHED agenda's filters and show/hide toggles, as last set. */
    val schedView: SchedViewPrefs get() = SchedViewPrefs.parse(sp.getString(KEY_SCHED_VIEW, null))

    fun setSchedView(view: SchedViewPrefs) {
        sp.edit().putString(KEY_SCHED_VIEW, view.serialise()).commit()
    }

    fun setDialLocked(locked: Boolean) {
        sp.edit().putBoolean(KEY_DIAL_LOCKED, locked).commit()
        _dialLocked.value = locked
    }

    fun recordLaunch(componentKey: String, now: Long = System.currentTimeMillis()) {
        val next = _launchHistory.value.record(componentKey, now)
        sp.edit().putString(KEY_LAUNCH_HISTORY, next.serialise()).apply()
        _launchHistory.value = next
    }

    fun recordSearch(query: String) {
        val next = RecentSearches.add(_recentSearches.value, query)
        if (next == _recentSearches.value) return
        writeList(KEY_RECENT_SEARCHES, next)
        _recentSearches.value = next
    }

    fun clearRecentSearches() {
        writeList(KEY_RECENT_SEARCHES, emptyList())
        _recentSearches.value = emptyList()
    }

    fun setContactsInSearch(on: Boolean) {
        sp.edit().putBoolean(KEY_CONTACTS_IN_SEARCH, on).commit()
        _contactsInSearch.value = on
    }

    fun setDrawerShowsLabels(show: Boolean) {
        sp.edit().putBoolean(KEY_DRAWER_LABELS, show).commit()
        _drawerShowsLabels.value = show
    }

    fun setSolidWallpaper(enabled: Boolean) {
        sp.edit().putBoolean(KEY_SOLID_WALLPAPER, enabled).commit()
        _solidWallpaper.value = enabled
    }

    fun setNotificationDots(enabled: Boolean) {
        sp.edit().putBoolean(KEY_NOTIFICATION_DOTS, enabled).commit()
        _notificationDots.value = enabled
    }

    fun setThemedIcons(enabled: Boolean) {
        sp.edit().putBoolean(KEY_THEMED_ICONS, enabled).commit()
        _themedIcons.value = enabled
    }

    fun setDoubleTapToLock(enabled: Boolean) {
        sp.edit().putBoolean(KEY_DOUBLE_TAP_LOCK, enabled).commit()
        _doubleTapToLock.value = enabled
    }

    fun setDemoData(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        sp.edit().putBoolean(KEY_DEMO_DATA, enabled).commit()
        _demoData.value = enabled
    }

    /** Every setting [SettingsBackup] carries that is set here, typed per its [SettingsBackup.Kind]. */
    fun exportValues(): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((key, field) in SettingsBackup.FIELDS) {
            if (!sp.contains(key)) continue
            out[key] = when (field.kind) {
                SettingsBackup.Kind.BOOL -> sp.getBoolean(key, false)
                SettingsBackup.Kind.STRING -> sp.getString(key, null) ?: continue
                SettingsBackup.Kind.LIST -> readList(key)
                SettingsBackup.Kind.SET -> sp.getStringSet(key, emptySet())!!.toSet()
            }
        }
        return out
    }

    /**
     * Replaces every setting [SettingsBackup] carries with [values] in one commit: a setting
     * missing from [values] goes back to its default. Then every flow is read again, so what is
     * on screen follows at once.
     */
    fun importValues(values: Map<String, Any>) {
        val e = sp.edit()
        for ((key, field) in SettingsBackup.FIELDS) {
            val v = values[key]
            if (v == null) { e.remove(key); continue }
            when (field.kind) {
                SettingsBackup.Kind.BOOL -> e.putBoolean(key, v as Boolean)
                SettingsBackup.Kind.STRING -> e.putString(key, v as String)
                SettingsBackup.Kind.LIST -> e.putString(key, (v as List<*>).joinToString("\n"))
                SettingsBackup.Kind.SET -> e.putStringSet(key, (v as Set<*>).filterIsInstance<String>().toSet())
            }
        }
        // The colour applied here is this device's bookkeeping: cleared so the wallpaper is applied
        // again from the imported settings (or left alone, as turning the toggle off does).
        e.remove(KEY_WALLPAPER_ARGB)
        e.commit()
        reload()
    }

    private fun reload() {
        _themeMode.value = readTheme()
        _dockEnabled.value = sp.getBoolean(KEY_DOCK, true)
        _dockShowsRecents.value = sp.getBoolean(KEY_DOCK_RECENTS, false)
        _hiddenApps.value = sp.getStringSet(KEY_HIDDEN, emptySet())!!.toSet()
        _homePins.value = readList(KEY_HOME_PINS)
        _dockPins.value = readList(KEY_DOCK_PINS)
        _use24HourClock.value = if (sp.contains(KEY_24H)) sp.getBoolean(KEY_24H, false) else null
        _dockShowsLabels.value = sp.getBoolean(KEY_DOCK_LABELS, true)
        _hideStatusBar.value = sp.getBoolean(KEY_HIDE_STATUS_BAR, false)
        _dialLocked.value = sp.getBoolean(KEY_DIAL_LOCKED, false)
        _launchHistory.value = LaunchHistory.parse(sp.getString(KEY_LAUNCH_HISTORY, null))
        _recentSearches.value = readList(KEY_RECENT_SEARCHES)
        _drawerShowsLabels.value = sp.getBoolean(KEY_DRAWER_LABELS, true)
        _contactsInSearch.value = sp.getBoolean(KEY_CONTACTS_IN_SEARCH, false)
        _solidWallpaper.value = readSolidWallpaper()
        _notificationDots.value = sp.getBoolean(KEY_NOTIFICATION_DOTS, true)
        _themedIcons.value = sp.getBoolean(KEY_THEMED_ICONS, false)
        _doubleTapToLock.value = sp.getBoolean(KEY_DOUBLE_TAP_LOCK, false)
    }

    /**
     * Stored, or else the default: off, except on an install from before 1.0.0 that never touched
     * the switch and was painted under the old default (it has an applied colour). Its wallpaper
     * is already solid, so it stays on rather than freezing on whatever colour it was last given.
     */
    private fun readSolidWallpaper(): Boolean = solidWallpaperOn(
        stored = if (sp.contains(KEY_SOLID_WALLPAPER)) sp.getBoolean(KEY_SOLID_WALLPAPER, false) else null,
        painted = sp.contains(KEY_WALLPAPER_ARGB),
    )

    private fun readTheme(): ThemeMode =
        ThemeMode.values().firstOrNull { it.name == sp.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM

    private fun readList(key: String): List<String> =
        sp.getString(key, "")!!.split('\n').filter { it.isNotBlank() }

    private fun writeList(key: String, values: List<String>) {
        sp.edit().putString(key, values.joinToString("\n")).commit()
    }

    /** The SharedPreferences keys; [SettingsBackup.FIELDS] names each setting by its key. */
    internal companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DOCK = "dock_enabled"
        const val KEY_DOCK_RECENTS = "dock_recents"
        const val KEY_HIDDEN = "hidden_apps"
        const val KEY_HOME_PINS = "home_pins"
        const val KEY_DOCK_PINS = "dock_pins"
        const val KEY_24H = "use_24h"
        const val KEY_SOLID_WALLPAPER = "solid_wallpaper"
        const val KEY_NOTIFICATION_DOTS = "notification_dots"
        const val KEY_THEMED_ICONS = "themed_icons"
        const val KEY_SCHED_VIEW = "sched_view"
        const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        const val KEY_DEMO_DATA = "demo_data"

        /** [readSolidWallpaper]'s rule, pure for the JVM tests: the stored choice, else on only if painted. */
        fun solidWallpaperOn(stored: Boolean?, painted: Boolean): Boolean = stored ?: painted
        const val KEY_DOCK_LABELS = "dock_labels"
        const val KEY_LAUNCH_HISTORY = "launch_history"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_DRAWER_LABELS = "drawer_labels"
        const val KEY_CONTACTS_IN_SEARCH = "contacts_in_search"
        const val KEY_DIAL_LOCKED = "dial_locked"
        const val KEY_HIDE_STATUS_BAR = "hide_status_bar"
        const val KEY_WALLPAPER_ARGB = "wallpaper_argb"
    }
}
