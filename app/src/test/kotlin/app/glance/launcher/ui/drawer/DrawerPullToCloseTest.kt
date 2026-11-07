package app.glance.launcher.ui.drawer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawerPullToCloseTest {
    private fun pull() = DrawerPullToClose(distancePx = 100f, velocityPx = 1000f)

    @Test
    fun `a pull past the distance closes on release, short of it springs back`() {
        val far = pull()
        assertEquals(60f, far.onPostScroll(60f))
        assertEquals(50f, far.onPostScroll(50f))
        assertTrue(far.release(0f))

        val short = pull()
        short.onPostScroll(60f)
        assertFalse(short.release(0f))
        assertEquals(0f, short.pulled)
    }

    @Test
    fun `a flick down closes a short pull, but a slow release does not`() {
        val flick = pull()
        flick.onPostScroll(20f)
        assertTrue(flick.release(1500f))

        val slow = pull()
        slow.onPostScroll(20f)
        assertFalse(slow.release(400f))
    }

    @Test
    fun `nothing pulled is never a close, however fast the fling`() {
        // A fling that carries the grid to its top arrives with no pull behind it.
        val p = pull()
        assertFalse(p.pulling)
        assertFalse(p.release(5000f))
    }

    @Test
    fun `dragging back up unwinds the pull before the grid scrolls`() {
        val p = pull()
        p.onPostScroll(80f)
        assertEquals(-30f, p.onPreScroll(-30f))
        assertEquals(50f, p.pulled)
        // More than is left: only the rest is taken; the grid scrolls by the remainder.
        assertEquals(-50f, p.onPreScroll(-70f))
        assertEquals(0f, p.pulled)
        assertEquals(0f, p.onPreScroll(-10f))
        assertFalse(p.release(0f))
    }

    @Test
    fun `the grid's own scrolling is left alone`() {
        val p = pull()
        // Upward drag the grid could not use (at its bottom), and downward drag with no pull yet: not taken before the grid.
        assertEquals(0f, p.onPostScroll(-40f))
        assertEquals(0f, p.onPreScroll(40f))
        assertEquals(0f, p.pulled)
    }
}
