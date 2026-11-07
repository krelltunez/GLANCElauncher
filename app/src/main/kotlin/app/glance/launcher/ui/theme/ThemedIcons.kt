package app.glance.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** A themed icon's two colours: the tile behind it and the app's monochrome glyph on top. */
@Immutable
data class ThemedIconColors(val tile: Color, val glyph: Color, val dark: Boolean)

/**
 * Themed icons (Android 13+): an app that ships a monochrome layer is drawn as that layer in
 * the GLANCE palette, blue on blue as dayGLANCE's today chip, in place of its own colours.
 * Pure, tested on the JVM; [app.glance.launcher.apps.AppsRepository.themedIcon] builds the
 * drawable.
 */
object ThemedIcons {
    /** `AdaptiveIconDrawable.getMonochrome()` arrived in Android 13. */
    const val MIN_SDK = 33
    private const val PREFIX = "themed:"

    val Light = ThemedIconColors(tile = Color(0xFFDBEAFE), glyph = Color(0xFF1D4ED8), dark = false)
    val Dark = ThemedIconColors(tile = Color(0xFF1E293B), glyph = Color(0xFF93C5FD), dark = true)

    /** The colours to theme with, or null to draw every app in its own. */
    fun colorsFor(enabled: Boolean, dark: Boolean, sdk: Int): ThemedIconColors? = when {
        !enabled || sdk < MIN_SDK -> null
        dark -> Dark
        else -> Light
    }

    /**
     * The rendered-bitmap cache key: the app's own for its own colours, and one per theme for
     * themed, so switching either never shows a bitmap drawn for the other.
     */
    fun cacheKey(appKey: String, colors: ThemedIconColors?): String = when {
        colors == null -> appKey
        colors.dark -> "${PREFIX}dark:$appKey"
        else -> "${PREFIX}light:$appKey"
    }
}

/** Provided by [GlanceLauncherTheme]: null while themed icons are off or unsupported. */
val LocalThemedIcons = staticCompositionLocalOf<ThemedIconColors?> { null }
