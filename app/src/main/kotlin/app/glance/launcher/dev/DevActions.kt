package app.glance.launcher.dev

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import app.glance.launcher.data.Freshness
import app.glance.launcher.data.ProviderRepository
import app.glance.launcher.data.provider.GlanceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-build hooks for `tools/freshness-test.sh`, so the script drives the REAL path (the
 * launcher's own signature sends the REFRESH, the launcher's own provider reads report the
 * outcome) instead of `adb shell` pretending to be it. Both are no-ops in a non-debuggable
 * build. Driven by `am start -n app.glance.launcher/.LauncherActivity -a <action>`:
 *
 * - [ACTION_REFRESH] with `--es app dayglance|lastglance`: send one REFRESH, log its client id.
 * - [ACTION_DUMP]: re-read both providers and log one JSON line per app with its freshness
 *   and refresh log, tagged [TAG], for the script to parse from logcat. dayGLANCE's line also
 *   says what forecast and location its snapshot carried (the dial's weather ring).
 */
object DevActions {
    const val TAG = "GLANCEdev"
    /** logcat cuts a message at about 4 KB; a dump with twenty refresh entries is longer. */
    private const val PART_CHARS = 3000

    /** `meta app=<app> part=<i>/<n> <chunk>`, one line per part; tools/freshness-test.sh reassembles them. */
    private fun logParts(app: String, json: String) {
        val parts = json.chunked(PART_CHARS)
        parts.forEachIndexed { i, chunk -> Log.i(TAG, "meta app=$app part=${i + 1}/${parts.size} $chunk") }
    }
    const val ACTION_REFRESH = "app.glance.launcher.DEV_REFRESH"
    const val ACTION_DUMP = "app.glance.launcher.DEV_DUMP"

    fun handle(context: Context, intent: Intent?, repo: ProviderRepository, scope: CoroutineScope): Boolean {
        val action = intent?.action ?: return false
        if (action != ACTION_REFRESH && action != ACTION_DUMP) return false
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            Log.w(TAG, "ignored $action: not a debuggable build")
            return true
        }
        when (action) {
            ACTION_REFRESH -> {
                val app = when (intent.getStringExtra("app")?.lowercase()) {
                    "dayglance" -> GlanceApp.DAYGLANCE
                    "lastglance" -> GlanceApp.LASTGLANCE
                    else -> { Log.w(TAG, "refresh: --es app dayglance|lastglance"); return true }
                }
                scope.launch {
                    val id = repo.requestRefresh(app)
                    Log.i(TAG, "refresh app=${app.name.lowercase()} clientId=${id ?: "none(demo)"}")
                }
            }
            ACTION_DUMP -> scope.launch {
                repo.refreshAll()
                val a = repo.availability.value
                logParts("dayglance", dump(a.dayGlanceFreshness, repo.refreshRequests.value[GlanceApp.DAYGLANCE]?.clientId, repo.dayGlanceWeatherForDump()))
                logParts("lastglance", dump(a.lastGlanceFreshness, repo.refreshRequests.value[GlanceApp.LASTGLANCE]?.clientId))
            }
        }
        return true
    }

    fun dump(
        f: Freshness,
        lastRequest: String?,
        weather: Pair<List<app.glance.launcher.data.WeatherDay>, app.glance.launcher.data.DayShape?>? = null,
    ): String = JSONObject()
        .put("source", f.source.name.lowercase())
        .put("installed", f.installed)
        .put("updatedAt", f.updatedAt?.toEpochMilli() ?: JSONObject.NULL)
        .put("checkedAt", f.checkedAt?.toEpochMilli() ?: JSONObject.NULL)
        .put("syncedAt", f.syncedAt?.toEpochMilli() ?: JSONObject.NULL)
        .put("rendererAlive", f.rendererAlive ?: JSONObject.NULL)
        .put("headlessRunning", f.headlessRunning ?: JSONObject.NULL)
        .put("lastRequest", lastRequest ?: JSONObject.NULL)
        .apply {
            if (weather != null) {
                // "2026-10-22 24h fahrenheit" per forecast day; empty when the snapshot carried none.
                put("weather", JSONArray(weather.first.map { w ->
                    "${w.date} ${w.hours.count { it.value.temp != null }}h ${w.unit}"
                }))
                put("location", weather.second?.let { s -> s.lat != null && s.lon != null } ?: false)
            }
        }
        .put("refreshLog", JSONArray().also { arr ->
            for (e in f.refreshLog) arr.put(JSONObject()
                .put("clientId", e.clientId)
                .put("requestedAt", e.requestedAt?.toEpochMilli() ?: JSONObject.NULL)
                .put("outcome", e.outcome)
                .put("reason", e.reason ?: JSONObject.NULL)
                .put("liveness", e.liveness ?: JSONObject.NULL)
                .put("durationMs", e.durationMs ?: JSONObject.NULL)
                .put("finishedAt", e.finishedAt?.toEpochMilli() ?: JSONObject.NULL)
                .put("waitMs", e.waitMs ?: JSONObject.NULL)
                .put("headless", e.headless)
                .put("pageClosed", e.pageClosed))
        })
        .toString()
}
