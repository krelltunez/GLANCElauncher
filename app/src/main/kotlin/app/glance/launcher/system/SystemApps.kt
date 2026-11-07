package app.glance.launcher.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The device's own clock and calendar apps, opened from the dial's hub (its clock and its
 * date) as a stock launcher's clock widget opens them. Whichever app the user made the
 * default answers; with none chosen, Android asks.
 */
object SystemApps {
    /** The clock app, on its alarms: the one standard intent every clock app answers. */
    fun openClock(context: Context) {
        val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(context, alarms)) Toast.makeText(context, "No clock app found", Toast.LENGTH_SHORT).show()
    }

    /**
     * The calendar app at [date]; one that cannot open a date (no `content://com.android.calendar/time`
     * handler) opens on its own main screen instead.
     */
    fun openCalendar(context: Context, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        val at = Intent(Intent.ACTION_VIEW)
            .setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(calendarTimeMillis(date, zone).toString()).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, at)) return
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(context, main)) Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
    }

    /**
     * The instant a calendar app is asked to show for [date]: local noon, so an app that reads
     * it in another zone, or across a daylight-saving change, still lands on the same day.
     */
    fun calendarTimeMillis(date: LocalDate, zone: ZoneId): Long =
        date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent, LaunchOptions.iconSplash())
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
