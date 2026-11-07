package app.glance.daydial

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Fonts the dial uses. The date in the hub is the only serif on the face (Lora in dayGLANCE). */
data class DayDialTypography(
    val serif: FontFamily = FontFamily.Serif,
    val sans: FontFamily = FontFamily.Default,
)

/**
 * The dayGLANCE Day Dial: a 24-hour face with the day's blocks as arcs on a ring, a now
 * needle, hour ticks and labels, sleep, sun marks, frames and routines, and a hub in the
 * centre narrating what is running and what comes next.
 *
 * Geometry, layers and tone follow the in-app web dial (`src/components/DayDial.jsx` and
 * `src/utils/dayDial.js`). On top of the reference's tap-to-inspect and action sheet this
 * version adds touch editing, exposed as callbacks only:
 * - drag a block along the ring to move it ([DayDialCallbacks.onMoveBlock]);
 * - drag a block's trailing edge to resize it ([DayDialCallbacks.onResizeBlock]);
 * - long-press an empty stretch to create something there ([DayDialCallbacks.onCreateAt]).
 *
 * @param nowMin minutes past midnight right now, or null when [date] is not today. The
 *   needle and the running/next readout exist only when it is non-null.
 */
@Composable
fun DayDial(
    date: LocalDate,
    blocks: List<DialBlock>,
    modifier: Modifier = Modifier,
    prevDayBlocks: List<DialBlock> = emptyList(),
    frames: List<DialFrame> = emptyList(),
    routines: List<DialRoutine> = emptyList(),
    dayWindow: DayWindow? = null,
    sun: SunTimes? = null,
    /** The daylight band from solar elevation and UV ([DialSky.daylightBand]); null draws the half-sine over [sun]. */
    daylight: List<DaylightStep>? = null,
    /** The day's hourly forecast by hour ([HourWeather]); null draws no weather ring. */
    hourlyWeather: Map<Int, HourWeather>? = null,
    /** The moon on the daylight band's track ([DialSky.moonBand]); null without coordinates. */
    moon: MoonBand? = null,
    /** Observer below the equator: the moon's lit limb is mirrored. */
    southern: Boolean = false,
    /** The next clock-app alarm ([DialAlarm.mark]), today only; [alarmLabel] is its time as the host formats it. */
    alarm: AlarmMark? = null,
    alarmLabel: String = "",
    focusSpans: List<FocusSpan> = emptyList(),
    nowMin: Int? = null,
    dayIsPast: Boolean = false,
    use24HourClock: Boolean = false,
    snapMinutes: Int = 15,
    typography: DayDialTypography = DayDialTypography(),
    callbacks: DayDialCallbacks = DayDialCallbacks(),
    locale: Locale = Locale.getDefault(),
    /** Up to four corner readouts (top-left, top-right, bottom-left, bottom-right); shown from 520 px dials. */
    complications: List<DialComplication?> = emptyList(),
    /** When true, blocks cannot be moved or resized and long-press creates nothing; tap, inspect and the sheet still work. */
    locked: Boolean = false,
) {
    val isToday = nowMin != null
    val laidOut = remember(blocks, prevDayBlocks) { layoutDayBlocks(blocks, prevDayBlocks) }

    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var selectedRoutineId by remember { mutableStateOf<String?>(null) }
    var selectedFrameId by remember { mutableStateOf<String?>(null) }
    var sheetBlock by remember { mutableStateOf<DialBlock?>(null) }
    var dragState by remember { mutableStateOf<DragState?>(null) }

    // A first tap inspects for 4 s, like the reference; a second tap opens the sheet.
    LaunchedEffect(selectedBlockId, selectedRoutineId, selectedFrameId) {
        if (selectedBlockId != null || selectedRoutineId != null || selectedFrameId != null) {
            kotlinx.coroutines.delay(4000)
            selectedBlockId = null
            selectedRoutineId = null
            selectedFrameId = null
        }
    }
    LaunchedEffect(sheetBlock) {
        if (sheetBlock != null) {
            kotlinx.coroutines.delay(20_000)
            sheetBlock = null
        }
    }
    // Drop a selection whose block disappeared.
    LaunchedEffect(blocks) {
        if (selectedBlockId != null && blocks.none { it.id == selectedBlockId }) selectedBlockId = null
        if (selectedFrameId != null && frames.none { it.id == selectedFrameId }) selectedFrameId = null
        if (sheetBlock != null && blocks.none { it.id == sheetBlock?.id }) sheetBlock = null
    }

    // While dragging, draw the preview instead of the stored block.
    val drawn: List<DayBlock> = remember(laidOut, dragState) {
        val d = dragState ?: return@remember laidOut
        val replaced = blocks.map { b ->
            if (b.id == d.block.id) b.copy(startMin = d.previewStart, durationMin = d.previewDuration) else b
        }
        layoutDayBlocks(replaced, prevDayBlocks)
    }

    val needleDegrees by animateFloatAsState(
        targetValue = DialGeometry.degrees((nowMin ?: 0).toFloat()),
        animationSpec = tween(1500, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "needle",
    )

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val latestLaidOut by rememberUpdatedState(laidOut)
    val latestRoutines by rememberUpdatedState(routines)
    val latestFrames by rememberUpdatedState(frames)
    val latestCallbacks by rememberUpdatedState(callbacks)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val sideDp: Dp = min(maxWidth.value, maxHeight.value).dp
        val sidePx = with(density) { sideDp.toPx() }
        val scale = sidePx / DialGeometry.BOX
        val compact = maxWidth < maxHeight
        val description = remember(date, blocks, nowMin) {
            buildString {
                append("Day dial for ")
                append(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)))
                append(". ")
                append(blocks.count { !it.isAllDay })
                append(" blocks")
            }
        }

        Canvas(
            modifier = Modifier
                .size(sideDp)
                .aspectRatio(1f)
                .semantics { contentDescription = description }
                .pointerInput(scale, snapMinutes) {
                    detectTapGestures(
                        onTap = { pos ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val hitBlock = hitTestBlock(latestLaidOut, pos.x, pos.y, cx, cy, scale)
                            if (hitBlock != null) {
                                val block = hitBlock.block
                                if (selectedBlockId == block.id) {
                                    sheetBlock = block
                                    selectedBlockId = null
                                } else {
                                    selectedBlockId = block.id
                                    selectedRoutineId = null
                                    selectedFrameId = null
                                    latestCallbacks.onBlockTap(block)
                                }
                                return@detectTapGestures
                            }
                            val hitRoutine = hitTestRoutine(latestRoutines, pos.x, pos.y, cx, cy, scale)
                            if (hitRoutine != null) {
                                if (selectedRoutineId == hitRoutine.id) {
                                    latestCallbacks.onToggleRoutine(hitRoutine)
                                    selectedRoutineId = null
                                } else {
                                    selectedRoutineId = hitRoutine.id
                                    selectedBlockId = null
                                    selectedFrameId = null
                                }
                                return@detectTapGestures
                            }
                            // A frame's outline reads it in the hub, as in the app. A frame has
                            // nothing to act on, so a second tap just keeps it read.
                            val hitFrame = DialFrames.hitTest(
                                latestFrames,
                                DialGeometry.radiusAt(cx, cy, pos.x, pos.y) / scale,
                                DialGeometry.minuteAt(cx, cy, pos.x, pos.y),
                                FRAME_HIT_DP.dp.toPx() / scale,
                                scale,
                            )
                            if (hitFrame != null) {
                                selectedFrameId = hitFrame.id
                                selectedBlockId = null
                                selectedRoutineId = null
                                return@detectTapGestures
                            }
                            selectedBlockId = null
                            selectedRoutineId = null
                            selectedFrameId = null
                        },
                        onLongPress = { pos ->
                            if (locked) return@detectTapGestures
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val r = DialGeometry.radiusAt(cx, cy, pos.x, pos.y) / scale
                            if (r < DialGeometry.R_INNER - 10f || r > DialGeometry.R_EDGE + 15f) return@detectTapGestures
                            if (hitTestBlock(latestLaidOut, pos.x, pos.y, cx, cy, scale) != null) return@detectTapGestures
                            val minute = DialGeometry.minuteAt(cx, cy, pos.x, pos.y)
                            latestCallbacks.onCreateAt(DialGeometry.floorTo(minute, snapMinutes))
                        },
                    )
                }
                .pointerInput(scale, snapMinutes, locked) {
                    if (locked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val hit = hitTestBlock(latestLaidOut, down.position.x, down.position.y, cx, cy, scale)
                            ?: return@awaitEachGesture
                        if (!hit.block.movable || hit.startedPrevDay) return@awaitEachGesture
                        val downMinute = DialGeometry.minuteAt(cx, cy, down.position.x, down.position.y)
                        val mode = dragModeFor(hit, downMinute)
                        val change: PointerInputChange = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                            ?: return@awaitEachGesture
                        var state = DragState(hit.block, mode, hit.block.startMin, hit.block.durationMin)
                        dragState = state
                        sheetBlock = null
                        selectedBlockId = hit.block.id
                        val completed = drag(change.id) { c ->
                            c.consume()
                            val m = DialGeometry.minuteAt(cx, cy, c.position.x, c.position.y)
                            state = applyDrag(state, hit.block, downMinute, m, snapMinutes)
                            dragState = state
                        }
                        dragState = null
                        if (completed) {
                            when (state.mode) {
                                DragMode.MOVE -> if (state.previewStart != hit.block.startMin) {
                                    latestCallbacks.onMoveBlock(hit.block, state.previewStart)
                                }
                                DragMode.RESIZE -> if (state.previewDuration != hit.block.durationMin) {
                                    latestCallbacks.onResizeBlock(hit.block, state.previewDuration)
                                }
                            }
                        }
                    }
                },
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val s = scale
            drawFace(cx, cy, s)
            drawTicks(cx, cy, s)
            val sunMarks = sun?.let { listOf(it.sunriseMin, it.sunsetMin) } ?: emptyList()
            val labelMarks = if (alarm != null && alarm.mode == AlarmMode.TODAY) sunMarks + alarm.minute else sunMarks
            drawHourLabels(cx, cy, s, use24HourClock, compact, labelMarks, textMeasurer, typography.sans)
            if (daylight != null) drawDaylightSteps(cx, cy, s, daylight) else if (sun != null) drawDaylight(cx, cy, s, sun)
            if (moon != null) drawMoonBand(cx, cy, s, moon, southern)
            if (hourlyWeather != null) drawWeatherRing(cx, cy, s, hourlyWeather, textMeasurer, typography.sans)
            for ((a, b) in sleepSegments(dayWindow)) drawSleep(cx, cy, s, a.toFloat(), b.toFloat(), nowMin, dayIsPast)
            if (sun != null) drawSunMarks(cx, cy, s, sun)
            if (alarm != null) drawAlarmMark(cx, cy, s, alarm, alarmLabel, use24HourClock, textMeasurer, typography.sans)
            drawBlocks(cx, cy, s, drawn, nowMin, dayIsPast, isToday, selectedBlockId, dragState?.block?.id)
            drawFocusRail(cx, cy, s, mergeFocusSpans(focusSpans))
            drawFrames(cx, cy, s, frames, selectedFrameId)
            if (isToday) drawRoutines(cx, cy, s, routines, selectedRoutineId, nowMin, textMeasurer, typography.sans)
            if (nowMin != null) drawNow(cx, cy, s, nowMin, needleDegrees)
        }

        if (complications.any { it != null }) {
            DialComplicationsLayer(complications, sidePx, typography.sans, Modifier.size(sideDp))
        }

        DialHub(
            date = date,
            blocks = laidOut,
            frames = frames,
            routines = routines,
            selectedBlockId = selectedBlockId,
            selectedRoutineId = selectedRoutineId,
            selectedFrameId = selectedFrameId,
            drag = dragState,
            nowMin = nowMin,
            use24HourClock = use24HourClock,
            typography = typography,
            callbacks = callbacks,
            dialPx = sidePx,
            locale = locale,
            // The reference pads the hub 18 % a side (64 % of the dial for text); the launcher keeps
            // 56 % where its hub type is larger than the app's, and widens to 60 % on a phone-size dial.
            modifier = Modifier.width(sideDp * if (sideDp < SMALL_HUB_DIAL) 0.6f else 0.56f).height(sideDp),
        )

        val sheet = sheetBlock
        if (sheet != null) {
            BlockActionSheet(
                block = sheet,
                laidOut = laidOut.firstOrNull { it.block.id == sheet.id },
                nowMin = nowMin,
                use24HourClock = use24HourClock,
                callbacks = callbacks,
                onDismiss = { sheetBlock = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// region: gestures

internal enum class DragMode { MOVE, RESIZE }

internal data class DragState(
    val block: DialBlock,
    val mode: DragMode,
    val previewStart: Int,
    val previewDuration: Int,
)

/** The trailing 20 % of a block (at least 10 minutes) resizes; anywhere else moves. */
internal fun dragModeFor(hit: DayBlock, downMinute: Float): DragMode {
    val zone = max(10f, hit.block.durationMin * 0.2f)
    return if (hit.endMin - downMinute <= zone && !hit.endsNextDay) DragMode.RESIZE else DragMode.MOVE
}

/** Circular delta in minutes, in (-720, 720]. */
internal fun circularDelta(from: Float, to: Float): Float {
    var d = to - from
    while (d > MINUTES_PER_DAY / 2f) d -= MINUTES_PER_DAY
    while (d <= -MINUTES_PER_DAY / 2f) d += MINUTES_PER_DAY
    return d
}

internal fun applyDrag(state: DragState, original: DialBlock, downMinute: Float, minute: Float, step: Int): DragState {
    val delta = circularDelta(downMinute, minute)
    return when (state.mode) {
        DragMode.MOVE -> {
            val raw = original.startMin + delta
            val snapped = DialGeometry.snap(raw, step).coerceIn(0, max(0, MINUTES_PER_DAY - original.durationMin))
            state.copy(previewStart = snapped)
        }
        DragMode.RESIZE -> {
            val rawEnd = original.endMin + delta
            val snappedEnd = DialGeometry.snap(rawEnd, step).coerceIn(original.startMin + step, MINUTES_PER_DAY)
            state.copy(previewDuration = snappedEnd - original.startMin)
        }
    }
}

/** The block under a pixel, or null. Outer lanes are drawn last, so they win ties. */
internal fun hitTestBlock(blocks: List<DayBlock>, x: Float, y: Float, cx: Float, cy: Float, scale: Float): DayBlock? {
    val r = DialGeometry.radiusAt(cx, cy, x, y) / scale
    if (r < DialGeometry.R_INNER - 8f || r > DialGeometry.R_EDGE + 12f) return null
    val minute = DialGeometry.minuteAt(cx, cy, x, y)
    var best: DayBlock? = null
    for (b in blocks) {
        if (minute < b.startMin || minute >= b.endMin) continue
        val band = DialGeometry.laneBand(DialGeometry.R_INNER, DialGeometry.R_EDGE, b.lane, b.laneCount)
        val slack = if (b.laneCount > 1) 2f else 12f
        if (r >= band.rInner - slack && r <= band.rOuter + slack) {
            if (best == null || b.lane >= best.lane) best = b
        }
    }
    return best
}

internal fun hitTestRoutine(routines: List<DialRoutine>, x: Float, y: Float, cx: Float, cy: Float, scale: Float): DialRoutine? {
    val r = DialGeometry.radiusAt(cx, cy, x, y) / scale
    if (r < DialGeometry.R_ROUTINE_INNER - 8f || r > DialGeometry.R_ROUTINE_OUTER + 8f) return null
    val minute = DialGeometry.minuteAt(cx, cy, x, y)
    return routines.firstOrNull { minute >= it.startMin - 4 && minute <= it.endMin + 4 }
}

// endregion

// region: drawing

private fun DrawScope.arcRect(cx: Float, cy: Float, r: Float): Pair<Offset, Size> =
    Pair(Offset(cx - r, cy - r), Size(r * 2f, r * 2f))

/** Compose arcs start at 3 o'clock; the dial's zero is 12 o'clock. */
private fun startAngleOf(min: Float): Float = DialGeometry.degrees(min) - 90f

private fun DrawScope.strokeArc(
    cx: Float, cy: Float, r: Float, startMin: Float, endMin: Float,
    color: Color, width: Float, alpha: Float, cap: StrokeCap = StrokeCap.Butt,
    dash: androidx.compose.ui.graphics.PathEffect? = null,
) {
    if (endMin <= startMin) return
    val (tl, sz) = arcRect(cx, cy, r)
    drawArc(
        color = color,
        startAngle = startAngleOf(startMin),
        sweepAngle = DialGeometry.degrees(endMin - startMin),
        useCenter = false,
        topLeft = tl,
        size = sz,
        alpha = alpha.coerceIn(0f, 1f),
        style = Stroke(width = width, cap = cap, pathEffect = dash),
    )
}

private fun sectorPath(cx: Float, cy: Float, rInner: Float, rOuter: Float, startMin: Float, endMin: Float): Path {
    val path = Path()
    val start = startAngleOf(startMin)
    val sweep = DialGeometry.degrees(endMin - startMin)
    path.arcTo(Rect(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter), start, sweep, forceMoveTo = true)
    path.arcTo(Rect(cx - rInner, cy - rInner, cx + rInner, cy + rInner), start + sweep, -sweep, forceMoveTo = false)
    path.close()
    return path
}

private fun DrawScope.drawFace(cx: Float, cy: Float, s: Float) {
    drawCircle(DialColors.Face, radius = 500f * s, center = Offset(cx, cy))
    // Chapter ring (bezel).
    drawCircle(DialColors.Ink, radius = DialGeometry.R_BEZEL * s, center = Offset(cx, cy), alpha = 0.07f, style = Stroke(2f * s))
}

private fun DrawScope.drawTicks(cx: Float, cy: Float, s: Float) {
    for (t in DialGeometry.ticks()) {
        val (r1, r2, w, a) = when (t.kind) {
            DialGeometry.TickKind.HOUR -> listOf(400f, 436f, 2.5f, 0.45f)
            DialGeometry.TickKind.QUARTER -> listOf(404f, 428f, 1.6f, 0.22f)
            DialGeometry.TickKind.MINOR -> listOf(407f, 421f, 1.0f, 0.10f)
        }
        val (x1, y1) = DialGeometry.point(cx, cy, r1 * s, t.min.toFloat())
        val (x2, y2) = DialGeometry.point(cx, cy, r2 * s, t.min.toFloat())
        drawLine(DialColors.Ink, Offset(x1, y1), Offset(x2, y2), strokeWidth = max(1f, w * s), alpha = a)
    }
}

private fun DrawScope.drawHourLabels(
    cx: Float, cy: Float, s: Float, use24: Boolean, compact: Boolean, marks: List<Int>,
    measurer: androidx.compose.ui.text.TextMeasurer, font: FontFamily,
) {
    // The 6 and 18 labels are dropped on every size: "6 AM" / "6 PM" need side margins the square
    // face does not have, and the sun marks live there anyway.
    for (label in DialGeometry.hourLabels(use24, compact = true)) {
        if (DialGeometry.labelYieldsToMark(label.hour * 60, marks)) continue
        val size = (if (label.cardinal) 26f else 21f) * s
        val alpha = if (label.cardinal) 0.4f else 0.26f
        val style = TextStyle(
            color = DialColors.Ink.copy(alpha = alpha),
            fontSize = (size / density).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            letterSpacing = 0.25.em(size / density),
        )
        val layout = measurer.measure(AnnotatedString(label.text), style)
        val (x, y) = DialGeometry.point(cx, cy, DialGeometry.R_LABEL * s, (label.hour * 60).toFloat())
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
    }
}

private fun Double.em(fontSizeSp: Float) = (this * fontSizeSp).sp

private fun DrawScope.drawDaylight(cx: Float, cy: Float, s: Float, sun: SunTimes) {
    if (sun.sunsetMin <= sun.sunriseMin) return
    val r = (DialGeometry.R_DAYLIGHT_INNER + DialGeometry.R_DAYLIGHT_OUTER) / 2f * s
    val w = (DialGeometry.R_DAYLIGHT_OUTER - DialGeometry.R_DAYLIGHT_INNER) * s
    val span = (sun.sunsetMin - sun.sunriseMin).toFloat()
    var m = sun.sunriseMin.toFloat()
    while (m < sun.sunsetMin) {
        val next = min(m + 4f, sun.sunsetMin.toFloat())
        val frac = (m - sun.sunriseMin) / span
        // Solar elevation approximated by a half sine over the day; the floor is 0.05, the peak 0.2.
        val lit = sin(PI.toFloat() * frac).coerceIn(0f, 1f)
        val alpha = 0.05f + (0.2f - 0.05f) * lit
        strokeArc(cx, cy, r, m, next + 0.6f, DialColors.Daylight, w, alpha)
        m = next
    }
}

/** The daylight band from computed steps (solar elevation and UV), on the same track and feather as the half-sine. */
private fun DrawScope.drawDaylightSteps(cx: Float, cy: Float, s: Float, steps: List<DaylightStep>) {
    val r = (DialGeometry.R_DAYLIGHT_INNER + DialGeometry.R_DAYLIGHT_OUTER) / 2f * s
    val w = (DialGeometry.R_DAYLIGHT_OUTER - DialGeometry.R_DAYLIGHT_INNER) * s
    for (step in steps) strokeArc(cx, cy, r, step.startMin.toFloat(), step.endMin + 0.6f, DialColors.Daylight, w, step.opacity)
}

/**
 * Weather ring: hour temperatures as quiet numerals at the 3-hour stations inside the needle's
 * root, and precipitation spells as a thin arc, solid for rain and dashed for snow, broken at
 * the midpoint for one glyph per spell. No per-hour condition icons and no temperature colour
 * ramp: numbers stay data, the palette stays the schedule's.
 */
private fun DrawScope.drawWeatherRing(
    cx: Float, cy: Float, s: Float, hourly: Map<Int, HourWeather>,
    measurer: androidx.compose.ui.text.TextMeasurer, font: FontFamily,
) {
    // 16 dial units, as in the app, but never under 9sp: on a phone-sized dial 16 units come
    // out near 5sp and the temperatures could not be read at all.
    val tempSp = maxOf(16f * s / density, TEMP_MIN_SP)
    val style = TextStyle(color = DialColors.Ink.copy(alpha = if (tempSp > 16f * s / density) 0.42f else 0.32f), fontSize = tempSp.sp, fontWeight = FontWeight.Medium, fontFamily = font)
    for (h in 0 until 24 step 3) {
        val temp = hourly[h]?.temp ?: continue
        val layout = measurer.measure(AnnotatedString("$temp°"), style)
        val (x, y) = DialGeometry.point(cx, cy, DialGeometry.R_TEMP * s, (h * 60).toFloat())
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
    }
    val ink = DialColors.Ink
    for (run in DialWeather.precipRuns(hourly)) {
        val dash = if (run.kind == PrecipKind.SNOW) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f * s, 7f * s)) else null
        for ((from, to) in DialWeather.precipArcSegments(run)) {
            strokeArc(cx, cy, DialGeometry.R_PRECIP * s, from, to, ink, 2.5f * s, 0.3f, StrokeCap.Round, dash)
        }
        val mid = (run.startMin + run.endMin) / 2f
        val (gx, gy) = DialGeometry.point(cx, cy, DialGeometry.R_PRECIP * s, mid)
        val k = 0.45f * s
        val stroke = 2.6f * k
        if (run.kind == PrecipKind.RAIN) {
            // Lucide's droplet, traced: a point at the top, round at the bottom.
            val drop = Path().apply {
                moveTo(0f, -10f * k)
                cubicTo(3f * k, -4f * k, 7f * k, -1f * k, 7f * k, 3f * k)
                arcTo(Rect(-7f * k, -4f * k, 7f * k, 10f * k), 0f, 180f, false)
                cubicTo(-7f * k, -1f * k, -3f * k, -4f * k, 0f, -10f * k)
                close()
            }
            translate(gx, gy) { drawPath(drop, ink, alpha = 0.3f, style = Stroke(stroke, join = StrokeJoin.Round)) }
        } else {
            // Snow: three crossed one-weight lines, a six-spoke star.
            for (i in 0 until 3) {
                val a = i * PI.toFloat() / 3f + PI.toFloat() / 2f
                drawLine(ink, Offset(gx + cos(a) * 9f * k, gy + sin(a) * 9f * k), Offset(gx - cos(a) * 9f * k, gy - sin(a) * 9f * k), stroke, StrokeCap.Round, alpha = 0.3f)
            }
        }
    }
}

private fun DrawScope.drawSleep(cx: Float, cy: Float, s: Float, start: Float, end: Float, nowMin: Int?, dayIsPast: Boolean) {
    val padStart = start > 0f
    val padEnd = end < MINUTES_PER_DAY
    val (a, b) = DialGeometry.padSpan(start, end, padStart, padEnd)
    if (b <= a) return
    val past = if (nowMin != null) end <= nowMin else dayIsPast
    val mute = if (past) 0.36f else 0.6f
    val intensity = DialGeometry.intensity((end - start).toInt())
    drawPath(sectorPath(cx, cy, DialGeometry.R_INNER * s, DialGeometry.R_EDGE * s, a, b), DialColors.Sleep, alpha = intensity.fillOpacity * mute)
    strokeArc(cx, cy, DialGeometry.R_EDGE * s, a, b, DialColors.Sleep, intensity.edgeWidth * s, intensity.edgeOpacity * mute, StrokeCap.Round)
}

private fun DrawScope.drawSunMarks(cx: Float, cy: Float, s: Float, sun: SunTimes) {
    for ((m, rise) in listOf(sun.sunriseMin to true, sun.sunsetMin to false)) {
        val (x1, y1) = DialGeometry.point(cx, cy, 288f * s, m.toFloat())
        val (x2, y2) = DialGeometry.point(cx, cy, 432f * s, m.toFloat())
        drawLine(DialColors.Sun, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.5f * s, alpha = 0.55f)
        // Lucide's sunrise and sunset, as the app draws them (SunMark in DayDial.jsx): the same
        // half-disc on the same horizon, the arrow through the stem the whole difference.
        val (gx, gy) = DialGeometry.point(cx, cy, DialGeometry.R_SUN_GLYPH * s, m.toFloat())
        val k = SUN_GLYPH_SCALE * s
        withTransform({
            translate(gx - 12f * k, gy - 12f * k)
            scale(k, k, pivot = Offset.Zero)
        }) {
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(HORIZON_SUN, DialColors.Sun, alpha = 0.55f, style = stroke)
            drawPath(if (rise) SUNRISE_STEM else SUNSET_STEM, DialColors.Sun, alpha = 0.55f, style = stroke)
        }
    }
}

/** The temperature labels' floor, so a phone-sized dial can still be read. */
private const val TEMP_MIN_SP = 9f

/** Lucide's 24-unit grid to dial units, as the app's GLYPH_SCALE. */
private const val SUN_GLYPH_SCALE = 0.9f

// Lazy: a Path needs the platform's graphics backend, which the dial's JVM unit tests do
// not load; only drawing builds these.
private fun lucide(d: String): Path = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()

/** The half-disc on the horizon both glyphs share (lucide sunrise/sunset). */
private val HORIZON_SUN by lazy { lucide("M4.93 10.93l1.41 1.41 M2 18h2 M20 18h2 M19.07 10.93l-1.41 1.41 M22 22H2 M16 18a4 4 0 0 0-8 0") }
/** Sunrise: the stem with its arrow pointing up. */
private val SUNRISE_STEM by lazy { lucide("M12 2v8 M16 6l-4-4-4 4") }
/** Sunset: the stem with its arrow pointing down. */
private val SUNSET_STEM by lazy { lucide("M12 10V2 M8 6l4 4 4-4") }

/** The three feathered sub-bands of the daylight track: inner edge, body, outer edge, with their weights. */
private val BAND_FEATHER = listOf(Triple(0f, 0.25f, 0.35f), Triple(0.25f, 0.75f, 1f), Triple(0.75f, 1f, 0.35f))

/**
 * The moon band on the daylight track, and the phase glyph at the moon's apex: the lit limb
 * on one side, the terminator (a semi-ellipse that collapses at the quarters) on the other,
 * with the unlit disc as a hairline so a new moon still reads as a moon that is up.
 */
private fun DrawScope.drawMoonBand(cx: Float, cy: Float, s: Float, moon: MoonBand, southern: Boolean) {
    val r0 = DialGeometry.R_DAYLIGHT_INNER * s
    val r1 = DialGeometry.R_DAYLIGHT_OUTER * s
    for (step in moon.steps) {
        for ((a, b, weight) in BAND_FEATHER) {
            drawPath(
                sectorPath(cx, cy, r0 + (r1 - r0) * a, r0 + (r1 - r0) * b, step.startMin.toFloat(), step.endMin + 0.6f),
                DialColors.Moon, alpha = (step.opacity * weight).coerceIn(0f, 1f),
            )
        }
    }
    val glyphMin = moon.glyphMin ?: return
    val (gx, gy) = DialGeometry.point(cx, cy, DialGeometry.R_MOON_GLYPH * s, glyphMin.toFloat())
    val radius = DialGeometry.MOON_GLYPH_RADIUS * s
    drawCircle(DialColors.Moon, radius, Offset(gx, gy), alpha = 0.3f, style = Stroke(1f * s))
    val lit = moonPhasePath(radius, moon.fraction, moon.waxing, southern)
    translate(gx, gy) { drawPath(lit, DialColors.Moon, alpha = 0.85f) }
}

/**
 * The lit portion of a moon disc of radius [r], centred on the origin: a port of
 * dayGLANCE's `moonPhasePath`. [waxing] lights the right side for a northern observer;
 * [mirror] flips it for a southern one.
 */
internal fun moonPhasePath(r: Float, fraction: Float, waxing: Boolean, mirror: Boolean): Path {
    val k = fraction.coerceIn(0f, 1f)
    val rx = r * abs(1f - 2f * k)
    val litRight = waxing != mirror
    // Past half, the terminator bulges away from the lit limb rather than toward it.
    val clockwise = if (litRight) k >= 0.5f else k < 0.5f
    return Path().apply {
        moveTo(0f, -r)
        // The limb: a semicircle on the lit side, top to bottom.
        arcTo(Rect(-r, -r, r, r), -90f, if (litRight) 180f else -180f, false)
        // The terminator: bottom back to top along the semi-ellipse.
        if (rx < 0.01f) lineTo(0f, -r)
        else arcTo(Rect(-rx, -r, rx, r), 90f, if (clockwise) 180f else -180f, false)
        close()
    }
}

/**
 * The next alarm: a hairline in the sun marks' grammar, in its own colour. Today's alarm
 * carries a small clock glyph at its minute, in the labels' band; tomorrow's stands at 00 with
 * the glyph, its time and an arrow written beside the midnight label.
 */
private fun DrawScope.drawAlarmMark(
    cx: Float, cy: Float, s: Float, alarm: AlarmMark, label: String, use24: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer, font: FontFamily,
) {
    val color = DialColors.Alarm
    val alpha = 0.75f
    val m = alarm.minute.toFloat()
    val (x1, y1) = DialGeometry.point(cx, cy, (DialGeometry.R_INNER - 12f) * s, m)
    val (x2, y2) = DialGeometry.point(cx, cy, (DialGeometry.R_BEZEL + 8f) * s, m)
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.5f * s, alpha = alpha, cap = StrokeCap.Round)
    if (alarm.mode == AlarmMode.TODAY) {
        val (gx, gy) = DialGeometry.point(cx, cy, DialGeometry.R_SUN_GLYPH * s, m)
        drawAlarmClock(gx, gy, s, color, alpha)
        return
    }
    // Tomorrow: the group starts just past the midnight label.
    val midnight = DialGeometry.hourLabels(use24, compact = true).firstOrNull { it.hour == 0 }?.text ?: "00"
    val labelStyle = TextStyle(fontSize = (26f * s / density).sp, fontWeight = FontWeight.Medium, fontFamily = font, letterSpacing = 0.25.em(26f * s / density))
    val labelHalf = measurer.measure(AnnotatedString(midnight), labelStyle).size.width / 2f
    val (tx, ty) = DialGeometry.point(cx, cy, DialGeometry.R_LABEL * s, 0f)
    var x = tx + labelHalf + 12f * s
    drawAlarmClock(x + 11f * s, ty, s, color, alpha)
    x += 28f * s
    val timeStyle = TextStyle(color = color.copy(alpha = 0.85f), fontSize = (22f * s / density).sp, fontWeight = FontWeight.Medium, fontFamily = font)
    val layout = measurer.measure(AnnotatedString(label), timeStyle)
    drawText(layout, topLeft = Offset(x, ty - layout.size.height / 2f))
    x += layout.size.width + 8f * s
    // An arrow: tomorrow.
    val ax = x + 11f * s
    val half = 7f * s
    drawLine(color, Offset(ax - half, ty), Offset(ax + half, ty), 2f * s, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(ax + half - 5f * s, ty - 5f * s), Offset(ax + half, ty), 2f * s, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(ax + half - 5f * s, ty + 5f * s), Offset(ax + half, ty), 2f * s, StrokeCap.Round, alpha = alpha)
}

/** A small alarm clock: the dial, two hands, two bells and two feet, in lucide's proportions. */
private fun DrawScope.drawAlarmClock(gx: Float, gy: Float, s: Float, color: Color, alpha: Float) {
    val k = 0.9f * s
    val stroke = 1.8f * s
    drawCircle(color, 8f * k, Offset(gx, gy + 1f * k), alpha = alpha, style = Stroke(stroke))
    drawLine(color, Offset(gx, gy - 3f * k), Offset(gx, gy + 1f * k), stroke, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(gx, gy + 1f * k), Offset(gx + 2.5f * k, gy + 3f * k), stroke, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(gx - 7f * k, gy - 9f * k), Offset(gx - 10f * k, gy - 6f * k), stroke, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(gx + 7f * k, gy - 9f * k), Offset(gx + 10f * k, gy - 6f * k), stroke, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(gx - 5.6f * k, gy + 6.7f * k), Offset(gx - 8f * k, gy + 9f * k), stroke, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(gx + 5.6f * k, gy + 6.7f * k), Offset(gx + 8f * k, gy + 9f * k), stroke, StrokeCap.Round, alpha = alpha)
}

private fun DrawScope.drawBlocks(
    cx: Float, cy: Float, s: Float, blocks: List<DayBlock>, nowMin: Int?, dayIsPast: Boolean,
    viewingToday: Boolean, selectedId: String?, draggingId: String?,
) {
    for (b in blocks.sortedBy { it.lane }) {
        val (a, e) = DialGeometry.padSpan(
            b.startMin.toFloat(), b.endMin.toFloat(),
            padStart = !b.startedPrevDay, padEnd = !b.endsNextDay,
        )
        if (e <= a) continue
        val band = DialGeometry.laneBand(DialGeometry.R_INNER, DialGeometry.R_EDGE, b.lane, b.laneCount)
        val color = DialGeometry.muteColor(b.block.color)
        val intensity = DialGeometry.intensity(b.block.durationMin)
        val tone = blockTone(b, nowMin, dayIsPast, viewingToday)
        val selected = b.block.id == selectedId || b.block.id == draggingId
        val fillMute = if (selected) 1f else tone.fill
        val edgeMute = if (selected) 1f else tone.edge
        val edgeAlpha = if (selected) 1f else tone.edge
        val rIn = band.rInner * s
        val rOut = band.rOuter * s
        drawPath(sectorPath(cx, cy, rIn, rOut, a, e), color, alpha = intensity.fillOpacity * fillMute)
        // Glow halo: the reference blurs a wide stroke; here three soft strokes stand in for it.
        val haloW = intensity.edgeWidth * 2.4f * s
        strokeArc(cx, cy, rOut, a, e, color, haloW * 1.8f, 0.08f * edgeAlpha, StrokeCap.Round)
        strokeArc(cx, cy, rOut, a, e, color, haloW, 0.16f * edgeAlpha, StrokeCap.Round)
        // Crisp outer edge.
        strokeArc(cx, cy, rOut, a, e, color, intensity.edgeWidth * s, intensity.edgeOpacity * edgeMute, StrokeCap.Round)
        if (selected) {
            drawPath(sectorPath(cx, cy, rIn, rOut, a, e), DialColors.Ink, alpha = 0.45f, style = Stroke(1.5f * s))
        }
        if (b.block.completed) {
            // A small check mark at the arc's middle, so a finished block reads at a glance.
            val mid = (a + e) / 2f
            val (mx, my) = DialGeometry.point(cx, cy, band.mid * s, mid)
            val k = 5f * s
            val p = Path().apply {
                moveTo(mx - k, my)
                lineTo(mx - k / 3f, my + k * 0.7f)
                lineTo(mx + k, my - k * 0.6f)
            }
            drawPath(p, DialColors.Ink, alpha = 0.55f, style = Stroke(1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawFocusRail(cx: Float, cy: Float, s: Float, spans: List<FocusSpan>) {
    for (sp in spans) {
        drawPath(
            sectorPath(cx, cy, DialGeometry.R_FOCUS_INNER * s, DialGeometry.R_FOCUS_OUTER * s, sp.startMin.toFloat(), sp.endMin.toFloat()),
            DialColors.Ink, alpha = 0.42f,
        )
    }
}

private fun DrawScope.drawFrames(cx: Float, cy: Float, s: Float, frames: List<DialFrame>, selectedId: String?) {
    val width = DialFrames.strokeWidth(s)
    for ((i, f) in frames.withIndex()) {
        if (f.endMin - f.startMin < DialFrames.MIN_MINUTES) continue
        val (inner, outer) = DialFrames.radii(frameDepth(frames, i), s)
        val color = DialGeometry.muteFrameColor(f.color)
        drawPath(
            sectorPath(cx, cy, inner * s, outer * s, f.startMin.toFloat(), f.endMin.toFloat()),
            color, alpha = if (f.id == selectedId) 0.95f else 0.45f, style = Stroke(width * s, join = StrokeJoin.Round),
        )
    }
}

private fun DrawScope.drawRoutines(
    cx: Float, cy: Float, s: Float, routines: List<DialRoutine>, selectedId: String?, nowMin: Int?,
    measurer: androidx.compose.ui.text.TextMeasurer, font: FontFamily,
) {
    if (routines.isEmpty()) return
    val slots = assignLanes(routines.mapIndexed { i, r -> LaneInput(i, r.startMin, r.endMin) })
    var chipFor: DialRoutine? = routines.firstOrNull { it.id == selectedId }
    if (chipFor == null && nowMin != null) {
        chipFor = routines.firstOrNull { !it.completed && it.startMin <= nowMin && nowMin < it.endMin }
    }
    for ((i, r) in routines.withIndex()) {
        val band = DialGeometry.laneBand(DialGeometry.R_ROUTINE_INNER, DialGeometry.R_ROUTINE_OUTER, slots[i].lane, slots[i].laneCount)
        val weight = min(14f, band.depth) * s
        val alpha = when {
            r.id == selectedId -> 0.95f
            r.completed -> 0.18f
            else -> 0.5f
        }
        strokeArc(cx, cy, band.mid * s, r.startMin.toFloat(), r.endMin.toFloat(), DialColors.Routine, weight, alpha, StrokeCap.Round)
        if (chipFor?.id == r.id) {
            val mid = (r.startMin + r.endMin) / 2f
            val (px, py) = DialGeometry.point(cx, cy, (band.rOuter + 22f) * s, mid)
            val fontPx = 15f * s
            val layout = measurer.measure(
                AnnotatedString(r.name),
                TextStyle(color = DialColors.Routine.copy(alpha = 0.9f), fontSize = (fontPx / density).sp, fontFamily = font),
            )
            val w = layout.size.width + 26f * s
            val h = 30f * s
            val onRight = mid < MINUTES_PER_DAY / 2f
            val left = if (onRight) px else px - w
            val top = py - h / 2f
            drawRoundRect(Color(0xFF0D1F1D), Offset(left, top), Size(w, h), androidx.compose.ui.geometry.CornerRadius(15f * s))
            drawRoundRect(DialColors.Routine, Offset(left, top), Size(w, h), androidx.compose.ui.geometry.CornerRadius(15f * s), alpha = 0.45f, style = Stroke(1f * s))
            drawText(layout, topLeft = Offset(left + 13f * s, top + (h - layout.size.height) / 2f))
        }
    }
}

private fun DrawScope.drawNow(cx: Float, cy: Float, s: Float, nowMin: Int, needleDegrees: Float) {
    // Afterglow: 15 sectors over the previous hour, stacking to about 0.15 at the needle.
    for (i in 0 until DialGeometry.TRAIL_STEPS) {
        val from = max(0f, nowMin - DialGeometry.TRAIL_STEP_MIN * (i + 1f))
        if (nowMin <= from) continue
        drawPath(sectorPath(cx, cy, DialGeometry.R_INNER * s, DialGeometry.R_EDGE * s, from, nowMin.toFloat()), DialColors.Now, alpha = 0.011f)
    }
    val angle = (needleDegrees) * PI.toFloat() / 180f
    val dir = Offset(sin(angle), -cos(angle))
    val root = Offset(cx, cy) + dir * (DialGeometry.R_NEEDLE_ROOT * s)
    val tip = Offset(cx, cy) + dir * (DialGeometry.R_NEEDLE_TIP * s)
    drawLine(DialColors.Now, root, tip, strokeWidth = 3f * s, cap = StrokeCap.Round, alpha = 0.9f)
    val dot = Offset(cx, cy) + dir * (DialGeometry.R_EDGE * s)
    drawCircle(DialColors.Now, radius = 13f * s, center = dot, alpha = 0.28f)
    drawCircle(DialColors.Now, radius = 7f * s, center = dot)
}

// endregion

// region: hub

@Composable
internal fun DialHub(
    date: LocalDate,
    blocks: List<DayBlock>,
    frames: List<DialFrame>,
    routines: List<DialRoutine>,
    selectedBlockId: String?,
    selectedRoutineId: String?,
    selectedFrameId: String?,
    drag: DragState?,
    nowMin: Int?,
    use24HourClock: Boolean,
    typography: DayDialTypography,
    callbacks: DayDialCallbacks,
    dialPx: Float,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dialDp = with(density) { dialPx.toDp() }
    // Reference sizes are clamp(min, N vmin, max); vmin here is the dial side.
    fun vmin(frac: Float, minSp: Float, maxSp: Float) = (dialDp.value * frac / 100f).coerceIn(minSp, maxSp).sp
    val isToday = nowMin != null
    // A phone-size dial: the reference's hub sits at every clamp's minimum there (its vmin is the
    // viewport, which on a phone is the dial's own width), so this mode takes those minimums and
    // the reference's tighter rhythm; the tablet keeps the launcher's larger clock and chevrons.
    val small = dialDp < SMALL_HUB_DIAL
    val textMeasurer = rememberTextMeasurer()

    // The clock, weekday and date fill the top part from the bottom up, so the divider sits a
    // little below the dial's midpoint (5 % of the side, where the balanced tablet stack put it)
    // whatever the readout under it runs to; the reference centres the whole stack, which lets a
    // three-line readout push the date upward. Phone sizes set line heights at the reference's
    // ratios (1.5 for the sans, 1.25 for the serif): Material's default 24 sp line would
    // otherwise pad a 10 sp weekday with 14 sp of air.
    Column(modifier = modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(0.55f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isToday) {
            Text(
                text = formatClock(nowMin!!, use24HourClock),
                color = DialColors.Ink.copy(alpha = 0.7f),
                fontSize = if (small) 14.sp else vmin(3.8f, 18f, 36f),
                lineHeight = if (small) 21.sp else TextUnit.Unspecified,
                fontWeight = FontWeight.Medium,
                fontFamily = typography.sans,
                maxLines = 1,
                modifier = hubTap(callbacks.onClockTap, "Open clock"),
            )
        } else {
            Text(
                text = "TODAY",
                color = DialColors.Ink.copy(alpha = 0.7f),
                fontSize = vmin(1.6f, 10f, 14f),
                letterSpacing = 0.2.em(12f),
                fontFamily = typography.sans,
                modifier = Modifier
                    .border(1.dp, DialColors.Ink.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .clickable { callbacks.onGoToday() }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        // The weekday sits in a fixed-width slot so the chevrons stay put whatever the name's length.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = if (small) Modifier.padding(top = 3.dp, bottom = 4.dp) else Modifier.padding(vertical = (dialDp.value * 0.012f).coerceIn(4f, 12f).dp),
        ) {
            HubChevron(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", small) { callbacks.onStepDay(-1) }
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE", locale)).uppercase(locale),
                color = DialColors.Ink.copy(alpha = 0.4f),
                fontSize = vmin(1.6f, 10f, 16f),
                lineHeight = if (small) 15.sp else TextUnit.Unspecified,
                letterSpacing = 0.35.em(12f),
                fontFamily = typography.sans,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // The slot is sized to WEDNESDAY, the longest English day, at each mode's type size.
                modifier = Modifier.width(dialDp * if (small) 0.30f else 0.34f),
            )
            HubChevron(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day", small) { callbacks.onStepDay(1) }
        }
        val longDate = date.format(DateTimeFormatter.ofPattern("MMMM d", locale))
        val shortDate = date.format(DateTimeFormatter.ofPattern("MMM d", locale))
        val dateSize = vmin(7f, 28f, 64f)
        // The long month falls back to the short one when it would not fit 44 % of the dial
        // (`dialDateFits`), measured in the serif rather than estimated, as the reference does.
        // The call is made per month, from its widest date, so a phone's November is "Nov"
        // all month rather than "November 1" and then "Nov 24"; a tablet keeps every name.
        val dateStyle = TextStyle(fontSize = dateSize, fontFamily = typography.serif, fontWeight = FontWeight.Medium)
        val fits = remember(YearMonth.from(date), locale, dateSize, typography.serif, dialPx) {
            val pattern = DateTimeFormatter.ofPattern("MMMM d", locale)
            DialGeometry.monthDateFits(date, dialPx) { d -> textMeasurer.measure(d.format(pattern), dateStyle).size.width.toFloat() }
        }
        Text(
            text = if (fits) longDate else shortDate,
            color = DialColors.Ink,
            fontSize = dateSize,
            lineHeight = if (small) dateSize * 1.25f else TextUnit.Unspecified,
            fontFamily = typography.serif,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = hubTap(callbacks.onDateTap?.let { tap -> { tap(date) } }, "Open calendar"),
        )
        Spacer(Modifier.height(if (small) 5.dp else 6.dp))
        }
        }
        // No lock mark here: the header's lock button already shows the state, and a mark beside
        // the divider pushed it off centre and crowded a phone's hub.
        Box(Modifier.width(96.dp).height(1.dp).background(DialColors.Ink.copy(alpha = 0.15f)))
        Spacer(Modifier.height(if (small) 5.dp else 8.dp))

        Box(Modifier.weight(0.45f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val inspected = drag?.let { d -> blocks.firstOrNull { it.block.id == d.block.id } }
            ?: blocks.firstOrNull { it.block.id == selectedBlockId }
        val inspectedRoutine = routines.firstOrNull { it.id == selectedRoutineId }
        val focus = findFocusBlock(blocks, nowMin)
        // A frame tapped wins the hub over the one now is in; a running block wins over that.
        val currentFrame = frames.firstOrNull { it.id == selectedFrameId }
            ?: if (focus?.let { nowMin != null && it.trueStart <= nowMin && nowMin < it.trueEnd } != true) {
                DialFrames.current(frames, nowMin)
            } else null

        when {
            inspected != null -> {
                val b = inspected
                val previewStart = drag?.previewStart ?: b.trueStart
                val previewDuration = drag?.previewDuration ?: b.block.durationMin
                ReadoutTitle(b.block.color, titleWithoutTags(b.block.title), extractTags(b.block.title), typography, dialDp.value)
                ReadoutLine(
                    rangeText(previewStart, previewStart + previewDuration, use24HourClock) + " · " + formatDuration(previewDuration),
                    typography, dialDp.value,
                )
                ReadoutLine(
                    when {
                        drag != null -> if (drag.mode == DragMode.MOVE) "moving" else "resizing"
                        b.block.completed -> "completed"
                        nowMin != null -> relativePhrase(b, nowMin)
                        else -> ""
                    },
                    typography, dialDp.value,
                )
            }
            inspectedRoutine != null -> {
                ReadoutTitle(DialColors.Routine, inspectedRoutine.name, emptyList(), typography, dialDp.value)
                ReadoutLine(rangeText(inspectedRoutine.startMin, inspectedRoutine.endMin, use24HourClock), typography, dialDp.value)
                ReadoutLine(if (inspectedRoutine.completed) "completed" else "routine · tap again to mark done", typography, dialDp.value)
            }
            currentFrame != null -> {
                val f = currentFrame
                FrameReadoutTitle(f.color, f.name, typography, dialDp.value)
                ReadoutLine(rangeText(f.startMin, f.endMin, use24HourClock), typography, dialDp.value)
                // Today only: another day's free time is not "still available".
                val available = if (nowMin == null) 0 else DialFrames.availableMinutes(f, blocks.map { it.startMin to it.endMin }, nowMin)
                if (available > 0) {
                    Text(
                        text = "${formatDuration(available)} available",
                        color = DialColors.FrameAvailable.copy(alpha = 0.75f),
                        fontSize = vmin(2.0f, 12f, 18f),
                        fontFamily = typography.sans,
                    )
                }
            }
            focus != null -> {
                val b = focus
                // The block the hub narrates unasked has no colour dot, as in the app; one tapped has.
                ReadoutTitle(b.block.color, titleWithoutTags(b.block.title), extractTags(b.block.title), typography, dialDp.value, dot = false)
                val running = b.trueStart <= nowMin!! && nowMin < b.trueEnd
                val lead = if (running) {
                    "until ${formatClock(b.trueEnd, use24HourClock)}${if (b.trueEnd > MINUTES_PER_DAY) " +1" else ""}"
                } else {
                    "next at ${formatClock(b.trueStart, use24HourClock)}"
                }
                // A phone-size hub narrows toward its foot; the one line would span the chord, so it is two.
                if (small) {
                    ReadoutLine(lead, typography, dialDp.value)
                    ReadoutLine(relativePhrase(b, nowMin), typography, dialDp.value)
                } else {
                    ReadoutLine("$lead · ${relativePhrase(b, nowMin)}", typography, dialDp.value)
                }
            }
            else -> {
                ReadoutLine(
                    if (isToday) "Nothing else scheduled" else "${blocks.size} blocks",
                    typography, dialDp.value, alpha = 0.35f,
                )
            }
        }
        }
        }
    }
}

private fun rangeText(startMin: Int, endMin: Int, use24: Boolean): String {
    val startSup = if (startMin < 0) "⁻¹" else ""
    val endSup = if (endMin > MINUTES_PER_DAY) "⁺¹" else ""
    return "${formatClock(startMin, use24)}$startSup – ${formatClock(endMin, use24)}$endSup"
}

/**
 * A tap on the hub's clock or date, when the host handles it: no ripple (the text is the
 * affordance, as on a home-screen clock), but labelled for accessibility. No handler, no
 * click target, so a read-only host keeps plain text.
 */
@Composable
private fun hubTap(onTap: (() -> Unit)?, label: String): Modifier =
    if (onTap == null) Modifier
    else Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = label,
        onClick = onTap,
    )

/** Below this dial side the hub takes the reference's phone sizes (see [DialHub]). */
internal val SMALL_HUB_DIAL = 420.dp

/** How far a tap may land from a frame's outline and still find it, each side: a 24 dp touch target. */
private const val FRAME_HIT_DP = 12f

@Composable
private fun HubChevron(icon: ImageVector, label: String, small: Boolean, onClick: () -> Unit) {
    if (small) {
        // The reference's 20 px chevron with its p-2 hit area. The hit box overflows the 20 dp the
        // row lays out, as the reference's absolutely positioned button does, so it adds no height.
        Box(Modifier.size(20.dp)) {
            Box(Modifier.requiredSize(32.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = DialColors.Ink.copy(alpha = 0.25f), modifier = Modifier.size(20.dp))
            }
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = DialColors.Ink.copy(alpha = 0.25f),
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onClick)
                .padding(2.dp),
        )
    }
}

/**
 * The hub's frame title, as the app draws it: the frames glyph and the name, both in the
 * frame's standard mute (the ring's outline takes the softer `muteFrameColor`), a task title's
 * size at medium weight.
 */
@Composable
private fun FrameReadoutTitle(color: Color, name: String, typography: DayDialTypography, dialDp: Float) {
    val small = dialDp < SMALL_HUB_DIAL.value
    val size = (dialDp * 0.024f).coerceIn(13f, 22f).sp
    val tint = DialGeometry.muteColor(color)
    val glyph = with(LocalDensity.current) { (size * 0.85f).toDp() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(DialGlyphs.LayoutGrid, contentDescription = null, tint = tint, modifier = Modifier.size(glyph))
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            color = tint,
            fontSize = size,
            lineHeight = if (small) size * 1.5f else TextUnit.Unspecified,
            fontFamily = typography.sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReadoutTitle(color: Color, title: String, tags: List<String>, typography: DayDialTypography, dialDp: Float, dot: Boolean = true) {
    val small = dialDp < SMALL_HUB_DIAL.value
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot) {
            Box(Modifier.size(8.dp).background(DialGeometry.muteColor(color), CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        val size = (dialDp * 0.024f).coerceIn(13f, 22f).sp
        Text(
            text = title,
            color = DialColors.Ink.copy(alpha = 0.85f),
            fontSize = size,
            lineHeight = if (small) size * 1.5f else TextUnit.Unspecified,
            fontFamily = typography.sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // Tags only on a hub with room for them: on a phone-sized dial the title, tags, time and
    // detail lines were three lines under the title in a hub that fits two.
    if (tags.isNotEmpty() && !small) {
        Text(
            text = tags.joinToString(" ") { "#$it" },
            color = DialColors.Ink.copy(alpha = 0.45f),
            fontStyle = FontStyle.Italic,
            fontSize = (dialDp * 0.018f).coerceIn(if (small) 10f else 11f, 16f).sp,
            lineHeight = if (small) 15.sp else TextUnit.Unspecified,
            fontFamily = typography.sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (small) Modifier.padding(top = 2.dp) else Modifier,
        )
    }
}

@Composable
private fun ReadoutLine(text: String, typography: DayDialTypography, dialDp: Float, alpha: Float = 0.4f) {
    if (text.isEmpty()) return
    val small = dialDp < SMALL_HUB_DIAL.value
    val size = (dialDp * 0.02f).coerceIn(if (small) 11f else 12f, 18f).sp
    Text(
        text = text,
        color = DialColors.Ink.copy(alpha = alpha),
        fontSize = size,
        lineHeight = if (small) size * 1.5f else TextUnit.Unspecified,
        fontFamily = typography.sans,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = if (small) Modifier.padding(top = 2.dp) else Modifier,
        overflow = TextOverflow.Ellipsis,
    )
}

// endregion

// region: action sheet

@Composable
internal fun BlockActionSheet(
    block: DialBlock,
    laidOut: DayBlock?,
    nowMin: Int?,
    use24HourClock: Boolean,
    callbacks: DayDialCallbacks,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 340.dp)
                .background(DialColors.Sheet, RoundedCornerShape(16.dp))
                .border(1.dp, DialColors.Ink.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(DialGeometry.muteColor(block.color), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = titleWithoutTags(block.title),
                    color = DialColors.Ink.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val start = laidOut?.trueStart ?: block.startMin
            val end = laidOut?.trueEnd ?: block.endMin
            Text(
                text = rangeText(start, end, use24HourClock) + " · " + formatDuration(block.durationMin),
                color = DialColors.Ink.copy(alpha = 0.45f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (block.completable) {
                SheetButton(
                    icon = if (block.completed) Icons.Filled.Refresh else Icons.Filled.Check,
                    label = if (block.completed) "Mark not complete" else "Mark complete",
                ) { callbacks.onToggleComplete(block); onDismiss() }
            }
            val running = nowMin != null && start <= nowMin && nowMin < end
            val startFocus = callbacks.onStartFocus
            if (running && startFocus != null) {
                SheetButton(Icons.Filled.PlayArrow, "Start focus session") { startFocus(block); onDismiss() }
            }
            SheetButton(Icons.Filled.Edit, "Open in planner") { callbacks.onOpenBlock(block); onDismiss() }
        }
    }
}

@Composable
private fun SheetButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(DialColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = DialColors.Ink.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = DialColors.Ink.copy(alpha = 0.9f), fontSize = 14.sp)
    }
    Spacer(Modifier.height(6.dp))
}

// endregion
