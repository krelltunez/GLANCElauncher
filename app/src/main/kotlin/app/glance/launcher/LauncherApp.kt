package app.glance.launcher

import android.app.Application
import app.glance.launcher.di.AppGraph

class LauncherApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
