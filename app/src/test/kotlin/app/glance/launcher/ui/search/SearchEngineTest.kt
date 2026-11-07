package app.glance.launcher.ui.search

import app.glance.launcher.apps.AppEntry
import app.glance.launcher.apps.AppShortcut
import app.glance.launcher.data.Chore
import app.glance.launcher.data.Goal
import app.glance.launcher.data.Person
import app.glance.launcher.data.Project
import app.glance.launcher.data.Task
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchEngineTest {
    private fun app(label: String) = AppEntry(label, "pkg.${label.lowercase()}", "Main")

    private val input = SearchEngine.Input(
        apps = listOf(app("Calendar"), app("Camera"), app("Chrome"), app("Hidden")),
        tasks = listOf(Task("t1", "Call dentist", LocalDate.of(2026, 10, 18), 600), Task("t2", "Calibrate monitor", null, null)),
        projects = listOf(Project("p1", "Camera roll cleanup")),
        goals = listOf(Goal("g1", "Calm mornings")),
        chores = listOf(Chore("c1", "Mop kitchen", "Kitchen", 7), Chore("c2", "Clean camera lens", "Tech", 30)),
        people = listOf(Person("pe1", "Cam Reyes", "Cam")),
        hiddenApps = setOf("pkg.hidden/Main"),
    )
    private val now = LocalDateTime.of(2026, 10, 17, 9, 0)

    @Test
    fun `dayGLANCE mode leads with a create-task row and covers apps and data`() {
        val results = SearchEngine.search("ca", CaptureTarget.DAYGLANCE, input, now)
        assertTrue(results.first() is SearchResult.CreateTask)
        val apps = results.filterIsInstance<SearchResult.App>().map { it.entry.label }
        assertEquals(listOf("Calendar", "Camera"), apps)
        assertTrue(results.any { it is SearchResult.TaskHit && it.task.id == "t1" })
        assertTrue(results.any { it is SearchResult.ProjectHit })
        assertTrue(results.any { it is SearchResult.GoalHit })
        assertTrue(results.any { it is SearchResult.ChoreHit && it.chore.id == "c2" })
        assertTrue(results.any { it is SearchResult.PersonHit })
    }

    @Test
    fun `hidden apps never appear`() {
        val results = SearchEngine.search("hidden", CaptureTarget.DAYGLANCE, input, now)
        assertTrue(results.none { it is SearchResult.App })
    }

    @Test
    fun `quick capture parses the query`() {
        val create = SearchEngine.search("call dentist tomorrow 2pm", CaptureTarget.DAYGLANCE, input, now).first() as SearchResult.CreateTask
        assertEquals("Call dentist", create.draft.title)
        assertEquals(LocalDate.of(2026, 10, 18), create.draft.date)
        assertEquals(14 * 60, create.draft.startMin)
    }

    @Test
    fun `lg prefix routes to lastGLANCE and logs a done for a unique chore match`() {
        val results = SearchEngine.search("lg mop", CaptureTarget.DAYGLANCE, input, now)
        val first = results.first()
        assertTrue(first is SearchResult.LogDone && first.chore.id == "c1")
    }

    @Test
    fun `lastGLANCE mode offers to add an unknown chore`() {
        val first = SearchEngine.search("descale kettle", CaptureTarget.LASTGLANCE, input, now).first()
        assertTrue(first is SearchResult.AddChore && first.name == "Descale kettle")
    }

    @Test
    fun `empty query returns nothing`() {
        assertTrue(SearchEngine.search("   ", CaptureTarget.DAYGLANCE, input, now).isEmpty())
        assertTrue(SearchEngine.search("lg ", CaptureTarget.DAYGLANCE, input, now).isEmpty())
    }

    // region app shortcuts

    private val chrome = AppEntry("Chrome", "com.android.chrome", "Main")
    private val maps = AppEntry("Maps", "com.google.maps", "Main")
    private val signal = AppEntry("Signal", "org.signal", "Main")
    private val secret = AppEntry("Vault", "com.secret", "Main")
    private fun sc(app: AppEntry, id: String, short: String, long: String? = null, rank: Int = 0, conversation: Boolean = false) =
        AppShortcut(app.packageName, id, short, long, app.label, rank, conversation)

    private val shortcuts = listOf(
        sc(chrome, "incognito", "Incognito tab", "New incognito tab", rank = 1),
        sc(chrome, "newtab", "New tab", rank = 0),
        sc(maps, "home", "Home", "Navigate home", rank = 0),
        sc(maps, "work", "Work", "Navigate to work", rank = 1),
        sc(signal, "c-mom", "Mom", conversation = true),
        sc(secret, "open", "Open vault"),
    )
    private val withShortcuts = input.copy(
        apps = input.apps + listOf(chrome, maps, signal, secret),
        hiddenApps = input.hiddenApps + secret.key,
        shortcuts = shortcuts,
    )
    private fun shortcutIds(query: String) =
        SearchEngine.search(query, CaptureTarget.DAYGLANCE, withShortcuts, now).filterIsInstance<SearchResult.Shortcut>().map { it.shortcut.id }

    @Test
    fun `a shortcut is found by its own label, a conversation by the contact's name`() {
        assertEquals(listOf("incognito"), shortcutIds("incog"))
        assertEquals(listOf("c-mom"), shortcutIds("mom"))
    }

    @Test
    fun `app and label together narrow to that app's shortcut`() {
        assertEquals(listOf("incognito"), shortcutIds("chrome inc"))
        assertEquals(listOf("work"), shortcutIds("maps wo"))
        // Words that only name the app are not a shortcut match on their own.
        assertEquals(listOf("newtab", "incognito"), shortcutIds("chrome"))
    }

    @Test
    fun `an app's name lists its shortcuts below it, in the app's own order, after label matches`() {
        val results = SearchEngine.search("maps", CaptureTarget.DAYGLANCE, withShortcuts, now)
        val appAt = results.indexOfFirst { it is SearchResult.App && it.entry == maps }
        val shortcutsAt = results.withIndex().filter { it.value is SearchResult.Shortcut }.map { it.index }
        assertTrue(appAt >= 0 && shortcutsAt.isNotEmpty() && shortcutsAt.all { it > appAt })
        assertEquals(listOf("home", "work"), shortcutIds("maps"))
        // "home" matches a label outright, which outranks matching an app's name.
        assertEquals("home", shortcutIds("home").first())
    }

    @Test
    fun `a word of the app's name lists its shortcuts too, and app-and-label outranks it`() {
        val gmaps = AppEntry("Google Maps", "com.google.android.apps.maps", "Main")
        val gHome = AppShortcut(gmaps.packageName, "g-home", "Home", null, gmaps.label, 0)
        val gWork = AppShortcut(gmaps.packageName, "g-work", "Work", null, gmaps.label, 1)
        val inputG = withShortcuts.copy(apps = withShortcuts.apps + gmaps, shortcuts = listOf(gHome, gWork))
        val ids = { q: String -> SearchEngine.search(q, CaptureTarget.DAYGLANCE, inputG, now).filterIsInstance<SearchResult.Shortcut>().map { it.shortcut.id } }
        assertEquals(listOf("g-home", "g-work"), ids("maps"))
        assertEquals(listOf("g-work"), ids("google wo"))
        // Several words that match nothing of the label still need every word somewhere.
        assertEquals(emptyList(), ids("maps xyz"))
    }

    @Test
    fun `a hidden app's shortcuts stay hidden, and the list is capped`() {
        assertEquals(emptyList(), shortcutIds("vault"))
        assertEquals(emptyList(), shortcutIds("open vault"))
        val many = (1..9).map { sc(maps, "s$it", "Saved place $it", rank = it) }
        val capped = SearchEngine.search("saved", CaptureTarget.DAYGLANCE, withShortcuts.copy(shortcuts = many), now)
            .filterIsInstance<SearchResult.Shortcut>().map { it.shortcut.id }
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), capped)
    }

    @Test
    fun `a shortcut row shows the long label when the app gives one`() {
        assertEquals("New incognito tab", shortcuts[0].label)
        assertEquals("New tab", shortcuts[1].label)
        assertEquals("Mom", shortcuts[4].label)
    }

    // endregion

    // region fallbacks

    @Test
    fun `arithmetic is answered first, a date stays quick capture`() {
        val sum = SearchEngine.search("12*7", CaptureTarget.DAYGLANCE, input, now)
        assertEquals(SearchResult.Calculation("12*7", "84"), sum.first())
        val date = SearchEngine.search("dentist 10/22", CaptureTarget.DAYGLANCE, input, now)
        assertTrue(date.first() is SearchResult.CreateTask && date.none { it is SearchResult.Calculation })
    }

    @Test
    fun `every search ends with the web, and Settings pages sit with the apps`() {
        val results = SearchEngine.search("bluetooth", CaptureTarget.DAYGLANCE, input, now)
        assertEquals(SearchResult.WebSearch("bluetooth"), results.last())
        assertTrue(results.any { it is SearchResult.Setting && it.page.label == "Bluetooth" })
        assertEquals(SearchResult.WebSearch("descale kettle"), SearchEngine.search("descale kettle", CaptureTarget.LASTGLANCE, input, now).last())
    }

    @Test
    fun `contacts match by the start of a name's words, only when there are any`() {
        val contacts = listOf(ContactHit(1, "k1", "Dana Whitfield"), ContactHit(2, "k2", "Danielle Ross"), ContactHit(3, "k3", "Sam Dane"))
        val withContacts = input.copy(contacts = contacts)
        val names = { q: String -> SearchEngine.search(q, CaptureTarget.DAYGLANCE, withContacts, now).filterIsInstance<SearchResult.Contact>().map { it.contact.name } }
        assertEquals(listOf("Dana Whitfield", "Danielle Ross", "Sam Dane"), names("dan"))
        assertEquals(listOf("Dana Whitfield"), names("dana wh"))
        assertEquals(emptyList(), names("hit"))
        assertTrue(SearchEngine.search("dan", CaptureTarget.DAYGLANCE, input, now).none { it is SearchResult.Contact })
    }

    // region: voice commands

    private val contacts = listOf(ContactHit(1, "k1", "Mom"), ContactHit(2, "k2", "Sam Dane"), ContactHit(3, "k3", "Sam Ortiz"))
    private val withContacts = input.copy(contacts = contacts)

    @Test
    fun `spoken, a clear command runs unasked`() {
        val auto = { q: String -> SearchEngine.autoRun(q, CaptureTarget.DAYGLANCE, withContacts, now) }
        assertEquals("Chrome", (auto("open chrome") as SearchResult.App).entry.label)
        assertEquals(SearchResult.SetTimer(600, null), auto("set a timer for 10 minutes"))
        assertEquals(SearchResult.SetAlarm(6, 30), auto("alarm 6:30 am"))
        assertEquals(SearchResult.Navigate("whole foods"), auto("navigate to whole foods"))
        assertEquals(SearchResult.WebSearch("tide times"), auto("search for tide times"))
        assertEquals("Dentist", (auto("add dentist friday 3pm") as SearchResult.CreateTask).draft.title)
        assertEquals("pe1", (auto("talked to cam") as SearchResult.LogInteraction).person.id)
    }

    @Test
    fun `a command in doubt, a call or a message, or no command at all waits for a tap`() {
        val auto = { q: String -> SearchEngine.autoRun(q, CaptureTarget.DAYGLANCE, withContacts, now) }
        // "ca" is in Calendar and Camera: not for the launcher to pick.
        assertEquals(null, auto("open ca"))
        // A hidden app is not opened by voice either.
        assertEquals(null, auto("open hidden"))
        assertEquals(null, auto("call mom"))
        assertEquals(null, auto("text sam"))
        assertEquals(null, auto("open zzz"))
        assertEquals(null, auto("dentist friday"))
        assertEquals(null, auto("talked to nobody"))
    }

    @Test
    fun `spoken, the command row leads, and typed, it follows the capture row so Enter still adds the task`() {
        val spoken = SearchEngine.search("call mom", CaptureTarget.DAYGLANCE, withContacts, now, voice = true)
        assertEquals(SearchResult.CallContact(contacts[0]), spoken.first())
        val typed = SearchEngine.search("call mom", CaptureTarget.DAYGLANCE, withContacts, now)
        assertTrue(typed.first() is SearchResult.CreateTask)
        assertEquals(SearchResult.CallContact(contacts[0]), typed[1])
        // A first name picks the first contact by that name.
        assertEquals(SearchResult.TextContact(contacts[1]), SearchEngine.search("text sam", CaptureTarget.DAYGLANCE, withContacts, now, voice = true).first())
        // Without contacts there is nothing to call: no command row.
        assertTrue(SearchEngine.search("call mom", CaptureTarget.DAYGLANCE, input, now, voice = true).none { it is SearchResult.CallContact })
    }

    @Test
    fun `a spoken add leads with its own task and no literal copy, an opened app is not listed twice`() {
        val add = SearchEngine.search("add dentist friday 3pm", CaptureTarget.DAYGLANCE, input, now, voice = true)
        assertEquals(listOf("Dentist"), add.filterIsInstance<SearchResult.CreateTask>().map { it.draft.title })
        val open = SearchEngine.search("open chrome", CaptureTarget.DAYGLANCE, input, now, voice = true)
        assertEquals(1, open.count { it is SearchResult.App && it.entry.label == "Chrome" })
        assertTrue(open.first() is SearchResult.App)
        // Typed, a timer shows its row but the task is still first.
        val timer = SearchEngine.search("timer 5 minutes", CaptureTarget.DAYGLANCE, input, now)
        assertTrue(timer.first() is SearchResult.CreateTask)
        assertTrue(timer.contains(SearchResult.SetTimer(300, null)))
    }

    // endregion
}
