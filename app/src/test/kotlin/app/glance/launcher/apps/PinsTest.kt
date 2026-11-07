package app.glance.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home pins of both kinds, and keeping Android's pin list in step with them. */
class PinsTest {
    private val chrome = AppEntry("Chrome", "com.android.chrome", "Main")
    private val maps = AppEntry("Maps", "com.google.maps", "Main")
    private val vault = AppEntry("Vault", "com.secret", "Main")
    private val apps = listOf(chrome, maps, vault)
    private fun sc(app: AppEntry, id: String, label: String, rank: Int = 0, listed: Boolean = true) =
        AppShortcut(app.packageName, id, label, null, app.label, rank, listed = listed)

    private val page = sc(chrome, "page#news", "News site", listed = false)
    private val home = sc(maps, "home", "Home")
    private val secret = sc(vault, "open", "Open vault")

    @Test
    fun `a shortcut key round-trips, an id may hold a hash, an app key is not a shortcut`() {
        val key = Pins.keyOf(page.packageName, page.id)
        assertEquals("shortcut:com.android.chrome#page#news", key)
        assertEquals("com.android.chrome" to "page#news", Pins.shortcutOf(key))
        assertNull(Pins.shortcutOf(chrome.key))
        assertNull(Pins.shortcutOf("shortcut:#x"))
        assertNull(Pins.shortcutOf("shortcut:pkg#"))
    }

    @Test
    fun `pins resolve in their order, apps and shortcuts alike, and what is missing or hidden is left out`() {
        val keys = listOf(
            Pins.keyOf(home.packageName, home.id),
            chrome.key,
            Pins.keyOf(page.packageName, page.id),
            "gone/app.Main",
            Pins.keyOf("com.google.maps", "not-loaded"),
            Pins.keyOf(secret.packageName, secret.id),
        )
        val pins = Pins.resolve(keys, apps, listOf(home, page, secret), hiddenApps = setOf(vault.key))
        assertEquals(listOf("Home", "Chrome", "News site"), pins.map { it.label })
        assertEquals(listOf(Pin.Shortcut(home), Pin.App(chrome), Pin.Shortcut(page)), pins)
        assertEquals(keys[0], pins[0].key)
    }

    @Test
    fun `Android's pin list follows Home, adding, releasing and leaving matches alone`() {
        val homeKeys = listOf(chrome.key, Pins.keyOf(maps.packageName, "home"), Pins.keyOf(maps.packageName, "work"))
        // Maps already right, Chrome's old page unpinned from Home: only Chrome changes, to nothing.
        assertEquals(
            mapOf(chrome.packageName to emptySet<String>()),
            Pins.pinChanges(homeKeys, mapOf(maps.packageName to setOf("home", "work"), chrome.packageName to setOf("page#news"))),
        )
        // A new pin on Home is added to its package's set.
        assertEquals(
            mapOf(maps.packageName to setOf("home", "work")),
            Pins.pinChanges(homeKeys, mapOf(maps.packageName to setOf("home"))),
        )
        assertEquals(emptyMap<String, Set<String>>(), Pins.pinChanges(homeKeys, mapOf(maps.packageName to setOf("work", "home"))))
    }

    @Test
    fun `an app's menu lists its own actions in its order, at most four, not pins or other apps'`() {
        val actions = (5 downTo 1).map { sc(maps, "a$it", "Action $it", rank = it) }
        val menu = ShortcutMenu.forApp(actions + home.copy(rank = 9) + sc(maps, "pinned-only", "Old route", listed = false) + page, maps.packageName)
        assertEquals(listOf("a1", "a2", "a3", "a4"), menu.map { it.id })
        assertEquals(emptyList<AppShortcut>(), ShortcutMenu.forApp(listOf(page), chrome.packageName))
    }
}
