package app.glance.launcher.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import app.glance.launcher.data.Availability
import app.glance.launcher.data.DataSource
import app.glance.launcher.data.Freshness
import app.glance.launcher.ui.theme.GlanceTheme
import java.time.Duration
import java.time.Instant

/** An app whose last confirmation is older than this is called out in the header. */
val STALE_AFTER: Duration = Duration.ofHours(1)

/**
 * One app's word in the header: "demo" on demo data, its age once the last confirmation
 * ([Freshness.confirmedAt]: the last completed sync, else the last look, else the last change)
 * passes [STALE_AFTER], else null.
 */
fun appStatusWord(f: Freshness, now: Instant): String? {
    if (f.source == DataSource.DEMO) return "demo"
    val at = f.confirmedAt ?: return "unconfirmed"
    val age = Duration.between(at, now)
    return if (age >= STALE_AFTER) formatAge(age) else null
}

/**
 * The header's data status, per app: "dayGLANCE 3h · lastGLANCE demo". Null when both are
 * live and confirmed within the hour, and while the screenshot demo is on.
 */
fun dataStatusText(a: Availability, now: Instant): String? {
    if (a.demoForced) return null
    val parts = listOfNotNull(
        appStatusWord(a.dayGlanceFreshness, now)?.let { "dayGLANCE $it" },
        appStatusWord(a.lastGlanceFreshness, now)?.let { "lastGLANCE $it" },
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/**
 * One app's status in words, for Settings: "live, confirmed 12m ago", "last confirmed 3h ago"
 * (past [STALE_AFTER], when Home's header badges the app's icon), "demo data", "not confirmed
 * yet" or "not installed".
 */
fun appStatusLine(f: Freshness, now: Instant): String {
    if (!f.installed) return "not installed"
    if (f.source == DataSource.DEMO) return "demo data"
    val at = f.confirmedAt ?: return "not confirmed yet"
    val age = Duration.between(at, now)
    val ago = formatAgo(age)
    return if (age >= STALE_AFTER) "last confirmed $ago" else "live, confirmed $ago"
}

/** [formatAge] as a phrase: "just now", "12m ago". */
fun formatAgo(age: Duration): String = formatAge(age).let { if (it == "now") "just now" else "$it ago" }

/** How long the OS held a scheduled headless run: finer than [formatAge], since seconds matter here. */
fun formatHeld(ms: Long): String = when {
    ms < 1000 -> "under 1 s"
    ms < 60_000 -> "${ms / 1000} s"
    ms < 3_600_000 -> "${ms / 60_000}m ${ms / 1000 % 60}s"
    else -> "${ms / 3_600_000}h ${ms / 60_000 % 60}m"
}

fun formatAge(age: Duration): String = when {
    age.isNegative || age.seconds < 60 -> "now"
    age.toHours() < 1 -> "${age.toMinutes()}m"
    age.toHours() < 48 -> "${age.toHours()}h"
    else -> "${age.toDays()}d"
}

/**
 * The status beside the wordmark. Reads "live" when there is nothing to say, so there is
 * always something to long-press for the diagnostics panel ([onLongPress]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataStatusLabel(availability: Availability, now: Instant, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    val text = dataStatusText(availability, now) ?: "live"
    val m = if (onLongPress != null) modifier.combinedClickable(onClick = {}, onLongClick = onLongPress) else modifier
    Text(text, color = GlanceTheme.colors.textMuted, fontSize = 11.sp, modifier = m, maxLines = 1)
}
