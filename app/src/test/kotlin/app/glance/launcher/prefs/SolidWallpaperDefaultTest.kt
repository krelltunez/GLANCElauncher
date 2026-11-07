package app.glance.launcher.prefs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolidWallpaperDefaultTest {
    @Test
    fun `a new install leaves the wallpaper alone`() {
        assertFalse(LauncherPrefs.solidWallpaperOn(stored = null, painted = false))
    }

    @Test
    fun `an older install painted under the old default stays on`() {
        assertTrue(LauncherPrefs.solidWallpaperOn(stored = null, painted = true))
    }

    @Test
    fun `a choice the user made wins either way`() {
        assertFalse(LauncherPrefs.solidWallpaperOn(stored = false, painted = true))
        assertTrue(LauncherPrefs.solidWallpaperOn(stored = true, painted = false))
    }
}
