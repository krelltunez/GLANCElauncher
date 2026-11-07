package app.glance.launcher.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.glance.launcher.intents.GlanceIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * lastGLANCE ships every lucide glyph as `ic_lucide_<snake_case>` drawables and stores a
 * chore's icon as the lucide PascalCase name. The launcher reads those drawables straight out
 * of the installed app's resources, so chores show their own icons without the launcher
 * bundling 1.7k vectors. Falls back to [fallback] when the app or the glyph is missing.
 *
 * The app has one drawable per icon, under whichever spelling won when its drawables were
 * generated (`ic_lucide_lucide_shower_head`), and a chore stores whichever spelling the picker
 * offered (`ShowerHead`), so the lookup tries them all ([candidates]).
 */
object LucideIcons {
    private val cache = HashMap<String, ImageBitmap?>()
    private var aliases: Map<String, List<String>>? = null

    /** "AlarmClock" -> "ic_lucide_alarm_clock", the same transform the app's widgets use. */
    fun resName(pascal: String): String {
        val sb = StringBuilder("ic_lucide_")
        for ((i, ch) in pascal.withIndex()) {
            if (ch in 'A'..'Z' && i > 0) sb.append('_')
            sb.append(ch.lowercaseChar())
        }
        return sb.toString()
    }

    /** lucide-aliases.txt: "Home House", one icon per line, to each name's other spellings. */
    fun parseAliases(text: String): Map<String, List<String>> {
        val result = HashMap<String, List<String>>()
        for (line in text.lineSequence()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val names = line.trim().split(' ')
            for (name in names) result[name] = names - name
        }
        return result
    }

    /** Every spelling of the icon, with and without lucide's prefix, the stored name first. */
    fun candidates(stored: String, aliases: Map<String, List<String>>): List<String> {
        val plain = stored.removePrefix("Lucide")
        val spellings = listOf(plain) + aliases[plain].orEmpty()
        return (listOf(stored) + spellings.flatMap { listOf(it, "Lucide$it") }).distinct()
    }

    private fun aliases(context: Context): Map<String, List<String>> = aliases ?: runCatching {
        parseAliases(context.assets.open("lucide-aliases.txt").bufferedReader().use { it.readText() })
    }.getOrDefault(emptyMap()).also { aliases = it }

    fun load(context: Context, pascal: String, sizePx: Int): ImageBitmap? {
        cache[pascal]?.let { return it }
        if (cache.containsKey(pascal)) return null
        val bitmap = runCatching {
            val pkg = GlanceIntents.LASTGLANCE_PACKAGE
            val appContext = context.createPackageContext(pkg, 0)
            val id = candidates(pascal, aliases(context)).firstNotNullOfOrNull { name ->
                appContext.resources.getIdentifier(resName(name), "drawable", pkg).takeIf { it != 0 }
            } ?: 0
            if (id == 0) null else {
                val d: Drawable = appContext.getDrawable(id) ?: return@runCatching null
                drawableToBitmap(d.mutate(), sizePx).asImageBitmap()
            }
        }.getOrNull()
        cache[pascal] = bitmap
        return bitmap
    }
}

@Composable
fun LucideIcon(name: String?, tint: Color, size: Dp, modifier: Modifier = Modifier, fallback: ImageVector) {
    val context = LocalContext.current
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 2).roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = name) {
        value = if (name == null) null else withContext(Dispatchers.IO) { LucideIcons.load(context, name, sizePx) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
    } else {
        Icon(fallback, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}
