package app.glance.launcher.ui.home

/**
 * The phone Home column's arithmetic: the dial wants a full-width square, and the pieces around
 * it adapt. Pure, so the rule is tested on the JVM; `SpreadColumn` measures and places.
 */
object PhoneColumnPlan {
    /**
     * Which [after] pieces (by index) are left out, which are shown in their smaller fallback
     * form ([fallen]), and the dial's side.
     */
    data class Plan(val dropped: Set<Int>, val side: Int, val fallen: Set<Int> = emptySet())

    /**
     * Keeps the dial at [width] when it can: the pieces in [dropOrder] give way one at a time,
     * in order, until every remaining piece and its gap fits beside a full-width dial. A piece
     * with a height in [fallbacks] (a one-row legend for the full one) first takes that smaller
     * form, and is left out only if that is still not enough. Only when nothing is left to give
     * way does the dial shrink to the height that remains.
     */
    fun plan(
        beforeHeights: List<Int>,
        afterHeights: List<Int>,
        dropOrder: List<Int>,
        width: Int,
        height: Int,
        gap: Int,
        fallbacks: Map<Int, Int> = emptyMap(),
    ): Plan {
        val dropped = LinkedHashSet<Int>()
        val fallen = LinkedHashSet<Int>()
        fun leftover(): Int {
            val pieces = beforeHeights + afterHeights.indices.filter { it !in dropped }.map { i ->
                if (i in fallen) fallbacks.getValue(i) else afterHeights[i]
            }
            return height - pieces.sum() - pieces.size * gap
        }
        for (i in dropOrder) {
            if (leftover() >= width) break
            if (i !in afterHeights.indices) continue
            if (i in fallbacks) {
                fallen += i
                if (leftover() >= width) break
                fallen -= i
            }
            dropped += i
        }
        return Plan(dropped, minOf(width, leftover()).coerceAtLeast(0), fallen)
    }
}
