package app.glance.launcher.ui.search

/**
 * One page of the system Settings app that search can open ("bluetooth", "battery"). [action]
 * is the page's public Settings intent action; a device without the page opens Settings itself.
 */
data class SettingsPage(val label: String, val action: String, val keywords: List<String> = emptyList())

/** The Settings pages search offers, and how a query finds them. Pure, tested on the JVM. */
object SettingsPages {
    const val MAIN = "android.settings.SETTINGS"

    val ALL: List<SettingsPage> = listOf(
        SettingsPage("Wi-Fi", "android.settings.WIFI_SETTINGS", listOf("wifi", "wireless", "network")),
        SettingsPage("Bluetooth", "android.settings.BLUETOOTH_SETTINGS", listOf("bt", "pair", "headphones")),
        SettingsPage("Network & internet", "android.settings.WIRELESS_SETTINGS", listOf("mobile", "cellular", "sim")),
        SettingsPage("Airplane mode", "android.settings.AIRPLANE_MODE_SETTINGS", listOf("flight")),
        SettingsPage("Data usage", "android.settings.DATA_USAGE_SETTINGS", listOf("mobile")),
        SettingsPage("VPN", "android.settings.VPN_SETTINGS"),
        SettingsPage("Battery", "android.intent.action.POWER_USAGE_SUMMARY", listOf("power", "usage")),
        SettingsPage("Battery saver", "android.settings.BATTERY_SAVER_SETTINGS", listOf("power", "saver")),
        SettingsPage("Display", "android.settings.DISPLAY_SETTINGS", listOf("brightness", "screen", "font", "dark")),
        SettingsPage("Sound & vibration", "android.settings.SOUND_SETTINGS", listOf("volume", "ringtone", "vibrate")),
        SettingsPage("Do Not Disturb", "android.settings.ZEN_MODE_PRIORITY_SETTINGS", listOf("dnd", "quiet", "silence")),
        SettingsPage("Location", "android.settings.LOCATION_SOURCE_SETTINGS", listOf("gps")),
        SettingsPage("Apps", "android.settings.APPLICATION_SETTINGS", listOf("applications")),
        SettingsPage("Default apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", listOf("browser", "default")),
        SettingsPage("Home app", "android.settings.HOME_SETTINGS", listOf("launcher", "default")),
        SettingsPage("Storage", "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("space", "disk")),
        SettingsPage("Date & time", "android.settings.DATE_SETTINGS", listOf("clock", "timezone", "time")),
        SettingsPage("Languages", "android.settings.LOCALE_SETTINGS", listOf("language", "locale")),
        SettingsPage("Keyboard", "android.settings.INPUT_METHOD_SETTINGS", listOf("input", "typing")),
        SettingsPage("Accessibility", "android.settings.ACCESSIBILITY_SETTINGS", listOf("talkback", "a11y")),
        SettingsPage("Security", "android.settings.SECURITY_SETTINGS", listOf("lock", "password", "pin", "fingerprint", "face")),
        SettingsPage("Privacy", "android.settings.PRIVACY_SETTINGS", listOf("permissions")),
        SettingsPage("Accounts", "android.settings.SYNC_SETTINGS", listOf("sync", "google")),
        SettingsPage("NFC", "android.settings.NFC_SETTINGS", listOf("tap", "pay")),
        SettingsPage("Cast", "android.settings.CAST_SETTINGS", listOf("chromecast", "screen")),
        SettingsPage("Developer options", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", listOf("developer", "usb", "debugging")),
        SettingsPage("About phone", "android.settings.DEVICE_INFO_SETTINGS", listOf("about", "version", "model")),
        SettingsPage("Settings", MAIN),
    )

    /** At most this many pages per query. */
    const val MAX = 3

    /**
     * The pages whose label or keywords each query word starts a word of, label matches first.
     * One letter matches nothing: every page would.
     */
    fun matches(lq: String, pages: List<SettingsPage> = ALL): List<SettingsPage> {
        val tokens = words(lq)
        if (tokens.isEmpty() || lq.trim().length < 2) return emptyList()
        return pages.mapNotNull { page ->
            val labelWords = words(page.label.lowercase())
            val all = labelWords + page.keywords
            when {
                page.label.lowercase().startsWith(lq) -> 0 to page
                tokens.all { t -> labelWords.any { it.startsWith(t) } } -> 1 to page
                tokens.all { t -> all.any { it.startsWith(t) } } -> 2 to page
                else -> null
            }
        }.sortedBy { it.first }.take(MAX).map { it.second }
    }

    private fun words(text: String): List<String> = text.split(' ', '-', '&', '/').filter { it.isNotEmpty() }
}
