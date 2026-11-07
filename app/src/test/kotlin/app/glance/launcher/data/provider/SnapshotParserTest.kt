package app.glance.launcher.data.provider

import app.glance.launcher.data.GoalStatus
import app.glance.launcher.data.TaskSource
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The two apps' snapshot rows, as their providers serve them, into the launcher's models. */
class SnapshotParserTest {
    private val today = LocalDate.of(2026, 10, 19)

    private val dayRows = mapOf(
        "meta" to listOf("""{"version":1,"updatedAt":1760000000000,"timezone":"America/Denver","today":"2026-10-19",
            "handledWrites":[{"clientId":"w-done","action":"complete","taskId":"t1","at":"2026-10-19T14:00:00.000Z"}],
            "checkedAt":1760000100000,"syncedAt":"2026-10-19T08:00:00.000Z","rendererAlive":true,
            "refreshLog":[{"clientId":"rf-1","requestedAt":1760000050000,"outcome":"queued","liveness":"alive"},
                          {"clientId":"rf-1","requestedAt":1760000050000,"outcome":"ran","liveness":"alive","durationMs":840,"finishedAt":1760000051000},
                          {"clientId":"rf-2","requestedAt":1760000060000,"outcome":"deferred","liveness":"dead","reason":"process not running"},
                          {"clientId":"rf-3","requestedAt":1760000070000,"outcome":"ran","liveness":"dead","startedAt":1760000090000,"waitMs":20000,"headless":true,"durationMs":4100,"finishedAt":1760000094100}],
            "headlessRunning":false,"inboxCount":4}"""),
        "tasks" to listOf(
            """{"id":"t1","title":"Run #training","date":"2026-10-19","startTime":"07:45","duration":45,"isAllDay":false,"completed":true,
                "completedAt":"2026-10-19T14:30:00.000Z","colorHex":"#22c55e","priority":0,"notes":"","projectId":"p1","deadline":null,"source":"dayglance","energy":null,"clientId":null}""",
            """{"id":"i1","title":"Call dentist","date":null,"startTime":null,"duration":15,"isAllDay":false,"completed":false,"completedAt":null,
                "colorHex":"#ef4444","priority":2,"notes":"ask about x","projectId":null,"deadline":"2026-10-21","source":"dayglance","energy":null,"clientId":"w-new"}""",
            """{"id":"e1","title":"Standup","date":"2026-10-19","startTime":"10:00","duration":15,"isAllDay":false,"completed":false,"source":"calendar"}""",
            """{"id":"a1","title":"Pay bill","date":"2026-10-19","startTime":null,"duration":0,"isAllDay":true,"completed":false,"source":"dayglance"}""",
            """not json""",
            """{"title":"no id"}""",
        ),
        "goals" to listOf("""{"id":"g1","title":"Half marathon","description":"","colorHex":"#22c55e","targetDate":"2027-01-10","status":"active"}"""),
        "projects" to listOf(
            """{"id":"p1","title":"Base training","goalId":"g1","colorHex":"#22c55e","description":"","status":"archived"}""",
            // A project with its own target date (dayGLANCE #2160).
            """{"id":"p2","title":"Roof","goalId":null,"colorHex":"#3b82f6","description":"","status":"active","targetDate":"2026-11-01"}""",
        ),
        "routines" to listOf(
            """{"id":"r1","name":"Stretch","startTime":"12:30","duration":15,"isAllDay":false,"completed":true}""",
            """{"id":"r2","name":"Journal","startTime":null,"duration":10,"isAllDay":true,"completed":false}""",
        ),
        "frames" to listOf(
            """{"id":"f1:2026-10-19","frameId":"f1","date":"2026-10-19","start":"08:30","end":"12:00","name":"Morning focus","colorHex":"#93c5fd","slots":[{"start":"08:30","end":"09:00"},{"start":"10:00","end":"12:00"},{"start":"11:00","end":"bad"}]}""",
            """{"id":"f1:2026-10-20","frameId":"f1","date":"2026-10-20","start":"08:30","end":"12:00","name":"Morning focus","colorHex":"#93c5fd","slots":[]}""",
            """{"id":"f2:2026-10-19","frameId":"f2","date":"2026-10-19","start":"18:00","end":"21:30","name":"Evening","colorHex":"#c4b5fd"}""",
        ),
        "dayShape" to listOf("""{"dayStartMin":390,"dayStopMin":1380,"sunriseMin":432,"sunsetMin":1104,"use24HourClock":true,"lat":39.74,"lon":-104.99,"dialAlarm":{"on":false,"fromMin":1260},"dialLayers":{"solar":true,"weather":false,"calendars":false,"routines":true,"frames":true}}"""),
        "weather" to listOf(
            """{"id":"2026-10-19","date":"2026-10-19","unit":"celsius","hours":[{"hour":9,"temp":12,"code":0,"uv":2.3},{"hour":12,"temp":18,"code":61,"uv":5.5},{"hour":13,"temp":null,"code":null,"uv":null},{"hour":99,"temp":1}]}""",
            """{"id":"2026-10-20","date":"2026-10-20","unit":"fahrenheit","hours":[]}""",
            """{"id":"bad"}""",
        ),
    )

    @Test
    fun `the client queries every collection the parser reads`() {
        // The real client reads only GlanceApp.collections; the other tests hand the parser every
        // row directly, which is how the weather ring stayed empty with nothing failing.
        val read = dayRows.filterKeys { it in GlanceApp.DAYGLANCE.collections }
        assertEquals(SnapshotParser.dayGlance(dayRows, today), SnapshotParser.dayGlance(read, today))
        assertEquals(2, SnapshotParser.dayGlance(read, today).weather.size)
    }

    @Test
    fun `tasks keep their id, time, kind and fields, and bad rows are skipped`() {
        val s = SnapshotParser.dayGlance(dayRows, today)
        assertEquals(listOf("t1", "i1", "e1", "a1"), s.tasks.map { it.id })
        val run = s.tasks[0]
        assertEquals(7 * 60 + 45, run.startMin)
        assertEquals(45, run.durationMin)
        assertTrue(run.completed)
        assertEquals(Instant.parse("2026-10-19T14:30:00Z"), run.completedAt)
        assertEquals("p1", run.projectId)
        val inbox = s.tasks[1]
        assertNull(inbox.date)
        assertNull(inbox.startMin)
        assertEquals(2, inbox.priority)
        assertEquals(LocalDate.of(2026, 10, 21), inbox.deadline)
        assertEquals(TaskSource.CALENDAR, s.tasks[2].source)
        assertFalse(s.tasks[2].completable)
        assertTrue(s.tasks[3].isAllDay)
        assertEquals(setOf("w-new"), s.createdClientIds)
        assertEquals(setOf("w-done", "w-new"), s.reflected)
    }

    @Test
    fun `goals, projects, routines, frames and the day shape map across`() {
        val s = SnapshotParser.dayGlance(dayRows, today)
        assertEquals(LocalDate.of(2027, 1, 10), s.goals.single().targetDate)
        assertEquals(GoalStatus.ACTIVE, s.goals.single().status)
        assertEquals(GoalStatus.ARCHIVED, s.projects.first { it.id == "p1" }.status)
        // A project's own target date reads like a goal's; absent is null.
        assertEquals(LocalDate.of(2026, 11, 1), s.projects.first { it.id == "p2" }.targetDate)
        assertEquals(null, s.projects.first { it.id == "p1" }.targetDate)
        // Only the timed routine lands in the dial's band; its completion is today's.
        val routine = s.routines.single()
        assertEquals("r1", routine.id)
        assertEquals(12 * 60 + 30 to 12 * 60 + 45, routine.startMin to routine.endMin)
        assertEquals(setOf(today), routine.completedDates)
        // One Frame per template, with the exact days the provider listed.
        assertEquals(listOf("f1", "f2"), s.frames.map { it.id })
        assertEquals(setOf(today, today.plusDays(1)), s.frames[0].dates)
        assertEquals(8 * 60 + 30 to 12 * 60, s.frames[0].startMin to s.frames[0].endMin)
        assertEquals(setOf(today), s.frames[1].dates)
        // Each day's free time, as dayGLANCE computed it; a full day is an empty list, an
        // unreadable span is skipped, and a build that sends none leaves it null.
        assertEquals(mapOf(today to listOf(8 * 60 + 30 to 9 * 60, 10 * 60 to 12 * 60), today.plusDays(1) to emptyList()), s.frames[0].slotsByDate)
        assertEquals(null, s.frames[1].slotsByDate)
        assertEquals(390, s.dayShape!!.dayStartMin)
        assertTrue(s.dayShape!!.use24HourClock)
        assertEquals(39.74, s.dayShape!!.lat!!, 0.0)
        assertEquals(-104.99, s.dayShape!!.lon!!, 0.0)
        assertEquals(app.glance.launcher.data.AlarmPrefs(false, 1260), s.dayShape!!.dialAlarm)
        assertEquals(app.glance.launcher.data.DialLayers(weather = false, calendars = false), s.dayShape!!.dialLayers)
        // Weather: one day per row, hours keyed, nulls kept as nulls, an out-of-range hour dropped, a dateless row skipped.
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20)), s.weather.map { it.date })
        val todayWeather = s.weather[0]
        assertEquals("celsius", todayWeather.unit)
        assertEquals(setOf(9, 12, 13), todayWeather.hours.keys)
        assertEquals(app.glance.daydial.HourWeather(18, 61, 5.5), todayWeather.hours[12])
        assertEquals(app.glance.daydial.HourWeather(null, null, null), todayWeather.hours[13])
        assertTrue(s.weather[1].hours.isEmpty())
        assertEquals(Instant.ofEpochMilli(1760000000000), s.updatedAt)
        assertEquals(today, s.today)
    }

    @Test
    fun `an unanswered entry says whether the app closed its page`() {
        val log = SnapshotParser.appMeta(org.json.JSONObject(
            """{"refreshLog":[{"clientId":"a","outcome":"unanswered","pageClosed":true},{"clientId":"b","outcome":"unanswered"}]}""",
        )).refreshLog
        assertEquals(listOf(true, false), log.map { it.pageClosed })
    }

    @Test
    fun `meta carries the heartbeat, liveness and the refresh log`() {
        val m = SnapshotParser.dayGlance(dayRows, today).meta
        assertEquals(Instant.ofEpochMilli(1760000100000), m.checkedAt)
        assertEquals(Instant.parse("2026-10-19T08:00:00Z"), m.syncedAt)
        assertEquals(true, m.rendererAlive)
        assertEquals(listOf("queued", "ran", "deferred", "ran"), m.refreshLog.map { it.outcome })
        assertEquals(840L, m.refreshLog[1].durationMs)
        assertEquals("process not running", m.refreshLog[2].reason)
        assertEquals("dead", m.refreshLog[2].liveness)
        // A headless run: how long the OS held it, and that it was headless.
        assertEquals(20000L, m.refreshLog[3].waitMs)
        assertEquals(true, m.refreshLog[3].headless)
        assertEquals(null, m.refreshLog[1].waitMs)
        assertEquals(false, m.refreshLog[1].headless)
        assertEquals(false, m.headlessRunning)
        assertEquals(4, m.inboxCount)
        assertEquals(true, m.refreshLog[0].pending)
        assertEquals(false, m.refreshLog[3].pending)
        // An older app build without the heartbeat: nulls, not guesses.
        val older = SnapshotParser.dayGlance(mapOf("meta" to listOf("""{"version":1,"updatedAt":1760000000000}""")), today).meta
        assertNull(older.checkedAt)
        assertNull(older.rendererAlive)
        assertTrue(older.refreshLog.isEmpty())
        assertNull(older.inboxCount)
    }

    @Test
    fun `an empty or partial snapshot parses to empty collections`() {
        val s = SnapshotParser.dayGlance(mapOf("tasks" to emptyList()), today)
        assertTrue(s.tasks.isEmpty())
        assertNull(s.dayShape)
        assertTrue(s.weather.isEmpty())
        assertNull(s.updatedAt)
        assertTrue(s.reflected.isEmpty())
    }

    @Test
    fun `a day shape from a build without layer toggles draws every layer`() {
        val s = SnapshotParser.dayGlance(mapOf("dayShape" to listOf("""{"dayStartMin":390,"dayStopMin":1380,"sunriseMin":432,"sunsetMin":1104,"use24HourClock":false}""")), today)
        assertEquals(app.glance.launcher.data.DialLayers(), s.dayShape!!.dialLayers)
        // A layer the app does not name stays on.
        val partial = SnapshotParser.dayGlance(mapOf("dayShape" to listOf("""{"dayStartMin":390,"dayStopMin":1380,"sunriseMin":432,"sunsetMin":1104,"use24HourClock":false,"dialLayers":{"frames":false}}""")), today)
        assertEquals(app.glance.launcher.data.DialLayers(frames = false), partial.dayShape!!.dialLayers)
        val others = SnapshotParser.dayGlance(mapOf("dayShape" to listOf("""{"dayStartMin":390,"dayStopMin":1380,"sunriseMin":432,"sunsetMin":1104,"use24HourClock":false,"dialLayers":{"weather":false}}""")), today)
        assertEquals(app.glance.launcher.data.DialLayers(weather = false), others.dayShape!!.dialLayers)
    }

    @Test
    fun `chores and people map across, with the soonest date as the special one`() {
        val rows = mapOf(
            "meta" to listOf("""{"version":1,"updatedAt":1760000000000,"today":"2026-10-19","handledWrites":[{"clientId":"w-c","action":"create","entityId":"c2"}]}"""),
            "chores" to listOf(
                """{"id":"c1","name":"Vacuum","category":"Kitchen","categoryId":"cat-1","targetCadenceDays":7,"lastDoneAt":"2026-10-15T12:00:00.000Z","elapsedDays":4.1,"icon":"Droplets","updatedAt":"2026-10-02T00:00:00.000Z","clientId":null,"pinned":true}""",
                """{"id":"c2","name":"Mow","category":"Garden","categoryId":"cat-2","targetCadenceDays":null,"lastDoneAt":null,"elapsedDays":null,"icon":null,"updatedAt":"2026-10-02T00:00:00.000Z","clientId":"w-c"}""",
            ),
            "people" to listOf(
                """{"id":"p1","name":"Ana","nickname":null,"circle":"Family","circleId":"circle-1","targetCadenceDays":30,"lastInteractionAt":"2026-10-10T18:00:00.000Z","lastChannel":"text","unreturned":true,"snoozedUntil":null,
                    "dates":[{"id":"d1","kind":"birthday","label":null,"month":11,"day":2,"year":1986},{"id":"d2","kind":"custom","label":"Moved","month":10,"day":25,"year":null}],"updatedAt":"2026-10-03T00:00:00.000Z"}""",
                """{"id":"p2","name":"Ben","nickname":"B","circle":"","circleId":null,"targetCadenceDays":null,"lastInteractionAt":null,"lastChannel":null,"unreturned":false,"snoozedUntil":"2026-10-25T00:00:00.000Z","dates":[],"updatedAt":"2026-10-03T00:00:00.000Z"}""",
            ),
        )
        val s = SnapshotParser.lastGlance(rows, today)
        assertEquals(7, s.chores[0].targetCadenceDays)
        assertEquals(Instant.parse("2026-10-15T12:00:00Z"), s.chores[0].lastDoneAt)
        assertEquals("Droplets", s.chores[0].icon)
        assertTrue(s.chores[0].pinned)
        // A lastGLANCE that predates pinning sends no field: not pinned.
        assertFalse(s.chores[1].pinned)
        assertNull(s.chores[1].targetCadenceDays)
        assertNull(s.chores[1].lastDoneAt)
        assertEquals(setOf("w-c"), s.createdClientIds)
        val ana = s.people[0]
        assertEquals("Family", ana.circle)
        assertTrue(ana.unreturned)
        assertEquals(MonthDay.of(10, 25), ana.specialDate)
        assertEquals("Moved", ana.specialLabel)
        assertEquals(6L, ana.daysUntilSpecial(today))
        val ben = s.people[1]
        assertEquals("B", ben.nickname)
        assertNull(ben.specialDate)
        assertTrue(ben.isSnoozed(Instant.parse("2026-10-20T00:00:00Z")))
        assertFalse(ben.isSnoozed(Instant.parse("2026-10-26T00:00:00Z")))
    }

    @Test
    fun `time and date helpers are lenient`() {
        assertEquals(0, SnapshotParser.hhmm("00:00"))
        assertEquals(23 * 60 + 59, SnapshotParser.hhmm("23:59"))
        assertNull(SnapshotParser.hhmm(null))
        assertNull(SnapshotParser.hhmm("noon"))
        assertEquals(LocalDate.of(2026, 10, 19), SnapshotParser.date("2026-10-19T10:00:00"))
        assertNull(SnapshotParser.date("2026"))
        assertEquals(Instant.parse("2026-10-19T14:00:00Z"), SnapshotParser.instant("2026-10-19T08:00:00-06:00"))
        assertNull(SnapshotParser.instant("yesterday"))
    }
}
