package app.glance.daydial

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The alarm mark: the same cases as dialAlarmMark's in dayGLANCE's nextAlarm.test.js. */
class DialAlarmTest {
    private val zone = ZoneId.of("America/Denver")
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 8, 2 + day, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun mark(now: Long, alarm: Long?, fromMin: Int = DialAlarm.DEFAULT_FROM_MIN) = DialAlarm.mark(now, alarm, fromMin, zone)

    @Test fun `tomorrow morning - nothing during the day, the 00 mark from the evening on`() {
        assertNull(mark(at(0, 14, 0), at(1, 6, 30)))
        assertNull(mark(at(0, 17, 59), at(1, 6, 30)))
        assertEquals(AlarmMark(AlarmMode.TOMORROW, 0, 390), mark(at(0, 18, 0), at(1, 6, 30)))
        assertEquals(AlarmMark(AlarmMode.TOMORROW, 0, 390), mark(at(0, 23, 1), at(1, 6, 30)))
    }

    @Test fun `after midnight the mark moves to the real time until it rings`() {
        assertEquals(AlarmMark(AlarmMode.TODAY, 390, 390), mark(at(1, 0, 40), at(1, 6, 30)))
        assertEquals(AlarmMark(AlarmMode.TODAY, 390, 390), mark(at(1, 6, 29), at(1, 6, 30)))
        assertNull(mark(at(1, 6, 30), at(1, 6, 30)))
    }

    @Test fun `an alarm later today shows at its time, whatever the hour`() {
        assertEquals(AlarmMark(AlarmMode.TODAY, 855, 855), mark(at(0, 10, 0), at(0, 14, 15)))
    }

    @Test fun `always shows tomorrow's all day, further out never shows`() {
        assertEquals(AlarmMark(AlarmMode.TOMORROW, 0, 390), mark(at(0, 9, 0), at(1, 6, 30), 0))
        assertNull(mark(at(0, 22, 0), at(2, 6, 30), 0))
    }

    @Test fun `no alarm, or one already rung, is no mark`() {
        assertNull(mark(at(0, 22, 0), null))
        assertNull(mark(at(0, 22, 0), at(0, 21, 0)))
    }

    @Test fun `an hour label gives way only to an alarm at its real time`() {
        val nine = AlarmMark(AlarmMode.TODAY, 530, 530)
        assertTrue(DialAlarm.labelYields(540, nine))
        assertFalse(DialAlarm.labelYields(720, nine))
        assertFalse(DialAlarm.labelYields(0, AlarmMark(AlarmMode.TOMORROW, 0, 390)))
        assertTrue(DialAlarm.labelYields(0, AlarmMark(AlarmMode.TODAY, 1435, 1435)))
    }
}
