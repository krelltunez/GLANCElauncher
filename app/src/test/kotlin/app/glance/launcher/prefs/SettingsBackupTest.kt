package app.glance.launcher.prefs

import org.json.JSONObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsBackupTest {
    private val at = Instant.parse("2026-10-25T05:00:00Z")

    private val sample: Map<String, Any> = linkedMapOf(
        "theme_mode" to "DARK",
        "dock_enabled" to false,
        "use_24h" to true,
        "home_pins" to listOf("com.spotify.music/.MainActivity", "com.dayglance.app/.MainActivity"),
        "hidden_apps" to setOf("com.example.a/.Main", "com.example.b/.Main"),
        "sched_view" to "tags\twork\nhideCompleted\ttrue",
        "launch_history" to "com.spotify.music/.MainActivity\t12\t1761368400000",
        "recent_searches" to listOf("timer 10 minutes", "spotify"),
    )

    private fun ok(text: String) = assertIs<SettingsBackup.Decoded.Ok>(SettingsBackup.decode(text))
    private fun invalid(text: String) = assertIs<SettingsBackup.Decoded.Invalid>(SettingsBackup.decode(text)).reason

    @Test
    fun `every kind of setting survives a round trip, with when and by which version`() {
        val d = ok(SettingsBackup.encode(sample, at, "0.1.0"))
        assertEquals(sample, d.values)
        assertEquals(emptyList(), d.skipped)
        assertEquals(at, d.exportedAt)
        assertEquals("0.1.0", d.appVersion)
    }

    @Test
    fun `every stored key is either carried by the file or named as bookkeeping`() {
        // A setting added to LauncherPrefs fails this until it is declared in FIELDS (or NOT_EXPORTED),
        // so a new setting is never silently left out of a backup.
        val keys = LauncherPrefs::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name.startsWith("KEY_") }
            .map { it.isAccessible = true; it.get(null) as String }
            .toSet()
        assertTrue(keys.size > 10, "found the keys: $keys")
        assertEquals(keys, SettingsBackup.FIELDS.keys + SettingsBackup.NOT_EXPORTED)
        assertTrue(SettingsBackup.FIELDS.keys.none { it in SettingsBackup.NOT_EXPORTED })
    }

    @Test
    fun `the wallpaper colour applied on this device and unknown keys are not written`() {
        val text = SettingsBackup.encode(sample + mapOf("wallpaper_argb" to 123, "something_else" to true), at, "0.1.0")
        val settings = JSONObject(text).getJSONObject("settings")
        assertFalse(settings.has("wallpaper_argb"))
        assertFalse(settings.has("something_else"))
        assertEquals(sample.keys, settings.keys().asSequence().toSet())
    }

    @Test
    fun `a file that is not ours, damaged, or from a newer launcher is refused`() {
        assertEquals("This is not a GLANCElauncher settings file", invalid("not json"))
        assertEquals("This is not a GLANCElauncher settings file", invalid("""{"format":"something-else","version":1,"settings":{}}"""))
        assertEquals("This is not a GLANCElauncher settings file", invalid("""{"format":"glance-launcher-settings","settings":{}}"""))
        assertEquals("This settings file is damaged", invalid("""{"format":"glance-launcher-settings","version":1}"""))
        assertTrue(invalid("""{"format":"glance-launcher-settings","version":2,"settings":{}}""").contains("newer"))
    }

    @Test
    fun `a value of the wrong type is skipped by name, and unknown keys are ignored`() {
        val d = ok(
            """{"format":"glance-launcher-settings","version":1,"settings":{
                "theme_mode":"PURPLE", "dock_enabled":"yes", "home_pins":["a/.A", 3],
                "hidden_apps":"a/.A", "dial_locked":true, "from_the_future":1
            }}""",
        )
        assertEquals(mapOf<String, Any>("dial_locked" to true), d.values)
        assertEquals(listOf("theme_mode", "dock_enabled", "home_pins", "hidden_apps"), d.skipped)
        assertEquals(null, d.exportedAt)
    }

    @Test
    fun `blank list entries and entries that would break the stored list are dropped`() {
        val d = ok("""{"format":"glance-launcher-settings","version":1,"settings":{"dock_pins":["a/.A", " ", "b/.B\nc/.C", "d/.D"]}}""")
        assertEquals(listOf("a/.A", "d/.D"), d.values["dock_pins"])
    }
}
