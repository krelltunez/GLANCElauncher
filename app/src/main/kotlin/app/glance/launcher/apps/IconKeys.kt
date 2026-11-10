package app.glance.launcher.apps

import app.glance.launcher.ui.theme.ThemedIcons

/**
 * Which app an [AppsRepository.bitmapCache] key belongs to, so an updated app's icons can be
 * dropped and drawn again. Pure, tested on the JVM.
 */
object IconKeys {
    /** Keys of shortcut icons: this, then the shortcut's "package#id" ([AppShortcut.key]). */
    const val SHORTCUT_PREFIX = "shortcut:"

    /** True for [cacheKey]s of [packageName]'s app icon (plain or themed) and of its shortcuts' icons. */
    fun isOfPackage(cacheKey: String, packageName: String): Boolean {
        if (cacheKey.startsWith(SHORTCUT_PREFIX)) return cacheKey.startsWith("$SHORTCUT_PREFIX$packageName#")
        return ThemedIcons.appKeyOf(cacheKey).startsWith("$packageName/")
    }
}
