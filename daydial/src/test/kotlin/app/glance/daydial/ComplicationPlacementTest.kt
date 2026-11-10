package app.glance.daydial

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class ComplicationPlacementTest {
    private val slotW = 90f
    private val slotH = 93f

    private fun gaps(p: ComplicationPlacement.Placement, w: Float, h: Float, r: Float): List<Float> {
        val side = w / 2 - p.x - slotW / 2
        val topBottom = h / 2 - p.y - slotH / 2
        val dial = sqrt(maxOf(p.x - slotW / 2, 0f).let { it * it } + maxOf(p.y - slotH / 2, 0f).let { it * it }) - r
        return listOf(side, topBottom, dial)
    }

    @Test fun `a wide box moves the slots out to the sides, clear of the dial and the edges`() {
        // A portrait tablet's dial card: wider than tall, the dial as tall as the box.
        val w = 708f; val h = 542f; val r = h / 2
        val p = ComplicationPlacement.place(w, h, r, slotW, slotH)
        // The fixed diagonal (1.2 radii out) hung past the top and into the hour labels here.
        val diagonal = r * 1.2f / sqrt(2f)
        assertTrue(gaps(ComplicationPlacement.Placement(diagonal, diagonal, 0f), w, h, r).any { it < 0 })
        assertTrue(p.x > diagonal, "out to the side: ${p.x}")
        assertTrue(gaps(p, w, h, r).all { it >= 15f }, "room on every side: ${gaps(p, w, h, r)}")
    }

    @Test fun `a tall box moves the slots up and down`() {
        val w = 542f; val h = 708f; val r = w / 2
        val p = ComplicationPlacement.place(w, h, r, slotW, slotH)
        assertTrue(p.y > p.x, "further up than across: $p")
        assertTrue(gaps(p, w, h, r).all { it >= 15f }, "room on every side: ${gaps(p, w, h, r)}")
    }

    @Test fun `the room is shared out evenly`() {
        val w = 708f; val h = 542f; val r = h / 2
        val p = ComplicationPlacement.place(w, h, r, slotW, slotH)
        val g = gaps(p, w, h, r)
        assertTrue(g.max() - g.min() < 3f, "balanced: $g")
        assertTrue(abs(p.room - g.min()) < 0.01f)
    }

    @Test fun `a square box leaves them on the diagonals`() {
        val p = ComplicationPlacement.place(560f, 560f, 280f, slotW, slotH)
        assertTrue(abs(p.x - p.y) < 6f, "diagonal: $p")
    }

    @Test fun `the slots never cross to the other side`() {
        for ((w, h) in listOf(300f to 300f, 1200f to 400f, 400f to 1200f)) {
            val p = ComplicationPlacement.place(w, h, minOf(w, h) / 2, slotW, slotH)
            assertTrue(p.x >= slotW / 2 && p.y >= slotH / 2, "$w x $h: $p")
        }
    }
}
