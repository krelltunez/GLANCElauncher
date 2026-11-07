package app.glance.launcher.data.provider

import android.content.Context
import app.glance.launcher.data.Chore
import app.glance.launcher.data.Person
import app.glance.launcher.data.Routine
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskColors
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * One write the launcher has sent and not yet seen reflected in a snapshot (the launcher
 * docs/spec.md, "Real data" 4). [patch] is what the write means for the launcher's own copy, applied
 * over every snapshot until the app shows [clientId] in its ledger or on the row it created.
 */
data class OutboxEntry(
    val clientId: String,
    val app: GlanceApp,
    val action: String,
    val payload: String,
    val createdAt: Instant,
    val patch: Patch,
) {
    fun expired(now: Instant): Boolean = Duration.between(createdAt, now) > Outbox.EXPIRY
}

/** The optimistic change a pending write makes to the launcher's copy of the data. */
sealed class Patch {
    data class CompleteTask(val taskId: String, val at: Instant) : Patch()
    data class UncompleteTask(val taskId: String) : Patch()
    /** [task]'s id is the write's client id, which is how the created row is matched later. */
    data class CreateTask(val task: Task) : Patch()
    data class MoveTask(val taskId: String, val date: LocalDate, val startMin: Int?) : Patch()
    data class ResizeTask(val taskId: String, val durationMin: Int) : Patch()
    /**
     * [completed] is the state the tap asked for, set rather than flipped, so a snapshot
     * that already shows it is left as it is. Null on an entry saved before it was
     * recorded: that one still flips.
     */
    data class ToggleRoutine(val routineId: String, val date: LocalDate, val completed: Boolean? = null) : Patch()
    data class LogChore(val choreId: String, val at: Instant) : Patch()
    data class AddChore(val chore: Chore) : Patch()
    data class LogInteraction(val personId: String, val at: Instant) : Patch()
}

object Outbox {
    /** A write the app has not reflected after this long is dropped and the launcher's copy snaps back. */
    val EXPIRY: Duration = Duration.ofHours(24)

    fun applyToTasks(tasks: List<Task>, entries: List<OutboxEntry>): List<Task> {
        var out = tasks
        for (e in entries) {
            when (val p = e.patch) {
                is Patch.CompleteTask -> out = out.map { if (it.id == p.taskId && it.completable) it.copy(completed = true, completedAt = p.at) else it }
                is Patch.UncompleteTask -> out = out.map { if (it.id == p.taskId && it.completable) it.copy(completed = false, completedAt = null) else it }
                is Patch.CreateTask -> if (out.none { it.id == p.task.id }) out = out + p.task
                is Patch.MoveTask -> out = out.map {
                    when {
                        it.id != p.taskId -> it
                        p.startMin == null -> it.copy(date = p.date)
                        it.date == null -> it.copy(date = p.date, startMin = p.startMin, isAllDay = false, deadline = null, priority = 0)
                        else -> it.copy(date = p.date, startMin = p.startMin, isAllDay = false)
                    }
                }
                is Patch.ResizeTask -> out = out.map { if (it.id == p.taskId) it.copy(durationMin = p.durationMin.coerceAtLeast(5)) else it }
                else -> {}
            }
        }
        return out
    }

    fun applyToRoutines(routines: List<Routine>, entries: List<OutboxEntry>): List<Routine> {
        var out = routines
        for (e in entries) {
            val p = e.patch as? Patch.ToggleRoutine ?: continue
            out = out.map { r ->
                if (r.id != p.routineId) r
                else {
                    val done = p.completed ?: (p.date !in r.completedDates)
                    r.copy(completedDates = if (done) r.completedDates + p.date else r.completedDates - p.date)
                }
            }
        }
        return out
    }

    fun applyToChores(chores: List<Chore>, entries: List<OutboxEntry>): List<Chore> {
        var out = chores
        for (e in entries) {
            when (val p = e.patch) {
                is Patch.LogChore -> out = out.map { if (it.id == p.choreId && (it.lastDoneAt == null || it.lastDoneAt < p.at)) it.copy(lastDoneAt = p.at) else it }
                is Patch.AddChore -> if (out.none { it.id == p.chore.id }) out = out + p.chore
                else -> {}
            }
        }
        return out
    }

    fun applyToPeople(people: List<Person>, entries: List<OutboxEntry>): List<Person> {
        var out = people
        for (e in entries) {
            val p = e.patch as? Patch.LogInteraction ?: continue
            out = out.map {
                if (it.id == p.personId && (it.lastInteractionAt == null || it.lastInteractionAt < p.at)) it.copy(lastInteractionAt = p.at, unreturned = false) else it
            }
        }
        return out
    }

    // region persistence

    fun encode(entries: List<OutboxEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject()
                .put("clientId", e.clientId)
                .put("app", e.app.name)
                .put("action", e.action)
                .put("payload", e.payload)
                .put("createdAt", e.createdAt.toEpochMilli())
                .put("patch", encodePatch(e.patch)))
        }
        return arr.toString()
    }

    fun decode(json: String?): List<OutboxEntry> {
        if (json.isNullOrEmpty()) return emptyList()
        val arr = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<OutboxEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val entry = try {
                OutboxEntry(
                    clientId = o.getString("clientId"),
                    app = GlanceApp.valueOf(o.getString("app")),
                    action = o.getString("action"),
                    payload = o.optString("payload", "{}"),
                    createdAt = Instant.ofEpochMilli(o.getLong("createdAt")),
                    patch = decodePatch(o.getJSONObject("patch")) ?: continue,
                )
            } catch (e: Exception) { continue }
            out.add(entry)
        }
        return out
    }

    private fun encodePatch(p: Patch): JSONObject = when (p) {
        is Patch.CompleteTask -> JSONObject().put("type", "completeTask").put("taskId", p.taskId).put("at", p.at.toEpochMilli())
        is Patch.UncompleteTask -> JSONObject().put("type", "uncompleteTask").put("taskId", p.taskId)
        is Patch.CreateTask -> JSONObject().put("type", "createTask").put("task", encodeTask(p.task))
        is Patch.MoveTask -> JSONObject().put("type", "moveTask").put("taskId", p.taskId).put("date", p.date.toString()).put("startMin", p.startMin ?: JSONObject.NULL)
        is Patch.ResizeTask -> JSONObject().put("type", "resizeTask").put("taskId", p.taskId).put("durationMin", p.durationMin)
        is Patch.ToggleRoutine -> JSONObject().put("type", "toggleRoutine").put("routineId", p.routineId).put("date", p.date.toString())
            .apply { p.completed?.let { put("completed", it) } }
        is Patch.LogChore -> JSONObject().put("type", "logChore").put("choreId", p.choreId).put("at", p.at.toEpochMilli())
        is Patch.AddChore -> JSONObject().put("type", "addChore").put("id", p.chore.id).put("name", p.chore.name).put("category", p.chore.category)
        is Patch.LogInteraction -> JSONObject().put("type", "logInteraction").put("personId", p.personId).put("at", p.at.toEpochMilli())
    }

    private fun decodePatch(o: JSONObject): Patch? = when (o.optString("type")) {
        "completeTask" -> Patch.CompleteTask(o.getString("taskId"), Instant.ofEpochMilli(o.getLong("at")))
        "uncompleteTask" -> Patch.UncompleteTask(o.getString("taskId"))
        "createTask" -> decodeTask(o.getJSONObject("task"))?.let { Patch.CreateTask(it) }
        "moveTask" -> Patch.MoveTask(o.getString("taskId"), LocalDate.parse(o.getString("date")), if (o.isNull("startMin")) null else o.getInt("startMin"))
        "resizeTask" -> Patch.ResizeTask(o.getString("taskId"), o.getInt("durationMin"))
        "toggleRoutine" -> Patch.ToggleRoutine(o.getString("routineId"), LocalDate.parse(o.getString("date")), if (o.has("completed")) o.getBoolean("completed") else null)
        "logChore" -> Patch.LogChore(o.getString("choreId"), Instant.ofEpochMilli(o.getLong("at")))
        "addChore" -> Patch.AddChore(Chore(o.getString("id"), o.getString("name"), o.optString("category", "Inbox")))
        "logInteraction" -> Patch.LogInteraction(o.getString("personId"), Instant.ofEpochMilli(o.getLong("at")))
        else -> null
    }

    private fun encodeTask(t: Task): JSONObject = JSONObject()
        .put("id", t.id).put("title", t.title)
        .put("date", t.date?.toString() ?: JSONObject.NULL)
        .put("startMin", t.startMin ?: JSONObject.NULL)
        .put("durationMin", t.durationMin).put("isAllDay", t.isAllDay)
        .put("priority", t.priority)
        .put("deadline", t.deadline?.toString() ?: JSONObject.NULL)
        .put("colorHex", t.colorHex)
        .put("projectId", t.projectId ?: JSONObject.NULL)

    private fun decodeTask(o: JSONObject): Task? = try {
        Task(
            id = o.getString("id"), title = o.getString("title"),
            date = if (o.isNull("date")) null else LocalDate.parse(o.getString("date")),
            startMin = if (o.isNull("startMin")) null else o.getInt("startMin"),
            durationMin = o.optInt("durationMin", 30), isAllDay = o.optBoolean("isAllDay", false),
            priority = o.optInt("priority", 0),
            deadline = if (o.isNull("deadline")) null else LocalDate.parse(o.getString("deadline")),
            colorHex = o.optString("colorHex", TaskColors.BLUE),
            projectId = if (o.isNull("projectId")) null else o.getString("projectId"),
        )
    } catch (e: Exception) { null }

    // endregion
}

/** Where the outbox survives the launcher being killed. */
interface OutboxStore {
    fun load(): String?
    fun save(json: String)
}

class PrefsOutboxStore(context: Context) : OutboxStore {
    private val sp = context.getSharedPreferences("glance_launcher_outbox", Context.MODE_PRIVATE)
    override fun load(): String? = sp.getString("entries", null)
    // Synchronous, like LauncherPrefs: a write must be on disk before the broadcast goes out.
    override fun save(json: String) { sp.edit().putString("entries", json).commit() }
}

class MemoryOutboxStore(private var json: String? = null) : OutboxStore {
    override fun load(): String? = json
    override fun save(json: String) { this.json = json }
}
