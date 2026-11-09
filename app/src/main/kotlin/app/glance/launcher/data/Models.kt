package app.glance.launcher.data

import java.time.Instant
import java.time.LocalDate

/** Where a task came from; read-only items cannot be completed from the launcher. */
enum class TaskSource { DAYGLANCE, CALENDAR }

/**
 * A dayGLANCE task as the launcher sees it. Times are minutes past midnight and dates are
 * local, mirroring dayGLANCE's `"HH:MM"` and `"YYYY-MM-DD"` strings. [date] is null for an
 * Inbox (unscheduled) task. [colorHex] is one of dayGLANCE's nine task colours.
 */
data class Task(
    val id: String,
    val title: String,
    val date: LocalDate?,
    val startMin: Int?,
    val durationMin: Int = 30,
    val isAllDay: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    val colorHex: String = TaskColors.BLUE,
    val priority: Int = 0,
    val notes: String = "",
    val projectId: String? = null,
    val deadline: LocalDate? = null,
    val source: TaskSource = TaskSource.DAYGLANCE,
    /** dayGLANCE's energy axis: "effort" (default) or "restore". */
    val energy: String? = null,
) {
    val isRestore: Boolean get() = energy == "restore" || (energy == null && RESTORE_WORDS.any { w -> cleanTitle.lowercase().split(' ', ':').contains(w) })
    val isScheduled: Boolean get() = date != null && (startMin != null || isAllDay)
    val completable: Boolean get() = source != TaskSource.CALENDAR
    val tags: List<String>
        get() = Regex("(?<=^|\\s)#([\\p{L}\\p{N}_/-]+)").findAll(title).map { it.groupValues[1].lowercase() }.toList()
    val cleanTitle: String
        get() = title.replace(Regex("(?<=^|\\s)#[\\p{L}\\p{N}_/-]+"), "").replace(Regex("\\s{2,}"), " ").trim()
}

/** Restore keywords, a short cut of dayGLANCE's energy-axis list, used when a task has no explicit energy. */
val RESTORE_WORDS = setOf("walk", "rest", "nap", "read", "lunch", "dinner", "breakfast", "stretch", "yoga", "meditate", "break", "coffee")

/** dayGLANCE's nine picker colours, same in light and dark. */
object TaskColors {
    const val BLUE = "#3b82f6"
    const val PURPLE = "#a855f7"
    const val GREEN = "#22c55e"
    const val ORANGE = "#f97316"
    const val PINK = "#ec4899"
    const val INDIGO = "#6366f1"
    const val RED = "#ef4444"
    const val TEAL = "#14b8a6"
    const val YELLOW = "#eab308"
    val all = listOf(BLUE, PURPLE, GREEN, ORANGE, PINK, INDIGO, RED, TEAL, YELLOW)
}

/**
 * A goal's or project's status as dayGLANCE sends it. ON_HOLD is a paused project
 * (dayGLANCE's `on-hold`, decision 78): still open, listed after the active ones,
 * counted toward its goal's progress, but with no target-date warning.
 */
enum class GoalStatus {
    ACTIVE, ON_HOLD, COMPLETED, ARCHIVED;

    /** Neither completed nor archived: active, or on hold. */
    val isOpen: Boolean get() = this == ACTIVE || this == ON_HOLD
}

data class Goal(
    val id: String,
    val title: String,
    val description: String = "",
    val colorHex: String = TaskColors.BLUE,
    val targetDate: LocalDate? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
)

data class Project(
    val id: String,
    val title: String,
    val goalId: String? = null,
    val colorHex: String = TaskColors.BLUE,
    val description: String = "",
    val status: GoalStatus = GoalStatus.ACTIVE,
    /** The project's own target date (dayGLANCE #2160); null when unset or from an older dayGLANCE. */
    val targetDate: LocalDate? = null,
)

/** A routine: a short recurring ritual, drawn in the dial's outer band. */
data class Routine(
    val id: String,
    val name: String,
    val startMin: Int,
    val endMin: Int,
    val completedDates: Set<LocalDate> = emptySet(),
)

/** A frame: a named stretch of the day the user plans inside of. */
data class Frame(
    val id: String,
    val name: String,
    val startMin: Int,
    val endMin: Int,
    val colorHex: String = "#c8c8d2",
    /** Weekdays the frame applies to (java.time DayOfWeek values 1..7). Empty means every day. */
    val days: Set<Int> = emptySet(),
    /**
     * The exact days the frame applies to, when known: dayGLANCE's provider lists frames per day
     * (exceptions and single-date frames included), so the launcher need not guess from weekdays.
     * Null means "use [days]".
     */
    val dates: Set<LocalDate>? = null,
    /**
     * Its free time on each day, as minute spans, as dayGLANCE computes it (the snapshot's
     * `slots`): what the dial's "available" reads. Null when the build sent none.
     */
    val slotsByDate: Map<LocalDate, List<Pair<Int, Int>>>? = null,
)

/** Settings read from dayGLANCE that shape the dial: the awake window and sun times. */
data class DayShape(
    val dayStartMin: Int,
    val dayStopMin: Int,
    val sunriseMin: Int,
    val sunsetMin: Int,
    val use24HourClock: Boolean,
    /** dayGLANCE's weather location, for the moon and solar elevation; null until the app shares one. */
    val lat: Double? = null,
    val lon: Double? = null,
    /** The dial's alarm-mark prefs as dayGLANCE keeps them; null means the app's defaults. */
    val dialAlarm: AlarmPrefs? = null,
    /** The dial's layer toggles as dayGLANCE's dial Layers panel sets them on this device; all on by default. */
    val dialLayers: DialLayers = DialLayers(),
)

/**
 * Which dial layers draw, following dayGLANCE's dial on this device (docs/decisions.md, decision 57).
 * Focus, the app's sixth layer, has no data here.
 */
data class DialLayers(
    /** Sunrise and sunset marks, the daylight band and the moon. */
    val solar: Boolean = true,
    /** The temperature ring. */
    val weather: Boolean = true,
    /** Imported calendar events; the user's own tasks, Obsidian's included, always draw. */
    val calendars: Boolean = true,
    val routines: Boolean = true,
    val frames: Boolean = true,
) {
    /** The tasks the dial draws: off, the calendars layer drops imported events and nothing else. */
    fun dialTasks(tasks: List<Task>): List<Task> =
        if (calendars) tasks else tasks.filter { it.source != TaskSource.CALENDAR }
}

/** Whether the next alarm rides the dial, and from which minute of day tomorrow's shows. */
data class AlarmPrefs(val on: Boolean = true, val fromMin: Int = 18 * 60)

/** One forecast day as dayGLANCE's snapshot carries it: the hours its dial's weather ring reads. */
data class WeatherDay(
    val date: LocalDate,
    /** "fahrenheit" or "celsius": the unit the temperatures are already in. */
    val unit: String,
    val hours: Map<Int, app.glance.daydial.HourWeather>,
)

// region lastGLANCE

enum class CadenceState { NONE, FINE, DUE_SOON, OVERDUE }

/**
 * A lastGLANCE chore. [elapsedDays] is derived from [lastDoneAt]; the state follows the
 * lastGLANCE rules: ratio = elapsed / cadence, fine below 0.75, due soon at 0.75, overdue at 1.
 */
data class Chore(
    val id: String,
    val name: String,
    val category: String,
    val targetCadenceDays: Int? = null,
    val lastDoneAt: Instant? = null,
    val icon: String? = null,
    /** Pinned and in season, as lastGLANCE's Pinned row shows it; false from a lastGLANCE that predates pinning. */
    val pinned: Boolean = false,
)

data class Person(
    val id: String,
    val name: String,
    val nickname: String? = null,
    val circle: String = "",
    val targetCadenceDays: Int? = null,
    val lastInteractionAt: Instant? = null,
    val lastChannel: String? = null,
    /** They reached out last and you have not replied. */
    val unreturned: Boolean = false,
    /** A birthday or anniversary (month and day); lastGLANCE lists it under "Coming up" within 30 days. */
    val specialDate: java.time.MonthDay? = null,
    val specialLabel: String = "Birthday",
    /** lastGLANCE hides a snoozed person from Reach out until this moment; null when not snoozed. */
    val snoozedUntil: Instant? = null,
) {
    fun isSnoozed(now: Instant): Boolean = snoozedUntil?.isAfter(now) == true

    /** Days until the next occurrence of [specialDate] from [today], or null without one. */
    fun daysUntilSpecial(today: LocalDate): Long? {
        val md = specialDate ?: return null
        var next = md.atYear(today.year)
        if (next.isBefore(today)) next = md.atYear(today.year + 1)
        return java.time.temporal.ChronoUnit.DAYS.between(today, next)
    }
}

object Cadence {
    const val ATTENTION_RATIO = 0.75

    fun elapsedDays(last: Instant?, now: Instant): Double? {
        if (last == null) return null
        val minutes = java.time.Duration.between(last, now).toMinutes().coerceAtLeast(0)
        return Math.round(minutes / 1440.0 * 10.0) / 10.0
    }

    fun ratio(elapsedDays: Double?, cadenceDays: Int?): Double? {
        if (elapsedDays == null || cadenceDays == null || cadenceDays <= 0) return null
        return elapsedDays / cadenceDays
    }

    /**
     * lastGLANCE's Pinned row (`collectPinned`): the pinned chores, most overdue first by
     * [ratio], chores without one (no cadence, or never done) last, ties by name.
     */
    fun pinned(chores: List<Chore>, now: Instant): List<Chore> {
        val ratios = chores.filter { it.pinned }.map { it to ratio(elapsedDays(it.lastDoneAt, now), it.targetCadenceDays) }
        return ratios.sortedWith(
            compareBy<Pair<Chore, Double?>> { it.second == null }
                .thenByDescending { it.second ?: 0.0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.name },
        ).map { it.first }
    }

    fun state(ratio: Double?): CadenceState = when {
        ratio == null -> CadenceState.NONE
        ratio >= 1.0 -> CadenceState.OVERDUE
        ratio >= ATTENTION_RATIO -> CadenceState.DUE_SOON
        else -> CadenceState.FINE
    }

    /**
     * lastGLANCE's continuous colour: green to amber across ratio 0..1, amber to red across 1..2.
     * Returns an ARGB int.
     */
    fun colorArgb(ratio: Double?): Long {
        if (ratio == null) return 0xFF64748B
        val green = Triple(0x22, 0xc5, 0x5e)
        val amber = Triple(0xf5, 0x9e, 0x0b)
        val red = Triple(0xef, 0x44, 0x44)
        fun blend(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>, t: Double): Long {
            val tt = t.coerceIn(0.0, 1.0)
            val r = (a.first + (b.first - a.first) * tt).toInt()
            val g = (a.second + (b.second - a.second) * tt).toInt()
            val bl = (a.third + (b.third - a.third) * tt).toInt()
            return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or bl.toLong()
        }
        return if (ratio <= 1.0) blend(green, amber, ratio) else blend(amber, red, ratio - 1.0)
    }

    /**
     * "never", "just now", "{n}m ago", "{h}h ago", "yesterday", "{n}d ago": lastGLANCE's
     * `formatElapsed`. Hours while under a day, even across midnight; days count by the calendar.
     */
    fun formatElapsed(last: Instant?, now: Instant, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        if (last == null) return "never"
        val minutes = java.time.Duration.between(last, now).toMinutes()
        if (minutes < 1) return "just now"
        if (minutes < 60) return "${minutes}m ago"
        val lastDate = last.atZone(zone).toLocalDate()
        val nowDate = now.atZone(zone).toLocalDate()
        val dayDiff = java.time.temporal.ChronoUnit.DAYS.between(lastDate, nowDate)
        if (dayDiff == 0L || (elapsedDays(last, now) ?: 0.0) < 1.0) return "${minutes / 60}h ago"
        if (dayDiff == 1L) return "yesterday"
        return "${dayDiff}d ago"
    }

    /**
     * "due now", "today", "tomorrow", "in {n}d": lastGLANCE's `formatNextDue`. "Due now" goes by
     * time elapsed, the rest by the calendar: the date last done plus the cadence, counted from
     * today's date.
     */
    fun formatNextDue(last: Instant?, cadenceDays: Int?, now: Instant, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
        val elapsed = elapsedDays(last, now)
        if (last == null || elapsed == null || cadenceDays == null) return null
        if (elapsed >= cadenceDays) return "due now"
        val dueDate = last.atZone(zone).toLocalDate().plusDays(cadenceDays.toLong())
        return when (val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), dueDate)) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in ${daysUntil}d"
        }
    }
}

// endregion

/** What quick capture produces before it becomes a task. */
data class TaskDraft(
    val title: String,
    val date: LocalDate? = null,
    val startMin: Int? = null,
    val durationMin: Int = 30,
    val priority: Int = 0,
    val deadline: LocalDate? = null,
    val colorHex: String = TaskColors.BLUE,
    val projectId: String? = null,
)

/** Progress as dayGLANCE computes it: duration-weighted, null when nothing is measurable. */
data class Progress(val completedMinutes: Int, val totalMinutes: Int, val done: Int, val total: Int) {
    val fraction: Float? get() = if (totalMinutes <= 0) null else completedMinutes.toFloat() / totalMinutes
}

object ProgressMath {
    /** Only non-archived tasks with the project count; a task without a duration counts as 30 minutes. */
    fun project(projectId: String, tasks: List<Task>): Progress {
        val mine = tasks.filter { it.projectId == projectId }
        val total = mine.sumOf { if (it.durationMin > 0) it.durationMin else 30 }
        val done = mine.filter { it.completed }.sumOf { if (it.durationMin > 0) it.durationMin else 30 }
        return Progress(done, total, mine.count { it.completed }, mine.size)
    }

    /** Weighted average of the goal's projects, each weighted by its total task minutes. */
    fun goal(goalId: String, projects: List<Project>, tasks: List<Task>): Float {
        val mine = projects.filter { it.goalId == goalId && it.status != GoalStatus.ARCHIVED }
        var weighted = 0f
        var weight = 0
        for (p in mine) {
            val pr = project(p.id, tasks)
            val f = pr.fraction ?: continue
            weighted += f * pr.totalMinutes
            weight += pr.totalMinutes
        }
        return if (weight == 0) 0f else weighted / weight
    }
}
