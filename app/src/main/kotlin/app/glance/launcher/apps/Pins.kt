package app.glance.launcher.apps

/** One Home pin: an app, or one of an app's shortcuts (a pinned "Add to Home screen" item). */
sealed class Pin {
    /** The key in [app.glance.launcher.prefs.LauncherPrefs.homePins]. */
    abstract val key: String
    abstract val label: String

    data class App(val entry: AppEntry) : Pin() {
        override val key: String get() = entry.key
        override val label: String get() = entry.label
    }

    data class Shortcut(val shortcut: AppShortcut) : Pin() {
        override val key: String get() = Pins.keyOf(shortcut.packageName, shortcut.id)
        override val label: String get() = shortcut.label
    }
}

/**
 * The Home pin list holds two kinds of key: an app's "package/class", and a shortcut's
 * "shortcut:package#id". Pure, tested on the JVM.
 */
object Pins {
    const val SHORTCUT_PREFIX = "shortcut:"

    fun keyOf(packageName: String, shortcutId: String): String = "$SHORTCUT_PREFIX$packageName#$shortcutId"

    /** The package and shortcut id of a shortcut pin key, or null for an app's. Ids may hold '#'; packages never do. */
    fun shortcutOf(key: String): Pair<String, String>? {
        if (!key.startsWith(SHORTCUT_PREFIX)) return null
        val rest = key.substring(SHORTCUT_PREFIX.length)
        val hash = rest.indexOf('#')
        if (hash <= 0 || hash == rest.length - 1) return null
        return rest.substring(0, hash) to rest.substring(hash + 1)
    }

    /**
     * The pins in their order, each resolved to its app or shortcut. A key with nothing behind
     * it right now (an app uninstalled, a shortcut the app removed or not loaded yet, a shortcut
     * of an app hidden from the drawer) is left out of the row but kept in the list.
     */
    fun resolve(keys: List<String>, apps: List<AppEntry>, shortcuts: List<AppShortcut>, hiddenApps: Set<String> = emptySet()): List<Pin> {
        val appsByKey = apps.associateBy { it.key }
        val shortcutsByKey = shortcuts.associateBy { keyOf(it.packageName, it.id) }
        val visiblePackages = apps.filter { it.key !in hiddenApps }.mapTo(HashSet()) { it.packageName }
        return keys.mapNotNull { key ->
            if (key.startsWith(SHORTCUT_PREFIX)) {
                shortcutsByKey[key]?.takeIf { it.packageName in visiblePackages }?.let { Pin.Shortcut(it) }
            } else {
                appsByKey[key]?.let { Pin.App(it) }
            }
        }
    }

    /**
     * What to tell Android so its list of shortcuts pinned by this launcher matches Home: for
     * each package whose set differs, the ids it should hold (empty unpins them all). [pinnedNow]
     * is what Android reports pinned; a pin Home dropped is released, so the app may delete it.
     */
    fun pinChanges(homeKeys: List<String>, pinnedNow: Map<String, Set<String>>): Map<String, Set<String>> {
        val wanted = shortcutIdsByPackage(homeKeys)
        val out = LinkedHashMap<String, Set<String>>()
        for (pkg in wanted.keys + pinnedNow.keys) {
            val ids = wanted[pkg].orEmpty()
            if (ids != pinnedNow[pkg].orEmpty()) out[pkg] = ids
        }
        return out
    }

    /** The shortcut ids pinned on Home, by package: what Android's own pin list for this launcher should hold. */
    fun shortcutIdsByPackage(keys: List<String>): Map<String, Set<String>> {
        val out = LinkedHashMap<String, MutableSet<String>>()
        for (key in keys) {
            val (pkg, id) = shortcutOf(key) ?: continue
            out.getOrPut(pkg) { LinkedHashSet() } += id
        }
        return out
    }
}
