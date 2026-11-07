package app.glance.launcher.ui.home

/** How Home's pinned row splits its pins between the row and the overflow pop-up. Pure. */
object PinnedLayout {
    /** How many slots of [slotDp] with [gapDp] between them fit in [widthDp]; at least one. */
    fun fit(widthDp: Float, slotDp: Float, gapDp: Float): Int =
        (((widthDp + gapDp) / (slotDp + gapDp)).toInt()).coerceAtLeast(1)

    /** The pins shown on the row and the rest: all of them when they fit, else the first [fit]. */
    fun <T> split(pins: List<T>, fit: Int): Pair<List<T>, List<T>> =
        if (pins.size <= fit) pins to emptyList() else pins.take(fit) to pins.drop(fit)
}
