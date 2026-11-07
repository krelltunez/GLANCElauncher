package app.glance.launcher.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Top padding for the status bar OR the display cutout, whichever is taller. With the status
 * bar hidden its inset drops to zero but the camera cutout stays, and a header padded for the
 * bar alone slides under the camera.
 */
@Composable
fun Modifier.topBarsPadding(): Modifier =
    windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
