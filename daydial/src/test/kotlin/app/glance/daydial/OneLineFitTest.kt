package app.glance.daydial

import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone pill's one-line rows: what fits beside the "+N" it leaves. */
class OneLineFitTest {
    private val more = { n: Int -> 20 + n.toString().length * 6 } // "+3" is 26 wide

    @Test
    fun `everything fits without a +N`() {
        assertEquals(3, OneLineFit.shown(listOf(60, 60, 60), extra = 0, available = 200, gap = 10, moreWidth = more))
    }

    @Test
    fun `the last chip gives way to the +N it would need`() {
        // Three chips need 200; two plus "+1" need 60+10+60+10+26 = 166.
        assertEquals(2, OneLineFit.shown(listOf(60, 60, 60), extra = 0, available = 199, gap = 10, moreWidth = more))
    }

    @Test
    fun `chips past the pill's limit always need room for +N`() {
        // Two chips and "+4": 60+10+60+10+26 = 166. Below that, one chip and "+5".
        assertEquals(2, OneLineFit.shown(listOf(60, 60), extra = 4, available = 166, gap = 10, moreWidth = more))
        assertEquals(1, OneLineFit.shown(listOf(60, 60), extra = 4, available = 165, gap = 10, moreWidth = more))
    }

    @Test
    fun `the first chip always shows, and no chips is none`() {
        assertEquals(1, OneLineFit.shown(listOf(300, 60), extra = 0, available = 100, gap = 10, moreWidth = more))
        assertEquals(0, OneLineFit.shown(emptyList(), extra = 0, available = 100, gap = 10, moreWidth = more))
    }
}
