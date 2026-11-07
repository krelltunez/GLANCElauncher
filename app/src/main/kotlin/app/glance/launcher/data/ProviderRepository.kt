package app.glance.launcher.data

import app.glance.launcher.data.provider.DayGlanceSnapshot
import app.glance.launcher.data.provider.GlanceApp
import app.glance.launcher.data.provider.GlanceClient
import app.glance.launcher.data.provider.LastGlanceSnapshot
import app.glance.launcher.data.provider.Outbox
import app.glance.launcher.data.provider.OutboxEntry
import app.glance.launcher.data.provider.OutboxStore
import app.glance.launcher.data.provider.Patch
import app.glance.launcher.data.provider.SnapshotParser
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The real backend (docs/spec.md, "Real data"): each app's launcher snapshot read from
 * its ContentProvider and kept fresh by a ContentObserver, writes sent as launcher intents with a
 * client id and held in a durable outbox until a snapshot shows them, and [fallback] (the seeded
 * demo) standing in, per app, when a provider is missing, refused or too old.
 *
 * Every flow is recomputed from three inputs whenever one changes: the latest snapshot per app,
 * the pending writes, and the fallback's own flows. [availability] says which apps are installed,
 * which are live rather than demo, and how old each live snapshot is.
 *
 * [demoOverride] (the screenshot demo, debug builds only) shows the fallback for both apps and
 * sends writes to it, while the providers are still read and the outbox still delivered, so
 * turning it off shows live data at once and leaves the user's data and queued writes alone.
 */
class ProviderRepository(
    private val client: GlanceClient,
    private val fallback: GlanceRepository,
    private val store: OutboxStore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val demoOverride: StateFlow<Boolean> = MutableStateFlow(false),
) : GlanceRepository {

    private val day = MutableStateFlow<DayGlanceSnapshot?>(null)
    private val last = MutableStateFlow<LastGlanceSnapshot?>(null)
    private val installed = MutableStateFlow(mapOf(GlanceApp.DAYGLANCE to false, GlanceApp.LASTGLANCE to false))
    private val outbox = MutableStateFlow(Outbox.decode(store.load()))
    private val outboxLock = Mutex()
    private val lastSent = HashMap<String, Instant>()
    private val refreshJobs = HashMap<GlanceApp, Job>()
    private val observers = ArrayList<AutoCloseable>()

    private val _tasks = MutableStateFlow(fallback.tasks.value)
    private val _goals = MutableStateFlow(fallback.goals.value)
    private val _projects = MutableStateFlow(fallback.projects.value)
    private val _routines = MutableStateFlow(fallback.routines.value)
    private val _frames = MutableStateFlow(fallback.frames.value)
    private val _dayShape = MutableStateFlow(fallback.dayShape.value)
    private val _weather = MutableStateFlow(fallback.weather.value)
    private val _inboxCount = MutableStateFlow(fallback.inboxCount.value)
    private val _chores = MutableStateFlow(fallback.chores.value)
    private val _people = MutableStateFlow(fallback.people.value)
    private val _availability = MutableStateFlow(Availability(dayGlanceInstalled = false, lastGlanceInstalled = false))

    override val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    override val goals: StateFlow<List<Goal>> = _goals.asStateFlow()
    override val projects: StateFlow<List<Project>> = _projects.asStateFlow()
    override val routines: StateFlow<List<Routine>> = _routines.asStateFlow()
    override val frames: StateFlow<List<Frame>> = _frames.asStateFlow()
    override val dayShape: StateFlow<DayShape> = _dayShape.asStateFlow()
    override val weather: StateFlow<List<WeatherDay>> = _weather.asStateFlow()
    override val inboxCount: StateFlow<Int?> = _inboxCount.asStateFlow()
    override val chores: StateFlow<List<Chore>> = _chores.asStateFlow()
    override val people: StateFlow<List<Person>> = _people.asStateFlow()
    override val availability: StateFlow<Availability> = _availability.asStateFlow()

    /** The pending writes, for tests and the settings sheet. */
    val pending: StateFlow<List<OutboxEntry>> = outbox.asStateFlow()

    private val _refreshRequests = MutableStateFlow<Map<GlanceApp, RefreshRequest>>(emptyMap())
    override val refreshRequests: StateFlow<Map<GlanceApp, RefreshRequest>> = _refreshRequests.asStateFlow()

    override suspend fun requestRefresh(app: GlanceApp): String? {
        val live = when (app) { GlanceApp.DAYGLANCE -> dayLive; GlanceApp.LASTGLANCE -> lastLive }
        if (!live) return null
        val clientId = UUID.randomUUID().toString()
        _refreshRequests.value = _refreshRequests.value + (app to RefreshRequest(clientId, clock.instant()))
        withContext(io) { client.send(app, app.action("REFRESH"), "{}", clientId) }
        return clientId
    }

    init {
        recompute()
        // Demo-mode writes go to the fallback, whose flows then move; mirror them.
        for (flow in listOf(fallback.tasks, fallback.goals, fallback.projects, fallback.routines, fallback.frames, fallback.dayShape, fallback.weather, fallback.chores, fallback.people)) {
            scope.launch { flow.collect { recompute() } }
        }
        scope.launch { demoOverride.collect { recompute() } }
    }

    /** Register the observers and read both providers. Called once, after the graph is built. */
    fun start() {
        for (app in GlanceApp.values()) {
            observers.add(client.observe(app) { scheduleRefresh(app) })
        }
        scope.launch { refreshAll() }
    }

    /**
     * dayGLANCE's own forecast and location as its last snapshot carried them, null before a
     * snapshot was read; for the debug dump only (the flows above fall back to demo data).
     */
    fun dayGlanceWeatherForDump(): Pair<List<WeatherDay>, DayShape?>? = day.value?.let { it.weather to it.dayShape }

    fun stop() {
        observers.forEach { it.close() }
        observers.clear()
    }

    /**
     * The launcher came to the foreground: re-read both providers, re-send what is still
     * pending, then ask each live app to confirm its data unless it confirmed within
     * [REFRESH_THROTTLE] or was asked within it.
     */
    fun onForeground() {
        scope.launch {
            refreshAll()
            maybeRequestRefreshes()
        }
    }

    /**
     * REFRESH for each live app whose last completed sync is older than [REFRESH_THROTTLE] and
     * that has not been asked within it. Returns the apps asked, for tests. An app without a
     * sync tier (`syncedAt` null) is asked on the same throttle, since a REFRESH still re-reads
     * its local data and re-stamps the heartbeat.
     */
    suspend fun maybeRequestRefreshes(): List<GlanceApp> {
        val now = clock.instant()
        val asked = ArrayList<GlanceApp>()
        for (app in GlanceApp.values()) {
            val f = when (app) { GlanceApp.DAYGLANCE -> _availability.value.dayGlanceFreshness; GlanceApp.LASTGLANCE -> _availability.value.lastGlanceFreshness }
            if (f.source != DataSource.LIVE) continue
            val synced = f.syncedAt
            if (synced != null && java.time.Duration.between(synced, now) < REFRESH_THROTTLE) continue
            val last = _refreshRequests.value[app]
            if (last != null && java.time.Duration.between(last.sentAt, now) < REFRESH_THROTTLE) continue
            if (requestRefresh(app) != null) asked.add(app)
        }
        return asked
    }

    suspend fun refreshAll() {
        refresh(GlanceApp.DAYGLANCE)
        refresh(GlanceApp.LASTGLANCE)
    }

    private fun scheduleRefresh(app: GlanceApp) {
        // A push notifies once per collection; one read a moment later covers them all.
        refreshJobs[app]?.cancel()
        refreshJobs[app] = scope.launch {
            delay(DEBOUNCE_MS)
            refresh(app)
        }
    }

    /** Read [app]'s provider, retire the writes it now reflects, re-send the rest, and recompute. */
    suspend fun refresh(app: GlanceApp) {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val isInstalled = withContext(io) { client.isInstalled(app) }
        installed.value = installed.value + (app to isInstalled)
        val rows = if (client.realDataSupported && isInstalled) withContext(io) { client.read(app) } else null
        val reflected: Set<String>
        when (app) {
            GlanceApp.DAYGLANCE -> {
                val snapshot = rows?.let { SnapshotParser.dayGlance(it, today) }
                day.value = snapshot
                reflected = snapshot?.reflected ?: emptySet()
            }
            GlanceApp.LASTGLANCE -> {
                val snapshot = rows?.let { SnapshotParser.lastGlance(it, today) }
                last.value = snapshot
                reflected = snapshot?.reflected ?: emptySet()
            }
        }
        val remaining = outboxLock.withLock {
            val kept = outbox.value.filterNot { e -> e.app == app && (e.clientId in reflected || e.expired(now)) }
            if (kept.size != outbox.value.size) {
                outbox.value = kept
                store.save(Outbox.encode(kept))
            }
            kept.filter { it.app == app }
        }
        recompute()
        // Still pending after this read: the app has not seen it (or has not run since). Send again,
        // but not on every tick in a row; the app dedupes on the id, so a repeat is harmless.
        if (rows != null) {
            for (e in remaining) {
                val sentAt = lastSent[e.clientId]
                if (sentAt == null || java.time.Duration.between(sentAt, now).seconds >= RESEND_SECONDS) send(e)
            }
        }
    }

    private fun recompute() {
        val now = clock.instant()
        val pending = outbox.value.filterNot { it.expired(now) }
        val forced = demoOverride.value
        val d = if (forced) null else day.value
        val l = if (forced) null else last.value
        val dayPending = pending.filter { it.app == GlanceApp.DAYGLANCE }
        val lastPending = pending.filter { it.app == GlanceApp.LASTGLANCE }
        _tasks.value = if (d != null) Outbox.applyToTasks(d.tasks, dayPending) else fallback.tasks.value
        _goals.value = d?.goals ?: fallback.goals.value
        _projects.value = d?.projects ?: fallback.projects.value
        _routines.value = if (d != null) Outbox.applyToRoutines(d.routines, dayPending) else fallback.routines.value
        _frames.value = d?.frames ?: fallback.frames.value
        _dayShape.value = d?.dayShape ?: fallback.dayShape.value
        _weather.value = d?.weather ?: fallback.weather.value
        // The live app's own count; the demo has none, so its rows are counted.
        _inboxCount.value = if (d != null) d.meta.inboxCount else null
        _chores.value = if (l != null) Outbox.applyToChores(l.chores, lastPending) else fallback.chores.value
        _people.value = if (l != null) Outbox.applyToPeople(l.people, lastPending) else fallback.people.value
        val dayInstalled = installed.value[GlanceApp.DAYGLANCE] == true
        val lastInstalled = installed.value[GlanceApp.LASTGLANCE] == true
        _availability.value = Availability(
            dayGlanceInstalled = dayInstalled,
            lastGlanceInstalled = lastInstalled,
            dayGlance = if (d != null) DataSource.LIVE else DataSource.DEMO,
            lastGlance = if (l != null) DataSource.LIVE else DataSource.DEMO,
            dayGlanceUpdatedAt = d?.updatedAt,
            lastGlanceUpdatedAt = l?.updatedAt,
            dayGlanceFreshness = freshness(d != null, dayInstalled, d?.updatedAt, d?.meta),
            lastGlanceFreshness = freshness(l != null, lastInstalled, l?.updatedAt, l?.meta),
            demoForced = forced,
        )
    }

    private fun freshness(live: Boolean, isInstalled: Boolean, updatedAt: Instant?, meta: app.glance.launcher.data.provider.AppMeta?): Freshness =
        if (!live || meta == null) Freshness(DataSource.DEMO, isInstalled)
        else Freshness(DataSource.LIVE, isInstalled, updatedAt, meta.checkedAt, meta.syncedAt, meta.rendererAlive, meta.refreshLog, meta.headlessRunning)

    // region writes

    private val dayLive: Boolean get() = day.value != null && !demoOverride.value
    private val lastLive: Boolean get() = last.value != null && !demoOverride.value

    private suspend fun enqueue(app: GlanceApp, action: String, payload: JSONObject, patch: Patch, clientId: String = UUID.randomUUID().toString()) {
        val entry = OutboxEntry(clientId, app, app.action(action), payload.toString(), clock.instant(), patch)
        outboxLock.withLock {
            val next = outbox.value + entry
            outbox.value = next
            store.save(Outbox.encode(next))
        }
        recompute()
        send(entry)
    }

    private suspend fun send(entry: OutboxEntry) {
        lastSent[entry.clientId] = clock.instant()
        withContext(io) { client.send(entry.app, entry.action, entry.payload, entry.clientId) }
    }

    private fun hhmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    override suspend fun moveTask(taskId: String, date: LocalDate, startMin: Int) {
        if (!dayLive) return fallback.moveTask(taskId, date, startMin)
        val start = startMin.coerceIn(0, 1439)
        enqueue(GlanceApp.DAYGLANCE, "MOVE", JSONObject().put("task_id", taskId).put("date", date.toString()).put("start", hhmm(start)), Patch.MoveTask(taskId, date, start))
    }

    override suspend fun moveTaskToDay(taskId: String, date: LocalDate) {
        if (!dayLive) return fallback.moveTaskToDay(taskId, date)
        if (_tasks.value.firstOrNull { it.id == taskId }?.date == null) return
        enqueue(GlanceApp.DAYGLANCE, "MOVE", JSONObject().put("task_id", taskId).put("date", date.toString()), Patch.MoveTask(taskId, date, null))
    }

    override suspend fun resizeTask(taskId: String, durationMin: Int) {
        if (!dayLive) return fallback.resizeTask(taskId, durationMin)
        val dur = durationMin.coerceAtLeast(5)
        enqueue(GlanceApp.DAYGLANCE, "RESIZE", JSONObject().put("task_id", taskId).put("duration", dur), Patch.ResizeTask(taskId, dur))
    }

    override suspend fun setTaskCompleted(taskId: String, completed: Boolean) {
        if (!dayLive) return fallback.setTaskCompleted(taskId, completed)
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (!task.completable || task.completed == completed) return
        if (completed) {
            val now = clock.instant()
            enqueue(GlanceApp.DAYGLANCE, "COMPLETE", JSONObject().put("task_id", taskId).put("completed_at", now.toString()), Patch.CompleteTask(taskId, now))
        } else {
            enqueue(GlanceApp.DAYGLANCE, "UNCOMPLETE", JSONObject().put("task_id", taskId), Patch.UncompleteTask(taskId))
        }
    }

    override suspend fun toggleRoutine(routineId: String, date: LocalDate) {
        if (!dayLive) return fallback.toggleRoutine(routineId, date)
        // The state the tap asks for, from what the launcher shows: dayGLANCE sets it rather
        // than flipping, so a tap on a view that was behind (a tick made on another device)
        // cannot undo it. An older dayGLANCE ignores `completed` and flips, as before.
        val done = _routines.value.firstOrNull { it.id == routineId }?.let { date in it.completedDates } ?: false
        val wanted = !done
        enqueue(
            GlanceApp.DAYGLANCE, "TOGGLE_ROUTINE",
            JSONObject().put("routine_id", routineId).put("date", date.toString()).put("completed", wanted),
            Patch.ToggleRoutine(routineId, date, wanted),
        )
    }

    override suspend fun createTask(draft: TaskDraft): Task {
        if (!dayLive) return fallback.createTask(draft)
        val clientId = UUID.randomUUID().toString()
        val task = Task(
            id = clientId, title = draft.title, date = draft.date, startMin = draft.startMin, durationMin = draft.durationMin,
            isAllDay = draft.date != null && draft.startMin == null, priority = draft.priority, deadline = draft.deadline,
            colorHex = draft.colorHex, projectId = draft.projectId,
        )
        // The automation CREATE shape: `due` is a date, or a date and time, in local terms.
        val payload = JSONObject().put("title", draft.title).put("duration", draft.durationMin)
        draft.date?.let { d -> payload.put("due", if (draft.startMin != null) "${d}T${hhmm(draft.startMin)}" else d.toString()) }
        if (draft.priority > 0) payload.put("priority", draft.priority)
        draft.deadline?.let { payload.put("deadline", it.toString()) }
        draft.projectId?.let { payload.put("project", it) }
        enqueue(GlanceApp.DAYGLANCE, "CREATE", payload, Patch.CreateTask(task), clientId)
        return task
    }

    override suspend fun logChoreDone(choreId: String) {
        if (!lastLive) return fallback.logChoreDone(choreId)
        val now = clock.instant()
        enqueue(GlanceApp.LASTGLANCE, "COMPLETE", JSONObject().put("chore_id", choreId).put("completed_at", now.toString()), Patch.LogChore(choreId, now))
    }

    override suspend fun addChore(name: String): Chore {
        if (!lastLive) return fallback.addChore(name)
        val clientId = UUID.randomUUID().toString()
        val chore = Chore(id = clientId, name = name, category = "Inbox")
        enqueue(GlanceApp.LASTGLANCE, "CREATE", JSONObject().put("title", name), Patch.AddChore(chore), clientId)
        return chore
    }

    override suspend fun logInteraction(personId: String) {
        if (!lastLive) return fallback.logInteraction(personId)
        val now = clock.instant()
        enqueue(GlanceApp.LASTGLANCE, "LOG_INTERACTION", JSONObject().put("person_id", personId), Patch.LogInteraction(personId, now))
    }

    // endregion

    companion object {
        const val DEBOUNCE_MS = 150L
        const val RESEND_SECONDS = 15L
        /** At most one REFRESH per app per this long, and none when the app synced within it. */
        val REFRESH_THROTTLE: java.time.Duration = java.time.Duration.ofMinutes(2)
    }
}
