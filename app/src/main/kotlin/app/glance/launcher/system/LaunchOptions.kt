package app.glance.launcher.system

import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle
import android.window.SplashScreen

/**
 * What an app launch from Home asks of the system: the icon splash screen. From Android 13 a
 * launch that does not ask gets the splash as the app's bare background colour, so an app opened
 * from GLANCElauncher showed a blank screen until its first frame, where a stock launcher shows
 * the app's icon. Null below 13, which has no such option.
 */
object LaunchOptions {
    fun iconSplash(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON).toBundle()
        } else {
            null
        }
}
