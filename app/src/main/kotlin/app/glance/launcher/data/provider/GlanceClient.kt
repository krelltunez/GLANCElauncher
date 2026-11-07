package app.glance.launcher.data.provider

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

/** The two apps, with the authority each serves its launcher snapshot on and the receiver that takes writes. */
enum class GlanceApp(val packageName: String, val authority: String, val receiver: String, val collections: List<String>) {
    DAYGLANCE("com.dayglance.app", "com.dayglance.app.launcher", "com.dayglance.app.launcher.LauncherIntentReceiver",
        // Every collection SnapshotParser.dayGlance reads: one left out here is never queried, and
        // its part of the launcher stays empty with nothing failing (the weather ring did).
        listOf("meta", "tasks", "goals", "projects", "routines", "frames", "dayShape", "weather")),
    LASTGLANCE("com.lastglance.app", "com.lastglance.app.launcher", "com.lastglance.app.launcher.LauncherIntentReceiver",
        listOf("meta", "chores", "people"));

    /** `com.dayglance.app.launcher.COMPLETE` and the like. */
    fun action(name: String): String = "$authority.$name"
}

/**
 * What [app.glance.launcher.data.ProviderRepository] needs from Android, behind an interface so
 * the repository is tested on the JVM with a scripted client. [AndroidGlanceClient] is the real one.
 */
interface GlanceClient {
    /** `knownSigner` exists from Android 12; below that the apps' permission is signature-only and real data is off. */
    val realDataSupported: Boolean

    fun isInstalled(app: GlanceApp): Boolean

    /**
     * Every row of each of [app]'s collections, as the JSON text of its `json` column, or null when
     * the provider cannot be read: the app is not installed, is older than its launcher integration,
     * or refuses the launcher's signature.
     */
    fun read(app: GlanceApp): Map<String, List<String>>?

    /** Broadcast one launcher write to [app]'s receiver: `payload` JSON and the `clientId` beside it. */
    fun send(app: GlanceApp, action: String, payload: String, clientId: String)

    /** Call [onChange] whenever [app]'s provider notifies; the returned handle stops it. */
    fun observe(app: GlanceApp, onChange: () -> Unit): AutoCloseable
}

class AndroidGlanceClient(private val context: Context) : GlanceClient {
    override val realDataSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun isInstalled(app: GlanceApp): Boolean = try {
        context.packageManager.getPackageInfo(app.packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun read(app: GlanceApp): Map<String, List<String>>? {
        val out = HashMap<String, List<String>>()
        for (collection in app.collections) {
            val uri = Uri.parse("content://${app.authority}/$collection")
            val rows = ArrayList<String>()
            try {
                val cursor = context.contentResolver.query(uri, arrayOf("json"), null, null, null) ?: return null
                cursor.use {
                    val col = it.getColumnIndex("json")
                    if (col < 0) return null
                    while (it.moveToNext()) rows.add(it.getString(col))
                }
            } catch (e: Exception) {
                // SecurityException (not our signer), IllegalArgumentException (unknown authority) and
                // anything else all mean the same thing to the launcher: no live data from this app.
                return null
            }
            out[collection] = rows
        }
        return out
    }

    override fun send(app: GlanceApp, action: String, payload: String, clientId: String) {
        val intent = Intent(action)
            .setComponent(ComponentName(app.packageName, app.receiver))
            .putExtra("payload", payload)
            .putExtra("clientId", clientId)
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // The outbox keeps the write; it is re-sent on the next tick.
        }
    }

    override fun observe(app: GlanceApp, onChange: () -> Unit): AutoCloseable {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        val uri = Uri.parse("content://${app.authority}")
        try {
            context.contentResolver.registerContentObserver(uri, true, observer)
        } catch (e: Exception) {
            return AutoCloseable { }
        }
        return AutoCloseable {
            try { context.contentResolver.unregisterContentObserver(observer) } catch (e: Exception) { /* already gone */ }
        }
    }
}
