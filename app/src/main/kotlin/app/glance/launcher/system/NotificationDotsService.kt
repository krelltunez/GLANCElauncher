package app.glance.launcher.system

import android.app.Notification
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification dots: the system binds this listener once the user grants notification access
 * (Settings → Notification dots), and it publishes which apps have something to read
 * ([NotificationDots]). It only reads which app posted what kind of notification; nothing of a
 * notification's content is kept or leaves the device. Without access, or with the switch off,
 * no app has a dot.
 */
class NotificationDotsService : NotificationListenerService() {
    override fun onListenerConnected() {
        _connected.value = true
        publish()
    }

    override fun onListenerDisconnected() {
        _connected.value = false
        _dots.value = emptySet()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) = publish()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = publish()

    /** Rebuilt from the active list each time: a missed or out-of-order event can never leave a dot stuck. */
    private fun publish() {
        val active = try {
            activeNotifications
        } catch (e: RuntimeException) {
            // SecurityException while the listener is not (or no longer) connected.
            Log.w(TAG, "Could not read the active notifications", e)
            null
        } ?: return
        val rankings = try { currentRanking } catch (e: RuntimeException) { null }
        val me = Process.myUserHandle()
        val posted = active.map { sbn ->
            val ranking = Ranking()
            val ranked = rankings?.getRanking(sbn.key, ranking) == true
            val n = sbn.notification
            val extras = n.extras
            PostedNotification(
                packageName = sbn.packageName,
                user = NotificationDots.userKey(sbn.user, me),
                canShowBadge = !ranked || ranking.canShowBadge(),
                ongoing = sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
                groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                hasContent = !extras?.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() ||
                    !extras?.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank(),
                suspended = ranked && Build.VERSION.SDK_INT >= 29 && ranking.isSuspended,
            )
        }
        _dots.value = NotificationDots.packages(posted, packageName)
    }

    companion object {
        private const val TAG = "NotificationDots"
        private val _dots = MutableStateFlow<Set<DotKey>>(emptySet())
        /** The apps with a dot right now; empty while the listener is not connected. */
        val dots: StateFlow<Set<DotKey>> = _dots.asStateFlow()

        private val _connected = MutableStateFlow(false)
        /** The system has bound the listener: access was granted, and it shows at once in Settings. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        fun hasAccess(context: Context): Boolean {
            val me = ComponentName(context, NotificationDotsService::class.java)
            if (Build.VERSION.SDK_INT >= 27) {
                return context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(me)
            }
            // Android 8.0: the setting the system itself reads, a ':'-separated list of components.
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * Opens the system page that grants access: this listener's own switch where Android has
         * one (11+), else the list of listeners, which some builds keep as the only page.
         */
        fun openAccessSettings(context: Context) {
            val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 30) {
                val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, NotificationDotsService::class.java).flattenToString(),
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(detail)
                    return
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "No per-listener page; opening the list", e)
                }
            }
            try {
                context.startActivity(list)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No notification access settings", e)
            }
        }
    }
}
