package app.glance.launcher.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only a clock app's alarm marks the dial: the bug was any app's alarm-clock alarm counting. */
class ClockAlarmTest {
    @Test
    fun `clock apps count, by package or by uid`() {
        assertTrue(ClockAlarm.accepts(true, "com.google.android.deskclock"))
        assertTrue(ClockAlarm.accepts(true, "com.sec.android.app.clockpackage"))
        assertTrue(ClockAlarm.accepts(true, null, listOf("com.other", "com.android.deskclock")))
    }

    @Test
    fun `any other app, an unknown creator, or no show intent is no alarm`() {
        assertFalse(ClockAlarm.accepts(true, "com.google.android.calendar"))
        assertFalse(ClockAlarm.accepts(true, "com.dayglance.app"))
        assertFalse(ClockAlarm.accepts(true, null))
        assertFalse(ClockAlarm.accepts(false, "com.google.android.deskclock"))
    }
}
