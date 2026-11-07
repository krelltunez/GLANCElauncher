package app.glance.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedLayoutTest {
    @Test
    fun `slots that fit, counting the gaps between them only`() {
        assertEquals(4, PinnedLayout.fit(274f, 64f, 6f)) // 4*64 + 3*6 = 274
        assertEquals(3, PinnedLayout.fit(273f, 64f, 6f))
        assertEquals(1, PinnedLayout.fit(10f, 64f, 6f))
    }

    @Test
    fun `everything on the row when it fits, the rest to the pop-up when not`() {
        assertEquals(listOf(1, 2, 3) to emptyList<Int>(), PinnedLayout.split(listOf(1, 2, 3), 4))
        assertEquals(listOf(1, 2) to listOf(3, 4, 5), PinnedLayout.split(listOf(1, 2, 3, 4, 5), 2))
    }
}
