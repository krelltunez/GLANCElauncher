package app.glance.daydial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The six-stat legend: an empty stat lends its place to a hidden one with data, in place. */
class LegendSlotsTest {
    private val labels = listOf("Effort", "Restore", "Sleep", "Focus", "Done", "Routines", "Frames", "Unblocked")
    private val hide = setOf("Focus", "Done")
    private fun day(sleep: Int? = 480, focus: Int = 0, planned: Int = 5, routines: Int = 3, frames: Float? = 0.5f) =
        DialBreakdown(effortMin = 0, restoreMin = 0, sleepMin = sleep, unblockedMin = 0, focusMin = focus,
            routinesDone = 0, routinesTotal = routines, framesFraction = frames, blocksDone = 0, blocksPlanned = planned)
    private fun slots(b: DialBreakdown, subs: List<String> = listOf("Done", "Focus")) =
        LegendSlots.choose(labels, hide, subs) { LegendSlots.hasData(b, it) }

    @Test
    fun `a full day shows the six defaults in their order`() {
        assertEquals(listOf("Effort", "Restore", "Sleep", "Routines", "Frames", "Unblocked"), slots(day()))
    }

    @Test
    fun `an empty stat lends its own place, nothing else moves`() {
        assertEquals(listOf("Effort", "Restore", "Sleep", "Done", "Frames", "Unblocked"), slots(day(routines = 0)))
        assertEquals(listOf("Effort", "Restore", "Sleep", "Done", "Focus", "Unblocked"), slots(day(routines = 0, frames = null, focus = 25)))
    }

    @Test
    fun `a substitute with nothing to say leaves the dash`() {
        // No focus time: only Done can stand in, and Frames keeps its dash.
        assertEquals(listOf("Effort", "Restore", "Sleep", "Done", "Frames", "Unblocked"), slots(day(routines = 0, frames = null)))
        // Nothing planned and no focus: both dashes stay.
        assertEquals(listOf("Effort", "Restore", "Sleep", "Routines", "Frames", "Unblocked"), slots(day(routines = 0, frames = null, planned = 0)))
    }

    @Test
    fun `the order of substitutes decides who goes first`() {
        assertEquals(listOf("Effort", "Restore", "Sleep", "Focus", "Frames", "Unblocked"), slots(day(routines = 0, focus = 25), listOf("Focus", "Done")))
    }

    @Test
    fun `zero effort is information and never gives way, a missing sleep window does`() {
        assertTrue(LegendSlots.hasData(day(), "Effort"))
        assertFalse(LegendSlots.hasData(day(sleep = null), "Sleep"))
        assertEquals(listOf("Effort", "Restore", "Done", "Routines", "Frames", "Unblocked"), slots(day(sleep = null)))
    }

    @Test
    fun `without substitutes the legend is as before`() {
        assertEquals(listOf("Effort", "Restore", "Sleep", "Routines", "Frames", "Unblocked"), slots(day(routines = 0), emptyList()))
    }
}
