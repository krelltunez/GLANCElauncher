package app.glance.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * dayGLANCE's palette as the web app uses it. Light is stone, dark is gray; blue is the
 * functional accent and orange `#fe8b00` the brand accent used sparingly.
 */
@Immutable
data class GlanceColors(
    val background: Color,
    val card: Color,
    val cardSubtle: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val brand: Color,
    val track: Color,
    val todayChip: Color,
    val todayChipText: Color,
    val todayTint: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean,
)

val LightGlanceColors = GlanceColors(
    background = Color(0xFFF5F5F4),
    card = Color(0xFFFFFFFF),
    cardSubtle = Color(0xFFFAFAF9),
    border = Color(0xFFD6D3D1),
    text = Color(0xFF1C1917),
    textSecondary = Color(0xFF57534E),
    textMuted = Color(0xFF78716C),
    accent = Color(0xFF2563EB),
    brand = Color(0xFFFE8B00),
    track = Color(0xFFE7E5E4),
    todayChip = Color(0xFFDBEAFE),
    todayChipText = Color(0xFF1D4ED8),
    todayTint = Color(0x66EFF6FF),
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFEF4444),
    isDark = false,
)

val DarkGlanceColors = GlanceColors(
    background = Color(0xFF111827),
    card = Color(0xFF1F2937),
    cardSubtle = Color(0x80374151),
    border = Color(0xFF374151),
    text = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9CA3AF),
    textMuted = Color(0xFF6B7280),
    accent = Color(0xFF3B82F6),
    brand = Color(0xFFFE8B00),
    track = Color(0xFF374151),
    todayChip = Color(0x801E3A8A),
    todayChipText = Color(0xFF93C5FD),
    todayTint = Color(0x1A1E3A8A),
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFEF4444),
    isDark = true,
)

val LocalGlanceColors = staticCompositionLocalOf { LightGlanceColors }

/** Parses "#rrggbb" or "#aarrggbb"; falls back to dayGLANCE blue. */
fun hexColor(hex: String?): Color {
    if (hex == null) return Color(0xFF3B82F6)
    val h = hex.trim().removePrefix("#")
    return runCatching {
        when (h.length) {
            6 -> Color(0xFF000000L or h.toLong(16))
            8 -> Color(h.toLong(16))
            else -> Color(0xFF3B82F6)
        }
    }.getOrDefault(Color(0xFF3B82F6))
}
