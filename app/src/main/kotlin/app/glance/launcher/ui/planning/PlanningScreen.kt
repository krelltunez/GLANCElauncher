@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.glance.launcher.ui.planning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.data.Cadence
import app.glance.launcher.data.CadenceState
import app.glance.launcher.data.Chore
import app.glance.launcher.data.Goal
import app.glance.launcher.data.Person
import app.glance.launcher.data.ProgressMath
import app.glance.launcher.data.Project
import app.glance.launcher.data.Task
import app.glance.launcher.intents.GlanceIntents
import app.glance.launcher.ui.GlanceIcons
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.common.DayCell
import app.glance.launcher.ui.common.DataStatusLabel
import app.glance.launcher.ui.common.GlanceCard
import app.glance.launcher.ui.common.LastGlanceWordmark
import app.glance.launcher.ui.common.LucideIcon
import app.glance.launcher.ui.common.ProgressBar
import app.glance.launcher.ui.common.SectionHeader
import app.glance.launcher.ui.common.Wordmark
import app.glance.launcher.ui.home.rememberNowMinute
import app.glance.launcher.ui.common.topBarsPadding
import app.glance.launcher.ui.theme.GlanceTheme
import app.glance.launcher.ui.theme.hexColor
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The planning screen: month with agenda, goal and project progress, and lastGLANCE's
 * overdue and due-soon items plus People recency. Fixed layout; taps deep-link into the apps.
 */
@Composable
fun PlanningScreen(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val repo = controller.repository
    val tasks by repo.tasks.collectAsState()
    val goals by repo.goals.collectAsState()
    val projects by repo.projects.collectAsState()
    val routines by repo.routines.collectAsState()
    val chores by repo.chores.collectAsState()
    val people by repo.people.collectAsState()
    val shape by repo.dayShape.collectAsState()
    val clockOverride by controller.graph.prefs.use24HourClock.collectAsState()
    val dockEnabled by controller.graph.prefs.dockEnabled.collectAsState()
    val use24 = clockOverride ?: shape.use24HourClock

    var quickAdd by remember { mutableStateOf<QuickAddKind?>(null) }
    BoxWithConstraints(modifier.background(c.background).topBarsPadding()) {
        val landscape = maxWidth > maxHeight
        // Phone widths: the month's cells go taller than square, since a seventh of the column is
        // only ~42 dp and the bands need the height more than the width.
        val compact = maxWidth < 600.dp
        val bottom: @Composable () -> Unit = { if (!dockEnabled) Spacer(Modifier.navigationBarsPadding()) }
        if (landscape) {
            // One column per app, each scrolling on its own.
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val jumps = rememberColumnJumps()
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(jumps.scroll).padding(vertical = 8.dp)) {
                    PlanningHeader("planning", controller)
                    MonthSection(controller, tasks, routines, projects, use24, SchedJumps(jumps::toGoals, jumps::toProjects), cellAspect = if (compact) 0.7f else 1f)
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.onGloballyPositioned { jumps.goalsY = it.positionInParent().y }) { GoalsSection(goals, projects, tasks, jumps) }
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.onGloballyPositioned { jumps.projectsY = it.positionInParent().y }) { ProjectsSection(projects, tasks, jumps, columns = 2) }
                    Spacer(Modifier.height(16.dp))
                    BackToTopRow(jumps)
                    Spacer(Modifier.height(24.dp))
                    bottom()
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    LastGlanceSection(controller, chores, people, jumps = null)
                    Spacer(Modifier.height(24.dp))
                    bottom()
                }
            }
        } else {
            val jumps = rememberColumnJumps()
            // As on the phone Home: the top inset is breathing room enough, and the 8 dp only tops it up.
            val topInset = with(LocalDensity.current) { WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(this).toDp() }
            Column(
                Modifier.fillMaxSize().verticalScroll(jumps.scroll)
                    .padding(start = 16.dp, end = 16.dp, top = if (compact) (8.dp - topInset).coerceAtLeast(0.dp) else 8.dp, bottom = 8.dp),
            ) {
                PlanningHeader("planning", controller)
                MonthSection(
                    controller, tasks, routines, projects, use24,
                    SchedJumps(jumps::toGoals, jumps::toProjects, toLastGlance = jumps::toLastGlance, compact = compact),
                    cellAspect = if (compact) 0.7f else 1f,
                )
                Spacer(Modifier.height(20.dp))
                Column(Modifier.onGloballyPositioned { jumps.goalsY = it.positionInParent().y }) { GoalsSection(goals, projects, tasks, jumps) }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.onGloballyPositioned { jumps.projectsY = it.positionInParent().y }) { ProjectsSection(projects, tasks, jumps, columns = if (compact) 1 else 2) }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.onGloballyPositioned { jumps.lastGlanceY = it.positionInParent().y }) { LastGlanceSection(controller, chores, people, jumps, compact = compact) }
                Spacer(Modifier.height(16.dp))
                BackToTopRow(jumps)
                Spacer(Modifier.height(24.dp))
                bottom()
            }
        }
        // Quick add, over everything on every size: tasks, chores and people (QuickAdd.kt).
        QuickAddButton(
            controller, onPick = { quickAdd = it },
            modifier = Modifier.align(Alignment.BottomEnd).then(if (dockEnabled) Modifier else Modifier.navigationBarsPadding()).padding(16.dp),
        )
        quickAdd?.let { kind -> QuickAddSheet(controller, kind, onDismiss = { quickAdd = null }) }
    }
}

// region jumps within the column

/**
 * The left column is one scrolling Column; the Goals and Projects sections report where they
 * sit in it, and the agenda's jump buttons, the headers' up arrows and the Back to top row
 * animate the scroll there. Positions are content coordinates, which the scroll offset does not
 * move, so they stay right while the user scrolls.
 */
class ColumnJumps(val scroll: ScrollState, private val scope: CoroutineScope) {
    var goalsY by mutableFloatStateOf(0f)
    var projectsY by mutableFloatStateOf(0f)
    var lastGlanceY by mutableFloatStateOf(0f)
    fun toGoals() { scope.launch { scroll.animateScrollTo(goalsY.roundToInt()) } }
    fun toProjects() { scope.launch { scroll.animateScrollTo(projectsY.roundToInt()) } }
    fun toLastGlance() { scope.launch { scroll.animateScrollTo(lastGlanceY.roundToInt()) } }
    fun toTop() { scope.launch { scroll.animateScrollTo(0) } }
}

@Composable
private fun rememberColumnJumps(): ColumnJumps {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    return remember(scroll, scope) { ColumnJumps(scroll, scope) }
}

/** The up arrow in a section header, for the way back after a jump: the jump buttons' bordered chip, icon only. */
@Composable
private fun TopArrow(jumps: ColumnJumps) {
    val c = GlanceTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape).border(1.dp, c.border, shape).clickable { jumps.toTop() }.padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(GlanceIcons.ArrowUp, contentDescription = "Back to top", tint = c.textSecondary, modifier = Modifier.size(13.dp))
    }
}

/** The bordered row at the foot of the column, in the agenda's "Show more days" style. */
@Composable
private fun BackToTopRow(jumps: ColumnJumps) {
    val c = GlanceTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, c.border, shape).clickable { jumps.toTop() }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GlanceIcons.ArrowUp, null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
        Text("Back to top", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// endregion

@Composable
private fun PlanningHeader(suffix: String, controller: LauncherController) {
    val c = GlanceTheme.colors
    val availability by controller.repository.availability.collectAsState()
    // No padding above the text, like Home's header, so the two wordmarks land at one height.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Wordmark("day", size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(suffix, color = c.textMuted, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        DataStatusLabel(availability, java.time.Instant.now(), onLongPress = { controller.diagnosticsOpen = true })
    }
}

// region month + agenda

@Composable
private fun MonthSection(controller: LauncherController, tasks: List<Task>, routines: List<app.glance.launcher.data.Routine>, projects: List<Project>, use24: Boolean, jumps: SchedJumps, cellAspect: Float = 1f) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val month = controller.planningMonth
    val selected = controller.planningSelectedDay
    // The controller's today, so the highlight moves at midnight with the selection (rollToday).
    val today = controller.today
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    val lead = ((month.dayOfWeek.value - firstDow.value) + 7) % 7
    val gridStart = month.minusDays(lead.toLong())
    val rows = ((lead + month.lengthOfMonth()) + 6) / 7
    val byDate = remember(tasks) { tasks.filter { it.date != null }.groupBy { it.date!! } }
    val deadlinesByDate = remember(tasks, today) { SchedAgenda.deadlinesByDate(tasks, today) }

    GlanceCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).clickable { GlanceIntents.openDay(context, selected) },
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = c.textSecondary, modifier = Modifier.size(28.dp).clickable { controller.planningMonth = month.minusMonths(1) })
            Text("Today", color = c.accent, fontSize = 12.sp, modifier = Modifier.clickable { controller.planningMonth = today.withDayOfMonth(1); controller.planningSelectedDay = today }.padding(horizontal = 8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = c.textSecondary, modifier = Modifier.size(28.dp).clickable { controller.planningMonth = month.plusMonths(1) })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val dow = DayOfWeek.of(((firstDow.value - 1 + i) % 7) + 1)
                Text(
                    dow.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                    color = c.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val date = gridStart.plusDays((r * 7 + col).toLong())
                    DayCell(
                        date = date,
                        inMonth = date.month == month.month,
                        isToday = date == today,
                        isSelected = date == selected,
                        items = byDate[date].orEmpty(),
                        routineStarts = if (date == today) routines.map { it.startMin } else emptyList(),
                        modifier = Modifier.weight(1f),
                        onClick = { controller.planningSelectedDay = date; controller.dialDate = date },
                        onLongClick = { GlanceIntents.openDay(context, date) },
                        aspect = cellAspect,
                        deadlines = deadlinesByDate[date].orEmpty(),
                    )
                }
            }
        }
    }
    // SCHED, as in the app: the rolling agenda starts at the selected day.
    SchedView(controller, tasks, routines, projects, use24, startDay = selected, jumps = jumps)
}

// endregion

// region goals + projects

@Composable
private fun GoalsSection(goals: List<Goal>, projects: List<Project>, tasks: List<Task>, jumps: ColumnJumps) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    SectionHeader("Goals") {
        TopArrow(jumps)
        Spacer(Modifier.width(12.dp))
        Text("Goals", color = c.accent, fontSize = 11.sp, modifier = Modifier.clickable { GlanceIntents.openGoalsPanel(context) })
    }
    Spacer(Modifier.height(8.dp))
    GlanceCard(Modifier.fillMaxWidth()) {
        val active = SchedAgenda.goalsInOrder(goals, projects, tasks)
        for ((i, goal) in active.withIndex()) {
            GoalRow(goal, projects.filter { it.goalId == goal.id }, tasks)
            if (i < active.lastIndex) Spacer(Modifier.height(14.dp))
        }
    }
}

/** dayGLANCE's GoalRing: 44dp ring, stroke 3.5, from 12 o'clock, percentage inside, segmented project bars beside. */
@Composable
private fun GoalRow(goal: Goal, projects: List<Project>, tasks: List<Task>) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val color = hexColor(goal.colorHex)
    val fraction = ProgressMath.goal(goal.id, projects, tasks)
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().clickable { GlanceIntents.openGoal(context, goal.id) }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.38f
                val stroke = 3.5.dp.toPx()
                val tl = Offset(center.x - r, center.y - r)
                drawArc(c.track, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(stroke))
                drawArc(if (fraction >= 1f) c.success else color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("${(fraction * 100).toInt()}%", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(goal.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (projects.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (p in projects) {
                        val pr = ProgressMath.project(p.id, tasks)
                        ProgressBar(pr.fraction, hexColor(p.colorHex), Modifier.weight(1f), height = 4.dp)
                    }
                }
            }
            goal.targetDate?.let { TargetDateText(it, today) }
        }
    }
}

/** A goal's target date under its title, or a project card's at its top right (TargetDateLabel). */
@Composable
private fun TargetDateText(target: LocalDate, today: LocalDate, modifier: Modifier = Modifier.padding(top = 4.dp)) {
    val c = GlanceTheme.colors
    val (text, tone) = TargetDateLabel.of(target, today)
    val col = when (tone) {
        DueTone.DANGER -> c.danger
        DueTone.WARNING -> c.warning
        DueTone.MUTED -> c.textMuted
    }
    Text(text, color = col, fontSize = 11.sp, maxLines = 1, modifier = modifier)
}

@Composable
private fun ProjectsSection(projects: List<Project>, tasks: List<Task>, jumps: ColumnJumps, columns: Int) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    SectionHeader("Projects") {
        TopArrow(jumps)
        Spacer(Modifier.width(12.dp))
        Text("Projects", color = c.accent, fontSize = 11.sp, modifier = Modifier.clickable { GlanceIntents.openProjectsPanel(context) })
    }
    Spacer(Modifier.height(8.dp))
    val rows = SchedAgenda.standaloneProjects(projects).chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in row) {
                    val pr = ProgressMath.project(p.id, tasks)
                    GlanceCard(Modifier.weight(1f).clickable { GlanceIntents.openProject(context, p.id) }, accentTop = hexColor(p.colorHex), padding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
                        // The project's own target date (dayGLANCE #2160) at the top right,
                        // beside the title; the title gives way first when space is short.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            p.targetDate?.let { TargetDateText(it, LocalDate.now(), Modifier.padding(start = 8.dp)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(pr.fraction, c.accent, height = 6.dp)
                        Row(Modifier.padding(top = 4.dp)) {
                            Text("${pr.done}/${pr.total} tasks", color = c.textSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            val pct = pr.fraction?.let { "${(it * 100).toInt()}%" } ?: "–"
                            Text(pct, color = if ((pr.fraction ?: 0f) >= 1f) c.success else c.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// endregion

// region lastGLANCE

@Composable
private fun LastGlanceSection(controller: LauncherController, chores: List<Chore>, people: List<Person>, jumps: ColumnJumps?, compact: Boolean = false) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val nowMin = rememberNowMinute()
    val now = remember(chores, people, nowMin, controller.today) { Instant.now() }
    data class Scored(val chore: Chore, val ratio: Double?, val state: CadenceState)
    val scored = chores.map { ch ->
        val ratio = Cadence.ratio(Cadence.elapsedDays(ch.lastDoneAt, now), ch.targetCadenceDays)
        Scored(ch, ratio, Cadence.state(ratio))
    }
    val attention = scored.filter { it.state == CadenceState.OVERDUE || it.state == CadenceState.DUE_SOON }.sortedByDescending { it.ratio ?: 0.0 }
    val overdueCount = scored.count { it.state == CadenceState.OVERDUE }

    // The arrow only where lastGLANCE follows the rest of the column; in landscape it is its own column.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LastGlanceWordmark(size = 18.dp)
        Spacer(Modifier.weight(1f))
        if (jumps != null) TopArrow(jumps)
    }
    Spacer(Modifier.height(8.dp))
    // Every pin, as the app keeps every pin in view; nothing when none is pinned. A pinned chore
    // that is due also shows under Due soon, as a pin also shows in its own category in the app.
    val pinned = remember(chores, now) { Cadence.pinned(chores, now) }
    if (pinned.isNotEmpty()) {
        // Not the Soon link the flame uses: Pinned shows every pin, so lastGLANCE opens with Soon off.
        SectionHeader("Pinned · ${pinned.size}") {
            Icon(GlanceIcons.Pin, contentDescription = "Open Pinned in lastGLANCE", tint = c.textSecondary, modifier = Modifier.size(14.dp).clickable { GlanceIntents.openPinned(context) })
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (ch in pinned) {
                val el = Cadence.elapsedDays(ch.lastDoneAt, now)
                ChoreRow(ch, Cadence.ratio(el, ch.targetCadenceDays), now, controller, showCategory = true)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    // The flame is the shortcut into lastGLANCE's Soon filter.
    SectionHeader(if (overdueCount > 0) "Due now $overdueCount · due soon ${attention.size - overdueCount}" else "Due soon ${attention.size}") {
        Icon(GlanceIcons.Flame, contentDescription = "Open Soon in lastGLANCE", tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp).clickable { GlanceIntents.openLastGlance(context) })
    }
    Spacer(Modifier.height(8.dp))
    if (attention.isEmpty()) Text("Everything is fine. Nice.", color = c.textMuted, fontSize = 13.sp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in attention.take(8)) ChoreRow(s.chore, s.ratio, now, controller)
    }

    Spacer(Modifier.height(16.dp))
    ComingUpSection(people, controller, compact)

    Spacer(Modifier.height(16.dp))
    val reachOut = people.map { p ->
        val el = Cadence.elapsedDays(p.lastInteractionAt, now) ?: 999.0
        val ratio = Cadence.ratio(el, p.targetCadenceDays)
        Triple(p, el, ratio)
    }.filter { (p, _, ratio) -> !p.isSnoozed(now) && ratio != null && (ratio >= Cadence.ATTENTION_RATIO || (p.unreturned && ratio >= 0.5)) }
        .sortedByDescending { it.third ?: 0.0 }
    SectionHeader("Reach out · ${reachOut.size}") {
        Icon(GlanceIcons.Users, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp).clickable { GlanceIntents.openPeople(context) })
    }
    Spacer(Modifier.height(8.dp))
    if (reachOut.isEmpty()) Text("No one is waiting on a hello.", color = c.textMuted, fontSize = 13.sp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((p, _, ratio) in reachOut.take(5)) PersonRow(p, ratio, now, controller)
    }
}

/**
 * lastGLANCE's ChoreRow: 12dp radius, a 4px stripe in the state colour, and a fill bar behind
 * the content whose width is the cadence ratio (clamped) in the same colour at 10 %. In Pinned
 * ([showCategory]) the subtitle leads with the chore's category, as the app's pinned tiles do.
 */
@Composable
private fun ChoreRow(chore: Chore, ratio: Double?, now: Instant, controller: LauncherController, showCategory: Boolean = false) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val state = Color(Cadence.colorArgb(ratio))
    val shape = RoundedCornerShape(12.dp)
    val fill = ((ratio ?: 0.0).coerceIn(0.0, 1.0)).toFloat()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card, shape)
            .border(1.dp, c.border, shape)
            .drawBehind {
                // Fill bar, then the stripe; both span the row's real height and follow the corners.
                drawRect(state.copy(alpha = 0.1f), size = Size(size.width * fill, size.height))
                drawRect(state.copy(alpha = 0.9f), size = Size(4.dp.toPx(), size.height))
            }
            .clickable { GlanceIntents.openChore(context, chore.id) },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(chore.icon, tint = state, size = 22.dp, fallback = GlanceIcons.Sparkles)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(chore.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val due = Cadence.formatNextDue(chore.lastDoneAt, chore.targetCadenceDays, now)
                Row {
                    if (showCategory && chore.category.isNotEmpty()) Text("${chore.category} · ", color = c.textMuted, fontSize = 12.sp, maxLines = 1)
                    Text(Cadence.formatElapsed(chore.lastDoneAt, now), color = c.textSecondary, fontSize = 12.sp)
                    if (due != null) Text(" - $due", color = c.textSecondary, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                }
            }
            Text(
                "Done", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .border(1.dp, c.border, RoundedCornerShape(8.dp))
                    .clickable { controller.logChoreDone(chore.id, chore.name) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PersonRow(person: Person, ratio: Double?, now: Instant, controller: LauncherController) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val state = Color(Cadence.colorArgb(ratio))
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(12.dp))
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .clickable { GlanceIntents.openPerson(context, person.id) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).background(state.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Text(person.name.take(1), color = state, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(person.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (person.nickname != null) Text("  ${person.nickname}", color = c.textMuted, fontSize = 13.sp)
            }
            val elapsed = if (person.lastInteractionAt == null) "no contact logged yet" else "${channelLabel(person.lastChannel)}${Cadence.formatElapsed(person.lastInteractionAt, now)}"
            Text(elapsed, color = c.textSecondary, fontSize = 12.sp)
            if (person.unreturned) Text("They reached out last", color = Color(0xFFF59E0B), fontSize = 11.sp)
        }
        Text(
            "+ Log", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .border(1.dp, c.border, RoundedCornerShape(8.dp))
                .clickable { controller.logInteraction(person.id, person.name) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * lastGLANCE's "Coming up" card (`PeopleHome.tsx`): the next 30 days of birthdays and
 * anniversaries as square tiles, four to a row, eight at most, soonest first. A tile within a
 * week takes the pink tint. Tapping a tile opens the person, as in the app.
 */
@Composable
private fun ComingUpSection(people: List<Person>, controller: LauncherController, compact: Boolean) {
    // A phone takes the app's own grid, three across and nine at most; the wider tablet column
    // runs four across, eight at most.
    val perRow = if (compact) 3 else COMING_UP_PER_ROW
    val maxTiles = if (compact) 9 else COMING_UP_TILES
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val today = LocalDate.now()
    val upcoming = remember(people, today) {
        people.mapNotNull { p -> p.daysUntilSpecial(today)?.let { d -> if (d <= COMING_UP_DAYS) p to d else null } }
            .sortedWith(compareBy<Pair<Person, Long>> { it.second }.thenBy { it.first.name })
            .take(maxTiles)
    }
    SectionHeader("Coming up · ${upcoming.size}") {
        Icon(GlanceIcons.Cake, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp).clickable { GlanceIntents.openPeople(context) })
    }
    Spacer(Modifier.height(8.dp))
    if (upcoming.isEmpty()) {
        Text("No dates in the next $COMING_UP_DAYS days.", color = c.textMuted, fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in upcoming.chunked(perRow)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((person, days) in row) DateTile(person, days, today, Modifier.weight(1f), padding = if (compact) 8.dp else 10.dp) { GlanceIntents.openPerson(context, person.id) }
                // A row is completed with faint outlines, so the grid reads as a grid rather than a gap.
                repeat(perRow - row.size) { GhostTile(Modifier.weight(1f)) }
            }
        }
    }
}

private const val COMING_UP_DAYS = 30
// The app draws three to a row, nine at most; the launcher's column is wider than a phone's and
// the tiles were running large, so four to a row, eight at most, with the text a step up.
private const val COMING_UP_PER_ROW = 4
private const val COMING_UP_TILES = 8

/** An empty slot in the Coming up grid: the tile's shape as a faint dashed outline, nothing else. */
@Composable
private fun GhostTile(modifier: Modifier) {
    val c = GlanceTheme.colors
    Box(
        modifier.aspectRatio(1f).drawBehind {
            val w = 1.dp.toPx()
            val dash = 3.dp.toPx()
            drawRoundRect(
                c.border.copy(alpha = 0.55f),
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
            )
        },
    )
}

/** `DateTile`: aspect-square, rounded-xl, p-2.5; day number top left, countdown top right, name and kind below. */
@Composable
private fun DateTile(person: Person, daysUntil: Long, today: LocalDate, modifier: Modifier, padding: Dp = 10.dp, onOpen: () -> Unit) {
    val c = GlanceTheme.colors
    val soon = daysUntil <= 7
    val pink = Color(0xFFF472B6)
    val on = today.plusDays(daysUntil)
    val shape = RoundedCornerShape(12.dp)
    val countdown = when (daysUntil) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in ${daysUntil}d"
    }
    Column(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (soon) pink.copy(alpha = 0.1f) else c.card, shape)
            .border(1.dp, if (soon) pink.copy(alpha = 0.4f) else c.border, shape)
            .clickable(onClick = onOpen)
            .padding(padding),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    on.dayOfMonth.toString(), color = if (soon) pink else c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                )
                Text(
                    on.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()),
                    color = c.textMuted, fontSize = 11.sp, letterSpacing = 0.3.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(countdown, color = c.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Column(Modifier.fillMaxWidth()) {
            Text(
                person.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                person.specialLabel, color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun channelLabel(channel: String?): String = when (channel) {
    "call" -> "call · "
    "video" -> "video · "
    "text" -> "text · "
    "email" -> "email · "
    "in_person" -> "in person · "
    else -> ""
}

// endregion
