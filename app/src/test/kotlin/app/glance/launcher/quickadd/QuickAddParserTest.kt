package app.glance.launcher.quickadd

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickAddParserTest {
    // A Saturday, so weekday maths is easy to follow.
    private val now = LocalDateTime.of(2026, 10, 17, 9, 0)
    private fun parse(s: String) = QuickAddParser.parse(s, now, Locale.US).draft

    @Test
    fun `call dentist tomorrow 2pm`() {
        val d = parse("call dentist tomorrow 2pm")
        assertEquals("Call dentist", d.title)
        assertEquals(LocalDate.of(2026, 10, 18), d.date)
        assertEquals(14 * 60, d.startMin)
        assertEquals(30, d.durationMin)
    }

    @Test
    fun `time without a date defaults to today`() {
        val d = parse("standup at 3:30 pm")
        assertEquals(now.toLocalDate(), d.date)
        assertEquals(15 * 60 + 30, d.startMin)
        assertEquals("Standup", d.title)
    }

    @Test
    fun `bare hour follows the working-hours rule`() {
        assertEquals(15 * 60, parse("gym at 3").startMin)
        assertEquals(9 * 60, parse("gym at 9").startMin)
        assertEquals(15 * 60, parse("gym 15:00").startMin)
        assertEquals(12 * 60, parse("lunch noon").startMin)
    }

    @Test
    fun `ranges set start and duration`() {
        val d = parse("workshop 3-4pm")
        assertEquals(15 * 60, d.startMin)
        assertEquals(60, d.durationMin)
        val e = parse("meeting from 9 to 11:30")
        assertEquals(9 * 60, e.startMin)
        assertEquals(150, e.durationMin)
        val f = parse("brunch 11-1pm")
        assertEquals(11 * 60, f.startMin)
        assertEquals(120, f.durationMin)
        // A bare 3-4 is not a range.
        assertNull(parse("read pages 3-4").startMin)
    }

    @Test
    fun `dates`() {
        assertEquals(LocalDate.of(2026, 10, 23), parse("dentist friday").date)
        assertEquals(LocalDate.of(2026, 10, 24), parse("dentist next saturday").date)
        assertEquals(LocalDate.of(2026, 10, 24), parse("dentist next week").date)
        assertEquals(LocalDate.of(2026, 10, 20), parse("dentist in 3 days").date)
        assertEquals(LocalDate.of(2027, 6, 3), parse("trip june 3").date)
        assertEquals(LocalDate.of(2026, 12, 3), parse("trip 12/3").date)
        val tonight = parse("call mum tonight")
        assertEquals(now.toLocalDate(), tonight.date)
        assertEquals(21 * 60, tonight.startMin)
        val tm = parse("walk tomorrow morning")
        assertEquals(now.toLocalDate().plusDays(1), tm.date)
        assertEquals(9 * 60, tm.startMin)
    }

    @Test
    fun `durations`() {
        assertEquals(90, parse("deep work for 1.5 hours").durationMin)
        assertEquals(90, parse("deep work 1h30m").durationMin)
        assertEquals(45, parse("run for 45 min").durationMin)
        assertEquals(60, parse("walk for an hour").durationMin)
        assertEquals(30, parse("nap for half an hour").durationMin)
        assertEquals("Deep work", parse("deep work 1h30m").title)
    }

    @Test
    fun `sigils`() {
        val d = parse("renew passport @tomorrow ~3pm %45 !!!")
        assertEquals("Renew passport", d.title)
        assertEquals(now.toLocalDate().plusDays(1), d.date)
        assertEquals(15 * 60, d.startMin)
        assertEquals(45, d.durationMin)
        assertEquals(3, d.priority)
        assertEquals(LocalDate.of(2026, 10, 20), parse("taxes \$10/20").deadline)
    }

    @Test
    fun `tags stay in the title`() {
        val r = QuickAddParser.parse("write spec #work tomorrow", now, Locale.US)
        assertEquals("Write spec #work", r.draft.title)
        assertEquals(true, "#work" in r.chips)
    }

    @Test
    fun `plain text is just a title`() {
        val d = parse("buy milk")
        assertEquals("Buy milk", d.title)
        assertNull(d.date)
        assertNull(d.startMin)
    }
}
