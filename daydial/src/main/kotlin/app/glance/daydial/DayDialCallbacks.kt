package app.glance.daydial

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * Everything the dial can ask its host to do. The dial never writes data itself: every
 * interaction that changes state goes through one of these so each host wires it to its
 * own write path (the launcher's repository, dayGLANCE's task store).
 *
 * All callbacks default to no-ops so a host can start with a read-only dial.
 */
@Immutable
class DayDialCallbacks(
    /** A block was tapped once (inspected). */
    val onBlockTap: (DialBlock) -> Unit = {},
    /** "Mark complete" / "Mark not complete" from the action sheet. */
    val onToggleComplete: (DialBlock) -> Unit = {},
    /** A routine bar was tapped twice; hosts usually toggle the routine's completion. */
    val onToggleRoutine: (DialRoutine) -> Unit = {},
    /** A block was dragged along the ring. [newStartMin] is already snapped and clamped. */
    val onMoveBlock: (block: DialBlock, newStartMin: Int) -> Unit = { _, _ -> },
    /** A block's end edge was dragged. [newDurationMin] is already snapped and at least one step. */
    val onResizeBlock: (block: DialBlock, newDurationMin: Int) -> Unit = { _, _ -> },
    /** "Open in planner" from the action sheet. */
    val onOpenBlock: (DialBlock) -> Unit = {},
    /** Long-press on an empty stretch of the ring. [startMin] is snapped. */
    val onCreateAt: (startMin: Int) -> Unit = {},
    /** "Start focus session" for the block running now. Null hides the button. */
    val onStartFocus: ((DialBlock) -> Unit)? = null,
    /** Chevrons beside the weekday in the hub. */
    val onStepDay: (delta: Int) -> Unit = {},
    /** The "Today" pill shown when the dial is on another day. */
    val onGoToday: () -> Unit = {},
    /** The clock in the hub, shown only on today. Null leaves it plain text (the launcher opens the clock app). */
    val onClockTap: (() -> Unit)? = null,
    /** The date in the hub, with the date the dial shows. Null leaves it plain text (the launcher opens the calendar there). */
    val onDateTap: ((LocalDate) -> Unit)? = null,
)
