package app.glance.launcher.system

import android.os.UserHandle

/** An app, per user, with a notification that earns it a dot. Null [user] is the launcher's own user. */
data class DotKey(val packageName: String, val user: UserHandle? = null)

/** What [NotificationDots.packages] needs to know of one posted notification. */
data class PostedNotification(
    val packageName: String,
    /** Null is the launcher's own user, as [DotKey.user]. */
    val user: UserHandle? = null,
    /** The channel allows a badge (the user can turn dots off per channel in the app's settings). */
    val canShowBadge: Boolean = true,
    /** Ongoing or a foreground service's: a music player, navigation, a download in progress. */
    val ongoing: Boolean = false,
    /** A group's summary, which stands for children counted on their own. */
    val groupSummary: Boolean = false,
    /** Has a title or text: an empty one is a placeholder, not something to read. */
    val hasContent: Boolean = true,
    /** The app is suspended (Digital Wellbeing's pause), which hides its notifications. */
    val suspended: Boolean = false,
)

/**
 * Which apps get a notification dot: the ones with a notification the user has something to
 * read in, as Android's own launcher decides it. Pure, tested on the JVM;
 * [NotificationDotsService] feeds it what the system posts.
 */
object NotificationDots {
    fun packages(posted: List<PostedNotification>, ownPackage: String): Set<DotKey> =
        posted.asSequence()
            .filter { it.packageName != ownPackage }
            .filter { it.canShowBadge && !it.ongoing && !it.groupSummary && it.hasContent && !it.suspended }
            .mapTo(LinkedHashSet()) { DotKey(it.packageName, it.user) }

    /** Whether the app has a dot. [user] null, or [ownUser], is the launcher's own user. */
    fun has(dots: Set<DotKey>, packageName: String, user: UserHandle?, ownUser: UserHandle?): Boolean =
        DotKey(packageName, userKey(user, ownUser)) in dots

    /**
     * A user as [DotKey] holds it: null for the launcher's own. Apps carry their real handle and
     * tests carry none, so both sides go through this or the main profile's apps never match.
     */
    fun <U> userKey(user: U?, ownUser: U?): U? = user?.takeIf { it != ownUser }
}
