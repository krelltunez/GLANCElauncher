package app.glance.launcher.ui.search

import app.glance.launcher.apps.AppEntry
import app.glance.launcher.apps.AppShortcut
import app.glance.launcher.data.Chore
import app.glance.launcher.data.Goal
import app.glance.launcher.data.Person
import app.glance.launcher.data.Project
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskDraft
import app.glance.launcher.quickadd.QuickAddParser
import java.time.LocalDateTime

/** Which app quick capture writes to. The `lg ` prefix (or the chip) switches to lastGLANCE. */
enum class CaptureTarget { DAYGLANCE, LASTGLANCE }

sealed class SearchResult {
    data class App(val entry: AppEntry) : SearchResult()
    /** One of an app's shortcuts: an action it offers, or a conversation it keeps for the launcher. */
    data class Shortcut(val shortcut: AppShortcut) : SearchResult()
    data class TaskHit(val task: Task) : SearchResult()
    data class ProjectHit(val project: Project) : SearchResult()
    data class GoalHit(val goal: Goal) : SearchResult()
    data class ChoreHit(val chore: Chore) : SearchResult()
    data class PersonHit(val person: Person) : SearchResult()
    data class CreateTask(val draft: TaskDraft, val chips: List<String>) : SearchResult()
    data class LogDone(val chore: Chore) : SearchResult()
    data class AddChore(val name: String) : SearchResult()
    data class LogInteraction(val person: Person) : SearchResult()
    /** Arithmetic in the query ("12*7"): its answer, copied on tap. */
    data class Calculation(val expression: String, val result: String) : SearchResult()
    /** A page of the system Settings app ("bluetooth"). */
    data class Setting(val page: SettingsPage) : SearchResult()
    /** A phone contact, when the user turned contacts on for search. */
    data class Contact(val contact: ContactHit) : SearchResult()
    /** The last row of every search: the query in the default web search. */
    data class WebSearch(val query: String) : SearchResult()
    /** "timer 10 minutes": the clock app's timer. */
    data class SetTimer(val seconds: Int, val label: String?) : SearchResult()
    /** "alarm 6:30": the clock app's alarm. */
    data class SetAlarm(val hour: Int, val minute: Int) : SearchResult()
    /** "navigate to…": directions in the maps app. */
    data class Navigate(val place: String) : SearchResult()
    /** "call Mom": the contact's number in the dialer. */
    data class CallContact(val contact: ContactHit) : SearchResult()
    /** "text Mom": a new message to the contact. */
    data class TextContact(val contact: ContactHit) : SearchResult()
}

/** A contact from the phone's address book, as search ranks it. */
data class ContactHit(val id: Long, val lookupKey: String, val name: String)

/**
 * Pure search over installed apps and GLANCE data, plus the quick-capture rows. The UI only
 * renders what this returns, so the ranking rules live in one testable place.
 */
object SearchEngine {
    const val LASTGLANCE_PREFIX = "lg "

    data class Input(
        val apps: List<AppEntry>,
        val tasks: List<Task>,
        val projects: List<Project>,
        val goals: List<Goal>,
        val chores: List<Chore>,
        val people: List<Person>,
        val hiddenApps: Set<String> = emptySet(),
        val shortcuts: List<AppShortcut> = emptyList(),
        /** Empty unless the user turned contacts on and granted the permission. */
        val contacts: List<ContactHit> = emptyList(),
    )

    /** At most this many contact rows. */
    const val MAX_CONTACTS = 3

    /** At most this many shortcut rows, below the apps. */
    const val MAX_SHORTCUTS = 5

    fun targetFor(rawQuery: String, toggle: CaptureTarget): Pair<CaptureTarget, String> {
        val q = rawQuery.trimStart()
        return if (q.lowercase().startsWith(LASTGLANCE_PREFIX)) Pair(CaptureTarget.LASTGLANCE, q.substring(LASTGLANCE_PREFIX.length))
        else Pair(toggle, q)
    }

    /** A command's row, and whether a spoken request may run it without a tap. */
    data class Command(val row: SearchResult, val autoRun: Boolean)

    /**
     * The row a [VoiceCommand] resolves to against what is installed and known, or null when it
     * names nothing (an app or contact that is not there). It runs unasked only when nothing is in
     * doubt: one app or person matched, or a timer, alarm, route, task or web search. A call or a
     * message always waits for a tap.
     */
    fun command(cmd: VoiceCommand, input: Input, now: LocalDateTime = LocalDateTime.now()): Command? = when (cmd) {
        is VoiceCommand.Open -> {
            val matches = appMatchesFor(cmd.appName, input)
            matches.firstOrNull()?.let { Command(SearchResult.App(it), matches.size == 1) }
        }
        is VoiceCommand.Call -> bestContact(cmd.name, input.contacts)?.let { Command(SearchResult.CallContact(it), autoRun = false) }
        is VoiceCommand.Text -> bestContact(cmd.name, input.contacts)?.let { Command(SearchResult.TextContact(it), autoRun = false) }
        is VoiceCommand.Timer -> Command(SearchResult.SetTimer(cmd.seconds, cmd.label), autoRun = true)
        is VoiceCommand.Alarm -> Command(SearchResult.SetAlarm(cmd.hour, cmd.minute), autoRun = true)
        is VoiceCommand.Navigate -> Command(SearchResult.Navigate(cmd.place), autoRun = true)
        is VoiceCommand.AddTask -> QuickAddParser.parse(cmd.text, now).let { Command(SearchResult.CreateTask(it.draft, it.chips), autoRun = true) }
        is VoiceCommand.TalkedTo -> {
            val people = peopleFor(cmd.name, input.people)
            people.firstOrNull()?.let { Command(SearchResult.LogInteraction(it), people.size == 1) }
        }
        is VoiceCommand.WebSearch -> Command(SearchResult.WebSearch(cmd.query), autoRun = true)
    }

    /**
     * What a finished voice request runs without a tap, or null when the transcript should stay in
     * the field as an ordinary search (no command, or one in doubt).
     */
    fun autoRun(rawQuery: String, toggle: CaptureTarget, input: Input, now: LocalDateTime = LocalDateTime.now()): SearchResult? {
        val (_, query) = targetFor(rawQuery, toggle)
        val cmd = VoiceCommands.parse(query, now.toLocalTime()) ?: return null
        return command(cmd, input, now)?.takeIf { it.autoRun }?.row
    }

    /** Visible apps for a spoken name: an exact name alone; otherwise every app whose name holds it, best first. */
    private fun appMatchesFor(name: String, input: Input): List<AppEntry> {
        val n = name.lowercase().trim()
        val visible = input.apps.filter { it.key !in input.hiddenApps }
        visible.filter { it.label.equals(n, ignoreCase = true) }.takeIf { it.isNotEmpty() }?.let { return it.take(1) }
        return visible.filter { it.label.lowercase().contains(n) }
            .sortedWith(compareBy({ rank(it.label.lowercase(), n) }, { it.label.lowercase() }))
    }

    /** The contact a spoken name means: the full name, else the first name, else the first name match. */
    private fun bestContact(name: String, contacts: List<ContactHit>): ContactHit? {
        val n = name.lowercase().trim()
        return contacts.firstOrNull { it.name.equals(n, ignoreCase = true) }
            ?: contacts.firstOrNull { words(it.name.lowercase()).firstOrNull() == n }
            ?: contactMatches(contacts, n).firstOrNull()
    }

    /** lastGLANCE people for a spoken name: by name or nickname, whole or first word. */
    private fun peopleFor(name: String, people: List<Person>): List<Person> {
        val n = name.lowercase().trim()
        fun names(p: Person) = listOfNotNull(p.name, p.nickname).map { it.lowercase() }
        people.filter { p -> names(p).any { it == n } }.takeIf { it.isNotEmpty() }?.let { return it }
        return people.filter { p -> names(p).any { words(it).firstOrNull() == n } }
    }

    /**
     * Every row for [rawQuery]. With [voice], a command in it leads, so the transcript of "open
     * Spotify" shows Spotify first; typed, a command row follows the capture row, so Enter on a
     * typed task still adds the task.
     */
    fun search(rawQuery: String, toggle: CaptureTarget, input: Input, now: LocalDateTime = LocalDateTime.now(), voice: Boolean = false): List<SearchResult> {
        val (target, query) = targetFor(rawQuery, toggle)
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val lq = q.lowercase()
        val out = ArrayList<SearchResult>()
        val cmd = VoiceCommands.parse(q, now.toLocalTime())
        val commandRow = cmd?.let { command(it, input, now) }?.row
            // Typed, these add nothing: the capture row is the task and the web search row ends every list.
            ?.takeUnless { !voice && (it is SearchResult.CreateTask || it is SearchResult.WebSearch) }

        if (voice && commandRow != null) out += commandRow

        // Arithmetic answers before anything: "12*7" is a question, not a task to add.
        Calculator.evaluate(q)?.let { out += SearchResult.Calculation(q, Calculator.format(it)) }

        // Capture row first: it is what the bar is for. A spoken "add…" already led with its task.
        when (target) {
            CaptureTarget.DAYGLANCE -> if (!(voice && commandRow is SearchResult.CreateTask)) {
                val parsed = QuickAddParser.parse(q, now)
                out += SearchResult.CreateTask(parsed.draft, parsed.chips)
            }
            CaptureTarget.LASTGLANCE -> {
                val exact = input.chores.firstOrNull { it.name.equals(lq, ignoreCase = true) }
                    ?: input.chores.filter { it.name.lowercase().contains(lq) }.singleOrNull()
                if (exact != null) out += SearchResult.LogDone(exact)
                else out += SearchResult.AddChore(q.replaceFirstChar { it.titlecase() })
            }
        }

        if (!voice && commandRow != null) out += commandRow

        // Apps: prefix matches first, then word starts, then contains.
        val visibleApps = input.apps.filter { it.key !in input.hiddenApps }
        val appMatches = visibleApps.filter { it.label.lowercase().contains(lq) }
            .sortedWith(compareBy({ rank(it.label.lowercase(), lq) }, { it.label.lowercase() }))
        out += appMatches.take(6).map { SearchResult.App(it) }
        out += shortcutMatches(input.shortcuts, visibleApps, lq).map { SearchResult.Shortcut(it) }
        out += SettingsPages.matches(lq).map { SearchResult.Setting(it) }

        if (target == CaptureTarget.DAYGLANCE || out.size < 4) {
            out += input.tasks.filter { !it.completed && it.title.lowercase().contains(lq) }
                .sortedWith(compareBy({ rank(it.title.lowercase(), lq) }, { it.date ?: java.time.LocalDate.MAX }))
                .take(6).map { SearchResult.TaskHit(it) }
            out += input.projects.filter { it.title.lowercase().contains(lq) }.take(3).map { SearchResult.ProjectHit(it) }
            out += input.goals.filter { it.title.lowercase().contains(lq) }.take(3).map { SearchResult.GoalHit(it) }
        }
        out += input.chores.filter { it.name.lowercase().contains(lq) || it.category.lowercase().contains(lq) }
            .take(if (target == CaptureTarget.LASTGLANCE) 8 else 3).map { SearchResult.ChoreHit(it) }
        out += input.people.filter { it.name.lowercase().contains(lq) || (it.nickname?.lowercase()?.contains(lq) == true) }
            .take(4).map { SearchResult.PersonHit(it) }
        if (target == CaptureTarget.LASTGLANCE) {
            out += input.people.filter { it.name.lowercase().contains(lq) }.take(2).map { SearchResult.LogInteraction(it) }
        }
        out += contactMatches(input.contacts, lq).map { SearchResult.Contact(it) }
        // Always last: what to do when nothing above is what was meant.
        out += SearchResult.WebSearch(q)
        // A command row repeats a row the plain search also found (the app it opens, a spoken
        // search): keep its place, drop the copy.
        return if (commandRow == null) out else out.filterIndexed { i, r -> r != commandRow || i == out.indexOf(commandRow) }
    }

    /**
     * Apps' shortcuts, as Sesame finds them: by their own label ("incognito", a contact's name),
     * by app and label together ("chrome inc", "maps work"), or by the app's name, which lists its
     * shortcuts under it. Only shortcuts of an app the drawer shows: a hidden app's stay hidden.
     * Best tier first, then the app's own order.
     */
    fun shortcutMatches(shortcuts: List<AppShortcut>, visibleApps: List<AppEntry>, lq: String): List<AppShortcut> {
        if (shortcuts.isEmpty()) return emptyList()
        val visiblePackages = visibleApps.mapTo(HashSet()) { it.packageName }
        val tokens = words(lq)
        return shortcuts
            .filter { it.packageName in visiblePackages }
            .mapNotNull { s -> shortcutTier(s, lq, tokens)?.let { tier -> tier to s } }
            .sortedWith(compareBy({ it.first }, { it.second.appLabel.lowercase() }, { it.second.rank }, { it.second.label.lowercase() }))
            .take(MAX_SHORTCUTS)
            .map { it.second }
    }

    /**
     * 0 to 2: the shortcut's own label matches, as an app's does ([rank]). 3: a query of several
     * words, each starting a word of the app's name or the label. 4: the app's name, or one of its
     * words, starts with the query ("maps" for Google Maps). Null: no match.
     */
    private fun shortcutTier(s: AppShortcut, lq: String, tokens: List<String>): Int? {
        val labels = listOfNotNull(s.shortLabel, s.longLabel).map { it.lowercase() }
        labels.filter { it.contains(lq) }.minOfOrNull { rank(it, lq) }?.let { return it }
        val appLabel = s.appLabel.lowercase()
        if (tokens.size > 1) {
            val allWords = words(appLabel) + labels.flatMap(::words)
            if (tokens.all { t -> allWords.any { it.startsWith(t) } }) return 3
        }
        if (rank(appLabel, lq) <= 1) return 4
        return null
    }

    /** Contacts whose name has a word starting with each word of the query, name prefixes first. */
    fun contactMatches(contacts: List<ContactHit>, lq: String): List<ContactHit> {
        val tokens = words(lq)
        if (tokens.isEmpty()) return emptyList()
        return contacts
            .filter { c -> val nameWords = words(c.name.lowercase()); tokens.all { t -> nameWords.any { it.startsWith(t) } } }
            .sortedWith(compareBy({ rank(it.name.lowercase(), lq) }, { it.name.lowercase() }))
            .take(MAX_CONTACTS)
    }

    private fun words(text: String): List<String> = text.split(' ', '-', '_', '.', ',', ':', '/').filter { it.isNotEmpty() }

    private fun rank(label: String, q: String): Int = when {
        label.startsWith(q) -> 0
        label.split(' ', '-', '_').any { it.startsWith(q) } -> 1
        else -> 2
    }
}
