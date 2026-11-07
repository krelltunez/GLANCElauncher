@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.glance.launcher.ui.planning

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.daydial.DialGlyphs
import app.glance.daydial.formatClock
import app.glance.launcher.data.Goal
import app.glance.launcher.data.Project
import app.glance.launcher.data.Routine
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskColors
import app.glance.launcher.data.TaskSource
import app.glance.launcher.intents.GlanceIntents
import app.glance.launcher.ui.GlanceIcons
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.home.rememberNowMinute
import app.glance.launcher.ui.theme.GlanceTheme
import app.glance.launcher.ui.theme.hexColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * A direct port of dayGLANCE's SCHED view (`src/components/sched/SchedView.jsx`,
 * `SchedTaskCard.jsx`, `SchedDayExtras.jsx`, `SchedFilterPopup.jsx`): the rolling day-grouped
 * agenda from the selected day, with its Overdue section, deadline cards, today's routine pills,
 * the filter sheet and the two view toggles. Tailwind metrics are carried over one to one
 * (text-xs 12sp, text-sm 14sp, text-base 16sp; gap-1.5 6dp, gap-2 8dp, gap-3 12dp; px-3 12dp,
 * py-2.5 10dp; rounded-xl 12dp) and the palette uses the same Tailwind stops.
 *
 * What differs is only what the launcher cannot do in place: tapping a card opens the task in
 * dayGLANCE instead of the in-app editor, the notes button and link icon open the task and the
 * link, and the two actions that need a picker in the app (a deadline card's calendar button and
 * a routine pill's long-press) schedule straight away. Pieces whose data the launcher does not
 * hold (recurrence, stars, Do sessions, plan history, subtasks, day notes, focus log) are absent.
 */

private val Blue500 = Color(0xFF3B82F6)
private val Blue600 = Color(0xFF2563EB)
private val Blue400 = Color(0xFF60A5FA)
private val Amber500 = Color(0xFFF59E0B)
private val Red500 = Color(0xFFEF4444)
private val Red400 = Color(0xFFF87171)
private val Green500 = Color(0xFF22C55E)
private val Gray600 = Color(0xFF4B5563)
private val Stone300 = Color(0xFFD6D3D1)
private val Teal700 = Color(0xFF0F766E)
private val Teal600 = Color(0xFF0D9488)
private val Teal100 = Color(0xFFCCFBF1)

private val META_ICON = 14.dp
private val META_HIT = 26.dp // p-1.5 -m-1.5 around a 14px icon
private val ACTION_ICON = 18.dp
private val CardShape = RoundedCornerShape(12.dp)

/**
 * Jump targets below the agenda, offered as buttons at the right end of the controls row.
 * [toLastGlance] is null where lastGLANCE is another column; [compact] tightens the chips so
 * Filter plus three arrowed chips fit a phone's one line with margin.
 */
class SchedJumps(val toGoals: () -> Unit, val toProjects: () -> Unit, val toLastGlance: (() -> Unit)? = null, val compact: Boolean = false)

/** SCHED, starting at [startDay]. [tasks] is the whole task list; the view does its own grouping. */
@Composable
fun SchedView(
    controller: LauncherController,
    tasks: List<Task>,
    routines: List<Routine>,
    projects: List<Project>,
    use24: Boolean,
    startDay: LocalDate,
    modifier: Modifier = Modifier,
    jumps: SchedJumps? = null,
) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val nowMin = rememberNowMinute()
    val today = LocalDate.now()
    val now = remember(nowMin, today) { LocalDateTime.of(today, LocalTime.of(nowMin / 60, nowMin % 60)) }
    // A remembered project that has since been deleted is ignored rather than emptying the
    // agenda; it stays in the saved selection, so it returns if the project list was only loading.
    val filters = remember(controller.schedFilters, projects) { controller.schedFilters.live(projects.map { it.id }.toSet()) }

    val window = remember(startDay, controller.schedDaysShown) { SchedAgenda.window(startDay, controller.schedDaysShown) }
    val days = remember(tasks, window, filters, controller.schedHideCompleted, controller.schedHidePastEvents, now) {
        SchedAgenda.days(tasks, window, today, filters, controller.schedHideCompleted, controller.schedHidePastEvents, now)
    }
    val todayRoutines = routines // the launcher's routines are today's set
    val visibleDays = SchedAgenda.visible(days, controller.schedShowEmptyDays, today, todayRoutines.isNotEmpty())
    val overdue = remember(tasks, today, startDay, filters, now) { SchedAgenda.overdue(tasks, today, startDay, filters, now) }

    fun dayLabel(d: LocalDate): String {
        val base = d.format(DateTimeFormatter.ofPattern("EEEE, MMM d", locale))
        return when (d) {
            today -> "Today · $base"
            today.plusDays(1) -> "Tomorrow · $base"
            else -> base
        }
    }

    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Controls: Filter (the app's Empty days toggle lives in its sheet here), then the jumps.
        // A flow row only as a safety net for very narrow phones; the chips are sized to one line.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val pad = if (jumps?.compact == true) 8.dp else 10.dp
            ControlButton(
                icon = GlanceIcons.ListFilter,
                label = "Filter" + if (filters.active) " · ${filters.count}" else "",
                active = filters.active,
                onClick = { controller.schedFiltersOpen = true },
                padH = pad,
            )
            if (jumps != null) {
                // Launcher addition: the agenda can run long, and Goals and Projects sit below it.
                Spacer(Modifier.weight(1f))
                ControlButton(icon = GlanceIcons.ArrowDown, label = "Goals", active = false, onClick = jumps.toGoals, padH = pad)
                ControlButton(icon = GlanceIcons.ArrowDown, label = "Projects", active = false, onClick = jumps.toProjects, padH = pad)
                if (jumps.toLastGlance != null) ControlButton(icon = GlanceIcons.ArrowDown, label = "lastGLANCE", active = false, onClick = jumps.toLastGlance, padH = pad)
            }
        }

        // Overdue: incomplete tasks from before the visible window
        if (overdue.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupHeader("Overdue · ${overdue.size}", Amber500)
                for (row in overdue) {
                    SchedTaskCard(
                        task = row.task, controller = controller, projects = projects, use24 = use24, now = now,
                        showOverdueDate = true,
                        // An Inbox deadline row is an inbox task wearing the overdue marker.
                        isInbox = row.deadlineKind,
                        onSchedule = { controller.scheduleTaskToday(it) },
                    )
                }
            }
        }

        // Day groups
        for (day in visibleDays) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupHeader(
                    dayLabel(day.date),
                    if (day.date == today) Blue500 else c.textSecondary,
                    // The app's header carries the day's note and focus-log buttons; here the header opens the day.
                    Modifier.clickable { GlanceIntents.openDay(context, day.date) },
                )
                for (t in day.deadlineTasks) SchedDeadlineCard(t, day.date, controller, projects, now)
                if (day.date == today) SchedRoutinePills(todayRoutines, today, controller, use24)
                if (day.tasks.isNotEmpty()) {
                    for (t in day.tasks) SchedTaskCard(t, controller, projects, use24, now)
                } else {
                    DashedButton(
                        icon = GlanceIcons.Plus, label = "Add task", iconSize = 13.dp,
                        onClick = { GlanceIntents.openDay(context, day.date) },
                    )
                }
            }
        }

        if (visibleDays.isEmpty()) {
            Text(
                if (filters.active) "No tasks match the current filters." else "Nothing scheduled in this window.",
                color = c.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
        }

        // Extend window
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .border(1.dp, c.border, CardShape)
                .clickable { controller.schedDaysShown += SchedAgenda.LOAD_MORE_DAYS }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GlanceIcons.ChevronDown, null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
            Text("Show ${SchedAgenda.LOAD_MORE_DAYS} more days", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GroupHeader(label: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        label.uppercase(Locale.getDefault()), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp,
        modifier = modifier.padding(top = 4.dp),
    )
}

/** The small bordered control (Filter, Empty days): text-xs font-medium px-2.5 py-1.5 rounded-lg border. */
@Composable
private fun ControlButton(icon: ImageVector?, label: String, active: Boolean, onClick: () -> Unit, padH: Dp = 10.dp) {
    val c = GlanceTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val fg = if (active) Color.White else c.textSecondary
    Row(
        Modifier
            .clip(shape)
            .background(if (active) Blue600 else Color.Transparent, shape)
            .border(1.dp, if (active) Blue600 else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = padH, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** The dashed "Add task" row on an empty day. */
@Composable
private fun DashedButton(icon: ImageVector, label: String, iconSize: Dp, onClick: () -> Unit) {
    val c = GlanceTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .dashedBorder(c.border, 12.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(iconSize))
        Text(label, color = c.textSecondary, fontSize = 12.sp)
    }
}

// region cards

/**
 * `SchedTaskCard`: two lines, title then meta row. [showOverdueDate] prints the row's own date
 * (the Overdue section); [isInbox] marks an Inbox deadline row; [onSchedule] adds the
 * schedule-for-today action and hides postpone.
 */
@Composable
private fun SchedTaskCard(
    task: Task,
    controller: LauncherController,
    projects: List<Project>,
    use24: Boolean,
    now: LocalDateTime,
    showOverdueDate: Boolean = false,
    isInbox: Boolean = false,
    onSchedule: ((Task) -> Unit)? = null,
) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val hex = hexColor(task.colorHex)
    val isEvent = task.source == TaskSource.CALENDAR
    val state = SchedAgenda.cardState(task, now, use24)
    val tags = task.tags
    val project = projects.firstOrNull { it.id == task.projectId }
    val linkUrl = remember(task.title, task.notes) { URL_REGEX.find(task.title)?.value ?: URL_REGEX.find(task.notes)?.value }
    val hasNotes = task.notes.isNotBlank()
    val canPostpone = !isEvent && !task.completed && task.date != null && !isInbox && onSchedule == null
    val monthDay = DateTimeFormatter.ofPattern("MMM d", locale)

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (task.completed || state.finishedEvent) 0.55f else 1f)
            .clip(CardShape)
            .background(c.card, CardShape)
            .border(1.dp, c.border, CardShape)
            .leftBorder(hex)
            // In the app an event's card is inert; the launcher's only affordance is the deep link, so it opens the day.
            .clickable { if (isEvent) GlanceIntents.openDay(context, task.date ?: LocalDate.now()) else GlanceIntents.openTask(context, task.id) }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isEvent) {
            // p-1 -m-0.5 around a 22px glyph: a 26dp hit that lays out as 26.
            Box(Modifier.size(26.dp).clickable(enabled = task.completable) { controller.toggleTask(task) }, contentAlignment = Alignment.Center) {
                if (task.completed) Icon(GlanceIcons.CheckCircle, "Mark incomplete", tint = Green500, modifier = Modifier.size(22.dp))
                else Icon(GlanceIcons.Circle, "Mark complete", tint = if (c.isDark) Gray600 else Stone300, modifier = Modifier.size(22.dp))
            }
        } else {
            // A read-only calendar event: a calendar glyph where the checkbox would be, so the slot
            // says "nothing to tick off". The app's SCHED leaves the slot empty (docs/decisions.md, decision 22).
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Icon(GlanceIcons.Calendar, "Calendar event", tint = c.textMuted, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                task.cleanTitle, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Meta row: always rendered on editable tasks so cards stay uniform.
            if (!isEvent || state.timeLabel.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ownDate = task.deadline ?: task.date
                    if (showOverdueDate && ownDate != null) {
                        Text(ownDate.format(monthDay), color = Amber500, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    if (state.inProgress) NowBadge()
                    if (state.timeLabel.isNotEmpty()) {
                        Text(
                            state.timeLabel, fontSize = 14.sp,
                            color = if (state.pastDue) Red400 else c.textSecondary,
                            fontWeight = if (state.pastDue) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                    if (!isEvent) {
                        MetaHit(onClick = { GlanceIntents.openTask(context, task.id) }) {
                            Icon(
                                GlanceIcons.FileText, if (hasNotes) "Notes" else "Add notes",
                                tint = c.textSecondary, modifier = Modifier.size(META_ICON).alpha(if (hasNotes) 0.7f else 0.35f),
                            )
                        }
                    }
                    // Deadline: the inbox's button, on inbox cards.
                    if (isInbox && !isEvent && !task.completed) {
                        val deadline = task.deadline
                        val tint = if (deadline != null) (if (c.isDark) Blue400 else Blue600) else c.textSecondary
                        Row(
                            Modifier.clickable { GlanceIntents.openTask(context, task.id) }.alpha(if (deadline != null) 1f else 0.35f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(GlanceIcons.Calendar, "Deadline", tint = tint, modifier = Modifier.size(META_ICON))
                            if (deadline != null) Text(deadlineLabel(deadline, now.toLocalDate(), monthDay), color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                    if (linkUrl != null) {
                        MetaHit(onClick = { openUrl(context, linkUrl) }) {
                            Icon(GlanceIcons.ExternalLink, linkUrl, tint = c.textSecondary, modifier = Modifier.size(META_ICON).alpha(0.7f))
                        }
                    }
                    if (tags.isNotEmpty()) {
                        Text(
                            tags.joinToString(" ") { "#$it" }, color = c.textSecondary, fontSize = 14.sp, fontStyle = FontStyle.Italic,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).alpha(0.75f),
                        )
                    }
                    if (project != null) {
                        if (tags.isEmpty()) Spacer(Modifier.weight(1f))
                        ProjectPill(project, 12.sp)
                    }
                }
            }
        }
        if (onSchedule != null && !task.completed && !isEvent) {
            ActionButton(GlanceIcons.CalendarPlus, "Schedule for today") { onSchedule(task) }
        }
        if (canPostpone) {
            ActionButton(GlanceIcons.SkipForward, "Postpone to tomorrow") { controller.postponeTask(task) }
        }
    }
}

/**
 * `SchedDeadlineCard`: an Inbox task on its deadline day, all-day look with a dashed border. The
 * card opens the task; the calendar button puts it on that day's clock (the app opens a time
 * picker defaulting to 09:00; the launcher schedules at 09:00, or the next quarter hour today).
 */
@Composable
private fun SchedDeadlineCard(task: Task, date: LocalDate, controller: LauncherController, projects: List<Project>, now: LocalDateTime) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val hex = hexColor(task.colorHex)
    val project = projects.firstOrNull { it.id == task.projectId }
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (task.completed) 0.55f else 1f)
            .clip(CardShape)
            .background(c.card, CardShape)
            .dashedBorder(c.border, 12.dp)
            .leftBorder(hex)
            .clickable { GlanceIntents.openTask(context, task.id) }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The deadline glyph (a calendar with a clock), as on Home's All day & deadlines pill; the
        // app's deadline card has no leading glyph (docs/decisions.md, decision 22).
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Icon(DialGlyphs.CalendarClock, "Deadline", tint = c.textMuted, modifier = Modifier.size(22.dp))
        }
        // Type sizes match the task and event cards around it (title 16, meta 14).
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                task.cleanTitle, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Deadline", color = Red400, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (task.durationMin > 0) Text("${task.durationMin}m", color = c.textSecondary, fontSize = 14.sp)
                if (project != null) {
                    Spacer(Modifier.weight(1f))
                    ProjectPill(project, 12.sp)
                }
            }
        }
        if (!task.completed) {
            val today = now.toLocalDate()
            val startMin = if (date == today) maxOf(9 * 60, SchedAgenda.nextSlot(now.hour * 60 + now.minute)).coerceAtMost(23 * 60 + 45) else 9 * 60
            Box(Modifier.size(META_ICON)) {
                Box(
                    Modifier.requiredSize(22.dp).clickable { controller.scheduleDeadlineTask(task, date, startMin) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(GlanceIcons.CalendarPlus, "Deadline, tap to schedule", tint = c.textSecondary, modifier = Modifier.size(META_ICON).alpha(0.6f))
                }
            }
        }
    }
}

/** `SchedRoutinePills`: today's routines as the familiar teal pills; tap toggles completion. Unplaced first, then by time. */
@Composable
private fun SchedRoutinePills(routines: List<Routine>, today: LocalDate, controller: LauncherController, use24: Boolean) {
    if (routines.isEmpty()) return
    val c = GlanceTheme.colors
    val sorted = remember(routines) { routines.sortedBy { it.startMin } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for (r in sorted) {
            val done = today in r.completedDates
            Text(
                "${formatClock(r.startMin, use24)} · ${r.name}",
                color = if (c.isDark) Teal100 else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .alpha(if (done) 0.75f else 1f)
                    .clip(RoundedCornerShape(50))
                    .background((if (c.isDark) Teal700 else Teal600).copy(alpha = 0.8f), RoundedCornerShape(50))
                    .clickable { controller.toggleRoutineOn(r.id, today) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ProjectPill(project: Project, size: androidx.compose.ui.unit.TextUnit) {
    val c = GlanceTheme.colors
    val pc = hexColor(project.colorHex)
    Text(
        project.title, color = pc, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 192.dp)
            .background(pc.copy(alpha = if (c.isDark) 0.22f else 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The pulsing red dot and "Now" of a card whose window contains the current minute. */
@Composable
private fun NowBadge() {
    val pulse = rememberInfiniteTransition(label = "now")
    val alpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )
    Row(Modifier.alpha(alpha), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(Red500, CircleShape))
        Text("NOW", color = Red500, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    }
}

/** A meta-row control: lays out at the icon's 14dp but takes taps on a 26dp square (p-1.5 -m-1.5). */
@Composable
private fun MetaHit(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(META_ICON)) {
        Box(Modifier.requiredSize(META_HIT).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
    }
}

/** A trailing card action (p-2 -my-1 around an 18px icon): 34 wide, lays out 26 tall. */
@Composable
private fun ActionButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = GlanceTheme.colors
    Box(Modifier.width(34.dp).height(26.dp)) {
        Box(
            Modifier.requiredSize(34.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = c.textSecondary, modifier = Modifier.size(ACTION_ICON).alpha(0.6f))
        }
    }
}

/** `formatDeadlineDate`: Today, Tomorrow, else "MMM d". */
private fun deadlineLabel(deadline: LocalDate, today: LocalDate, monthDay: DateTimeFormatter): String = when (deadline) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> deadline.format(monthDay)
}

private val URL_REGEX = Regex("https?://[^\\s<>\"')\\]]+")

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

/** `border-left: 4px solid`; the row is clipped to its shape, so a plain rectangle follows the corners. */
private fun Modifier.leftBorder(color: Color): Modifier = this.drawBehind {
    drawRect(color, size = Size(4.dp.toPx(), size.height))
}

/** Tailwind's `border border-dashed` on a rounded box: a 1dp stroke in 3dp dashes. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = this.drawBehind {
    val w = 1.dp.toPx()
    val dash = 3.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

// endregion

// region filter sheet

/**
 * `SchedFilterPopup`: colours, tags and projects (grouped by goal) plus the completed and
 * past-events view toggles. Options come from the agenda's own window, as in the app. The
 * recurring-tasks section is absent because the launcher holds no recurrence.
 */
@Composable
fun SchedFilterSheet(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val repo = controller.repository
    val tasks by repo.tasks.collectAsState()
    val projects by repo.projects.collectAsState()
    val goals by repo.goals.collectAsState()
    val filters = controller.schedFilters
    val window = remember(controller.planningSelectedDay, controller.schedDaysShown) {
        SchedAgenda.window(controller.planningSelectedDay, controller.schedDaysShown)
    }
    val inWindow = remember(tasks, window) { window.flatMap { d -> SchedAgenda.tasksOn(tasks, d) } }
    val availableColors = remember(inWindow) { inWindow.map { it.colorHex.lowercase() }.toSet() }
    val availableTags = remember(inWindow) { inWindow.flatMap { it.tags }.toSortedSet().toList() }
    val colorOptions = TaskColors.all.filter { it.lowercase() in availableColors }
    val groups = remember(projects, goals) { SchedAgenda.projectGroups(projects, goals) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { controller.schedFiltersOpen = false },
    ) {
        val maxH = maxHeight * 0.75f
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .heightIn(max = maxH)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.card, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filter tasks", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (filters.active) {
                    Text(
                        "Clear all", color = Blue500, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { controller.schedFilters = SchedFilters.NONE }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).clickable { controller.schedFiltersOpen = false }, contentAlignment = Alignment.Center) {
                    Icon(GlanceIcons.Close, "Close filters", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            if (colorOptions.isNotEmpty()) {
                FilterSection("Colors") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (hex in colorOptions) {
                            val selected = hex.lowercase() in filters.colors
                            // w-7 h-7 rounded-full; ring-2 ring-offset-2 ring-blue-500 scale-110 when selected.
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .then(if (selected) Modifier.scale(1.1f).border(2.dp, Blue500, CircleShape) else Modifier)
                                    .clickable { controller.schedFilters = filters.toggleColor(hex) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(28.dp).alpha(if (selected) 1f else 0.8f).background(hexColor(hex), CircleShape))
                            }
                        }
                    }
                }
            }

            if (availableTags.isNotEmpty()) {
                FilterSection("Tags") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (tag in availableTags) FilterChip("#$tag", tag in filters.tags) { controller.schedFilters = filters.toggleTag(tag) }
                    }
                }
            }

            if (groups.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Projects", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    for (g in groups) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                g.label.uppercase(Locale.getDefault()), color = c.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.25.sp, modifier = Modifier.alpha(0.6f),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (p in g.projects) FilterChip(p.title, p.id in filters.projectIds) { controller.schedFilters = filters.toggleProject(p.id) }
                            }
                        }
                    }
                    FilterChip("No project", SchedFilters.NO_PROJECT in filters.projectIds) { controller.schedFilters = filters.toggleProject(SchedFilters.NO_PROJECT) }
                }
            }

            // The app's Empty days toggle is a button beside Filter; the launcher's controls row has
            // the jump buttons there instead, so the toggle lives in the sheet with its siblings.
            FilterSection("Empty days") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("Show", controller.schedShowEmptyDays) { controller.schedShowEmptyDays = true }
                    FilterChip("Hide", !controller.schedShowEmptyDays) { controller.schedShowEmptyDays = false }
                }
            }
            FilterSection("Completed tasks") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("Show", !controller.schedHideCompleted) { controller.schedHideCompleted = false }
                    FilterChip("Hide", controller.schedHideCompleted) { controller.schedHideCompleted = true }
                }
            }
            FilterSection("Past events") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("Show", !controller.schedHidePastEvents) { controller.schedHidePastEvents = false }
                    FilterChip("Hide", controller.schedHidePastEvents) { controller.schedHidePastEvents = true }
                }
            }

            if (colorOptions.isEmpty() && availableTags.isEmpty() && groups.isEmpty()) {
                Text("Nothing to filter yet. Schedule some tasks first.", color = c.textSecondary, fontSize = 14.sp)
            }
            Text(
                "Selections within a section match any; sections combine.",
                color = c.textSecondary, fontSize = 11.sp, modifier = Modifier.alpha(if (c.isDark) 0.5f else 0.6f),
            )
        }
    }
}

@Composable
private fun FilterSection(label: String, content: @Composable () -> Unit) {
    val c = GlanceTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

/** px-2.5 py-1 rounded-full text-xs font-medium border; selected is blue-600 with white text. */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = GlanceTheme.colors
    val shape = RoundedCornerShape(50)
    Text(
        label, color = if (selected) Color.White else c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Blue600 else Color.Transparent, shape)
            .border(1.dp, if (selected) Blue600 else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// endregion
