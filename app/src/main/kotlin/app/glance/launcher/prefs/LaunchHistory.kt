package app.glance.launcher.prefs

/**
 * Which apps were opened from the launcher, and when: the app drawer's recent row, search's
 * suggested apps and the dock's recents. The launcher's own record rather than usage access,
 * so it needs no permission and reflects what the user opens from here (the dock adds usage
 * access when the user has granted it: apps/DockRecents.kt). Pure: ranking and serialisation
 * are tested on the JVM.
 *
 * Bounded: at most [MAX_ENTRIES] apps are kept, the least recently opened dropped first.
 */
data class LaunchHistory(val entries: Map<String, Stat> = emptyMap()) {
    /** How often and how lately one app (by component key) was opened. */
    data class Stat(val count: Int, val lastAt: Long)

    fun record(key: String, now: Long): LaunchHistory {
        val prev = entries[key]
        val next = entries + (key to Stat((prev?.count ?: 0) + 1, now))
        if (next.size <= MAX_ENTRIES) return LaunchHistory(next)
        return LaunchHistory(next.entries.sortedByDescending { it.value.lastAt }.take(MAX_ENTRIES).associate { it.key to it.value })
    }

    fun forget(key: String): LaunchHistory = if (key in entries) LaunchHistory(entries - key) else this

    /** The [limit] most recently opened, newest first, among [eligible]. */
    fun recent(limit: Int, eligible: (String) -> Boolean = { true }): List<String> =
        entries.entries.filter { eligible(it.key) }.sortedByDescending { it.value.lastAt }.take(limit).map { it.key }

    /**
     * The [limit] most used, by frecency: each launch counts, a launch's weight halving every
     * [HALF_LIFE_MS], so last month's habit yields to this week's. Ties go to the more recent.
     */
    fun frequent(limit: Int, now: Long, eligible: (String) -> Boolean = { true }): List<String> =
        entries.entries.filter { eligible(it.key) }
            .sortedWith(compareByDescending<Map.Entry<String, Stat>> { score(it.value, now) }.thenByDescending { it.value.lastAt })
            .take(limit).map { it.key }

    fun serialise(): String = entries.entries.joinToString("\n") { "${it.key}\t${it.value.count}\t${it.value.lastAt}" }

    companion object {
        const val MAX_ENTRIES = 60
        const val HALF_LIFE_MS = 7L * 24 * 60 * 60 * 1000

        fun score(stat: Stat, now: Long): Double {
            val age = (now - stat.lastAt).coerceAtLeast(0L).toDouble()
            return stat.count * Math.pow(0.5, age / HALF_LIFE_MS)
        }

        fun parse(text: String?): LaunchHistory {
            if (text.isNullOrBlank()) return LaunchHistory()
            val map = HashMap<String, Stat>()
            for (line in text.split('\n')) {
                val parts = line.split('\t')
                if (parts.size != 3) continue
                val count = parts[1].toIntOrNull() ?: continue
                val last = parts[2].toLongOrNull() ?: continue
                if (parts[0].isNotBlank()) map[parts[0]] = Stat(count, last)
            }
            return LaunchHistory(map)
        }
    }
}

/** Recent search queries, newest first, deduplicated without regard to case, at most [MAX]. Pure. */
object RecentSearches {
    const val MAX = 6

    fun add(list: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.length < 2) return list
        return (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
    }
}
