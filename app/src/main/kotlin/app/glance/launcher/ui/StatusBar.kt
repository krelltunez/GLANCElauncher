package app.glance.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** Hides or shows the status bar. When hidden it comes back with a swipe from the top edge. */
@Composable
fun StatusBarEffect(hidden: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(hidden) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        applyStatusBarHidden(activity, hidden)
    }
}

fun applyStatusBarHidden(activity: Activity, hidden: Boolean) {
    val window = activity.window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController ?: return
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.statusBars())
        } else {
            controller.show(WindowInsets.Type.statusBars())
        }
    } else {
        @Suppress("DEPRECATION")
        val flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (hidden) {
            flags or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            flags
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
