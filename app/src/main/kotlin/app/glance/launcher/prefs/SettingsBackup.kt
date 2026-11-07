package app.glance.launcher.prefs

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * The launcher's settings as a file, so they survive an uninstall (a build signed with another
 * key cannot install over this one) and move between devices. Pure: tested on the JVM.
 *
 * A file is a whole snapshot. Importing one replaces every setting it covers, so a setting the
 * file does not carry goes back to its default, the state the exporting device had it in.
 * Permissions and the Home-app choice are Android's and are not in it.
 */
object SettingsBackup {
    const val FORMAT = "glance-launcher-settings"
    const val VERSION = 1

    /** How a setting is stored in SharedPreferences, and so how it is written in the file. */
    enum class Kind { BOOL, STRING, LIST, SET }

    data class Field(val kind: Kind, val valid: (String) -> Boolean = { true })

    /** Every setting the file carries, by its SharedPreferences key. */
    val FIELDS: Map<String, Field> = linkedMapOf(
        LauncherPrefs.KEY_THEME to Field(Kind.STRING) { v -> ThemeMode.values().any { it.name == v } },
        LauncherPrefs.KEY_DOCK to Field(Kind.BOOL),
        LauncherPrefs.KEY_DOCK_RECENTS to Field(Kind.BOOL),
        LauncherPrefs.KEY_DOCK_LABELS to Field(Kind.BOOL),
        LauncherPrefs.KEY_DOCK_PINS to Field(Kind.LIST),
        LauncherPrefs.KEY_HOME_PINS to Field(Kind.LIST),
        LauncherPrefs.KEY_HIDDEN to Field(Kind.SET),
        LauncherPrefs.KEY_24H to Field(Kind.BOOL),
        LauncherPrefs.KEY_SOLID_WALLPAPER to Field(Kind.BOOL),
        LauncherPrefs.KEY_HIDE_STATUS_BAR to Field(Kind.BOOL),
        LauncherPrefs.KEY_NOTIFICATION_DOTS to Field(Kind.BOOL),
        LauncherPrefs.KEY_THEMED_ICONS to Field(Kind.BOOL),
        LauncherPrefs.KEY_DOUBLE_TAP_LOCK to Field(Kind.BOOL),
        LauncherPrefs.KEY_DRAWER_LABELS to Field(Kind.BOOL),
        LauncherPrefs.KEY_CONTACTS_IN_SEARCH to Field(Kind.BOOL),
        LauncherPrefs.KEY_DIAL_LOCKED to Field(Kind.BOOL),
        LauncherPrefs.KEY_SCHED_VIEW to Field(Kind.STRING),
        LauncherPrefs.KEY_LAUNCH_HISTORY to Field(Kind.STRING),
        LauncherPrefs.KEY_RECENT_SEARCHES to Field(Kind.LIST),
    )

    /** Keys that are bookkeeping rather than settings: the wallpaper colour last applied on THIS device. */
    val NOT_EXPORTED: Set<String> = setOf(LauncherPrefs.KEY_WALLPAPER_ARGB, LauncherPrefs.KEY_DEMO_DATA)

    sealed class Decoded {
        /** [values] by key, typed per [Kind]; [skipped] names the known keys whose value was unreadable. */
        data class Ok(val values: Map<String, Any>, val skipped: List<String>, val exportedAt: Instant?, val appVersion: String?) : Decoded()
        data class Invalid(val reason: String) : Decoded()
    }

    /**
     * The file for [values] (by key: Boolean, String, List<String> or Set<String>, as [FIELDS]
     * says). Keys outside [FIELDS] and values of the wrong type are left out.
     */
    fun encode(values: Map<String, Any?>, exportedAt: Instant, appVersion: String): String {
        val settings = JSONObject()
        for ((key, field) in FIELDS) {
            val v = values[key] ?: continue
            when (field.kind) {
                Kind.BOOL -> (v as? Boolean)?.let { settings.put(key, it) }
                Kind.STRING -> (v as? String)?.let { settings.put(key, it) }
                Kind.LIST -> (v as? List<*>)?.let { settings.put(key, JSONArray(it.filterIsInstance<String>())) }
                Kind.SET -> (v as? Set<*>)?.let { settings.put(key, JSONArray(it.filterIsInstance<String>().sorted())) }
            }
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt.toString())
            .put("appVersion", appVersion)
            .put("settings", settings)
            .toString(2)
    }

    fun decode(text: String): Decoded {
        val root = runCatching { JSONObject(text) }.getOrNull()
        if (root == null || root.optString("format") != FORMAT) return Decoded.Invalid("This is not a GLANCElauncher settings file")
        val version = root.optInt("version", 0)
        if (version < 1) return Decoded.Invalid("This is not a GLANCElauncher settings file")
        if (version > VERSION) return Decoded.Invalid("This file was made by a newer GLANCElauncher: update the launcher first")
        val settings = root.optJSONObject("settings") ?: return Decoded.Invalid("This settings file is damaged")
        val values = LinkedHashMap<String, Any>()
        val skipped = ArrayList<String>()
        for ((key, field) in FIELDS) {
            if (!settings.has(key)) continue
            val v = read(settings.opt(key), field)
            if (v == null) skipped += key else values[key] = v
        }
        val exportedAt = root.optString("exportedAt").takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val appVersion = root.optString("appVersion").takeIf { it.isNotEmpty() }
        return Decoded.Ok(values, skipped, exportedAt, appVersion)
    }

    private fun read(v: Any?, field: Field): Any? = when (field.kind) {
        Kind.BOOL -> v as? Boolean
        Kind.STRING -> (v as? String)?.takeIf(field.valid)
        Kind.LIST -> strings(v)
        Kind.SET -> strings(v)?.toSet()
    }

    /** A JSON array of strings, blanks dropped; null when it is anything else. */
    private fun strings(v: Any?): List<String>? {
        val arr = v as? JSONArray ?: return null
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.opt(i) as? String ?: return null
            if (s.isNotBlank() && !s.contains('\n')) out += s
        }
        return out
    }
}
