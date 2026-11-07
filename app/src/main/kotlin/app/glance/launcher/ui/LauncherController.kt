package app.glance.launcher.ui

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.glance.daydial.DialBlock
import app.glance.daydial.DialBlockKind
import app.glance.daydial.DialFrame
import app.glance.daydial.DialRoutine
import app.glance.launcher.data.Frame
import app.glance.launcher.data.Routine
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskDraft
import app.glance.launcher.di.AppGraph
import app.glance.launcher.prefs.SchedViewPrefs
import app.glance.launcher.system.DoubleTapLock
import app.glance.launcher.system.LockScreenService
import app.glance.launcher.ui.planning.SchedAgenda
import app.glance.launcher.ui.planning.SchedFilters
import app.glance.launcher.ui.search.CaptureTarget
import app.glance.launcher.ui.theme.hexColor
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * UI state that is not data: which day the dial shows, the search text, which overlay is
 * open. Data itself is read straight from the repository flows.
 */
class LauncherController(val graph: AppGraph, private val scope: CoroutineScope) {
    val repository get() = graph.repository

    /** Today, as the views last saw it; [rollToday] moves it, and the views on it, at midnight. */
    var today by mutableStateOf(LocalDate.now())
        private set
    var dialDate by mutableStateOf(today)
    var planningMonth by mutableStateOf(today.withDayOfMonth(1))
    var planningSelectedDay by mutableStateOf(today)

    var searchQuery by mutableStateOf("")
    /** The full-screen search is open (the bar on Home is only its trigger). */
    var searchActive by mutableStateOf(false)
    var captureTarget by mutableStateOf(CaptureTarget.DAYGLANCE)
    var drawerOpen by mutableStateOf(false)
    /** When true, tapping an app in the drawer pins it to Home instead of launching it. */
    var drawerPickMode by mutableStateOf<PickMode?>(null)
    var settingsOpen by mutableStateOf(false)
    /** The freshness diagnostics panel (long-press the header status). */
    var diagnosticsOpen by mutableStateOf(false)
    /** The pinned-apps editor (reorder, remove, add) is open. */
    var pinnedEditorOpen by mutableStateOf(false)
    /** The long-press menu of a pinned app or shortcut on Home ([app.glance.launcher.ui.common.AppMenu]). */
    var appMenuFor by mutableStateOf<app.glance.launcher.apps.Pin?>(null)
    /** Bumped to ask the root to scroll to the planning page. */
    var planningRequests by mutableStateOf(0)
    /** Set by the mic on Home: the search overlay it opens starts listening (once) instead of showing the keyboard. */
    var voiceStartPending by mutableStateOf(false)

    // SCHED agenda state. The filters and the show/hide toggles are kept across launches
    // (LauncherPrefs.schedView), as dayGLANCE keeps its toggles in localStorage; how far the
    // window has been extended is not.
    private var schedView by mutableStateOf(graph.prefs.schedView)
    var schedFilters: SchedFilters
        get() = SchedFilters(schedView.colors, schedView.tags, schedView.projectIds)
        set(value) = saveSchedView(schedView.copy(colors = value.colors, tags = value.tags, projectIds = value.projectIds))
    var schedShowEmptyDays: Boolean
        get() = schedView.showEmptyDays
        set(value) = saveSchedView(schedView.copy(showEmptyDays = value))
    var schedHideCompleted: Boolean
        get() = schedView.hideCompleted
        set(value) = saveSchedView(schedView.copy(hideCompleted = value))
    var schedHidePastEvents: Boolean
        get() = schedView.hidePastEvents
        set(value) = saveSchedView(schedView.copy(hidePastEvents = value))
    var schedFiltersOpen by mutableStateOf(false)
    var schedDaysShown by mutableStateOf(SchedAgenda.INITIAL_DAYS)
    var toast by mutableStateOf<String?>(null)

    enum class PickMode { HOME, DOCK }

    private fun saveSchedView(view: SchedViewPrefs) {
        if (view == schedView) return
        schedView = view
        graph.prefs.setSchedView(view)
    }

    /** After a settings import: the agenda's filters as the imported file set them. */
    fun reloadSchedView() { schedView = graph.prefs.schedView }

    fun stepDialDay(delta: Int) { dialDate = dialDate.plusDays(delta.toLong()) }
    fun goToday() { dialDate = LocalDate.now() }

    /**
     * Called each minute and on coming back on screen: once the date has turned, the dial and
     * the Planning month move to the new today if they were on the old one (DayRollover).
     */
    fun rollToday(now: LocalDate = LocalDate.now()) {
        val old = today
        if (now == old) return
        val next = DayRollover.follow(DayRollover.Days(dialDate, planningMonth, planningSelectedDay), old, now)
        today = now
        dialDate = next.dial
        planningMonth = next.planningMonth
        planningSelectedDay = next.planningSelected
    }

    fun closeOverlays() {
        drawerOpen = false
        drawerPickMode = null
        settingsOpen = false
        diagnosticsOpen = false
        pinnedEditorOpen = false
        appMenuFor = null
        schedFiltersOpen = false
        searchQuery = ""
        searchActive = false
        voiceStartPending = false
    }

    fun showPlanning() { planningRequests++ }

    fun openDrawer(pick: PickMode? = null) {
        drawerPickMode = pick
        drawerOpen = true
    }

    // region dial callbacks → repository

    fun moveTask(taskId: String, newStartMin: Int) = scope.launch { repository.moveTask(taskId, dialDate, newStartMin) }
    fun resizeTask(taskId: String, newDuration: Int) = scope.launch { repository.resizeTask(taskId, newDuration) }
    fun toggleTask(task: Task) = scope.launch { repository.setTaskCompleted(task.id, !task.completed) }
    fun toggleRoutine(routineId: String) = scope.launch { repository.toggleRoutine(routineId, dialDate) }
    fun toggleRoutineOn(routineId: String, date: LocalDate) = scope.launch { repository.toggleRoutine(routineId, date) }

    /** A double-tap on Home's empty space: lock, when switched on ([DoubleTapLock]). */
    fun doubleTapHome() {
        val outcome = DoubleTapLock.onDoubleTap(graph.prefs.doubleTapToLock.value, LockScreenService.connected.value, Build.VERSION.SDK_INT)
        when (outcome) {
            DoubleTapLock.Outcome.LOCK -> if (!LockScreenService.lock()) toast = "Android did not lock the screen"
            DoubleTapLock.Outcome.NEEDS_SERVICE -> toast = "Turn on GLANCElauncher in Accessibility settings to double-tap to lock"
            DoubleTapLock.Outcome.NONE -> Unit
        }
    }

    // SCHED card actions, the same writes dayGLANCE's buttons make.

    /** "Schedule for today (next open slot)": today at the next quarter hour. An Inbox row loses its deadline. */
    fun scheduleTaskToday(task: Task) = scope.launch {
        val now = LocalTime.now()
        repository.moveTask(task.id, LocalDate.now(), SchedAgenda.nextSlot(now.hour * 60 + now.minute).coerceAtMost(23 * 60 + 45))
        toast = "Scheduled for today: ${task.cleanTitle}"
    }

    /** "Postpone to tomorrow": the day after the task's own day, time unchanged. */
    fun postponeTask(task: Task) = scope.launch {
        val date = task.date ?: return@launch
        repository.moveTaskToDay(task.id, date.plusDays(1))
        toast = "Postponed to tomorrow: ${task.cleanTitle}"
    }

    /** A deadline card's calendar button: the task goes onto that day's clock at [startMin]. */
    fun scheduleDeadlineTask(task: Task, date: LocalDate, startMin: Int) = scope.launch {
        repository.moveTask(task.id, date, startMin)
        toast = "Scheduled: ${task.cleanTitle}"
    }

    fun createTask(draft: TaskDraft, onCreated: (Task) -> Unit = {}) = scope.launch {
        val t = repository.createTask(draft)
        toast = if (t.date != null) "Added to dayGLANCE: ${t.cleanTitle}" else "Added to Inbox: ${t.cleanTitle}"
        onCreated(t)
    }

    fun createTaskAt(date: LocalDate, startMin: Int) = scope.launch {
        repository.createTask(TaskDraft(title = "New block", date = date, startMin = startMin, durationMin = 30))
        toast = "New block at ${app.glance.daydial.formatClock(startMin, false)}"
    }

    fun logChoreDone(choreId: String, name: String) = scope.launch {
        repository.logChoreDone(choreId)
        toast = "Logged: $name"
    }

    fun addChore(name: String) = scope.launch {
        repository.addChore(name)
        toast = "Added to lastGLANCE: $name"
    }

    fun logInteraction(personId: String, name: String) = scope.launch {
        repository.logInteraction(personId)
        toast = "Logged contact with $name"
    }

    // endregion
}

// region mapping to the dial module

/** All-day tasks map too (start 0, no duration); the dial keeps them off the ring and the pill shows them. */
fun Task.toDialBlock(): DialBlock? {
    val start = startMin ?: if (isAllDay) 0 else return null
    return DialBlock(
        id = id,
        title = title,
        startMin = start,
        durationMin = durationMin,
        color = hexColor(colorHex),
        completed = completed,
        completable = completable,
        isAllDay = isAllDay,
        movable = completable,
        kind = if (isRestore) DialBlockKind.RESTORE else DialBlockKind.EFFORT,
    )
}

/** The frame on [date], with that day's free time when dayGLANCE sent it. */
fun Frame.toDialFrame(date: java.time.LocalDate): DialFrame =
    DialFrame(id, name, startMin, endMin, hexColor(colorHex), slots = slotsByDate?.get(date))

fun Routine.toDialRoutine(date: LocalDate): DialRoutine = DialRoutine(id, name, startMin, endMin, completed = date in completedDates)

fun Frame.appliesTo(date: LocalDate): Boolean = dates?.let { date in it } ?: (days.isEmpty() || date.dayOfWeek.value in days)

// endregion
