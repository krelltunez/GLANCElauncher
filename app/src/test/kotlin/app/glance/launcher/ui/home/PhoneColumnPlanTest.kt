package app.glance.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The dial stays full width; the pieces around it give way first. */
class PhoneColumnPlanTest {
    // A 360-wide phone: header 40, then pill 70, legend 60, pinned 50, search 52; 8 gaps.
    private val before = listOf(40)
    private val after = listOf(70, 60, 50, 52)

    @Test
    fun `room for everything keeps everything and a full-width dial`() {
        // 40 + 232 + 5*8 = 312; 312 + 360 = 672.
        assertEquals(PhoneColumnPlan.Plan(emptySet(), 360), PhoneColumnPlan.plan(before, after, listOf(1), 360, 700, 8))
        assertEquals(PhoneColumnPlan.Plan(emptySet(), 360), PhoneColumnPlan.plan(before, after, listOf(1), 360, 672, 8))
    }

    @Test
    fun `short of room, the legend goes before the dial shrinks`() {
        assertEquals(PhoneColumnPlan.Plan(setOf(1), 360), PhoneColumnPlan.plan(before, after, listOf(1), 360, 671, 8))
        // Without the legend: 40 + 172 + 4*8 = 244 beside the dial, so 604 in all.
        assertEquals(PhoneColumnPlan.Plan(setOf(1), 360), PhoneColumnPlan.plan(before, after, listOf(1), 360, 604, 8))
    }

    @Test
    fun `with nothing left to drop, the dial takes what remains`() {
        assertEquals(PhoneColumnPlan.Plan(setOf(1), 350), PhoneColumnPlan.plan(before, after, listOf(1), 360, 594, 8))
        // A second droppable piece is used before the dial gives way.
        assertEquals(PhoneColumnPlan.Plan(setOf(1, 0), 360), PhoneColumnPlan.plan(before, after, listOf(1, 0), 360, 594, 8))
    }

    @Test
    fun `short of room, a piece takes its smaller form before it is left out`() {
        // The one-row legend is 30 high. 671 does not fit the full legend (needs 672) but fits the
        // one-row one (needs 642).
        val oneRow = mapOf(1 to 30)
        assertEquals(PhoneColumnPlan.Plan(emptySet(), 360, setOf(1)), PhoneColumnPlan.plan(before, after, listOf(1), 360, 671, 8, oneRow))
        assertEquals(PhoneColumnPlan.Plan(emptySet(), 360, setOf(1)), PhoneColumnPlan.plan(before, after, listOf(1), 360, 642, 8, oneRow))
        // One pixel short of the one-row legend: it goes, and the dial keeps its full width.
        assertEquals(PhoneColumnPlan.Plan(setOf(1), 360), PhoneColumnPlan.plan(before, after, listOf(1), 360, 641, 8, oneRow))
    }

    @Test
    fun `with room for the full piece, its smaller form is not used`() {
        assertEquals(PhoneColumnPlan.Plan(emptySet(), 360), PhoneColumnPlan.plan(before, after, listOf(1), 360, 700, 8, mapOf(1 to 30)))
    }
}
