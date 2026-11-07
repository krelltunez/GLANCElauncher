package app.glance.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import app.glance.launcher.prefs.LauncherPrefs
import app.glance.launcher.ui.theme.GlanceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The wallpaper is a system window behind the launcher, and Android paints it during its own
 * transitions (the home gesture, app launches) no matter what the launcher draws. The only way
 * to keep it from ever showing is to make it the launcher's own background colour. While the
 * "Solid wallpaper" setting is on, this keeps the device's home-screen wallpaper a solid fill of
 * the current theme background, rewriting it only when the colour changes. The lock-screen
 * wallpaper is left alone.
 */
@Composable
fun SolidWallpaperEffect(prefs: LauncherPrefs) {
    val context = LocalContext.current
    val enabled by prefs.solidWallpaper.collectAsState()
    val color = GlanceTheme.colors.background.toArgb()
    LaunchedEffect(enabled, color) {
        if (!enabled || prefs.appliedWallpaperArgb == color) return@LaunchedEffect
        val ok = withContext(Dispatchers.IO) { applySolidWallpaper(context, color) }
        if (ok) prefs.appliedWallpaperArgb = color
    }
}

/** Writes a solid [argb] home wallpaper. Returns false if the system refused. */
fun applySolidWallpaper(context: Context, argb: Int): Boolean = try {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }
    WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
    true
} catch (e: Exception) {
    Log.w("GLANCElauncher", "Could not set the solid wallpaper", e)
    false
}
