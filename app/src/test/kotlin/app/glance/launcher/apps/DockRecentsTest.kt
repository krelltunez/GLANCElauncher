package app.glance.launcher.apps

import app.glance.launcher.prefs.LaunchHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class DockRecentsTest {
    private val mail = AppEntry("Mail", "com.mail", "com.mail.Main")
    private val maps = AppEntry("Maps", "com.maps", "com.maps.Main")
    private val chat = AppEntry("Chat", "com.chat", "com.chat.Main")
    private val chatCompose = AppEntry("Chat compose", "com.chat", "com.chat.Compose")
    private val apps = listOf(chat, chatCompose, mail, maps)

    private fun history(vararg launches: Pair<String, Long>) =
        launches.fold(LaunchHistory()) { h, (key, at) -> h.record(key, at) }

    private fun keys(list: List<AppEntry>) = list.map { it.key }

    @Test
    fun `without usage access the launcher's own launches are the recents, newest first`() {
        val h = history(mail.key to 100L, maps.key to 300L, chat.key to 200L)
        assertEquals(keys(listOf(maps, chat, mail)), keys(DockRecents.pick(h, emptyMap(), apps, emptySet(), 6)))
    }

    @Test
    fun `usage access adds apps opened elsewhere and moves an app by its later use`() {
        val h = history(mail.key to 100L, maps.key to 300L)
        // Chat was opened from a notification at 400; Mail was last used at 500, after its launch here.
        val usage = mapOf("com.chat" to 400L, "com.mail" to 500L)
        assertEquals(keys(listOf(mail, chat, maps)), keys(DockRecents.pick(h, usage, apps, emptySet(), 6)))
    }

    @Test
    fun `a package shows as the activity opened from the launcher, else its first`() {
        val launched = history(chatCompose.key to 100L)
        assertEquals(listOf(chatCompose.key), keys(DockRecents.pick(launched, mapOf("com.chat" to 900L), apps, emptySet(), 6)))
        assertEquals(listOf(chat.key), keys(DockRecents.pick(LaunchHistory(), mapOf("com.chat" to 900L), apps, emptySet(), 6)))
    }

    @Test
    fun `one per package, pins and hidden apps excluded, uninstalled apps dropped, limited`() {
        val h = history(chat.key to 100L, chatCompose.key to 200L, mail.key to 300L, "gone/app.Main" to 400L, maps.key to 50L)
        assertEquals(listOf(chatCompose.key), keys(DockRecents.pick(h, emptyMap(), apps, setOf(mail.key, maps.key), 6)))
        assertEquals(keys(listOf(mail, chatCompose)), keys(DockRecents.pick(h, emptyMap(), apps, emptySet(), 2)))
    }
}
