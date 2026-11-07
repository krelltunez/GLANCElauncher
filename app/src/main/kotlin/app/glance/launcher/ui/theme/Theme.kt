package app.glance.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import app.glance.launcher.prefs.ThemeMode

object GlanceTheme {
    val colors: GlanceColors
        @Composable @ReadOnlyComposable get() = LocalGlanceColors.current
}

@Composable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Light and dark themes matching dayGLANCE. The Day Dial keeps its own dark face in both.
 * [themedIcons] draws apps with a monochrome icon in the palette ([ThemedIcons]).
 */
@Composable
fun GlanceLauncherTheme(mode: ThemeMode, themedIcons: Boolean = false, content: @Composable () -> Unit) {
    val dark = isDarkFor(mode)
    val colors = if (dark) DarkGlanceColors else LightGlanceColors
    val scheme: ColorScheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.text,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.card,
            onSurface = colors.text,
            surfaceVariant = colors.card,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.card,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.card,
            onSurface = colors.text,
            surfaceVariant = colors.cardSubtle,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            error = colors.danger,
        )
    }
    CompositionLocalProvider(
        LocalGlanceColors provides colors,
        LocalThemedIcons provides ThemedIcons.colorsFor(themedIcons, dark, Build.VERSION.SDK_INT),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
