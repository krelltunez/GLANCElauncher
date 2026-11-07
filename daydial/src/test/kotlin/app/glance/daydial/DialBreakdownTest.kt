package app.glance.daydial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DialBreakdownTest {
    @Test
    fun `totals split effort and restore and measure unblocked awake time`() {
        val blocks = listOf(
            DialBlock("a", "Work", 9 * 60, 120),
            DialBlock("b", "Walk", 12 * 60, 60, kind = DialBlockKind.RESTORE),
            DialBlock("c", "Overlap", 9 * 60 + 60, 60), // inside a; must not double count unblocked
            DialBlock("d", "All day", 0, 0, isAllDay = true),
        )
        val b = computeBreakdown(blocks, DayWindow(7 * 60, 23 * 60), listOf(FocusSpan(9 * 60, 9 * 60 + 50)), listOf(DialRoutine("r", "R", 0, 10, completed = true), DialRoutine("s", "S", 0, 10)))
        assertEquals(180, b.effortMin)
        assertEquals(60, b.restoreMin)
        assertEquals(8 * 60, b.sleepMin)
        assertEquals(16 * 60 - 180, b.unblockedMin)
        assertEquals(50, b.focusMin)
        assertEquals(1, b.routinesDone)
        assertEquals(2, b.routinesTotal)
        assertNull(b.framesFraction)
        assertEquals(0, b.blocksDone)
        assertEquals(4, b.blocksPlanned)
    }

    @Test
    fun `frames fraction counts block minutes inside top-level frames only`() {
        val blocks = listOf(DialBlock("a", "Work", 9 * 60, 60), DialBlock("b", "Late", 20 * 60, 60))
        val frames = listOf(DialFrame("f", "Morning", 8 * 60, 12 * 60), DialFrame("g", "Deep", 9 * 60, 10 * 60))
        val b = computeBreakdown(blocks, null, frames = frames)
        assertEquals(0.25f, b.framesFraction)
        assertNull(b.sleepMin)
    }

    @Test
    fun `complication tiers follow the measured dial size`() {
        assertNull(complicationTier(519f))
        assertEquals("sm", complicationTier(520f)?.key)
        assertEquals("md", complicationTier(700f)?.key)
        assertEquals("lg", complicationTier(1200f)?.key)
    }
}
