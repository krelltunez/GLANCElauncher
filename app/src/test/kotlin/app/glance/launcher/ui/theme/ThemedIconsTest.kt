package app.glance.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemedIconsTest {
    @Test fun `off, or before Android 13, every app keeps its own colours`() {
        assertNull(ThemedIcons.colorsFor(enabled = false, dark = false, sdk = 35))
        assertNull(ThemedIcons.colorsFor(enabled = true, dark = true, sdk = 32))
    }

    @Test fun `on, the palette follows the launcher's light or dark theme`() {
        assertEquals(ThemedIcons.Light, ThemedIcons.colorsFor(enabled = true, dark = false, sdk = 33))
        assertEquals(ThemedIcons.Dark, ThemedIcons.colorsFor(enabled = true, dark = true, sdk = 35))
    }

    @Test fun `each way of drawing an app caches under its own key`() {
        val keys = listOf(
            ThemedIcons.cacheKey("com.chat/.Main", null),
            ThemedIcons.cacheKey("com.chat/.Main", ThemedIcons.Light),
            ThemedIcons.cacheKey("com.chat/.Main", ThemedIcons.Dark),
        )
        assertEquals(3, keys.toSet().size)
        // Its own colours keep the key the rest of the launcher already uses.
        assertEquals("com.chat/.Main", keys[0])
    }

    @Test fun `the glyph reads on its tile in both palettes`() {
        for (colors in listOf(ThemedIcons.Light, ThemedIcons.Dark)) {
            val ratio = contrast(colors.glyph, colors.tile)
            assertTrue("${colors.dark}: $ratio", ratio >= 4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
