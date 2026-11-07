@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.glance.launcher.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.glance.launcher.R

/**
 * Lora, dayGLANCE's brand serif, as two variable fonts: upright for the wordmark's "day"
 * and the dial date (weight 500), italic for "GLANCE" (weight 700). Everything else is the
 * system sans, as on the web.
 */
val LoraFamily: FontFamily = FontFamily(
    Font(R.font.lora_wght, FontWeight.Normal, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lora_wght, FontWeight.Medium, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.lora_wght, FontWeight.SemiBold, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.lora_wght, FontWeight.Bold, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.lora_italic_wght, FontWeight.Normal, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lora_italic_wght, FontWeight.Bold, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
