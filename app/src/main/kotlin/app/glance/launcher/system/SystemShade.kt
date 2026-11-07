package app.glance.launcher.system

import android.content.Context
import android.util.Log

/**
 * Pulling the notification shade down from Home, as every launcher does. Android has no
 * public call for it: `StatusBarManager.expandNotificationsPanel()` is a hidden method that
 * launchers reach by reflection, behind the normal-level EXPAND_STATUS_BAR permission (granted
 * at install, no prompt). If a future release removes it, the gesture does nothing rather than
 * crash; the status bar itself still opens the shade.
 */
object SystemShade {
    private const val TAG = "SystemShade"

    fun expandNotifications(context: Context): Boolean = try {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        true
    } catch (e: Throwable) {
        Log.w(TAG, "Could not expand the notification shade", e)
        false
    }
}
