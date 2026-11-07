package app.glance.daydial

/** Half-open interval [start, end) with the index of the item it came from. */
data class LaneInput(val index: Int, val start: Int, val end: Int)

/** The lane an interval landed in and how many lanes its cluster needed. */
data class LaneSlot(val lane: Int, val laneCount: Int)

/**
 * Packs overlapping intervals into concentric lanes.
 *
 * Port of `assignLanes` in dayGLANCE `src/utils/intervalLanes.js`:
 * - intervals are half-open, so touching intervals share a lane;
 * - input is sorted by start then end;
 * - a cluster is a chain of transitively overlapping intervals;
 * - inside a cluster each interval takes the first lane whose last end <= its start,
 *   otherwise a new lane; laneCount is per cluster;
 * - lane 0 is innermost.
 *
 * The result is indexed like the input.
 */
fun assignLanes(intervals: List<LaneInput>): List<LaneSlot> {
    if (intervals.isEmpty()) return emptyList()
    val order = intervals.indices.sortedWith(compareBy({ intervals[it].start }, { intervals[it].end }))
    val sorted = order.map { intervals[it] }
    val result = arrayOfNulls<LaneSlot>(intervals.size)
    var i = 0
    while (i < sorted.size) {
        // Grow a cluster.
        var clusterEnd = sorted[i].end
        var j = i + 1
        while (j < sorted.size && sorted[j].start < clusterEnd) {
            clusterEnd = maxOf(clusterEnd, sorted[j].end)
            j++
        }
        val laneEnds = ArrayList<Int>()
        val lanesFor = IntArray(j - i)
        for (k in i until j) {
            val item = sorted[k]
            var lane = laneEnds.indexOfFirst { it <= item.start }
            if (lane < 0) {
                lane = laneEnds.size
                laneEnds.add(item.end)
            } else {
                laneEnds[lane] = item.end
            }
            lanesFor[k - i] = lane
        }
        val count = laneEnds.size
        for (k in i until j) {
            result[order[k]] = LaneSlot(lanesFor[k - i], count)
        }
        i = j
    }
    return result.map { it ?: LaneSlot(0, 1) }
}
