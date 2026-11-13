package app.glance.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedLayoutTest {
    @Test
    fun `slots that fit, each an icon and its minimum gap`() {
        assertEquals(4, PinnedLayout.fit(256f, 48f, 16f)) // 4 * (48 + 16) = 256
        assertEquals(3, PinnedLayout.fit(255f, 48f, 16f))
        assertEquals(1, PinnedLayout.fit(10f, 48f, 16f))
    }

    @Test
    fun `the widths the row sees on the test devices`() {
        // Pixel Tablet, landscape: ~523 dp inside the card, less the 44 dp button.
        assertEquals(7, PinnedLayout.fit(523f - 44f, 48f, 16f))
        // A 411 dp phone: ~359 dp inside the card, less the 40 dp button.
        assertEquals(5, PinnedLayout.fit(359f - 40f, 40f, 16f))
    }

    @Test
    fun `everything on the row when it fits, the rest to the pop-up when not`() {
        assertEquals(listOf(1, 2, 3) to emptyList<Int>(), PinnedLayout.split(listOf(1, 2, 3), 4))
        assertEquals(listOf(1, 2) to listOf(3, 4, 5), PinnedLayout.split(listOf(1, 2, 3, 4, 5), 2))
    }
}
