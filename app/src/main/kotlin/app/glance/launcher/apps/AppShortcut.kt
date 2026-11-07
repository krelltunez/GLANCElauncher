package app.glance.launcher.apps

import android.os.UserHandle

/**
 * One app shortcut (Android's `ShortcutInfo`: the actions an app offers on a long-press of its
 * icon, and the conversations a messaging app publishes), as search ranks it. Pure, so the
 * ranking is tested on the JVM; [AppsRepository] keeps the platform object for the icon and the
 * launch.
 *
 * @param appLabel the label of the app's launcher activity, which search also matches
 * @param rank the app's own order for its shortcuts, lowest first
 * @param conversation a conversation the app keeps for the launcher (a chat, a contact), rather
 *   than an action
 * @param pinned pinned by this launcher (a Home pin, [Pins])
 * @param listed one the app offers on a long-press of its icon (declared in its manifest, or
 *   published as dynamic), rather than only pinned or cached
 */
data class AppShortcut(
    val packageName: String,
    val id: String,
    val shortLabel: String,
    val longLabel: String? = null,
    val appLabel: String,
    val rank: Int = 0,
    val conversation: Boolean = false,
    val pinned: Boolean = false,
    val listed: Boolean = true,
    /** Null means the current user, as [AppEntry.user]. */
    val user: UserHandle? = null,
) {
    /** Unique per user's app: the platform's shortcut ids are unique within a package. */
    val key: String get() = "$packageName#$id"

    /** The label a row shows: the long one when the app gives one, as the launcher's long-press menu does. */
    val label: String get() = longLabel?.takeIf { it.isNotBlank() } ?: shortLabel
}

/** The shortcuts an app's long-press menu lists. Pure, tested on the JVM. */
object ShortcutMenu {
    /** Android's guidance for how many shortcuts a launcher shows on one app. */
    const val MAX = 4

    /**
     * The app's own actions in its order: what it declares or publishes for the long-press, not
     * shortcuts that are only pinned (a page another time put on Home) or cached conversations.
     */
    fun forApp(shortcuts: List<AppShortcut>, packageName: String): List<AppShortcut> =
        shortcuts.filter { it.packageName == packageName && it.listed }.sortedBy { it.rank }.take(MAX)
}
