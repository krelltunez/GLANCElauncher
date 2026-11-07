package app.glance.daydial

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Minutes past midnight. The dial is always a 24-hour face: one revolution per day,
 * midnight at the top, clockwise. 1440 minutes per revolution.
 */
const val MINUTES_PER_DAY = 1440

/**
 * A scheduled block on the ring. Hosts build these from their own task or event rows.
 *
 * [startMin] is the true start, [durationMin] the true length; a block may run past
 * midnight and the dial clips it to the day it is drawn on. [completable] is false for
 * read-only items (imported calendar events) so the dial never offers to complete them.
 */
@Immutable
data class DialBlock(
    val id: String,
    val title: String,
    val startMin: Int,
    val durationMin: Int,
    val color: Color = DialColors.DefaultTask,
    val completed: Boolean = false,
    val completable: Boolean = true,
    val isAllDay: Boolean = false,
    val movable: Boolean = true,
    /** Effort or restore; feeds the breakdown totals only, never the wedge colour. */
    val kind: DialBlockKind = DialBlockKind.EFFORT,
) {
    val endMin: Int get() = startMin + durationMin
}

/** The energy a block takes, as dayGLANCE derives it from the task's energy field or restore keywords. */
enum class DialBlockKind { EFFORT, RESTORE }

/** A frame (enclosure): a named stretch of the day drawn as an outline around the ring. */
@Immutable
data class DialFrame(
    val id: String,
    val name: String,
    val startMin: Int,
    val endMin: Int,
    val color: Color = Color(0xFFC8C8D2),
    /**
     * Its free time as minute spans, as dayGLANCE computes it (the snapshot's `slots`), from
     * which the hub says how much is still available. Null when the build sent none.
     */
    val slots: List<Pair<Int, Int>>? = null,
)

/** A routine: a teal bar in the band outside the ring, inside the tick field. */
@Immutable
data class DialRoutine(
    val id: String,
    val name: String,
    val startMin: Int,
    val endMin: Int,
    val completed: Boolean = false,
)

/** The awake window. Time outside it is drawn as sleep. */
@Immutable
data class DayWindow(val startMin: Int, val stopMin: Int)

/** Sunrise and sunset, minutes past midnight, for the daylight band and the sun marks. */
@Immutable
data class SunTimes(val sunriseMin: Int, val sunsetMin: Int)

/** A completed focus span, drawn on the rail just inside the ring. */
@Immutable
data class FocusSpan(val startMin: Int, val endMin: Int)

/**
 * Fixed colours of the dial. The dial has a single dark look that is deliberately
 * independent of the host theme, as in the dayGLANCE web app.
 */
object DialColors {
    val Now = Color(0xFFFE8B00)
    val Effort = Color(0xFF93C5FD)
    val Restore = Color(0xFF5EEAD4)
    val Sleep = Color(0xFFC4B5FD)
    val Unblocked = Color(0xFF9CA3AF)
    val Routine = Color(0xFF5EEAD4)
    val Daylight = Color(0xFFFCD34D)
    val Sun = Color(0xFFFBBF24)
    val Moon = Color(0xFFCBD5E1)
    val Alarm = Color(0xFF38BDF8)
    val FrameLegend = Color(0xFFC8C8D2)
    val FrameAvailable = Color(0xFF4EC9B0)
    val DefaultTask = Color(0xFF3B82F6)
    val FallbackMuted = Color(0xFF93C5FD)
    val Face = Color(0xFF0B0D12)
    val Sheet = Color(0xFF12151C)
    val Select = Color(0xFF1A1E27)
    val Ink = Color.White
}
