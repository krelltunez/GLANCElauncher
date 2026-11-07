package app.glance.launcher.alarm

import android.app.AlarmManager
import android.content.Context

/**
 * The next alarm the user set in a CLOCK app: the wake-up the dial marks. A port of
 * dayGLANCE's `alarm/ClockAlarm.kt` (GLANCEahead), so the launcher and the app agree on
 * whether there is an alarm at all.
 *
 * `AlarmManager.getNextAlarmClock()` returns the next alarm-clock alarm of ANY app:
 * calendar reminders, dayGLANCE's own task reminders and automation apps all schedule
 * them, and reading it raw put an alarm mark on the dial with no alarm set. The alarm
 * carries no label, but its show intent names the app that created it, and only alarms
 * a known clock app created count. A null show intent, or any other app, is no alarm.
 *
 * The API gives only ONE alarm: when another app's alarm comes first, the wake alarm
 * behind it is hidden until that one fires, and the dial shows none. That is the same
 * accepted failure as in the app. The launcher holds QUERY_ALL_PACKAGES, so the creator
 * is always visible; the uid lookup is the fallback the app needs, kept for parity.
 */
object ClockAlarm {
    /** Clock apps whose alarms count; the same list as dayGLANCE's CLOCK_PACKAGES. */
    val CLOCK_PACKAGES: Set<String> = setOf(
        "com.google.android.deskclock",      // Google Clock
        "com.sec.android.app.clockpackage",  // Samsung Clock
        "com.android.deskclock",             // AOSP Clock, Xiaomi/MIUI
        "com.oneplus.deskclock",             // OnePlus (OxygenOS, older)
        "com.coloros.alarmclock",            // OPPO / realme / OnePlus (ColorOS)
        "com.huawei.deskclock",              // Huawei (EMUI)
        "com.hihonor.deskclock",             // Honor
    )

    /** Whether an alarm with this show-intent creator is a clock-app alarm. Pure. */
    fun accepts(hasShowIntent: Boolean, creatorPackage: String?, uidPackages: List<String> = emptyList()): Boolean =
        hasShowIntent && ((creatorPackage != null && creatorPackage in CLOCK_PACKAGES) || uidPackages.any { it in CLOCK_PACKAGES })

    /** Epoch millis of the next clock-app alarm, or null (none, or another app's). */
    fun nextTriggerMillis(context: Context): Long? = try {
        val next = (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.nextAlarmClock
        if (next == null) {
            null
        } else {
            val show = next.showIntent
            val creator = try { show?.creatorPackage } catch (_: Throwable) { null }
            val uid = try { show?.creatorUid } catch (_: Throwable) { null }
            val uidPackages = if (creator == null && uid != null && uid >= 0) {
                try { context.packageManager.getPackagesForUid(uid)?.toList().orEmpty() } catch (_: Throwable) { emptyList() }
            } else emptyList()
            if (accepts(show != null, creator, uidPackages)) next.triggerTime else null
        }
    } catch (_: Exception) {
        null
    }
}
