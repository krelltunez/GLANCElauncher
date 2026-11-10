package app.glance.launcher.apps

import app.glance.launcher.ui.theme.ThemedIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconKeysTest {
    @Test fun `an app's plain and themed icons belong to its package`() {
        for (colors in listOf(null, ThemedIcons.Light, ThemedIcons.Dark)) {
            assertTrue(IconKeys.isOfPackage(ThemedIcons.cacheKey("com.life/com.life.Main", colors), "com.life"))
        }
    }

    @Test fun `a package whose name another's starts with is not matched`() {
        assertFalse(IconKeys.isOfPackage("com.lifeglance.app/.Main", "com.life"))
        assertFalse(IconKeys.isOfPackage(ThemedIcons.cacheKey("com.lifeglance.app/.Main", ThemedIcons.Dark), "com.life"))
        assertFalse(IconKeys.isOfPackage("com.other/.Main", "com.life"))
    }

    @Test fun `shortcut icons belong to their app's package, whatever their id holds`() {
        val key = IconKeys.SHORTCUT_PREFIX + AppShortcut(
            packageName = "com.chat", id = "conv:42#a/b", shortLabel = "Sam", longLabel = null, appLabel = "Chat",
        ).key
        assertTrue(IconKeys.isOfPackage(key, "com.chat"))
        assertFalse(IconKeys.isOfPackage(key, "com.cha"))
        assertFalse(IconKeys.isOfPackage(key, "conv"))
    }

    @Test fun `the themed key undoes to its app key`() {
        for (colors in listOf(null, ThemedIcons.Light, ThemedIcons.Dark)) {
            assertEquals("com.chat/.Main", ThemedIcons.appKeyOf(ThemedIcons.cacheKey("com.chat/.Main", colors)))
        }
    }
}
