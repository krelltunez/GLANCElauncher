package app.glance.launcher.data.provider

import app.glance.daydial.HourWeather
import app.glance.launcher.data.AlarmPrefs
import app.glance.launcher.data.DialLayers
import app.glance.launcher.data.Chore
import app.glance.launcher.data.DayShape
import app.glance.launcher.data.Frame
import app.glance.launcher.data.Goal
import app.glance.launcher.data.GoalStatus
import app.glance.launcher.data.Person
import app.glance.launcher.data.Project
import app.glance.launcher.data.Routine
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskColors
import app.glance.launcher.data.TaskSource
import app.glance.launcher.data.WeatherDay
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

/** One entry of an app's handled-writes ledger (`meta.handledWrites`). */
data class HandledWrite(val clientId: String, val action: String?, val entityId: String?)

/** One REFRESH the app logged (`meta.refreshLog`): what the receiver did with it and how the web layer's run went. */
data class RefreshEntry(
    val clientId: String,
    val requestedAt: Instant?,
    /** queued, coalesced, scheduled, deferred, ran, skipped, failed, unanswered, aborted, throttled (the apps' docs, "Freshness"). */
    val outcome: String,
    val reason: String?,
    /** alive or dead, as the receiver saw the renderer. */
    val liveness: String?,
    val durationMs: Long?,
    val finishedAt: Instant?,
    /** How long the OS held a scheduled headless run before it started (dayGLANCE); null otherwise. */
    val waitMs: Long? = null,
    /** True when a headless run answered it. */
    val headless: Boolean = false,
    /**
     * True on an `unanswered` entry when the app closed its unresponsive page afterwards and
     * scheduled a headless run, logged as `<clientId>-headless` (both apps' LauncherPageRetire).
     */
    val pageClosed: Boolean = false,
) {
    /** No outcome yet: the request is with the app, or with the OS. */
    val pending: Boolean get() = outcome in PENDING

    companion object {
        val PENDING = setOf("queued", "scheduled")
    }
}

/** The app's meta row beyond the ledger: the stamps the launcher measures freshness from. */
data class AppMeta(
    val updatedAt: Instant?,
    /** When the app last looked (the heartbeat). */
    val checkedAt: Instant?,
    /** When a sync tier last completed a cycle; null without one. */
    val syncedAt: Instant?,
    /** Whether the app's renderer was alive at the moment of the read. */
    val rendererAlive: Boolean?,
    val refreshLog: List<RefreshEntry>,
    /** Whether a headless refresh was running at the moment of the read (dayGLANCE); null when the app does not say. */
    val headlessRunning: Boolean? = null,
    /** dayGLANCE's Inbox count as the app shows it (its filters and user lens); null from a build that sends none. */
    val inboxCount: Int? = null,
) {
    companion object {
        val EMPTY = AppMeta(null, null, null, null, emptyList())
    }
}

/** dayGLANCE's launcher snapshot as the launcher's models (its `docs/launcher-integration.md`). */
data class DayGlanceSnapshot(
    val tasks: List<Task>,
    val goals: List<Goal>,
    val projects: List<Project>,
    val routines: List<Routine>,
    val frames: List<Frame>,
    val dayShape: DayShape?,
    val weather: List<WeatherDay>,
    val updatedAt: Instant?,
    val today: LocalDate?,
    val handledWrites: List<HandledWrite>,
    /** Client ids carried by the rows the launcher created, the other way a write shows as landed. */
    val createdClientIds: Set<String>,
    val meta: AppMeta = AppMeta.EMPTY,
) {
    val reflected: Set<String> get() = handledWrites.mapTo(HashSet()) { it.clientId } + createdClientIds
}

/** lastGLANCE's launcher snapshot as the launcher's models (its `docs/launcher-integration.md`). */
data class LastGlanceSnapshot(
    val chores: List<Chore>,
    val people: List<Person>,
    val updatedAt: Instant?,
    val handledWrites: List<HandledWrite>,
    val createdClientIds: Set<String>,
    val meta: AppMeta = AppMeta.EMPTY,
) {
    val reflected: Set<String> get() = handledWrites.mapTo(HashSet()) { it.clientId } + createdClientIds
}

/**
 * Turns the JSON rows the two providers serve into the launcher's models. Pure over the row text
 * so it is tested on the JVM; a malformed row is skipped, never fatal, since a snapshot the
 * launcher cannot read in full is still better than none.
 */
object SnapshotParser {

    fun dayGlance(rows: Map<String, List<String>>, today: LocalDate): DayGlanceSnapshot {
        val meta = rows["meta"]?.firstOrNull()?.let { obj(it) }
        val snapshotToday = meta?.let { date(it.str("today")) } ?: today
        val tasks = rows.objects("tasks").mapNotNull { task(it) }
        return DayGlanceSnapshot(
            tasks = tasks,
            goals = rows.objects("goals").mapNotNull { goal(it) },
            projects = rows.objects("projects").mapNotNull { project(it) },
            routines = rows.objects("routines").mapNotNull { routine(it, snapshotToday) },
            frames = frames(rows.objects("frames")),
            dayShape = rows["dayShape"]?.firstOrNull()?.let { obj(it) }?.let { dayShape(it) },
            weather = rows.objects("weather").mapNotNull { weatherDay(it) },
            updatedAt = meta?.let { instantMillis(it, "updatedAt") },
            today = meta?.let { date(it.str("today")) },
            handledWrites = handledWrites(meta),
            createdClientIds = rows.objects("tasks").mapNotNullTo(HashSet()) { it.str("clientId") },
            meta = appMeta(meta),
        )
    }

    fun lastGlance(rows: Map<String, List<String>>, today: LocalDate): LastGlanceSnapshot {
        val meta = rows["meta"]?.firstOrNull()?.let { obj(it) }
        val chores = rows.objects("chores")
        return LastGlanceSnapshot(
            chores = chores.mapNotNull { chore(it) },
            people = rows.objects("people").mapNotNull { person(it, today) },
            updatedAt = meta?.let { instantMillis(it, "updatedAt") },
            handledWrites = handledWrites(meta),
            createdClientIds = chores.mapNotNullTo(HashSet()) { it.str("clientId") },
            meta = appMeta(meta),
        )
    }

    // region dayGLANCE rows

    private fun task(o: JSONObject): Task? {
        val id = o.str("id") ?: return null
        return Task(
            id = id,
            title = o.str("title") ?: "",
            date = date(o.str("date")),
            startMin = hhmm(o.str("startTime")),
            durationMin = o.optInt("duration", 30),
            isAllDay = o.optBoolean("isAllDay", false),
            completed = o.optBoolean("completed", false),
            completedAt = instant(o.str("completedAt")),
            colorHex = o.str("colorHex") ?: TaskColors.BLUE,
            priority = o.optInt("priority", 0),
            notes = o.str("notes") ?: "",
            projectId = o.str("projectId"),
            deadline = date(o.str("deadline")),
            source = if (o.str("source") == "calendar") TaskSource.CALENDAR else TaskSource.DAYGLANCE,
            energy = o.str("energy"),
        )
    }

    private fun goal(o: JSONObject): Goal? {
        val id = o.str("id") ?: return null
        return Goal(id, o.str("title") ?: "", o.str("description") ?: "", o.str("colorHex") ?: TaskColors.BLUE, date(o.str("targetDate")), status(o.str("status")))
    }

    private fun project(o: JSONObject): Project? {
        val id = o.str("id") ?: return null
        return Project(id, o.str("title") ?: "", o.str("goalId"), o.str("colorHex") ?: TaskColors.BLUE, o.str("description") ?: "", status(o.str("status")), date(o.str("targetDate")))
    }

    private fun status(s: String?): GoalStatus = when (s?.lowercase()) {
        "completed", "done" -> GoalStatus.COMPLETED
        "archived" -> GoalStatus.ARCHIVED
        "on-hold" -> GoalStatus.ON_HOLD
        else -> GoalStatus.ACTIVE
    }

    /** Today's routines; an untimed one has no place in the dial's band and is left out. */
    private fun routine(o: JSONObject, today: LocalDate): Routine? {
        val id = o.str("id") ?: return null
        val start = hhmm(o.str("startTime")) ?: return null
        val end = (start + o.optInt("duration", 15)).coerceAtMost(24 * 60)
        return Routine(id, o.str("name") ?: "", start, end, if (o.optBoolean("completed", false)) setOf(today) else emptySet())
    }

    /** The provider lists one instance per day; the launcher's Frame is one per template with the exact days it applies to. */
    private fun frames(instances: List<JSONObject>): List<Frame> {
        val byTemplate = LinkedHashMap<String, Pair<Frame, MutableSet<LocalDate>>>()
        val slots = HashMap<String, MutableMap<LocalDate, List<Pair<Int, Int>>>>()
        for (o in instances) {
            val frameId = o.str("frameId") ?: o.str("id")?.substringBefore(':') ?: continue
            val date = date(o.str("date")) ?: continue
            val entry = byTemplate.getOrPut(frameId) {
                val start = hhmm(o.str("start")) ?: 0
                val end = hhmm(o.str("end")) ?: start
                Frame(frameId, o.str("name") ?: "", start, end, o.str("colorHex") ?: "#c8c8d2") to LinkedHashSet()
            }
            entry.second.add(date)
            // The day's free time, when the build sends it; an unreadable span is skipped.
            val spans = o.optJSONArray("slots") ?: continue
            slots.getOrPut(frameId) { HashMap() }[date] = (0 until spans.length()).mapNotNull { i ->
                val span = spans.optJSONObject(i) ?: return@mapNotNull null
                val s = hhmm(span.str("start")) ?: return@mapNotNull null
                val e = hhmm(span.str("end")) ?: return@mapNotNull null
                if (e > s) s to e else null
            }
        }
        return byTemplate.map { (id, entry) -> entry.first.copy(dates = entry.second, slotsByDate = slots[id]) }
    }

    private fun dayShape(o: JSONObject): DayShape = DayShape(
        dayStartMin = o.optInt("dayStartMin", 6 * 60),
        dayStopMin = o.optInt("dayStopMin", 23 * 60),
        sunriseMin = o.optInt("sunriseMin", 7 * 60),
        sunsetMin = o.optInt("sunsetMin", 18 * 60),
        use24HourClock = o.optBoolean("use24HourClock", false),
        lat = if (o.isNull("lat")) null else o.optDouble("lat").takeIf { it.isFinite() },
        lon = if (o.isNull("lon")) null else o.optDouble("lon").takeIf { it.isFinite() },
        dialAlarm = o.optJSONObject("dialAlarm")?.let { a ->
            val from = a.optInt("fromMin", 18 * 60)
            AlarmPrefs(a.optBoolean("on", true), if (from in 0 until 1440) from else 18 * 60)
        },
        // A build that sends none, or a layer it does not name, draws: the dial as before.
        dialLayers = o.optJSONObject("dialLayers")?.let { l ->
            DialLayers(
                solar = l.optBoolean("solar", true), weather = l.optBoolean("weather", true),
                calendars = l.optBoolean("calendars", true), routines = l.optBoolean("routines", true),
                frames = l.optBoolean("frames", true),
            )
        } ?: DialLayers(),
    )

    private fun weatherDay(o: JSONObject): WeatherDay? {
        val date = date(o.str("date") ?: o.str("id")) ?: return null
        val arr = o.optJSONArray("hours") ?: return null
        val hours = HashMap<Int, HourWeather>()
        for (i in 0 until arr.length()) {
            val h = arr.optJSONObject(i) ?: continue
            val hour = h.optInt("hour", -1)
            if (hour !in 0..23) continue
            hours[hour] = HourWeather(
                temp = if (h.isNull("temp")) null else h.optInt("temp"),
                code = if (h.isNull("code")) null else h.optInt("code"),
                uv = if (h.isNull("uv")) null else h.optDouble("uv").takeIf { it.isFinite() },
            )
        }
        return WeatherDay(date, if (o.str("unit") == "celsius") "celsius" else "fahrenheit", hours)
    }

    // endregion

    // region lastGLANCE rows

    private fun chore(o: JSONObject): Chore? {
        val id = o.str("id") ?: return null
        return Chore(
            id = id,
            name = o.str("name") ?: "",
            category = o.str("category") ?: "",
            targetCadenceDays = if (o.isNull("targetCadenceDays")) null else o.optInt("targetCadenceDays"),
            lastDoneAt = instant(o.str("lastDoneAt")),
            icon = o.str("icon"),
            pinned = o.optBoolean("pinned", false),
        )
    }

    private fun person(o: JSONObject, today: LocalDate): Person? {
        val id = o.str("id") ?: return null
        // The soonest upcoming date is the one the launcher shows under Coming up.
        var best: Pair<Long, JSONObject>? = null
        val dates = o.optJSONArray("dates") ?: JSONArray()
        for (i in 0 until dates.length()) {
            val d = dates.optJSONObject(i) ?: continue
            val month = d.optInt("month", 0)
            val day = d.optInt("day", 0)
            if (month !in 1..12 || day !in 1..31) continue
            val md = try { MonthDay.of(month, day) } catch (e: Exception) { continue }
            var next = md.atYear(today.year)
            if (next.isBefore(today)) next = md.atYear(today.year + 1)
            val days = ChronoUnit.DAYS.between(today, next)
            if (best == null || days < best.first) best = days to d
        }
        val special = best?.second
        return Person(
            id = id,
            name = o.str("name") ?: "",
            nickname = o.str("nickname"),
            circle = o.str("circle") ?: "",
            targetCadenceDays = if (o.isNull("targetCadenceDays")) null else o.optInt("targetCadenceDays"),
            lastInteractionAt = instant(o.str("lastInteractionAt")),
            lastChannel = o.str("lastChannel"),
            unreturned = o.optBoolean("unreturned", false),
            specialDate = special?.let { MonthDay.of(it.optInt("month"), it.optInt("day")) },
            specialLabel = special?.let { dateLabel(it) } ?: "Birthday",
            snoozedUntil = instant(o.str("snoozedUntil")),
        )
    }

    private fun dateLabel(d: JSONObject): String = when (d.str("kind")) {
        "birthday" -> "Birthday"
        "anniversary" -> "Anniversary"
        else -> d.str("label") ?: "Date"
    }

    // endregion

    // region JSON helpers

    private fun obj(json: String): JSONObject? = try { JSONObject(json) } catch (e: Exception) { null }

    private fun Map<String, List<String>>.objects(collection: String): List<JSONObject> =
        this[collection]?.mapNotNull { obj(it) } ?: emptyList()

    /** A string value, with JSON null and absence both read as null. */
    private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

    /** The meta row's stamps, liveness and refresh log; empty when there is no meta row. */
    fun appMeta(meta: JSONObject?): AppMeta {
        if (meta == null) return AppMeta.EMPTY
        val log = ArrayList<RefreshEntry>()
        meta.optJSONArray("refreshLog")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val clientId = o.str("clientId") ?: continue
                log.add(RefreshEntry(
                    clientId = clientId,
                    requestedAt = instantMillis(o, "requestedAt"),
                    outcome = o.str("outcome") ?: "unknown",
                    reason = o.str("reason"),
                    liveness = o.str("liveness"),
                    durationMs = if (o.isNull("durationMs")) null else o.optLong("durationMs"),
                    finishedAt = instantMillis(o, "finishedAt"),
                    waitMs = if (o.isNull("waitMs")) null else o.optLong("waitMs"),
                    headless = o.optBoolean("headless", false),
                    pageClosed = o.optBoolean("pageClosed", false),
                ))
            }
        }
        return AppMeta(
            updatedAt = instantMillis(meta, "updatedAt"),
            checkedAt = instantMillis(meta, "checkedAt"),
            syncedAt = instant(meta.str("syncedAt")),
            rendererAlive = if (meta.isNull("rendererAlive")) null else meta.optBoolean("rendererAlive"),
            refreshLog = log,
            headlessRunning = if (meta.isNull("headlessRunning")) null else meta.optBoolean("headlessRunning"),
            inboxCount = if (meta.isNull("inboxCount")) null else meta.optInt("inboxCount"),
        )
    }

    private fun handledWrites(meta: JSONObject?): List<HandledWrite> {
        val arr = meta?.optJSONArray("handledWrites") ?: return emptyList()
        val out = ArrayList<HandledWrite>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val clientId = o.str("clientId") ?: continue
            out.add(HandledWrite(clientId, o.str("action"), o.str("entityId") ?: o.str("taskId")))
        }
        return out
    }

    /** "HH:MM" to minutes past midnight. */
    fun hhmm(s: String?): Int? {
        if (s == null) return null
        val parts = s.split(':')
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].take(2).toIntOrNull() ?: return null
        if (h !in 0..24 || m !in 0..59) return null
        return h * 60 + m
    }

    fun date(s: String?): LocalDate? = if (s == null || s.length < 10) null else try { LocalDate.parse(s.take(10)) } catch (e: Exception) { null }

    fun instant(s: String?): Instant? {
        if (s.isNullOrEmpty()) return null
        return try { Instant.parse(s) } catch (e: Exception) {
            try { OffsetDateTime.parse(s).toInstant() } catch (e2: Exception) { null }
        }
    }

    private fun instantMillis(o: JSONObject, key: String): Instant? {
        if (o.isNull(key)) return null
        val n = o.optLong(key, 0L)
        return if (n > 0) Instant.ofEpochMilli(n) else instant(o.str(key))
    }

    // endregion
}
