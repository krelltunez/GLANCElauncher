package app.glance.daydial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ports of dayGLANCE's `dialCurrentFrame` and `dialFrameAvailableMinutes` rules
 * (`src/utils/dayDial.test.js`), plus the frame outline's tap target.
 */
class DialFramesTest {
    private val work = DialFrame("work", "Work", 9 * 60, 17 * 60)
    private val deep = DialFrame("deep", "Deep work", 10 * 60, 12 * 60)

    @Test
    fun `the hub names the innermost frame now is in`() {
        // Work encloses Deep work: at 10:30 the hub says Deep work, whatever the order.
        assertEquals("deep", DialFrames.current(listOf(work, deep), 10 * 60 + 30)?.id)
        assertEquals("deep", DialFrames.current(listOf(deep, work), 10 * 60 + 30)?.id)
        assertEquals("work", DialFrames.current(listOf(work, deep), 13 * 60)?.id)
        assertNull(DialFrames.current(listOf(work, deep), 18 * 60))
        assertNull(DialFrames.current(listOf(work), null))
    }

    @Test
    fun `between overlapping frames at one depth, the later-starting speaks`() {
        val a = DialFrame("a", "A", 9 * 60, 12 * 60)
        val b = DialFrame("b", "B", 11 * 60, 14 * 60)
        assertEquals("b", DialFrames.current(listOf(a, b), 11 * 60 + 30)?.id)
    }

    @Test
    fun `available time comes from the frame's own slots when dayGLANCE sent them`() {
        val f = deep.copy(slots = listOf(10 * 60 to 10 * 60 + 30, 11 * 60 to 12 * 60))
        // From 10:15: 15 minutes left of the first slot, all 60 of the second. Blocks are ignored.
        assertEquals(75, DialFrames.availableMinutes(f, listOf(10 * 60 to 12 * 60), 10 * 60 + 15))
        assertEquals(90, DialFrames.availableMinutes(f, emptyList(), null))
        assertEquals(0, DialFrames.availableMinutes(f.copy(slots = emptyList()), emptyList(), 10 * 60))
    }

    @Test
    fun `without slots, overlapping blocks are not counted twice`() {
        // 10:00-12:00, two blocks overlapping 10:30-11:00 and 10:45-11:15: 45 minutes busy, 75 free.
        val blocks = listOf(10 * 60 + 30 to 11 * 60, 10 * 60 + 45 to 11 * 60 + 15)
        assertEquals(75, DialFrames.availableMinutes(deep, blocks, 10 * 60))
        // From 11:00, only the 15 minutes of the second block still ahead count.
        assertEquals(45, DialFrames.availableMinutes(deep, blocks, 11 * 60))
        assertEquals(0, DialFrames.availableMinutes(deep, blocks, 12 * 60))
    }

    @Test
    fun `a tap finds a frame on its outline, not inside it`() {
        val scale = 0.4f
        val (inner, outer) = DialFrames.radii(0, scale)
        val tol = 30f
        val frames = listOf(deep)
        // On the outer and the inner arc, mid-span.
        assertEquals("deep", DialFrames.hitTest(frames, outer + 5f, 11 * 60f, tol, scale)?.id)
        assertEquals("deep", DialFrames.hitTest(frames, inner - 5f, 11 * 60f, tol, scale)?.id)
        // Midway between the arcs, mid-span: inside the enclosure, not on it.
        assertNull(DialFrames.hitTest(frames, (inner + outer) / 2f, 11 * 60f, tol, scale))
        // On a radial edge, midway between the arcs: two minutes past the start is a few units away.
        assertEquals("deep", DialFrames.hitTest(frames, (inner + outer) / 2f, 10 * 60f + 2f, tol, scale)?.id)
        // Past the span along the arc: nothing.
        assertNull(DialFrames.hitTest(frames, outer, 14 * 60f, tol, scale))
    }

    @Test
    fun `where outlines meet, the nested frame wins, and a sliver has no target`() {
        val scale = 0.4f
        // Deep work starts on the hour inside Work; its outline steps in, so aim at its outer arc.
        val (_, deepOuter) = DialFrames.radii(1, scale)
        assertEquals("deep", DialFrames.hitTest(listOf(work, deep), deepOuter, 11 * 60f, 30f, scale)?.id)
        val sliver = DialFrame("s", "Sliver", 9 * 60, 9 * 60 + 5)
        val (_, outer) = DialFrames.radii(0, scale)
        assertNull(DialFrames.hitTest(listOf(sliver), outer, 9 * 60 + 2f, 30f, scale))
    }
}
