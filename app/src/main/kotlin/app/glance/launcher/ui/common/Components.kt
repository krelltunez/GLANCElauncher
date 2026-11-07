package app.glance.launcher.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.apps.AppEntry
import app.glance.launcher.apps.AppShortcut
import app.glance.launcher.apps.AppsRepository
import app.glance.launcher.apps.Pin
import app.glance.launcher.prefs.LauncherPrefs
import app.glance.launcher.system.DotKey
import app.glance.launcher.system.NotificationDots
import app.glance.launcher.system.NotificationDotsService
import app.glance.launcher.ui.theme.GlanceTheme
import app.glance.launcher.ui.theme.LocalThemedIcons
import app.glance.launcher.ui.theme.ThemedIcons
import app.glance.launcher.ui.theme.LoraFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** dayGLANCE's flat card: 1px border, 12dp radius, no shadow. */
@Composable
fun GlanceCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(14.dp),
    accentTop: Color? = null,
    accentLeft: Color? = null,
    verticalArrangement: androidx.compose.foundation.layout.Arrangement.Vertical = androidx.compose.foundation.layout.Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = GlanceTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = verticalArrangement,
        modifier = modifier
            .clip(shape)
            .background(c.card, shape)
            .border(1.dp, c.border, shape)
            .then(if (accentTop != null) Modifier.topStrip(accentTop) else Modifier)
            .then(if (accentLeft != null) Modifier.leftStrip(accentLeft) else Modifier)
            .padding(padding),
        content = content,
    )
}

private fun Modifier.topStrip(color: Color): Modifier = this.then(
    Modifier.drawBehindStrip(color, top = true),
)

private fun Modifier.leftStrip(color: Color): Modifier = this.then(
    Modifier.drawBehindStrip(color, top = false),
)

private fun Modifier.drawBehindStrip(color: Color, top: Boolean): Modifier =
    this.drawBehind {
        // The card is clipped to its rounded shape, so plain rectangles follow the corners.
        if (top) {
            drawRect(color.copy(alpha = 0.73f), size = androidx.compose.ui.geometry.Size(size.width, 6.dp.toPx()))
        } else {
            drawRect(color, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
        }
    }

/** "Today · Saturday, Oct 17" style section label: xs, semibold, uppercase, wide tracking. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = GlanceTheme.colors.textSecondary, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** "day" in Lora 500 followed by "GLANCE" in Lora 700 italic, brand orange. */
@Composable
fun Wordmark(prefix: String, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val c = GlanceTheme.colors
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(prefix, fontFamily = LoraFamily, fontWeight = FontWeight.Medium, fontSize = size.value.sp, color = c.textSecondary)
        Text("GLANCE", fontFamily = LoraFamily, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = size.value.sp, color = c.brand)
    }
}

/**
 * The launcher's own wordmark: "GLANCE" in Lora 700 in the text colour, then "launcher" in Lora
 * 500 italic, muted. No brand colour: orange says dayGLANCE and green says lastGLANCE, and the
 * launcher frames both (their icons sit beside it in the header). The family's case contrast
 * stays, with the weight reversed since GLANCE comes first here.
 */
@Composable
fun LauncherWordmark(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val c = GlanceTheme.colors
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("GLANCE", fontFamily = LoraFamily, fontWeight = FontWeight.Bold, fontSize = size.value.sp, color = c.text, maxLines = 1)
        Text("launcher", fontFamily = LoraFamily, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = size.value.sp, color = c.textSecondary, maxLines = 1)
    }
}

/** lastGLANCE's wordmark: "last" heavy in the text colour, "GLANCE" in its brand green, italic. Sans, as in that app. */
@Composable
fun LastGlanceWordmark(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val c = GlanceTheme.colors
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("last", fontWeight = FontWeight.ExtraBold, fontSize = size.value.sp, color = c.text)
        Text("GLANCE", fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = size.value.sp, color = LastGlanceGreen)
    }
}

/** lastGLANCE brand green. */
val LastGlanceGreen = Color(0xFF3DDC84)

/**
 * The apps with a notification dot ([NotificationDotsService]), or none while the switch in
 * Settings is off. Provided once at the root, so an icon that shows dots reads it without
 * every icon collecting the flows itself.
 */
val LocalNotificationDots = staticCompositionLocalOf<Set<DotKey>> { emptySet() }

@Composable
fun NotificationDotsProvider(prefs: LauncherPrefs, content: @Composable () -> Unit) {
    val enabled by prefs.notificationDots.collectAsState()
    val dots by NotificationDotsService.dots.collectAsState()
    CompositionLocalProvider(LocalNotificationDots provides if (enabled) dots else emptySet(), content = content)
}

/**
 * An installed app's icon, rendered from its Drawable off the main thread.
 *
 * @param notificationDot this placement shows a dot when the app has notifications (the drawer,
 *   the taskbar, the pinned row); search, the editors and the header, which has its own status
 *   dot, do not
 */
@Composable
fun AppIcon(entry: AppEntry, apps: AppsRepository, size: Dp = 48.dp, modifier: Modifier = Modifier, notificationDot: Boolean = false) {
    val themed = LocalThemedIcons.current
    val cacheKey = ThemedIcons.cacheKey(entry.key, themed)
    // The state is keyed on the app, not only the loader. produceState remembers its value
    // WITHOUT its key, so a row reused for another app (search results as the query changes,
    // any list without item keys) kept the previous app's bitmap, and the loader, seeing a
    // value, never replaced it: apps showed other apps' icons.
    var bitmap by remember(cacheKey) { mutableStateOf(apps.bitmapCache[cacheKey]) }
    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                apps.bitmapCache[cacheKey] ?: (themed?.let { apps.themedIcon(entry, it.tile.toArgb(), it.glyph.toArgb()) } ?: apps.icon(entry))
                    ?.let { drawableToBitmap(it, 144).asImageBitmap() }?.also { apps.bitmapCache[cacheKey] = it }
            }
        }
    }
    val bmp = bitmap
    val dot = notificationDot && NotificationDots.has(LocalNotificationDots.current, entry.packageName, entry.user, remember { Process.myUserHandle() })
    Box(modifier.size(size)) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = entry.label, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(GlanceTheme.colors.track, RoundedCornerShape(12.dp)))
        }
        if (dot) NotificationDot(size, Modifier.align(Alignment.TopEnd))
    }
}

/** The dot itself: the accent colour, ringed in the background so it reads over any icon. */
@Composable
private fun NotificationDot(iconSize: Dp, modifier: Modifier) {
    val c = GlanceTheme.colors
    Box(
        modifier
            .size(iconSize * 0.26f)
            .background(c.accent, CircleShape)
            .border(iconSize * 0.04f, c.background, CircleShape),
    )
}

/**
 * An app shortcut's own icon, as the launcher's long-press menu shows it; until it loads, or when
 * the app gives none, its app's icon.
 */
@Composable
fun ShortcutIcon(shortcut: AppShortcut, apps: AppsRepository, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val cacheKey = AppsRepository.SHORTCUT_BITMAP_PREFIX + shortcut.key
    var bitmap by remember(cacheKey) { mutableStateOf(apps.bitmapCache[cacheKey]) }
    var missing by remember(cacheKey) { mutableStateOf(false) }
    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                apps.bitmapCache[cacheKey] ?: apps.shortcutIcon(shortcut)?.let { drawableToBitmap(it, 144).asImageBitmap() }?.also { apps.bitmapCache[cacheKey] = it }
            }
            missing = bitmap == null
        }
    }
    val bmp = bitmap
    val app = if (bmp == null && missing) apps.apps.value.firstOrNull { it.packageName == shortcut.packageName } else null
    when {
        bmp != null -> Image(bitmap = bmp, contentDescription = shortcut.label, modifier = modifier.size(size))
        app != null -> AppIcon(app, apps, size, modifier)
        else -> Box(modifier.size(size).background(GlanceTheme.colors.track, RoundedCornerShape(12.dp)))
    }
}

/** A Home pin's icon: its app's, or the pinned shortcut's own. A shortcut never has a notification dot. */
@Composable
fun PinIcon(pin: Pin, apps: AppsRepository, size: Dp = 48.dp, modifier: Modifier = Modifier, notificationDot: Boolean = false) = when (pin) {
    is Pin.App -> AppIcon(pin.entry, apps, size, modifier, notificationDot)
    is Pin.Shortcut -> ShortcutIcon(pin.shortcut, apps, size, modifier)
}

fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return bmp
}

@Composable
fun LabeledIcon(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = GlanceTheme.colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 8dp rounded bar, dayGLANCE's GoalProgress. */
@Composable
fun ProgressBar(fraction: Float?, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val c = GlanceTheme.colors
    Box(modifier.fillMaxWidth().height(height).background(c.track, RoundedCornerShape(50))) {
        if (fraction != null && fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(if (fraction >= 1f) c.success else color, RoundedCornerShape(50)),
            )
        }
    }
}
