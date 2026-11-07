package app.glance.daydial

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId

enum class AlarmMode { TOMORROW, TODAY }

/**
 * The next clock-app alarm on the dial, a port of dayGLANCE's `dialAlarmMark`
 * (`src/utils/nextAlarm.js`). Tomorrow's alarm stands at 00 with its time beside the "00"
 * label, from [DialAlarm.DEFAULT_FROM_MIN] on; after midnight it moves to its real minute until
 * it rings. An alarm later today shows at its time whatever the hour.
 */
@Immutable
data class AlarmMark(
    val mode: AlarmMode,
    /** Where the line goes: 0 for tomorrow, the alarm's minute today. */
    val minute: Int,
    /** The alarm's own minute of day, for its time label. */
    val alarmMinute: Int,
)

object DialAlarm {
    /** dayGLANCE's default: tomorrow's alarm shows from 18:00. */
    const val DEFAULT_FROM_MIN = 18 * 60

    fun mark(nowMs: Long, alarmMs: Long?, fromMin: Int, zone: ZoneId): AlarmMark? {
        if (alarmMs == null || alarmMs <= nowMs) return null
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val alarm = Instant.ofEpochMilli(alarmMs).atZone(zone)
        val alarmMin = alarm.hour * 60 + alarm.minute
        return when (alarm.toLocalDate()) {
            now.toLocalDate() -> AlarmMark(AlarmMode.TODAY, alarmMin, alarmMin)
            now.toLocalDate().plusDays(1) ->
                if (now.hour * 60 + now.minute >= fromMin) AlarmMark(AlarmMode.TOMORROW, 0, alarmMin) else null
            else -> null
        }
    }

    /** An hour label gives way to the mark once it stands at its real time, in the labels' band. */
    fun labelYields(labelMin: Int, mark: AlarmMark?): Boolean =
        mark != null && mark.mode == AlarmMode.TODAY && DialGeometry.labelYieldsToMark(labelMin, listOf(mark.minute))
}
