package app.glance.launcher.ui

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DayRolloverTest {
    private val wed = LocalDate.of(2026, 10, 28)
    private val thu = LocalDate.of(2026, 10, 29)
    private val oct = LocalDate.of(2026, 10, 1)

    @Test
    fun `views left on today move to the new today at midnight`() {
        val days = DayRollover.Days(dial = wed, planningMonth = oct, planningSelected = wed)
        assertEquals(DayRollover.Days(thu, oct, thu), DayRollover.follow(days, wed, thu))
    }

    @Test
    fun `a day the user went to stays put`() {
        val friday = LocalDate.of(2026, 10, 30)
        val days = DayRollover.Days(dial = friday, planningMonth = LocalDate.of(2026, 12, 1), planningSelected = LocalDate.of(2026, 12, 25))
        assertEquals(days, DayRollover.follow(days, wed, thu))
    }

    @Test
    fun `the month follows the selected day into a new month, but not a month the user paged to`() {
        val oct31 = LocalDate.of(2026, 10, 31)
        val nov1 = LocalDate.of(2026, 11, 1)
        assertEquals(
            DayRollover.Days(nov1, nov1, nov1),
            DayRollover.follow(DayRollover.Days(oct31, oct, oct31), oct31, nov1),
        )
        val paged = DayRollover.Days(oct31, LocalDate.of(2027, 1, 1), oct31)
        assertEquals(DayRollover.Days(nov1, LocalDate.of(2027, 1, 1), nov1), DayRollover.follow(paged, oct31, nov1))
    }

    @Test
    fun `the same day changes nothing, and a date that goes back is followed too`() {
        val days = DayRollover.Days(wed, oct, wed)
        assertEquals(days, DayRollover.follow(days, wed, wed))
        assertEquals(DayRollover.Days(LocalDate.of(2026, 10, 27), oct, LocalDate.of(2026, 10, 27)), DayRollover.follow(days, wed, LocalDate.of(2026, 10, 27)))
    }
}
