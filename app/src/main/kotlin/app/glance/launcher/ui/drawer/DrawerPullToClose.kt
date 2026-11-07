package app.glance.launcher.ui.drawer

/**
 * Swipe down at the top of the drawer closes it. Fed the downward drag the app grid could not
 * use (it is already at the top) and the drags on the header, it keeps how far the drawer has
 * been pulled over one gesture and, on release, says whether that was a close. Pure: tested on
 * the JVM. Positive is down, in pixels (and pixels per second for a fling).
 *
 * A pull that is let go short of [distancePx] springs back, unless it was flicked down at
 * [velocityPx] or faster; dragging back up unwinds it before the grid scrolls again. A fling
 * that carries the grid up to its top does not start a pull: only a finger can.
 */
class DrawerPullToClose(private val distancePx: Float, private val velocityPx: Float) {
    /** How far the drawer is pulled down right now; the drawer is drawn this far (scaled) lower. */
    var pulled: Float = 0f
        private set

    /** Before the grid scrolls: an upward drag unwinds a pull first. Returns the part it took. */
    fun onPreScroll(dy: Float): Float {
        if (pulled <= 0f || dy >= 0f) return 0f
        val take = maxOf(dy, -pulled)
        pulled += take
        return take
    }

    /** After the grid scrolled: the downward drag it could not use becomes pull. Returns the part it took. */
    fun onPostScroll(dy: Float): Float {
        if (dy <= 0f) return 0f
        pulled += dy
        return dy
    }

    /** True while a pull is in progress, so the release's fling is the drawer's, not the grid's. */
    val pulling: Boolean get() = pulled > 0f

    /** The finger lifted, flinging at [vy]: whether the drawer closes. The pull ends either way. */
    fun release(vy: Float): Boolean {
        val close = pulled >= distancePx || (pulled > 0f && vy >= velocityPx)
        pulled = 0f
        return close
    }
}
