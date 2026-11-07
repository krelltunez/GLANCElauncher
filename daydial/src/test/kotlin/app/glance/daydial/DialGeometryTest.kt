package app.glance.daydial

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ports of the rules asserted in dayGLANCE `src/utils/dayDial.test.js` and
 * `intervalLanes` tests, so the Compose dial keeps the reference geometry.
 */
class DialGeometryTest {
    private fun near(expected: Float, actual: Float, eps: Float = 0.01f) =
        assertTrue(abs(expected - actual) < eps, "expected $expected, got $actual")

    @Test
    fun `midnight is at the top and the dial runs clockwise`() {
        val (x6, y6) = DialGeometry.point(500f, 500f, 100f, 360f)
        near(600f, x6); near(500f, y6)
        val (x12, y12) = DialGeometry.point(500f, 500f, 100f, 720f)
        near(500f, x12); near(600f, y12)
        val (x0, y0) = DialGeometry.point(500f, 500f, 100f, 0f)
        near(500f, x0); near(400f, y0)
    }

    @Test
    fun `minuteAt inverts point`() {
        for (m in listOf(0f, 90f, 360f, 719f, 720f, 1000f, 1439f)) {
            val (x, y) = DialGeometry.point(500f, 500f, 300f, m)
            near(m % 1440f, DialGeometry.minuteAt(500f, 500f, x, y), 0.05f)
        }
    }

    @Test
    fun `ticks every five minutes with 24 hour and 72 quarter ticks`() {
        val ticks = DialGeometry.ticks()
        assertEquals(288, ticks.size)
        assertEquals(24, ticks.count { it.kind == DialGeometry.TickKind.HOUR })
        assertEquals(72, ticks.count { it.kind == DialGeometry.TickKind.QUARTER })
    }

    @Test
    fun `intensity saturates at 180 minutes and a sliver keeps a visible edge`() {
        val long = DialGeometry.intensity(180)
        val longer = DialGeometry.intensity(600)
        assertEquals(long, longer)
        near(0.16f, long.fillOpacity)
        near(1f, long.edgeOpacity)
        near(4f, long.edgeWidth)
        assertTrue(DialGeometry.intensity(5).edgeOpacity > 0.4f)
    }

    @Test
    fun `padding yields so slivers keep two thirds of their span`() {
        val (a, b) = DialGeometry.padSpan(540f, 660f)
        near(543f, a); near(657f, b)
        val (c, d) = DialGeometry.padSpan(540f, 552f)
        near(542f, c); near(550f, d)
        val (e, f) = DialGeometry.padSpan(540f, 660f, padStart = false)
        near(540f, e); near(657f, f)
    }

    @Test
    fun `lanes - no overlaps gives lane 0 of 1 for every block`() {
        val slots = assignLanes(listOf(LaneInput(0, 540, 600), LaneInput(1, 600, 660), LaneInput(2, 700, 720)))
        assertTrue(slots.all { it == LaneSlot(0, 1) })
    }

    @Test
    fun `lanes - nested block goes outward and count is per cluster`() {
        val slots = assignLanes(listOf(LaneInput(0, 540, 1020), LaneInput(1, 570, 630), LaneInput(2, 1100, 1160)))
        assertEquals(LaneSlot(0, 2), slots[0])
        assertEquals(LaneSlot(1, 2), slots[1])
        assertEquals(LaneSlot(0, 1), slots[2])
    }

    @Test
    fun `lanes - a freed lane is reused`() {
        val slots = assignLanes(listOf(LaneInput(0, 540, 600), LaneInput(1, 570, 630), LaneInput(2, 600, 660)))
        assertEquals(listOf(0, 1, 0), slots.map { it.lane })
    }

    @Test
    fun `lane bands stay thicker than 10 units for six lanes`() {
        for (i in 0 until 6) {
            val band = DialGeometry.laneBand(300f, 385f, i, 6)
            assertTrue(band.depth > 10f)
            assertTrue(band.rInner >= 300f && band.rOuter <= 385.01f)
        }
        assertEquals(DialGeometry.Band(300f, 385f), DialGeometry.laneBand(300f, 385f, 0, 1))
    }

    @Test
    fun `midnight clipping keeps the true end and carries the previous day's overrun`() {
        val late = DialBlock("a", "Late", startMin = 23 * 60, durationMin = 120)
        val today = layoutDayBlocks(listOf(late))
        assertEquals(1, today.size)
        assertEquals(1440, today[0].endMin)
        assertTrue(today[0].endsNextDay)
        assertEquals(25 * 60, today[0].trueEnd)

        val tomorrow = layoutDayBlocks(emptyList(), prevDayBlocks = listOf(late))
        assertEquals(1, tomorrow.size)
        assertEquals(0, tomorrow[0].startMin)
        assertEquals(60, tomorrow[0].endMin)
        assertTrue(tomorrow[0].startedPrevDay)
        assertEquals(-60, tomorrow[0].trueStart)
    }

    @Test
    fun `all-day and zero-length blocks stay off the ring`() {
        val blocks = listOf(
            DialBlock("a", "All day", 0, 60, isAllDay = true),
            DialBlock("b", "Zero", 600, 0),
            DialBlock("c", "Real", 600, 30),
        )
        assertEquals(listOf("c"), layoutDayBlocks(blocks).map { it.block.id })
    }

    @Test
    fun `no sleep without both window markers`() {
        assertTrue(sleepSegments(null).isEmpty())
        assertTrue(sleepSegments(DayWindow(600, 600)).isEmpty())
        assertEquals(listOf(Pair(0, 420), Pair(1380, 1440)), sleepSegments(DayWindow(420, 1380)))
    }

    @Test
    fun `muted colour keeps hue and pins lightness`() {
        val muted = DialGeometry.muteColor(Color(0xFF3B82F6))
        // Lightness 0.73 means (max + min) / 2 == 0.73.
        val mx = maxOf(muted.red, muted.green, muted.blue)
        val mn = minOf(muted.red, muted.green, muted.blue)
        near(0.73f, (mx + mn) / 2f, 0.02f)
        assertTrue(muted.blue > muted.red, "hue should still be blue")
        val grey = DialGeometry.muteColor(Color(0xFF808080))
        near(grey.red, grey.green, 0.01f)
    }

    @Test
    fun `focus block - running wins, nested beats container, else next, else null`() {
        val blocks = layoutDayBlocks(
            listOf(
                DialBlock("conf", "Conference", 540, 480),
                DialBlock("keynote", "Keynote", 570, 60),
                DialBlock("dinner", "Dinner", 1140, 90),
            ),
        )
        assertEquals("keynote", findFocusBlock(blocks, 600)?.block?.id)
        assertEquals("conf", findFocusBlock(blocks, 700)?.block?.id)
        assertEquals("dinner", findFocusBlock(blocks, 1100)?.block?.id)
        assertNull(findFocusBlock(blocks, 1300))
        assertNull(findFocusBlock(blocks, null))
    }

    @Test
    fun `labels yield to the sun with an exclusive 30 minute boundary measured circularly`() {
        assertTrue(DialGeometry.labelYieldsToMark(360, listOf(380)))
        assertTrue(!DialGeometry.labelYieldsToMark(360, listOf(390)))
        assertTrue(DialGeometry.labelYieldsToMark(0, listOf(1430)))
    }

    @Test
    fun `focus spans merge within two minutes and clip to the day`() {
        val merged = mergeFocusSpans(listOf(FocusSpan(600, 625), FocusSpan(626, 650), FocusSpan(700, 1500)))
        assertEquals(listOf(FocusSpan(600, 650), FocusSpan(700, 1440)), merged)
    }

    @Test
    fun `frame depth is capped at one and equal spans make the earlier frame outer`() {
        val frames = listOf(
            DialFrame("a", "Morning", 480, 720),
            DialFrame("b", "Deep", 540, 660),
            DialFrame("c", "Deeper", 560, 600),
            DialFrame("d", "Twin", 480, 720),
        )
        assertEquals(0, frameDepth(frames, 0))
        assertEquals(1, frameDepth(frames, 1))
        assertEquals(1, frameDepth(frames, 2))
        assertEquals(1, frameDepth(frames, 3))
    }

    @Test
    fun `tone - completed, owed and past`() {
        val b = layoutDayBlocks(listOf(DialBlock("a", "A", 540, 60))).single()
        assertEquals(Tone(1f, 1f), blockTone(b, nowMin = 500, dayIsPast = false, viewingToday = true))
        assertEquals(Tone(0.35f, 1f), blockTone(b, nowMin = 700, dayIsPast = false, viewingToday = true))
        assertEquals(Tone(0.6f, 0.6f), blockTone(b, nowMin = null, dayIsPast = true, viewingToday = false))
        val done = layoutDayBlocks(listOf(DialBlock("a", "A", 540, 60, completed = true))).single()
        assertEquals(Tone(0.6f, 0.25f), blockTone(done, 500, false, true))
    }

    @Test
    fun `clock and relative phrases`() {
        assertEquals("9:05 AM", formatClock(545, false))
        assertEquals("09:05", formatClock(545, true))
        assertEquals("12:00 AM", formatClock(1440, false))
        assertEquals("11:00 PM", formatClock(-60, false))
        assertEquals("1h 30m", formatDuration(90))
        val b = layoutDayBlocks(listOf(DialBlock("a", "A", 540, 60))).single()
        assertEquals("in 40m", relativePhrase(b, 500))
        assertEquals("30m left", relativePhrase(b, 570))
        assertEquals("ended 1h ago", relativePhrase(b, 660))
    }

    @Test
    fun `tags are read from the title`() {
        assertEquals(listOf("work", "deep/focus"), extractTags("Write spec #Work #deep/focus"))
        assertEquals("Write spec", titleWithoutTags("Write spec #Work #deep/focus"))
    }

    @Test
    fun `drag - move snaps and clamps, resize keeps at least one step`() {
        val block = DialBlock("a", "A", 540, 60)
        val hit = layoutDayBlocks(listOf(block)).single()
        val start = DragState(block, DragMode.MOVE, 540, 60)
        assertEquals(555, applyDrag(start, block, downMinute = 560f, minute = 577f, step = 15).previewStart)
        // Dragging across midnight uses the short way round: 560 -> 10 is -550 minutes, clamped at 0.
        assertEquals(0, applyDrag(start, block, 560f, 10f, 15).previewStart)
        // And a block cannot be pushed past the end of the day.
        val late = DialBlock("b", "B", 1200, 60)
        val lateState = DragState(late, DragMode.MOVE, 1200, 60)
        assertEquals(1380, applyDrag(lateState, late, 1220f, 1430f, 15).previewStart)

        val resize = DragState(block, DragMode.RESIZE, 540, 60)
        assertEquals(90, applyDrag(resize, block, 598f, 628f, 15).previewDuration)
        assertEquals(15, applyDrag(resize, block, 598f, 300f, 15).previewDuration)

        assertEquals(DragMode.RESIZE, dragModeFor(hit, 595f))
        assertEquals(DragMode.MOVE, dragModeFor(hit, 560f))
    }

    @Test
    fun `hit test finds the block under the ring and nothing in the hub`() {
        val blocks = layoutDayBlocks(listOf(DialBlock("a", "A", 540, 60)))
        val (x, y) = DialGeometry.point(500f, 500f, 340f, 570f)
        assertNotNull(hitTestBlock(blocks, x, y, 500f, 500f, 1f))
        val (hx, hy) = DialGeometry.point(500f, 500f, 100f, 570f)
        assertNull(hitTestBlock(blocks, hx, hy, 500f, 500f, 1f))
        val (ox, oy) = DialGeometry.point(500f, 500f, 340f, 700f)
        assertNull(hitTestBlock(blocks, ox, oy, 500f, 500f, 1f))
    }

    // A width per character, digits narrower than letters, so "November 28" is wider than
    // "November 1": the shape that made a phone's month switch form partway through.
    private val longWidth = { d: LocalDate ->
        d.format(DateTimeFormatter.ofPattern("MMMM d", Locale.US)).sumOf { c -> if (c.isDigit()) 6.0 else 10.0 }.toFloat()
    }

    @Test
    fun `a month keeps one form all month, decided by its widest date`() {
        // 44 % of 250 px is 110 px. "November 1" measures 96, "November 28" 102 and fits;
        // a 230 px dial (101.2) fits the 1st but not the 28th, so all of November abbreviates.
        val dial = 230f
        val first = LocalDate.of(2026, 11, 1)
        assertTrue(longWidth(first) <= dial * DialGeometry.DATE_MAX_FRAC, "the 1st alone would fit")
        val forms = (1..30).map { DialGeometry.monthDateFits(LocalDate.of(2026, 11, it), dial, longWidth) }.toSet()
        assertEquals(setOf(false), forms)
        assertTrue(DialGeometry.monthDateFits(first, 250f, longWidth))
    }

    @Test
    fun `short months stay spelled out where long ones abbreviate`() {
        // A phone-size dial: March to July fit; January, September, November and December do not.
        val dial = 200f
        for (m in listOf(3, 4, 5, 6, 7)) assertTrue(DialGeometry.monthDateFits(LocalDate.of(2026, m, 15), dial, longWidth), "month $m")
        for (m in listOf(1, 9, 11, 12)) assertFalse(DialGeometry.monthDateFits(LocalDate.of(2026, m, 15), dial, longWidth), "month $m")
    }
}
