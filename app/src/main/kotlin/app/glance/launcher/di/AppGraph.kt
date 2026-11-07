package app.glance.launcher.di

import android.content.Context
import app.glance.launcher.apps.AppsRepository
import app.glance.launcher.data.FakeRepository
import app.glance.launcher.data.GlanceRepository
import app.glance.launcher.data.ProviderRepository
import app.glance.launcher.data.provider.AndroidGlanceClient
import app.glance.launcher.data.provider.PrefsOutboxStore
import app.glance.launcher.prefs.LauncherPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency graph. One object, created once by [app.glance.launcher.LauncherApp].
 *
 * The repository is [ProviderRepository] over the two apps' ContentProviders, with
 * [FakeRepository] as the demo it falls back to, per app, while a provider is missing. The
 * binding below is still the single line that selects the backend.
 */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs: LauncherPrefs = LauncherPrefs(context)

    private val demo = FakeRepository()

    val providerRepository: ProviderRepository = ProviderRepository(
        client = AndroidGlanceClient(context),
        fallback = demo,
        store = PrefsOutboxStore(context),
        scope = scope,
        demoOverride = prefs.demoData,
    ).also { it.start() }
    val repository: GlanceRepository = providerRepository
    val apps: AppsRepository = AppsRepository(context, prefs)

    init {
        // A shortcut unpinned from Home is released to its app as well (Pins.pinChanges).
        scope.launch { prefs.homePins.collect { apps.syncPinnedShortcuts(it) } }
        // The screenshot demo starts from a demo built around today, whenever it is turned on.
        scope.launch { prefs.demoData.drop(1).collect { on -> if (on) demo.reseed() } }
    }
}
