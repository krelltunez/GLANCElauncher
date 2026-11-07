package app.glance.launcher.apps

import app.glance.launcher.prefs.LaunchHistory

/**
 * The dock's recent apps: what was last opened from the launcher ([LaunchHistory], no
 * permission needed), and, only when the user has granted usage access, what was last used
 * anywhere, so an app opened from a notification counts too. Pure: tested on the JVM.
 */
object DockRecents {

    /**
     * The [limit] most recently used apps, newest first, one per package, none in [exclude]
     * (the dock's pins and the hidden apps) and none no longer installed.
     *
     * @param usage last-used time by package name; empty without usage access. A package is
     *   shown as the activity last opened from the launcher, or else its first.
     */
    fun pick(history: LaunchHistory, usage: Map<String, Long>, apps: List<AppEntry>, exclude: Set<String>, limit: Int): List<AppEntry> {
        val byKey = apps.associateBy { it.key }
        val lastAt = HashMap<String, Long>()
        val launchedKeyOf = HashMap<String, String>()
        for ((key, stat) in history.entries.entries.sortedBy { it.value.lastAt }) {
            val entry = byKey[key] ?: continue
            lastAt[key] = stat.lastAt
            launchedKeyOf[entry.packageName] = key
        }
        if (usage.isNotEmpty()) {
            val firstOfPackage = HashMap<String, String>()
            for (a in apps) firstOfPackage.putIfAbsent(a.packageName, a.key)
            for ((pkg, at) in usage) {
                if (at <= 0) continue
                val key = launchedKeyOf[pkg] ?: firstOfPackage[pkg] ?: continue
                lastAt[key] = maxOf(lastAt[key] ?: 0L, at)
            }
        }
        return lastAt.entries
            .sortedByDescending { it.value }
            .mapNotNull { byKey[it.key] }
            .filter { it.key !in exclude }
            .distinctBy { it.packageName }
            .take(limit)
    }
}
