package app.glance.daydial

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Where the four corner readouts sit, worked out from the room the dial leaves them. Pure, tested
 * on the JVM.
 *
 * The four slots mirror one another, so one offset places them all: the centre of the bottom-right
 * slot, measured from the dial's centre, and negated on either axis for the other three. The
 * offset chosen is the one that leaves each slot the most breathing room. That room is its
 * smallest gap to the box's side, to the box's top or bottom, to the dial (a circle of
 * [dialRadius]: the hour labels reach its edge), and to the slot mirrored across the dial.
 * A wide box moves the slots out to the sides; a tall one moves them up and down; a square one
 * leaves them on the diagonals, where the corners have the only room.
 */
object ComplicationPlacement {
    /** The offset of the slot centres from the dial's centre, both positive, and the room it leaves. */
    data class Placement(val x: Float, val y: Float, val room: Float)

    /**
     * @param boxWidth the box the slots may use, centred on the dial
     * @param slotWidth the widest slot; [slotHeight] the tallest
     */
    fun place(boxWidth: Float, boxHeight: Float, dialRadius: Float, slotWidth: Float, slotHeight: Float): Placement {
        val maxX = max(boxWidth / 2f, 0f)
        val maxY = max(boxHeight / 2f, 0f)
        // A grid over the quarter box, then twice a finer one around the best point. The room is
        // not a smooth hill (the gap to the circle is not), so this beats any one-shot formula.
        var best = Placement(0f, 0f, Float.NEGATIVE_INFINITY)
        var x0 = 0f; var x1 = maxX; var y0 = 0f; var y1 = maxY
        repeat(3) {
            val steps = GRID
            val dx = (x1 - x0) / steps
            val dy = (y1 - y0) / steps
            for (i in 0..steps) {
                val x = x0 + dx * i
                for (j in 0..steps) {
                    val y = y0 + dy * j
                    val r = room(x, y, boxWidth, boxHeight, dialRadius, slotWidth, slotHeight)
                    if (r > best.room) best = Placement(x, y, r)
                }
            }
            x0 = (best.x - dx).coerceAtLeast(0f); x1 = (best.x + dx).coerceAtMost(maxX)
            y0 = (best.y - dy).coerceAtLeast(0f); y1 = (best.y + dy).coerceAtMost(maxY)
        }
        return best
    }

    /** The smallest gap a slot centred at ([x], [y]) leaves to the box edges, the dial and its mirror images. */
    fun room(x: Float, y: Float, boxWidth: Float, boxHeight: Float, dialRadius: Float, slotWidth: Float, slotHeight: Float): Float {
        val side = boxWidth / 2f - x - slotWidth / 2f
        val topBottom = boxHeight / 2f - y - slotHeight / 2f
        // The slot's corner nearest the dial's centre, or its edge when it straddles an axis.
        val nearX = max(x - slotWidth / 2f, 0f)
        val nearY = max(y - slotHeight / 2f, 0f)
        val dial = hypot(nearX, nearY) - dialRadius
        val acrossX = 2f * x - slotWidth
        val acrossY = 2f * y - slotHeight
        return min(min(side, topBottom), min(dial, min(acrossX, acrossY)))
    }

    private const val GRID = 48
}
