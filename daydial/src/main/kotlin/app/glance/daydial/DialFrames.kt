package app.glance.daydial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Frames as the hub reads them and a tap finds them, ported from dayGLANCE
 * `src/utils/dayDial.js` (`dialCurrentFrame`, `dialFrameAvailableMinutes`) and `DayDial.jsx`'s
 * `FrameEnclosures`. Pure, so the rules are tested on the JVM.
 */
object DialFrames {
    /** Frames shorter than this draw no enclosure, so nothing to tap (`DIAL_FRAME_MIN_MINUTES`). */
    const val MIN_MINUTES = 10

    /**
     * The frame the hub speaks for at [nowMin]: the innermost one it is inside, and between two
     * at the same depth the later-starting. Null on another day.
     */
    fun current(frames: List<DialFrame>, nowMin: Int?): DialFrame? {
        if (nowMin == null) return null
        var pick: DialFrame? = null
        var pickDepth = -1
        for ((i, f) in frames.withIndex()) {
            if (f.startMin > nowMin || nowMin >= f.endMin) continue
            val depth = frameDepth(frames, i)
            if (pick == null || depth > pickDepth || (depth == pickDepth && f.startMin >= pick.startMin)) {
                pick = f
                pickDepth = depth
            }
        }
        return pick
    }

    /**
     * Free minutes still ahead in [frame] from [nowMin] (null: from the start of the day). From
     * the frame's own slots when dayGLANCE sent them, as the app's dial reads them. A build that
     * sends none leaves the busy time to [blocks], merged first so overlapping blocks are not
     * counted twice.
     */
    fun availableMinutes(frame: DialFrame, blocks: List<Pair<Int, Int>>, nowMin: Int?): Int {
        frame.slots?.let { slots -> return slots.sumOf { (s, e) -> max(0, e - max(s, nowMin ?: 0)) } }
        val from = max(frame.startMin, nowMin ?: 0)
        if (frame.endMin <= from) return 0
        val busy = mergeSpans(blocks).sumOf { (s, e) -> max(0, min(e, frame.endMin) - max(s, from)) }
        return frame.endMin - from - busy
    }

    /** The outline's stroke width in box units at [scale] px per unit: 1.2 phone px, at most 2.4 screen px. */
    fun strokeWidth(scale: Float): Float = min(PHONE_STROKE, 2.4f / scale)

    /** The enclosure's inner and outer radii (box units) at [depth]: a nested frame steps in on both. */
    fun radii(depth: Int, scale: Float): Pair<Float, Float> {
        val width = strokeWidth(scale)
        val step = min(depth, 1) * (3.2f / 22f) * 85f
        val outerEdge = DialGeometry.R_EDGE + (2f / 22f) * 85f + PHONE_STROKE / 2f
        return (DialGeometry.R_INNER + width / 2f + step) to (outerEdge - width / 2f - step)
    }

    /**
     * The frame whose outline a tap lands on, as the app's wide transparent stroke: within [tol]
     * box units of either arc across the frame's span, or of a radial edge between the arcs. The
     * tap is at radius [r] (box units) and [minute] of the day. When outlines meet, the innermost
     * frame wins, then the later-starting, as [current] picks.
     */
    fun hitTest(frames: List<DialFrame>, r: Float, minute: Float, tol: Float, scale: Float): DialFrame? {
        var pick: DialFrame? = null
        var pickDepth = -1
        for ((i, f) in frames.withIndex()) {
            if (f.endMin - f.startMin < MIN_MINUTES) continue
            val depth = frameDepth(frames, i)
            val (inner, outer) = radii(depth, scale)
            val onArc = minute >= f.startMin && minute <= f.endMin && (abs(r - inner) <= tol || abs(r - outer) <= tol)
            val arcPerMinute = (2f * PI.toFloat() * r) / MINUTES_PER_DAY
            val onEdge = r >= inner - tol && r <= outer + tol &&
                (abs(minute - f.startMin) * arcPerMinute <= tol || abs(minute - f.endMin) * arcPerMinute <= tol)
            if (!onArc && !onEdge) continue
            if (pick == null || depth > pickDepth || (depth == pickDepth && f.startMin >= pick.startMin)) {
                pick = f
                pickDepth = depth
            }
        }
        return pick
    }

    private const val PHONE_STROKE = 1.2f / 22f * 85f
}
