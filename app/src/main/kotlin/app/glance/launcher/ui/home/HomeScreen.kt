@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.glance.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.daydial.AllDayPill
import app.glance.daydial.DayDial
import app.glance.daydial.DayDialCallbacks
import app.glance.daydial.DayDialTypography
import app.glance.daydial.DayWindow
import app.glance.daydial.DialColors
import app.glance.daydial.DialComplication
import app.glance.daydial.DialGlyphs
import app.glance.daydial.DialLegend
import app.glance.daydial.SunTimes
import app.glance.daydial.formatClock
import app.glance.daydial.DialSky
import app.glance.daydial.DialWeather
import app.glance.daydial.DialAlarm
import app.glance.daydial.computeBreakdown
import app.glance.launcher.data.Cadence
import app.glance.launcher.data.CadenceState
import app.glance.launcher.data.Task
import app.glance.launcher.intents.GlanceIntents
import app.glance.launcher.ui.GlanceIcons
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.appliesTo
import app.glance.launcher.ui.common.AppIcon
import app.glance.launcher.ui.common.PinIcon
import app.glance.launcher.ui.common.appStatusWord
import app.glance.launcher.ui.common.GlanceCard
import app.glance.launcher.ui.common.LastGlanceGreen
import app.glance.launcher.ui.common.LastGlanceWordmark
import app.glance.launcher.ui.common.LucideIcon
import app.glance.launcher.ui.common.DayCell
import app.glance.launcher.ui.common.SectionHeader
import app.glance.launcher.ui.common.LauncherWordmark
import app.glance.launcher.ui.common.topBarsPadding
import app.glance.launcher.ui.theme.GlanceTheme
import app.glance.launcher.ui.theme.LoraFamily
import app.glance.launcher.ui.toDialBlock
import app.glance.launcher.ui.toDialFrame
import app.glance.launcher.ui.toDialRoutine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.widthIn

/**
 * Home: the Day Dial is the star. Landscape puts it full height on the left with a column
 * beside it (All Day pill, breakdown, week strip, lastGLANCE chores and people cards, pinned
 * apps, search bar); portrait stacks that column under the dial, which takes the height the
 * column leaves. Nothing scrolls; swipe up opens the drawer, swipe down the notification shade.
 */
@Composable
fun HomeScreen(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val repo = controller.repository
    val tasks by repo.tasks.collectAsState()
    val frames by repo.frames.collectAsState()
    val routines by repo.routines.collectAsState()
    val shape by repo.dayShape.collectAsState()
    val availability by repo.availability.collectAsState()
    val chores by repo.chores.collectAsState()
    val people by repo.people.collectAsState()
    val clockOverride by controller.graph.prefs.use24HourClock.collectAsState()
    val dockEnabled by controller.graph.prefs.dockEnabled.collectAsState()
    val use24 = clockOverride ?: shape.use24HourClock
    val nowMin = rememberNowMinute()
    val today = remember(nowMin) { LocalDate.now() }
    val date = controller.dialDate
    val context = LocalContext.current

    // The dial follows dayGLANCE's layer toggles (DialLayers): its blocks, frames, routines and
    // the legend under it; the sky and weather layers are applied in DialPanel.
    val layers = shape.dialLayers
    val dialTasks = remember(tasks, layers) { layers.dialTasks(tasks) }
    val dayTasks = remember(dialTasks, date) { dialTasks.filter { it.date == date } }
    val dayBlocks = remember(dayTasks) { dayTasks.mapNotNull { it.toDialBlock() } }
    val allDayBlocks = remember(dayBlocks) { dayBlocks.filter { it.isAllDay } }
    // Deadlines due within a week of the shown day, from anywhere (Inbox included), soonest first,
    // as chips beside the all-day items. The chip text carries the distance ("in 2d").
    val deadlineBlocks = remember(tasks, date) {
        tasks.filter { t -> !t.completed && t.deadline != null && ChronoUnit.DAYS.between(date, t.deadline).let { it in 0..7 } }
            .sortedBy { it.deadline }
            .map { t ->
                val days = ChronoUnit.DAYS.between(date, t.deadline)
                val label = when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in ${days}d" }
                app.glance.daydial.DialBlock(t.id, "${t.cleanTitle} · $label", 0, 0, app.glance.launcher.ui.theme.hexColor(t.colorHex), t.completed, t.completable, isAllDay = true)
            }
    }
    val dialFrames = remember(frames, date, layers) { if (!layers.frames) emptyList() else frames.filter { it.appliesTo(date) }.map { it.toDialFrame(date) } }
    val dialRoutines = remember(routines, date, layers) { if (!layers.routines) emptyList() else routines.map { it.toDialRoutine(date) } }
    val breakdown = remember(dayBlocks, shape, dialRoutines, dialFrames) {
        computeBreakdown(dayBlocks, DayWindow(shape.dayStartMin, shape.dayStopMin), routines = dialRoutines, frames = dialFrames)
    }
    val nowInstant = remember(nowMin) { Instant.now() }
    val lastGlance = remember(chores, people, nowInstant) { lastGlanceStats(chores, people, nowInstant) }

    var dragUp by remember { mutableStateOf(0f) }
    val doubleTapToLock by controller.graph.prefs.doubleTapToLock.collectAsState()
    // Phone widths get their own layout (below). Swipe up opens the drawer and swipe down pulls
    // the notification shade, on every size: nothing on Home scrolls, so neither competes. The
    // dial, the cards and the search bar take their own drags first.
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val homeContext = LocalContext.current
    BoxWithConstraints(
        modifier
            .background(c.background)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragUp = 0f },
                    onVerticalDrag = { _, dy -> dragUp += dy },
                    onDragEnd = {
                        when {
                            dragUp < -120f -> controller.openDrawer()
                            dragUp > 120f -> app.glance.launcher.system.SystemShade.expandNotifications(homeContext)
                        }
                        dragUp = 0f
                    },
                    onDragCancel = { dragUp = 0f },
                )
            }
            // Double-tap empty space to lock. The cards, the dial and the icons consume their own
            // taps, so only the background reaches this; off, it waits for nothing.
            .then(
                if (doubleTapToLock) {
                    Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { controller.doubleTapHome() }) }
                } else {
                    Modifier
                },
            ),
    ) {
        val landscape = maxWidth > maxHeight
        val legendColumns = if (maxWidth < 600.dp) 2 else 4
        val lastGlanceTwoColumns = maxWidth >= 560.dp
        // With the taskbar on the column loses its height; tighten rather than overflow.
        val tight = dockEnabled
        val gap = if (tight) 8.dp else 12.dp
        // The dial takes the height, but never so much width that the column drops under 560 dp:
        // that is where the card stats and the search hint start to crowd.
        val dialSide = minOf(maxHeight - 16.dp, maxWidth - 28.dp - 560.dp)
        val bottomPad = if (dockEnabled) Modifier else Modifier.navigationBarsPadding()
        if (compact) {
            // Phone: nothing scrolls. The dial takes the full width, with no complications at this
            // size; under it the pill (two lines at most), the six-item legend, the pinned row and
            // the search bar at their natural heights. When they do not fit, the legend goes first;
            // the dial shrinks only if that is still not enough. The week strip and lastGLANCE are tablet-only.
            // Spare height (a tall phone, or the status bar hidden) spreads across every gap rather
            // than pooling above and below the dial, which read bottom-heavy.
            // The top inset (status bar or cutout) is breathing room enough; the column's own 8 dp
            // only tops it up when there is none.
            val topInset = with(LocalDensity.current) { WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(this).toDp() }
            SpreadColumn(
                modifier = Modifier.fillMaxSize().topBarsPadding().then(bottomPad)
                    .padding(start = 8.dp, end = 8.dp, top = (8.dp - topInset).coerceAtLeast(0.dp), bottom = 8.dp),
                gap = gap, maxExtraGap = 20.dp,
                // The dial takes the column's whole width, 8 dp from the screen edges, while every
                // other piece is inset to 16; and it pads its face by 2 dp rather than 6. The band
                // beyond the ring holds only the 00 and 12 labels and the sun glyphs, so the face
                // had room to spare at the sides. Its extra height comes out of the spare gaps.
                pieceInset = 8.dp,
                before = listOf {
                    Column {
                        HomeHeader(controller)
                        if (!availability.dayGlanceInstalled || !availability.lastGlanceInstalled) InstallBanner(availability.dayGlanceInstalled, availability.lastGlanceInstalled)
                    }
                },
                dial = { side ->
                    DialPanel(controller, dayBlocks, dialTasks, dialFrames, dialRoutines, shape, nowMin, today, date, use24, lastGlance, Modifier.size(side), showComplications = false, facePadding = 2.dp)
                },
                // The pill is two lines at most (one per row, the rest behind "+N", which opens
                // dayGLANCE's day): wrapping to three or four took that height from the dial.
                after = listOf(
                    {
                        AllDayPill(
                            allDayBlocks, Modifier.fillMaxWidth(), deadlines = deadlineBlocks, title = "All day & deadlines", ink = GlanceTheme.colors.text,
                            singleLine = true, onMore = { GlanceIntents.openDayGlance(context, "dayglance://today") },
                        ) { b -> GlanceIntents.openTask(context, b.id) }
                    },
                    // Six stats; a day with no routines, frames or sleep window lends the empty place to
                    // Done, then Focus. Done first: the phone dial has no corner readouts to show it.
                    { DialLegend(breakdown, Modifier.fillMaxWidth(), columns = 3, compact = true, hide = setOf("Focus", "Done"), substitutes = listOf("Done", "Focus"), ink = GlanceTheme.colors.text) },
                    { PinnedRow(controller, compact = true) },
                    { SearchBarTrigger(controller, Modifier.fillMaxWidth()) },
                ),
                // A short phone shows the legend as one row (Effort, Restore, Unblocked, which always
                // have a value) before leaving it out, and leaves it out before the dial loses width.
                dropOrder = listOf(1),
                fallbacks = mapOf(1 to {
                    DialLegend(breakdown, Modifier.fillMaxWidth(), columns = 3, compact = true, hide = setOf("Sleep", "Focus", "Done", "Routines", "Frames"), ink = GlanceTheme.colors.text)
                }),
            )
        } else if (landscape) {
            Row(Modifier.fillMaxSize().topBarsPadding().then(bottomPad).padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                // The dark panel runs the full column height; the dial sits centred inside it.
                Box(
                    Modifier.fillMaxHeight().width(dialSide).clip(RoundedCornerShape(20.dp)).background(DialColors.Face),
                    contentAlignment = Alignment.Center,
                ) {
                    // The whole panel, not the dial's square: the corner readouts use the room above and below.
                    DialPanel(controller, dayBlocks, dialTasks, dialFrames, dialRoutines, shape, nowMin, today, date, use24, lastGlance, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(16.dp))
                // Everything takes its natural height except the week strip, which gets what is left.
                // The lastGLANCE cards give way first: the column measures itself with two, one and
                // no next-up rows and keeps the first that leaves the strip a usable height, so a
                // shorter tablet drops rows rather than crushing the strip.
                AdaptiveColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    gap = gap, minStrip = 56.dp, comfortableStrip = 120.dp, maxStrip = 220.dp,
                    above = {
                        HomeHeader(controller)
                        if (!availability.dayGlanceInstalled || !availability.lastGlanceInstalled) InstallBanner(availability.dayGlanceInstalled, availability.lastGlanceInstalled)
                        AllDayPill(allDayBlocks, Modifier.fillMaxWidth(), deadlines = deadlineBlocks, title = "All day & deadlines", ink = GlanceTheme.colors.text, roomy = !tight) { b -> GlanceIntents.openTask(context, b.id) }
                        Spacer(Modifier.height(gap))
                        DialLegend(breakdown, Modifier.fillMaxWidth(), columns = 4, compact = tight, ink = GlanceTheme.colors.text)
                    },
                    strip = { WeekStrip(controller, tasks, routines, today, Modifier.fillMaxWidth().fillMaxHeight()) },
                    below = { rows ->
                        LastGlanceSection(controller, lastGlance, nowInstant, twoColumns = true, rows = rows)
                        Spacer(Modifier.height(gap))
                        PinnedRow(controller, compact = tight)
                        Spacer(Modifier.height(gap))
                        SearchBarTrigger(controller, Modifier.fillMaxWidth())
                    },
                )
            }
        } else {
            // Portrait: the content below takes its natural height and the dial takes whatever is
            // left, capped at the width. Nothing scrolls or clips; the taskbar only shrinks the dial.
            Column(Modifier.fillMaxSize().topBarsPadding().then(bottomPad).padding(horizontal = 16.dp, vertical = 8.dp)) {
                HomeHeader(controller)
                if (!availability.dayGlanceInstalled || !availability.lastGlanceInstalled) InstallBanner(availability.dayGlanceInstalled, availability.lastGlanceInstalled)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    Box(
                        Modifier.fillMaxWidth().height(side).clip(RoundedCornerShape(20.dp)).background(DialColors.Face),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The whole panel, not the dial's square: the corner readouts use the room at the sides.
                        DialPanel(controller, dayBlocks, dialTasks, dialFrames, dialRoutines, shape, nowMin, today, date, use24, lastGlance, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(gap))
                // Pill and legend share a row (the legend drops Focus and Done, which the dial's
                // hub and corner already show); lastGLANCE is stats only; the week strip gets real
                // height. The dial takes what is left above.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    AllDayPill(allDayBlocks, Modifier.weight(1f).fillMaxHeight(), deadlines = deadlineBlocks, title = "All day & deadlines", ink = GlanceTheme.colors.text) { b -> GlanceIntents.openTask(context, b.id) }
                    // Focus before Done here: the dial's corner already shows Done.
                    DialLegend(breakdown, Modifier.weight(1f).fillMaxHeight(), columns = 3, compact = true, hide = setOf("Focus", "Done"), substitutes = listOf("Focus", "Done"), ink = GlanceTheme.colors.text)
                }
                Spacer(Modifier.height(gap))
                WeekStrip(controller, tasks, routines, today, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(gap))
                LastGlanceSection(controller, lastGlance, nowInstant, twoColumns = lastGlanceTwoColumns, rows = 0)
                Spacer(Modifier.height(gap))
                PinnedRow(controller, compact = true)
                Spacer(Modifier.height(gap))
                SearchBarTrigger(controller, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The landscape column's layout: [above] and [below] take their natural heights, [strip] the
 * rest. [below] is composed with 2, 1 and then 0 lastGLANCE rows, and the first that leaves the
 * strip at least [comfortableStrip] wins; failing all three, no rows and the strip at whatever is
 * left, never under [minStrip]. Leftover beyond [maxStrip] stays empty under the strip, so the
 * pieces below keep their place at the foot of the column.
 */
@Composable
private fun AdaptiveColumn(
    modifier: Modifier,
    gap: Dp,
    minStrip: Dp,
    comfortableStrip: Dp,
    maxStrip: Dp,
    above: @Composable ColumnScope.() -> Unit,
    strip: @Composable () -> Unit,
    below: @Composable ColumnScope.(rows: Int) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(maxWidth = width)
        val gapPx = gap.roundToPx()
        val abovePlaceables = subcompose("above") { Column(Modifier.fillMaxWidth(), content = above) }.map { it.measure(loose) }
        val aboveHeight = abovePlaceables.sumOf { it.height }

        var belowPlaceables: List<Placeable> = emptyList()
        var leftover = 0
        for (rows in intArrayOf(2, 1, 0)) {
            belowPlaceables = subcompose("below-$rows") { Column(Modifier.fillMaxWidth()) { below(rows) } }.map { it.measure(loose) }
            leftover = height - aboveHeight - belowPlaceables.sumOf { it.height } - 2 * gapPx
            if (leftover >= comfortableStrip.roundToPx()) break
        }
        val stripHeight = leftover.coerceIn(minStrip.roundToPx(), maxStrip.roundToPx())
        val stripPlaceables = subcompose("strip", strip).map { it.measure(Constraints(maxWidth = width, minHeight = stripHeight, maxHeight = stripHeight)) }
        val slot = maxOf(leftover, stripHeight)

        layout(width, height) {
            var y = 0
            for (p in abovePlaceables) { p.place(0, y); y += p.height }
            y += gapPx
            for (p in stripPlaceables) p.place(0, y)
            y += slot + gapPx
            for (p in belowPlaceables) { p.place(0, y); y += p.height }
        }
    }
}

/**
 * The phone column: [before] pieces, the [dial], then [after] pieces, each at its natural height
 * with [gap] between. The dial is a full-width square: the content adapts to it, never the other
 * way round, because a dial squeezed by a few lines of chips broke its hub and labels. When the
 * pieces do not fit beside a full-width dial, the [after] pieces named in [dropOrder] give way,
 * in that order, first: one with a smaller form in [fallbacks] takes it, and is left out only if
 * that is still not enough. Only then does the dial shrink to what is left. Height beyond that is shared equally by every gap (up to [maxExtraGap] each), and
 * anything still left centres the whole stack. Every piece but the dial is inset by [pieceInset]
 * on each side, so the dial alone can run wider than the rest.
 */
@Composable
private fun SpreadColumn(
    modifier: Modifier,
    gap: Dp,
    maxExtraGap: Dp,
    before: List<@Composable () -> Unit>,
    dial: @Composable (side: Dp) -> Unit,
    after: List<@Composable () -> Unit>,
    dropOrder: List<Int> = emptyList(),
    fallbacks: Map<Int, @Composable () -> Unit> = emptyMap(),
    pieceInset: Dp = 0.dp,
) {
    SubcomposeLayout(modifier) { constraints ->
        val dialWidth = constraints.maxWidth
        val inset = pieceInset.roundToPx()
        val width = dialWidth - 2 * inset
        val height = constraints.maxHeight
        val loose = Constraints(maxWidth = width)
        val beforePlaceables = before.mapIndexed { i, piece -> subcompose("before$i", piece).map { it.measure(loose) } }
        val allAfter = after.mapIndexed { i, piece -> subcompose("after$i", piece).map { it.measure(loose) } }
        val allFallbacks = fallbacks.mapValues { (i, piece) -> subcompose("fallback$i", piece).map { it.measure(loose) } }
        val gapPx = gap.roundToPx()
        val plan = PhoneColumnPlan.plan(
            beforeHeights = beforePlaceables.map { g -> g.sumOf { it.height } },
            afterHeights = allAfter.map { g -> g.sumOf { it.height } },
            dropOrder = dropOrder, width = dialWidth, height = height, gap = gapPx,
            fallbacks = allFallbacks.mapValues { (_, g) -> g.sumOf { it.height } },
        )
        val afterPlaceables = allAfter.indices.filter { it !in plan.dropped }
            .map { i -> if (i in plan.fallen) allFallbacks.getValue(i) else allAfter[i] }
        val fixed = (beforePlaceables + afterPlaceables).sumOf { group -> group.sumOf { it.height } }
        val gaps = beforePlaceables.size + afterPlaceables.size
        val leftover = height - fixed - gaps * gapPx
        val side = plan.side
        val extra = (leftover - side).coerceAtLeast(0)
        val extraPerGap = if (gaps == 0) 0 else minOf(extra / gaps, maxExtraGap.roundToPx())
        val topPad = (extra - extraPerGap * gaps) / 2
        val dialPlaceables = subcompose("dial") { dial(side.toDp()) }.map { it.measure(Constraints(maxWidth = dialWidth, maxHeight = side)) }

        layout(dialWidth, height) {
            var y = topPad
            for (group in beforePlaceables) {
                for (p in group) { p.place(inset, y); y += p.height }
                y += gapPx + extraPerGap
            }
            for (p in dialPlaceables) p.place((dialWidth - p.width) / 2, y)
            y += side
            for (group in afterPlaceables) {
                y += gapPx + extraPerGap
                for (p in group) { p.place(inset, y); y += p.height }
            }
        }
    }
}

/**
 * The next CLOCK-app alarm as epoch milliseconds, re-read on each minute tick; null without
 * one. Another app's alarm-clock alarm (a calendar or task reminder) is not a wake-up
 * ([ClockAlarm], the same rule as dayGLANCE's GLANCEahead).
 */
@Composable
fun rememberNextAlarmMs(nowMin: Int): Long? {
    val context = LocalContext.current
    return remember(nowMin) { app.glance.launcher.alarm.ClockAlarm.nextTriggerMillis(context) }
}

/** Minutes past midnight, ticking at each minute boundary. */
@Composable
fun rememberNowMinute(): Int {
    var now by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = LocalTime.now()
            now = t.hour * 60 + t.minute
            delay((60 - t.second) * 1000L + 50)
        }
    }
    return now
}

@Composable
private fun HomeHeader(controller: LauncherController) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LauncherWordmark(size = 20.dp)
        val availability by controller.repository.availability.collectAsState()
        Spacer(Modifier.weight(1f))
        // One tap into each app, by its own launcher icon; an app that is not installed has none.
        // The icon carries the app's data status: an amber dot once its data is past STALE_AFTER
        // (or demo), where opening the app is also what refreshes it. Long-press for diagnostics;
        // Settings has the status in words. The header row has no room left for it as text.
        val apps by controller.graph.apps.apps.collectAsState()
        val now = Instant.now()
        for ((pkg, name, freshness, open) in listOf(
            HeaderApp(GlanceIntents.DAYGLANCE_PACKAGE, "dayGLANCE", availability.dayGlanceFreshness) { GlanceIntents.launchDayGlance(context) },
            HeaderApp(GlanceIntents.LASTGLANCE_PACKAGE, "lastGLANCE", availability.lastGlanceFreshness) { GlanceIntents.launchLastGlance(context) },
        )) {
            val entry = apps.firstOrNull { it.packageName == pkg } ?: continue
            // The screenshot demo hides the badge: a clean header (Settings says it is on).
            val word = if (availability.demoForced) null else appStatusWord(freshness, now)
            Box(
                Modifier
                    .size(34.dp)
                    .combinedClickable(onClick = open, onLongClick = { controller.diagnosticsOpen = true })
                    .semantics { contentDescription = if (word == null) "Open $name" else "Open $name, data $word" },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(entry, controller.graph.apps, size = 26.dp)
                if (word != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(9.dp)
                            .background(c.warning, CircleShape)
                            .border(1.5.dp, c.background, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(2.dp))
        val locked by controller.graph.prefs.dialLocked.collectAsState()
        Icon(
            if (locked) DialGlyphs.Lock else DialGlyphs.Unlock,
            contentDescription = if (locked) "Unlock the dial" else "Lock the dial",
            tint = if (locked) c.brand else c.textSecondary,
            modifier = Modifier.size(32.dp).clickable { controller.graph.prefs.setDialLocked(!locked) }.padding(6.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            GlanceIcons.Sliders, contentDescription = "Launcher settings", tint = c.textSecondary,
            modifier = Modifier.size(32.dp).clickable { controller.settingsOpen = true }.padding(5.dp),
        )
    }
}

/** One app's quick-open icon in Home's header, with what its status badge reads from. */
private data class HeaderApp(val pkg: String, val name: String, val freshness: app.glance.launcher.data.Freshness, val open: () -> Unit)

@Composable
private fun InstallBanner(day: Boolean, last: Boolean) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    GlanceCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), accentLeft = c.warning) {
        Text("GLANCElauncher needs both apps", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (!day) Text("Install dayGLANCE", color = c.accent, fontSize = 13.sp, modifier = Modifier.clickable { GlanceIntents.installPrompt(context, GlanceIntents.DAYGLANCE_PACKAGE) })
        if (!last) Text("Install lastGLANCE", color = c.accent, fontSize = 13.sp, modifier = Modifier.clickable { GlanceIntents.installPrompt(context, GlanceIntents.LASTGLANCE_PACKAGE) })
    }
}

/** A chore with its cadence maths done once. */
data class ScoredChore(val chore: app.glance.launcher.data.Chore, val ratio: Double?, val state: CadenceState)

/** A person with their recency maths done once. */
data class ScoredPerson(val person: app.glance.launcher.data.Person, val ratio: Double?, val reachOut: Boolean, val daysUntilSpecial: Long?)

/** lastGLANCE's numbers and lists for the complication and the cards. */
data class LastGlanceStats(
    val dueNow: Int,
    val dueSoon: Int,
    /** Chores with a cadence that are neither due soon nor due now. */
    val onTrack: Int,
    val doneToday: Int,
    val reachOut: Int,
    /** People who reached out last and are waiting on a reply. */
    val unreturned: Int,
    val comingUp: Int,
    /** People with a cadence who are neither due nor unreturned. */
    val inTouch: Int,
    /** Chores needing attention, most pressing first. */
    val nextChores: List<ScoredChore>,
    /** People to reach out to, most pressing first (a date within 7 days boosts to the top). */
    val nextPeople: List<ScoredPerson>,
    /** On-track chores, soonest due first: a caught-up card's "up next" (LastGlanceRows). */
    val upcomingChores: List<ScoredChore> = emptyList(),
    /** People with a cadence who are not yet due nor snoozed, soonest due first: the same for People. */
    val upcomingPeople: List<ScoredPerson> = emptyList(),
)

fun lastGlanceStats(chores: List<app.glance.launcher.data.Chore>, people: List<app.glance.launcher.data.Person>, now: Instant): LastGlanceStats {
    val today = now.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val scored = chores.map { ch ->
        val ratio = Cadence.ratio(Cadence.elapsedDays(ch.lastDoneAt, now), ch.targetCadenceDays)
        ScoredChore(ch, ratio, Cadence.state(ratio))
    }
    val attention = scored.filter { it.state == CadenceState.OVERDUE || it.state == CadenceState.DUE_SOON }.sortedByDescending { it.ratio ?: 0.0 }
    val doneToday = chores.count { it.lastDoneAt != null && it.lastDoneAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today }
    val scoredPeople = people.map { p ->
        val ratio = Cadence.ratio(Cadence.elapsedDays(p.lastInteractionAt, now) ?: 999.0, p.targetCadenceDays)
        val reach = !p.isSnoozed(now) && ratio != null && (ratio >= Cadence.ATTENTION_RATIO || (p.unreturned && ratio >= 0.5))
        ScoredPerson(p, ratio, reach, p.daysUntilSpecial(today))
    }
    val reachOut = scoredPeople.filter { it.reachOut || (it.daysUntilSpecial != null && it.daysUntilSpecial <= 7) }
        .sortedWith(compareBy<ScoredPerson>({ !(it.daysUntilSpecial != null && it.daysUntilSpecial <= 7) }, { -(it.ratio ?: 0.0) }))
    val comingUp = scoredPeople.count { it.daysUntilSpecial != null && it.daysUntilSpecial <= 30 }
    return LastGlanceStats(
        dueNow = scored.count { it.state == CadenceState.OVERDUE },
        dueSoon = scored.count { it.state == CadenceState.DUE_SOON },
        onTrack = scored.count { it.state == CadenceState.FINE },
        doneToday = doneToday,
        reachOut = reachOut.size,
        unreturned = scoredPeople.count { it.person.unreturned },
        comingUp = comingUp,
        inTouch = scoredPeople.count { it.ratio != null && !it.reachOut },
        nextChores = attention,
        nextPeople = reachOut,
        upcomingChores = scored.filter { it.state == CadenceState.FINE && it.ratio != null }.sortedByDescending { it.ratio },
        upcomingPeople = scoredPeople.filter { it.ratio != null && it !in reachOut && !it.person.isSnoozed(now) }.sortedByDescending { it.ratio },
    )
}

@Composable
private fun DialPanel(
    controller: LauncherController,
    dayBlocks: List<app.glance.daydial.DialBlock>,
    tasks: List<Task>,
    dialFrames: List<app.glance.daydial.DialFrame>,
    dialRoutines: List<app.glance.daydial.DialRoutine>,
    shape: app.glance.launcher.data.DayShape,
    nowMin: Int,
    today: LocalDate,
    date: LocalDate,
    use24: Boolean,
    lastGlance: LastGlanceStats,
    modifier: Modifier,
    showComplications: Boolean = true,
    facePadding: Dp = 6.dp,
) {
    val context = LocalContext.current
    val c = GlanceTheme.colors
    val locked by controller.graph.prefs.dialLocked.collectAsState()
    // The moon rides the daylight track once dayGLANCE shares its location; the demo has one.
    val moon = remember(date, shape) {
        val lat = shape.lat
        val lon = shape.lon
        if (lat == null || lon == null) null
        else DialSky.moonBand(date, lat, lon, SunTimes(shape.sunriseMin, shape.sunsetMin), java.time.ZoneId.systemDefault())
    }
    // The day's forecast, for the weather ring and the daylight band's UV scaling.
    val weatherDays by controller.repository.weather.collectAsState()
    val hourly = remember(weatherDays, date) { weatherDays.firstOrNull { it.date == date }?.hours }
    val daylight = remember(date, shape, hourly) {
        val lat = shape.lat
        val lon = shape.lon
        if (lat == null || lon == null) null
        else DialSky.daylightBand(date, lat, lon, SunTimes(shape.sunriseMin, shape.sunsetMin), DialWeather.peakUv(hourly), java.time.ZoneId.systemDefault())
    }
    // The next clock-app alarm, today's dial only, re-read every minute, shown as dayGLANCE's prefs say.
    val alarmMs = rememberNextAlarmMs(nowMin)
    val alarmPrefs = shape.dialAlarm
    val alarm = remember(alarmMs, nowMin, date, today, alarmPrefs) {
        if (date != today || alarmPrefs?.on == false) null
        else DialAlarm.mark(System.currentTimeMillis(), alarmMs, alarmPrefs?.fromMin ?: DialAlarm.DEFAULT_FROM_MIN, java.time.ZoneId.systemDefault())
    }
    val prevBlocks = remember(tasks, date) { tasks.filter { it.date == date.minusDays(1) }.mapNotNull { it.toDialBlock() } }
    val tasksById = remember(tasks) { tasks.associateBy { it.id } }

    val callbacks = remember(controller, date, tasksById) {
        DayDialCallbacks(
            onToggleComplete = { b -> tasksById[b.id]?.let { controller.toggleTask(it) } },
            onToggleRoutine = { r -> controller.toggleRoutine(r.id) },
            onMoveBlock = { b, start -> controller.moveTask(b.id, start) },
            onResizeBlock = { b, dur -> controller.resizeTask(b.id, dur) },
            onOpenBlock = { b -> GlanceIntents.openTask(context, b.id) },
            onCreateAt = { start -> controller.createTaskAt(date, start) },
            onStartFocus = { b -> GlanceIntents.openDayGlance(context, "dayglance://startFocus?id=${b.id}") },
            onStepDay = { d -> controller.stepDialDay(d) },
            onGoToday = { controller.goToday() },
            // The hub's clock and date open the device's clock and calendar apps.
            onClockTap = { app.glance.launcher.system.SystemApps.openClock(context) },
            onDateTap = { d -> app.glance.launcher.system.SystemApps.openCalendar(context, d) },
        )
    }

    // Corner readouts, clockwise from top-left: inbox count, deadlines in the next seven days,
    // lastGLANCE's due-now count (launcher-only), done ring. Inbox and done are dayGLANCE's own.
    val done = tasks.count { it.date == date && it.completed }
    val total = tasks.count { it.date == date }
    val appInboxCount by controller.repository.inboxCount.collectAsState()
    val inbox = InboxCount.of(appInboxCount, tasks)
    val deadlines = tasks.count { t -> !t.completed && t.deadline != null && ChronoUnit.DAYS.between(today, t.deadline).let { it in 0..7 } }
    val complications = remember(done, total, inbox, deadlines, lastGlance.dueNow) {
        listOf(
            DialComplication.Count("Inbox", inbox, DialGlyphs.Inbox, onClick = { GlanceIntents.openInbox(context) }),
            DialComplication.Count("Deadlines", deadlines, DialGlyphs.CalendarClock, onClick = { GlanceIntents.openDayGlance(context, "dayglance://today") }),
            DialComplication.Ring("Done", if (total == 0) 0f else done.toFloat() / total, "${(if (total == 0) 0 else done * 100 / total)}", onClick = { GlanceIntents.openDay(context, date, dial = true) }),
            DialComplication.Count("Due now", lastGlance.dueNow, GlanceIcons.Flame, accent = if (lastGlance.dueNow > 0) c.danger else null, onClick = { GlanceIntents.openLastGlance(context) }),
        )
    }

    Box(modifier.clip(RoundedCornerShape(20.dp)).background(DialColors.Face)) {
        DayDial(
            date = date,
            blocks = dayBlocks,
            prevDayBlocks = prevBlocks,
            frames = dialFrames,
            routines = dialRoutines,
            dayWindow = DayWindow(shape.dayStartMin, shape.dayStopMin),
            // Sun & moon and Weather follow dayGLANCE's layer toggles; off draws nothing, never a stand-in.
            sun = if (shape.dialLayers.solar) SunTimes(shape.sunriseMin, shape.sunsetMin) else null,
            daylight = if (shape.dialLayers.solar) daylight else null,
            hourlyWeather = if (shape.dialLayers.weather) hourly else null,
            moon = if (shape.dialLayers.solar) moon else null,
            southern = (shape.lat ?: 0.0) < 0,
            alarm = alarm,
            alarmLabel = alarm?.let { formatClock(it.alarmMinute, use24) } ?: "",
            nowMin = if (date == today) nowMin else null,
            dayIsPast = date.isBefore(today),
            use24HourClock = use24,
            typography = DayDialTypography(serif = LoraFamily),
            callbacks = callbacks,
            complications = if (showComplications) complications else emptyList(),
            locked = locked,
            modifier = Modifier.fillMaxSize().padding(facePadding),
        )
    }
}

/**
 * lastGLANCE on Home: a header, then two cards side by side (stacked when narrow). Chores:
 * due now, due soon, done today, and the next two to do with "Done" inline. People: reach out
 * and coming up, and the next two to contact with "+ Log" inline.
 */
@Composable
private fun LastGlanceSection(controller: LauncherController, stats: LastGlanceStats, now: Instant, twoColumns: Boolean, rows: Int = 2, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LastGlanceWordmark(size = 16.dp)
        Spacer(Modifier.weight(1f))
        Text("Open", color = LastGlanceGreen, fontSize = 12.sp, modifier = Modifier.clickable { GlanceIntents.launchLastGlance(context) })
    }
    // Side by side, a caught-up card lists no more "up next" rows than the other card's due rows.
    val choreDue = stats.nextChores.take(rows).size
    val peopleDue = stats.nextPeople.take(rows).size
    val chores = LastGlanceRows.plan(stats.nextChores, stats.upcomingChores, rows, if (twoColumns) peopleDue else null)
    val people = LastGlanceRows.plan(stats.nextPeople, stats.upcomingPeople, rows, if (twoColumns) choreDue else null)
    // A layout with no room for rows (rows = 0) stays as it was: numbers only, no caption.
    if (twoColumns) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoresCard(controller, stats, chores, rows > 0, now, Modifier.weight(1f).fillMaxHeight())
            PeopleCard(controller, stats, people, rows > 0, now, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        ChoresCard(controller, stats, chores, rows > 0, now, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        PeopleCard(controller, stats, people, rows > 0, now, Modifier.fillMaxWidth())
    }
    }
}

@Composable
private fun ChoresCard(controller: LauncherController, stats: LastGlanceStats, plan: LastGlanceRows.Plan<ScoredChore>, roomForRows: Boolean, now: Instant, modifier: Modifier) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    GlanceCard(modifier.clickable { GlanceIntents.openChores(context) }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        SectionHeader("Chores", trailing = if (plan.caughtUp && roomForRows) ({ CaughtUp(plan.items.isNotEmpty()) }) else null)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("${stats.dueNow}", "due now", if (stats.dueNow > 0) c.danger else null, Modifier.weight(1f))
            Stat("${stats.dueSoon}", "due soon", if (stats.dueSoon > 0) c.warning else null, Modifier.weight(1f))
            Stat("${stats.onTrack}", "on track", if (stats.onTrack > 0) LastGlanceGreen else null, Modifier.weight(1f))
            Stat("${stats.doneToday}", "done today", null, Modifier.weight(1f))
        }
        val next = plan.items
        if (next.isNotEmpty()) Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (s in next) {
            val state = Color(Cadence.colorArgb(s.ratio))
            Row(
                Modifier.fillMaxWidth().clickable { GlanceIntents.openChore(context, s.chore.id) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIcon(s.chore.icon, tint = state, size = 18.dp, fallback = GlanceIcons.Sparkles)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    // Up next reads quieter than what is due.
                    Text(s.chore.name, color = if (plan.caughtUp) c.textSecondary else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val due = Cadence.formatNextDue(s.chore.lastDoneAt, s.chore.targetCadenceDays, now)
                    Text(
                        Cadence.formatElapsed(s.chore.lastDoneAt, now) + (if (due != null) " - $due" else ""),
                        color = c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                SmallOutlinedButton("Done") { controller.logChoreDone(s.chore.id, s.chore.name) }
            }
        }
        }
    }
}

@Composable
private fun PeopleCard(controller: LauncherController, stats: LastGlanceStats, plan: LastGlanceRows.Plan<ScoredPerson>, roomForRows: Boolean, now: Instant, modifier: Modifier) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    GlanceCard(modifier.clickable { GlanceIntents.openPeople(context) }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        SectionHeader("People", trailing = if (plan.caughtUp && roomForRows) ({ CaughtUp(plan.items.isNotEmpty()) }) else null)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("${stats.reachOut}", "reach out", if (stats.reachOut > 0) c.warning else null, Modifier.weight(1f))
            Stat("${stats.unreturned}", "unreturned", if (stats.unreturned > 0) c.danger else null, Modifier.weight(1f))
            Stat("${stats.comingUp}", "coming up", if (stats.comingUp > 0) Color(0xFFF472B6) else null, Modifier.weight(1f))
            Stat("${stats.inTouch}", "in touch", if (stats.inTouch > 0) LastGlanceGreen else null, Modifier.weight(1f))
        }
        val next = plan.items
        if (next.isNotEmpty()) Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (s in next) {
            val p = s.person
            val state = Color(Cadence.colorArgb(s.ratio))
            Row(
                Modifier.fillMaxWidth().clickable { GlanceIntents.openPerson(context, p.id) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).background(state.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Text(p.name.take(1), color = state, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.nickname ?: p.name, color = if (plan.caughtUp) c.textSecondary else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val soon = s.daysUntilSpecial
                    val line = when {
                        soon != null && soon <= 7 -> "${p.specialLabel.lowercase()} ${if (soon == 0L) "today" else "in ${soon}d"}"
                        plan.caughtUp -> Cadence.formatNextDue(p.lastInteractionAt, p.targetCadenceDays, now)?.let { "due $it" } ?: Cadence.formatElapsed(p.lastInteractionAt, now)
                        p.unreturned -> "they reached out last"
                        p.lastInteractionAt == null -> "no contact logged yet"
                        else -> Cadence.formatElapsed(p.lastInteractionAt, now)
                    }
                    Text(line, color = if (soon != null && soon <= 7) Color(0xFFF472B6) else c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SmallOutlinedButton("+ Log") { controller.logInteraction(p.id, p.name) }
            }
        }
        }
    }
}

/**
 * A caught-up card's note, on its header line so it adds no height: "All caught up", and
 * "· up next" when rows of what comes due next follow.
 */
@Composable
private fun CaughtUp(upNext: Boolean) {
    val c = GlanceTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(GlanceIcons.CheckCircle, contentDescription = null, tint = LastGlanceGreen, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (upNext) "All caught up · up next" else "All caught up", color = c.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Stat(value: String, label: String, valueColor: Color? = null, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    Column(modifier) {
        Text(value, color = valueColor ?: c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = c.textMuted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallOutlinedButton(label: String, onClick: () -> Unit) {
    val c = GlanceTheme.colors
    Text(
        label, color = c.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

/** The next seven days as day cells, today first. Tap shows that day on the dial; long-press opens it in dayGLANCE. */
@Composable
private fun WeekStrip(controller: LauncherController, tasks: List<Task>, routines: List<app.glance.launcher.data.Routine>, today: LocalDate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = java.util.Locale.getDefault()
    val start = today
    val byDate = remember(tasks) { tasks.filter { it.date != null }.groupBy { it.date!! } }
    val deadlinesByDate = remember(tasks, today) { app.glance.launcher.ui.planning.SchedAgenda.deadlinesByDate(tasks, today) }
    Row(modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
            val date = start.plusDays(i.toLong())
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale).uppercase(locale),
                    color = if (date == today) GlanceTheme.colors.accent else GlanceTheme.colors.textMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp),
                )
                DayCell(
                    date = date,
                    inMonth = true,
                    isToday = date == today,
                    isSelected = date == controller.dialDate && date != today,
                    items = byDate[date].orEmpty(),
                    routineStarts = if (date == today) routines.map { it.startMin } else emptyList(),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onClick = { controller.dialDate = date },
                    onLongClick = { GlanceIntents.openDay(context, date) },
                    aspect = null,
                    deadlines = deadlinesByDate[date].orEmpty(),
                )
            }
        }
    }
}

/**
 * One row of pinned app shortcuts: as many as fit, then a button that opens a pop-up with the
 * rest of the pins and the editor. Without overflow the button opens the editor directly.
 */
@Composable
private fun PinnedRow(controller: LauncherController, compact: Boolean = false) {
    val c = GlanceTheme.colors
    val prefs = controller.graph.prefs
    val apps = controller.graph.apps
    val pins by prefs.homePins.collectAsState()
    val all by apps.apps.collectAsState()
    val shortcuts by apps.shortcuts.collectAsState()
    val hidden by prefs.hiddenApps.collectAsState()
    val entries = remember(pins, all, shortcuts, hidden) { app.glance.launcher.apps.Pins.resolve(pins, all, shortcuts, hidden) }
    var overflowOpen by remember { mutableStateOf(false) }
    val buttonDp = if (compact) 40f else 44f

    // The split is decided here, before the row is composed, from the width the row will have.
    // It used to be decided inside a BoxWithConstraints around the pins alone, whose content
    // runs at measure time, after the button beside it had already been composed: the row showed
    // the pins that fit while the button still saw no overflow, and stayed the pencil.
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(12.dp))
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 8.dp),
    ) {
        val fit = PinnedLayout.fit(maxWidth.value - buttonDp, PINNED_SLOT_DP, PINNED_GAP_DP)
        val (shown, overflow) = PinnedLayout.split(entries, fit)
        val hasOverflow = overflow.isNotEmpty()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (entries.isEmpty()) {
                Text(
                    "Pin apps with the pencil, or swipe up for all apps.",
                    color = c.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(PINNED_GAP_DP.dp)) {
                    for (entry in shown) androidx.compose.runtime.key(entry.key) { PinnedApp(entry, controller, compact) }
                }
            }
            // "+N" opens the rest and the editor; with nothing hidden it is the editor's pencil.
            Box {
                Box(
                    Modifier
                        .size(buttonDp.dp)
                        .background(if (hasOverflow) c.track else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { if (hasOverflow) overflowOpen = true else controller.pinnedEditorOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasOverflow) {
                        Text("+${overflow.size}", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit pinned apps", tint = c.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
                if (overflowOpen && hasOverflow) {
                    androidx.compose.ui.window.Popup(
                        popupPositionProvider = AboveAnchor,
                        onDismissRequest = { overflowOpen = false },
                        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                    ) {
                        PinnedOverflow(overflow, controller, onLaunched = { overflowOpen = false }, onEdit = {
                            overflowOpen = false
                            controller.pinnedEditorOpen = true
                        })
                    }
                }
            }
        }
    }
}

private const val PINNED_SLOT_DP = 64f
private const val PINNED_GAP_DP = 6f

/** The pins that did not fit on the row, four to a line, and the editor. */
@Composable
private fun PinnedOverflow(
    entries: List<app.glance.launcher.apps.Pin>,
    controller: LauncherController,
    onLaunched: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = GlanceTheme.colors
    Column(
        Modifier
            .padding(8.dp)
            .widthIn(max = 320.dp)
            .background(c.card, RoundedCornerShape(16.dp))
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(10.dp),
    ) {
        for (row in entries.chunked(4)) {
            Row {
                for (entry in row) {
                    Column(
                        Modifier
                            .width(72.dp)
                            .combinedClickable(
                                onClick = { controller.graph.apps.launch(entry); onLaunched() },
                                // The pop-up closes first: the menu is a full-screen layer under it.
                                onLongClick = { onLaunched(); controller.appMenuFor = entry },
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PinIcon(entry, controller.graph.apps, size = 44.dp, notificationDot = true)
                        Text(entry.label, color = c.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onEdit).padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Edit pinned apps", color = c.textSecondary, fontSize = 13.sp)
        }
    }
}

/** Places a pop-up above its anchor, right-aligned to it, or below when there is no room above. */
private object AboveAnchor : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val above = anchorBounds.top - popupContentSize.height
        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom)
    }
}

@Composable
private fun PinnedApp(entry: app.glance.launcher.apps.Pin, controller: LauncherController, compact: Boolean) {
    val apps = controller.graph.apps
    // Tap launches. A long-press opens the app's menu (its shortcuts, App info, unpin, ...) the
    // moment it registers, as the drawer's does; carrying on into a drag upward closes the menu
    // and launches the app beside the current one (split screen), the same gesture as the dock.
    // The long-press belongs to the click handler, not the drag detector: a click handler with
    // no long-press of its own treats releasing a held press as a tap, which would also launch
    // the app under the menu. Reordering lives in the pencil's editor.
    var dragUp by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .width(64.dp)
            .combinedClickable(onClick = { apps.launch(entry) }, onLongClick = { controller.appMenuFor = entry })
            .pointerInput(entry.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragUp = 0f },
                    onDrag = { change, amount -> change.consume(); dragUp += amount.y },
                    onDragEnd = {
                        if (dragUp < -80f && entry is app.glance.launcher.apps.Pin.App) {
                            controller.appMenuFor = null
                            apps.launch(entry.entry, adjacent = true)
                        }
                        dragUp = 0f
                    },
                    onDragCancel = { dragUp = 0f },
                )
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icons only: the row stays short so the cards above keep their two rows.
        PinIcon(entry, apps, size = if (compact) 40.dp else 48.dp, notificationDot = true)
    }
}
