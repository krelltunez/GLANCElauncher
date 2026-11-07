package app.glance.launcher.data

import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

/**
 * The only way the UI reads or writes GLANCE data.
 *
 * v1 is meant to be backed by [ProviderRepository] (ContentProviders in the installed
 * dayGLANCE and lastGLANCE apps, intents for writes); the proof of concept runs on
 * [FakeRepository]. v2 swaps in a GLANCEvault client. Screens never see the difference.
 */
interface GlanceRepository {
    val tasks: StateFlow<List<Task>>
    val goals: StateFlow<List<Goal>>
    val projects: StateFlow<List<Project>>
    val routines: StateFlow<List<Routine>>
    val frames: StateFlow<List<Frame>>
    val dayShape: StateFlow<DayShape>
    /** dayGLANCE's hourly forecast per date, for the dial's weather ring; empty without one. */
    val weather: StateFlow<List<WeatherDay>>
    /**
     * dayGLANCE's Inbox count as the app shows it, through its Inbox filters and user lens;
     * null when the app sends none, and the caller counts the rows ([app.glance.launcher.ui.home.InboxCount]).
     */
    val inboxCount: StateFlow<Int?>
    val chores: StateFlow<List<Chore>>
    val people: StateFlow<List<Person>>

    /** Which apps back this repository, so the UI can prompt to install what is missing. */
    val availability: StateFlow<Availability>

    // dayGLANCE writes
    suspend fun moveTask(taskId: String, date: LocalDate, startMin: Int)
    /** Changes only the day: a timed task keeps its time, an all-day task stays all-day (dayGLANCE's postpone). */
    suspend fun moveTaskToDay(taskId: String, date: LocalDate)
    suspend fun resizeTask(taskId: String, durationMin: Int)
    suspend fun setTaskCompleted(taskId: String, completed: Boolean)
    suspend fun toggleRoutine(routineId: String, date: LocalDate)
    suspend fun createTask(draft: TaskDraft): Task

    // lastGLANCE writes
    suspend fun logChoreDone(choreId: String)
    suspend fun addChore(name: String): Chore
    suspend fun logInteraction(personId: String)

    /**
     * Ask [app] to confirm its data now (the apps' REFRESH action). Not a write: nothing is
     * held in the outbox; the outcome arrives in the next meta row's refresh log. Returns the
     * request's client id, or null when there is nothing to ask (demo data).
     */
    suspend fun requestRefresh(app: app.glance.launcher.data.provider.GlanceApp): String?

    /** The last refresh request sent per app, for the diagnostics panel. */
    val refreshRequests: StateFlow<Map<app.glance.launcher.data.provider.GlanceApp, RefreshRequest>>
}

/** A REFRESH the launcher sent: when, under which id. */
data class RefreshRequest(val clientId: String, val sentAt: java.time.Instant)

/** Where an app's data comes from: its provider, or the seeded demo. */
enum class DataSource { LIVE, DEMO }

/**
 * One app's freshness, as its meta row reports it (the apps' docs, "Freshness"): when the
 * snapshot last changed, when the app last looked, when a sync tier last completed, whether the
 * renderer was alive at the read, and the last REFRESH requests with their outcomes.
 */
data class Freshness(
    val source: DataSource = DataSource.DEMO,
    val installed: Boolean = false,
    val updatedAt: java.time.Instant? = null,
    val checkedAt: java.time.Instant? = null,
    val syncedAt: java.time.Instant? = null,
    val rendererAlive: Boolean? = null,
    val refreshLog: List<app.glance.launcher.data.provider.RefreshEntry> = emptyList(),
    /** Whether a headless refresh was running at the read (dayGLANCE); null when the app does not say. */
    val headlessRunning: Boolean? = null,
) {
    /** The stamp staleness is measured from: the last completed sync when there is one, else the last look, else the last change. */
    val confirmedAt: java.time.Instant? get() = syncedAt ?: checkedAt ?: updatedAt
}

/**
 * Which apps back this repository. [dayGlanceInstalled] and [lastGlanceInstalled] drive the
 * install banner; [dayGlance] and [lastGlance] say whether the data shown is the app's own or
 * the demo (a provider that is missing, refused or too old falls back to the demo); the
 * `UpdatedAt` stamps are the snapshots' own, for the stale indicator.
 */
data class Availability(
    val dayGlanceInstalled: Boolean,
    val lastGlanceInstalled: Boolean,
    val dayGlance: DataSource = DataSource.DEMO,
    val lastGlance: DataSource = DataSource.DEMO,
    val dayGlanceUpdatedAt: java.time.Instant? = null,
    val lastGlanceUpdatedAt: java.time.Instant? = null,
    val dayGlanceFreshness: Freshness = Freshness(dayGlance, dayGlanceInstalled, dayGlanceUpdatedAt),
    val lastGlanceFreshness: Freshness = Freshness(lastGlance, lastGlanceInstalled, lastGlanceUpdatedAt),
    /** The screenshot demo is on (debug builds): both apps show demo data, and the header says nothing about it. */
    val demoForced: Boolean = false,
)
