package app.glance.launcher.ui.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import app.glance.launcher.system.LaunchOptions

/** What the fallback and command rows open: a Settings page, the web search, the clock, maps, the dialer. */
object SearchActions {
    /** The page, or Settings itself where a device lacks it. */
    fun openSettings(context: Context, page: SettingsPage) {
        val open = { action: String -> context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        try {
            open(page.action)
        } catch (e: Exception) {
            runCatching { open(SettingsPages.MAIN) }
        }
    }

    /** The default web search app with the query, or the browser on a search page where there is none. */
    fun webSearch(context: Context, query: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** The clock app's timer, started without its screen; false when no clock app takes it. */
    fun setTimer(context: Context, seconds: Int, label: String?): Boolean = start(context,
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .apply { if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label) },
    )

    /** The clock app's alarm at [hour]:[minute], set without its screen; false when no clock app takes it. */
    fun setAlarm(context: Context, hour: Int, minute: Int): Boolean = start(context,
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
    )

    /** Turn-by-turn directions where Google Maps is there, else the maps app on a search for [place]. */
    fun navigate(context: Context, place: String) {
        if (start(context, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place))))) return
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
    }

    /** The dialer with [number] filled in: one tap from calling, never a call by itself. */
    fun dial(context: Context, number: String): Boolean = start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    /** A new message to [number] in the default messaging app. */
    fun message(context: Context, number: String): Boolean = start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), LaunchOptions.iconSplash())
        true
    } catch (e: Exception) {
        false
    }
}
