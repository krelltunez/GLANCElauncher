package app.glance.launcher.data.provider

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last rows read from each app's provider, kept so a cold start shows them at once instead
 * of the demo while the providers are read again. The rows are kept as the provider gave them and
 * parsed again on load, so the cache never needs its own format for tasks, chores and the rest.
 */
interface SnapshotCache {
    fun load(app: GlanceApp): Map<String, List<String>>?
    /** Keep [rows] for [app]; null forgets it. */
    fun save(app: GlanceApp, rows: Map<String, List<String>>?)

    companion object {
        fun encode(rows: Map<String, List<String>>): String {
            val o = JSONObject()
            for ((collection, list) in rows) o.put(collection, JSONArray(list))
            return o.toString()
        }

        fun decode(json: String?): Map<String, List<String>>? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                o.keys().asSequence().associateWith { k ->
                    val a = o.getJSONArray(k)
                    List(a.length()) { a.getString(it) }
                }
            } catch (e: org.json.JSONException) {
                null
            }
        }
    }
}

/**
 * One file per app under `noBackupFilesDir`: it is a copy of the user's data in the other apps,
 * so it stays out of device backups, and it is rewritten only when the rows change.
 */
class FileSnapshotCache(context: Context) : SnapshotCache {
    private val dir = context.noBackupFilesDir
    private val written = HashMap<GlanceApp, String?>()

    private fun file(app: GlanceApp) = File(dir, "snapshot-${app.name.lowercase()}.json")

    override fun load(app: GlanceApp): Map<String, List<String>>? {
        val json = try { file(app).takeIf { it.exists() }?.readText() } catch (e: java.io.IOException) { null }
        synchronized(written) { written[app] = json }
        return SnapshotCache.decode(json)
    }

    override fun save(app: GlanceApp, rows: Map<String, List<String>>?) {
        val json = rows?.let { SnapshotCache.encode(it) }
        synchronized(written) {
            if (written.containsKey(app) && written[app] == json) return
            written[app] = json
        }
        try {
            val f = file(app)
            if (json == null) {
                f.delete()
            } else {
                // Write beside it and rename, so a kill mid-write never leaves half a file.
                val tmp = File(dir, f.name + ".tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            }
        } catch (e: java.io.IOException) {
            // A cache: losing it only costs the next cold start its head start.
            synchronized(written) { written.remove(app) }
        }
    }
}

class MemorySnapshotCache : SnapshotCache {
    val rows = HashMap<GlanceApp, Map<String, List<String>>>()
    override fun load(app: GlanceApp) = rows[app]
    override fun save(app: GlanceApp, rows: Map<String, List<String>>?) {
        if (rows == null) this.rows.remove(app) else this.rows[app] = rows
    }
}
