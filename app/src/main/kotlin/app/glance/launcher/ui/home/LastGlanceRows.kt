package app.glance.launcher.ui.home

/**
 * What one of Home's lastGLANCE cards lists under its numbers. Pure: tested on the JVM.
 *
 * What needs doing, up to [rows]. When nothing does, the card is caught up and lists what comes
 * due next instead, so it does not sit empty beside a busy one. A caught-up card never lists more
 * rows than the card beside it ([otherDue], the other card's own due rows, side by side only), so
 * it can never make the pair taller than the busy card alone; beside another caught-up card, or
 * stacked, it takes the same [rows] a busy card would.
 */
object LastGlanceRows {
    data class Plan<T>(val items: List<T>, val caughtUp: Boolean)

    fun <T> plan(due: List<T>, upcoming: List<T>, rows: Int, otherDue: Int? = null): Plan<T> {
        if (due.isNotEmpty()) return Plan(due.take(rows), caughtUp = false)
        val cap = otherDue?.takeIf { it > 0 }?.coerceAtMost(rows) ?: rows
        return Plan(upcoming.take(cap), caughtUp = true)
    }
}
