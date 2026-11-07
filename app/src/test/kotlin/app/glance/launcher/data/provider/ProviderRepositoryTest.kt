package app.glance.launcher.data.provider

import app.glance.launcher.data.DataSource
import app.glance.launcher.data.FakeRepository
import app.glance.launcher.data.FakeSeed
import app.glance.launcher.data.ProviderRepository
import app.glance.launcher.data.TaskDraft
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** A scripted client: what each provider answers, what was broadcast, and the observers to poke. */
private class FakeClient : GlanceClient {
    override var realDataSupported = true
    val installed = HashMap<GlanceApp, Boolean>()
    val rows = HashMap<GlanceApp, Map<String, List<String>>?>()
    val sent = ArrayList<Triple<GlanceApp, String, Pair<String, String>>>()
    val observers = HashMap<GlanceApp, () -> Unit>()
    override fun isInstalled(app: GlanceApp) = installed[app] == true
    override fun read(app: GlanceApp) = rows[app]
    override fun send(app: GlanceApp, action: String, payload: String, clientId: String) { sent.add(Triple(app, action, payload to clientId)) }
    override fun observe(app: GlanceApp, onChange: () -> Unit): AutoCloseable { observers[app] = onChange; return AutoCloseable { } }
}

class ProviderRepositoryTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-19T15:00:00Z")
    private val today = LocalDate.of(2026, 10, 19)
    private val clock = Clock.fixed(now, zone)
    private val client = FakeClient()
    private val store = MemoryOutboxStore()
    private val fallback = FakeRepository(clock, FakeSeed(today, now, zone))
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val demo = kotlinx.coroutines.flow.MutableStateFlow(false)

    private fun repo(storeJson: String? = null): ProviderRepository {
        if (storeJson != null) store.save(storeJson)
        return ProviderRepository(client, fallback, store, scope, clock, Dispatchers.Unconfined, demo)
    }

    private fun dayRows(handled: List<String> = emptyList(), tasks: List<String> = listOf(
        """{"id":"t1","title":"Run","date":"2026-10-19","startTime":"07:45","duration":45,"completed":false,"source":"dayglance"}""",
    )) = mapOf(
        "meta" to listOf("""{"version":1,"updatedAt":${now.minusSeconds(120).toEpochMilli()},"today":"2026-10-19","handledWrites":[${handled.joinToString(",") { """{"clientId":"$it","action":"complete"}""" }}]}"""),
        "tasks" to tasks,
        "goals" to emptyList(), "projects" to emptyList(), "routines" to emptyList(), "frames" to emptyList(),
        "dayShape" to listOf("""{"dayStartMin":390,"dayStopMin":1380,"sunriseMin":432,"sunsetMin":1104,"use24HourClock":false}"""),
    )

    private fun lastRows() = mapOf(
        "meta" to listOf("""{"version":1,"updatedAt":${now.toEpochMilli()},"today":"2026-10-19","handledWrites":[]}"""),
        "chores" to listOf("""{"id":"c1","name":"Vacuum","category":"Kitchen","targetCadenceDays":7,"lastDoneAt":"2026-10-10T12:00:00.000Z"}"""),
        "people" to listOf("""{"id":"p1","name":"Ana","circle":"Family","targetCadenceDays":30,"lastInteractionAt":null,"unreturned":false,"dates":[]}"""),
    )

    @Test
    fun `without a provider the demo stands in and the status says so`() = runBlocking {
        val r = repo()
        r.refreshAll()
        assertEquals(fallback.tasks.value, r.tasks.value)
        assertEquals(fallback.chores.value, r.chores.value)
        val a = r.availability.value
        assertFalse(a.dayGlanceInstalled)
        assertEquals(DataSource.DEMO, a.dayGlance)
        assertEquals(DataSource.DEMO, a.lastGlance)
        assertNull(a.dayGlanceUpdatedAt)
        // A demo write goes to the fallback, not the outbox, and still shows.
        val before = r.tasks.value.first { !it.completed && it.completable }
        r.setTaskCompleted(before.id, true)
        assertTrue(r.tasks.value.first { it.id == before.id }.completed)
        assertTrue(client.sent.isEmpty())
        assertTrue(r.pending.value.isEmpty())
    }

    @Test
    fun `the screenshot demo shows the fallback for live apps, leaves the user's data and writes alone, and turns off at once`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.installed[GlanceApp.LASTGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        client.rows[GlanceApp.LASTGLANCE] = lastRows()
        val r = repo()
        r.refreshAll()
        // A real write, still waiting for dayGLANCE to show it.
        r.setTaskCompleted("t1", true)
        val queued = r.pending.value.single()
        client.sent.clear()

        demo.value = true
        assertEquals(fallback.tasks.value, r.tasks.value)
        assertEquals(fallback.chores.value, r.chores.value)
        assertEquals(fallback.people.value, r.people.value)
        val a = r.availability.value
        assertTrue(a.demoForced)
        assertEquals(DataSource.DEMO, a.dayGlance)
        assertEquals(DataSource.DEMO, a.lastGlance)
        assertNull(app.glance.launcher.ui.common.dataStatusText(a, now))
        // A tap on the demo changes the demo: nothing is queued or sent to the real app.
        val demoTask = r.tasks.value.first { !it.completed && it.completable }
        r.setTaskCompleted(demoTask.id, true)
        assertTrue(r.tasks.value.first { it.id == demoTask.id }.completed)
        assertEquals(listOf(queued), r.pending.value)
        assertNull(r.requestRefresh(GlanceApp.DAYGLANCE))
        assertTrue(r.maybeRequestRefreshes().isEmpty())
        assertTrue(client.sent.isEmpty())
        // The providers are still read while it is on: a change made meanwhile shows once it is off.
        client.rows[GlanceApp.DAYGLANCE] = dayRows(tasks = listOf(
            """{"id":"t1","title":"Run","date":"2026-10-19","startTime":"07:45","duration":45,"completed":false,"source":"dayglance"}""",
            """{"id":"t2","title":"Read","date":"2026-10-19","startTime":"20:00","duration":30,"completed":false,"source":"dayglance"}""",
        ))
        r.refresh(GlanceApp.DAYGLANCE)
        assertEquals(fallback.tasks.value, r.tasks.value)
        assertEquals(listOf(queued), r.pending.value)

        demo.value = false
        assertEquals(listOf("t1", "t2"), r.tasks.value.map { it.id })
        // The real write is still pending and still applied over the snapshot.
        assertTrue(r.tasks.value.first { it.id == "t1" }.completed)
        assertEquals(listOf("c1"), r.chores.value.map { it.id })
        assertFalse(r.availability.value.demoForced)
        assertEquals(DataSource.LIVE, r.availability.value.dayGlance)
    }

    @Test
    fun `an installed app that refuses or lacks the provider is installed but demo`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = null
        val r = repo()
        r.refreshAll()
        val a = r.availability.value
        assertTrue(a.dayGlanceInstalled)
        assertEquals(DataSource.DEMO, a.dayGlance)
        assertEquals(fallback.tasks.value, r.tasks.value)
    }

    @Test
    fun `below Android 12 nothing is read even when both apps are there`() = runBlocking {
        client.realDataSupported = false
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        val r = repo()
        r.refreshAll()
        assertEquals(DataSource.DEMO, r.availability.value.dayGlance)
        assertTrue(r.availability.value.dayGlanceInstalled)
    }

    @Test
    fun `a readable provider makes that app live, per app`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        val r = repo()
        r.refreshAll()
        assertEquals(listOf("t1"), r.tasks.value.map { it.id })
        assertEquals(390, r.dayShape.value.dayStartMin)
        // No weather rows in this snapshot: the live app has none, so the ring is empty rather than the demo's.
        assertTrue(r.weather.value.isEmpty())
        assertEquals(DataSource.LIVE, r.availability.value.dayGlance)
        assertEquals(now.minusSeconds(120), r.availability.value.dayGlanceUpdatedAt)
        // lastGLANCE is still the demo.
        assertEquals(DataSource.DEMO, r.availability.value.lastGlance)
        assertEquals(fallback.chores.value, r.chores.value)
    }

    @Test
    fun `the app's own Inbox count reaches the dial, and a build without one leaves the count to the rows`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        val r = repo()
        r.refreshAll()
        assertNull(r.inboxCount.value)
        client.rows[GlanceApp.DAYGLANCE] = dayRows().toMutableMap().apply {
            put("meta", listOf("""{"version":1,"updatedAt":${now.toEpochMilli()},"today":"2026-10-19","handledWrites":[],"inboxCount":3}"""))
        }
        r.refreshAll()
        assertEquals(3, r.inboxCount.value)
    }

    @Test
    fun `a write is applied at once, persisted, sent with its client id, and retired when the snapshot shows it`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        val r = repo()
        r.refreshAll()

        r.setTaskCompleted("t1", true)
        assertTrue(r.tasks.value.single().completed)
        val (app, action, extras) = client.sent.single()
        assertEquals(GlanceApp.DAYGLANCE, app)
        assertEquals("com.dayglance.app.launcher.COMPLETE", action)
        assertEquals("t1", JSONObject(extras.first).getString("task_id"))
        val clientId = extras.second
        assertEquals(1, r.pending.value.size)
        assertTrue(store.load()!!.contains(clientId))

        // The app has not run yet: a new snapshot without the id keeps the optimistic state and re-sends.
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        r.refresh(GlanceApp.DAYGLANCE)
        assertTrue(r.tasks.value.single().completed)
        assertEquals(1, client.sent.size, "re-sent too soon")
        // ... but a later tick does re-send it.
        val later = ProviderRepository(client, fallback, store, scope, Clock.fixed(now.plusSeconds(60), zone), Dispatchers.Unconfined)
        later.refresh(GlanceApp.DAYGLANCE)
        assertEquals(2, client.sent.size)
        assertEquals(clientId, client.sent[1].third.second)

        // The app handled it: the ledger lists the id, the row is complete, the outbox entry goes.
        client.rows[GlanceApp.DAYGLANCE] = dayRows(handled = listOf(clientId), tasks = listOf(
            """{"id":"t1","title":"Run","date":"2026-10-19","startTime":"07:45","duration":45,"completed":true,"completedAt":"2026-10-19T15:00:00Z","source":"dayglance"}""",
        ))
        r.refresh(GlanceApp.DAYGLANCE)
        assertTrue(r.pending.value.isEmpty())
        assertEquals("[]", store.load())
        assertTrue(r.tasks.value.single().completed)
    }

    @Test
    fun `un-completing a live task sends UNCOMPLETE and reopens it at once`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows(tasks = listOf(
            """{"id":"t1","title":"Run","date":"2026-10-19","startTime":"07:45","duration":45,"completed":true,"completedAt":"2026-10-19T14:30:00Z","source":"dayglance"}""",
        ))
        val r = repo()
        r.refreshAll()
        // Already in the asked-for state: nothing is sent.
        r.setTaskCompleted("t1", true)
        assertTrue(client.sent.isEmpty())
        r.setTaskCompleted("t1", false)
        assertFalse(r.tasks.value.single().completed)
        assertNull(r.tasks.value.single().completedAt)
        val (_, action, extras) = client.sent.single()
        assertEquals("com.dayglance.app.launcher.UNCOMPLETE", action)
        assertEquals("t1", JSONObject(extras.first).getString("task_id"))
        assertEquals(1, r.pending.value.size)
    }

    @Test
    fun `a created task carries the client id until the snapshot shows a row with it`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        val r = repo()
        r.refreshAll()
        val task = r.createTask(TaskDraft("From launcher", today, 14 * 60, 30, priority = 1, projectId = "p1"))
        assertEquals(task, r.tasks.value.first { it.title == "From launcher" })
        val (_, action, extras) = client.sent.single()
        assertEquals("com.dayglance.app.launcher.CREATE", action)
        assertEquals(task.id, extras.second)
        val payload = JSONObject(extras.first)
        assertEquals("2026-10-19T14:00", payload.getString("due"))
        assertEquals("p1", payload.getString("project"))
        assertEquals(1, payload.getInt("priority"))

        client.rows[GlanceApp.DAYGLANCE] = dayRows(tasks = listOf(
            """{"id":"t1","title":"Run","date":"2026-10-19","startTime":"07:45","duration":45,"completed":false,"source":"dayglance"}""",
            """{"id":"real-9","title":"From launcher","date":"2026-10-19","startTime":"14:00","duration":30,"completed":false,"source":"dayglance","clientId":"${task.id}"}""",
        ))
        r.refresh(GlanceApp.DAYGLANCE)
        assertTrue(r.pending.value.isEmpty())
        assertEquals(listOf("t1", "real-9"), r.tasks.value.map { it.id })
    }

    @Test
    fun `the outbox is reloaded from the store and expired entries are dropped`() = runBlocking {
        client.installed[GlanceApp.LASTGLANCE] = true
        client.rows[GlanceApp.LASTGLANCE] = lastRows()
        val fresh = OutboxEntry("keep", GlanceApp.LASTGLANCE, "com.lastglance.app.launcher.COMPLETE", """{"chore_id":"c1"}""", now.minus(Duration.ofHours(1)), Patch.LogChore("c1", now.minus(Duration.ofHours(1))))
        val stale = OutboxEntry("old", GlanceApp.LASTGLANCE, "com.lastglance.app.launcher.LOG_INTERACTION", """{"person_id":"p1"}""", now.minus(Duration.ofHours(30)), Patch.LogInteraction("p1", now.minus(Duration.ofHours(30))))
        val r = repo(Outbox.encode(listOf(fresh, stale)))
        // Before any read: both applied over the demo? No: pending writes only shape live data.
        r.refreshAll()
        assertEquals(listOf("keep"), r.pending.value.map { it.clientId })
        assertEquals(now.minus(Duration.ofHours(1)), r.chores.value.single().lastDoneAt)
        assertNull(r.people.value.single().lastInteractionAt)
        // The kept one was re-sent on this read.
        assertEquals(listOf("keep"), client.sent.map { it.third.second })
    }

    @Test
    fun `lastGLANCE writes name the chore and person by id`() = runBlocking {
        client.installed[GlanceApp.LASTGLANCE] = true
        client.rows[GlanceApp.LASTGLANCE] = lastRows()
        val r = repo()
        r.refreshAll()
        r.logChoreDone("c1")
        r.logInteraction("p1")
        val chore = r.addChore("Dust")
        assertEquals(now, r.chores.value.first { it.id == "c1" }.lastDoneAt)
        assertEquals(now, r.people.value.single().lastInteractionAt)
        assertEquals("Dust", r.chores.value.first { it.id == chore.id }.name)
        assertEquals(
            listOf("com.lastglance.app.launcher.COMPLETE", "com.lastglance.app.launcher.LOG_INTERACTION", "com.lastglance.app.launcher.CREATE"),
            client.sent.map { it.second },
        )
        assertEquals("c1", JSONObject(client.sent[0].third.first).getString("chore_id"))
        assertEquals("p1", JSONObject(client.sent[1].third.first).getString("person_id"))
        assertEquals("Dust", JSONObject(client.sent[2].third.first).getString("title"))
        assertNotEquals(client.sent[0].third.second, client.sent[1].third.second)
    }

    @Test
    fun `a refresh request is sent outside the outbox and only to a live app`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        val r = repo()
        r.refreshAll()
        assertNull(r.requestRefresh(GlanceApp.LASTGLANCE))
        val id = r.requestRefresh(GlanceApp.DAYGLANCE)
        assertNotNull(id)
        val (app, action, extras) = client.sent.single()
        assertEquals(GlanceApp.DAYGLANCE, app)
        assertEquals("com.dayglance.app.launcher.REFRESH", action)
        assertEquals(id, extras.second)
        assertTrue(r.pending.value.isEmpty())
        assertEquals(id, r.refreshRequests.value.getValue(GlanceApp.DAYGLANCE).clientId)
        // The freshness the UI reads comes from the app's meta, per app.
        val f = r.availability.value.dayGlanceFreshness
        assertEquals(DataSource.LIVE, f.source)
        assertEquals(now.minusSeconds(120), f.updatedAt)
        assertEquals(DataSource.DEMO, r.availability.value.lastGlanceFreshness.source)
    }

    @Test
    fun `on foreground a live app is asked to refresh, throttled, and not when it synced within the throttle`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.installed[GlanceApp.LASTGLANCE] = true
        // dayGLANCE synced 10 minutes ago; lastGLANCE 30 seconds ago.
        client.rows[GlanceApp.DAYGLANCE] = dayRows().toMutableMap().also { rows ->
            rows["meta"] = listOf("""{"version":1,"updatedAt":${now.minusSeconds(120).toEpochMilli()},"today":"2026-10-19","handledWrites":[],"checkedAt":${now.minusSeconds(60).toEpochMilli()},"syncedAt":"${now.minusSeconds(600)}","rendererAlive":true}""")
        }
        client.rows[GlanceApp.LASTGLANCE] = lastRows().toMutableMap().also { rows ->
            rows["meta"] = listOf("""{"version":1,"updatedAt":${now.toEpochMilli()},"today":"2026-10-19","handledWrites":[],"checkedAt":${now.toEpochMilli()},"syncedAt":"${now.minusSeconds(30)}","rendererAlive":true}""")
        }
        val r = repo()
        r.refreshAll()
        assertEquals(listOf(GlanceApp.DAYGLANCE), r.maybeRequestRefreshes())
        assertEquals(1, client.sent.size)
        assertEquals("com.dayglance.app.launcher.REFRESH", client.sent[0].second)
        // Asked a moment ago: not again.
        assertTrue(r.maybeRequestRefreshes().isEmpty())
        assertEquals(1, client.sent.size)
        // Past the throttle, with no newer sync, both are asked (lastGLANCE's 30 s is now over two minutes old).
        val later = ProviderRepository(client, fallback, store, scope, Clock.fixed(now.plus(ProviderRepository.REFRESH_THROTTLE).plusSeconds(1), zone), Dispatchers.Unconfined)
        later.refreshAll()
        assertEquals(listOf(GlanceApp.DAYGLANCE, GlanceApp.LASTGLANCE), later.maybeRequestRefreshes())
        // A demo app is never asked.
        client.rows[GlanceApp.DAYGLANCE] = null
        val demo = repo()
        demo.refreshAll()
        assertTrue(demo.maybeRequestRefreshes().isEmpty())
    }

    @Test
    fun `an observer poke re-reads after the debounce`() = runBlocking {
        client.installed[GlanceApp.DAYGLANCE] = true
        client.rows[GlanceApp.DAYGLANCE] = dayRows()
        val r = repo()
        r.start()
        r.refreshAll()
        assertEquals(1, r.tasks.value.size)
        client.rows[GlanceApp.DAYGLANCE] = dayRows(tasks = emptyList())
        client.observers.getValue(GlanceApp.DAYGLANCE)()
        kotlinx.coroutines.delay(ProviderRepository.DEBOUNCE_MS * 4)
        assertTrue(r.tasks.value.isEmpty())
        r.stop()
    }
}
