package app.glance.launcher.ui.home

/** How Home's pinned row splits its pins between the row and the overflow pop-up. Pure. */
object PinnedLayout {
    /**
     * How many pins fit in [widthDp]: each needs its icon ([iconDp]) and at least [minGapDp] of
     * room beside it (the gap before the next pin, or before the button after the last one).
     * At least one. The row then splits [widthDp] into that many equal slots, so a full row has
     * even gaps and no leftover pooled before the button.
     */
    fun fit(widthDp: Float, iconDp: Float, minGapDp: Float): Int =
        (widthDp / (iconDp + minGapDp)).toInt().coerceAtLeast(1)

    /** The pins shown on the row and the rest: all of them when they fit, else the first [fit]. */
    fun <T> split(pins: List<T>, fit: Int): Pair<List<T>, List<T>> =
        if (pins.size <= fit) pins to emptyList() else pins.take(fit) to pins.drop(fit)
}
