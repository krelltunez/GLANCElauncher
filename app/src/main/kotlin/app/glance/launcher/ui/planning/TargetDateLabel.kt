package app.glance.launcher.ui.planning

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How a target date reads: the colour the label takes. */
enum class DueTone { DANGER, WARNING, MUTED }

/**
 * The "due today" / "3d left" / "overdue" line under a goal's or a project's
 * title on the Planning screen. A project carries its own target date since
 * dayGLANCE #2160, read the same way as a goal's.
 */
object TargetDateLabel {
    fun of(target: LocalDate, today: LocalDate): Pair<String, DueTone> {
        val days = ChronoUnit.DAYS.between(today, target)
        return when {
            days < 0 -> "overdue" to DueTone.DANGER
            days == 0L -> "due today" to DueTone.WARNING
            days <= 3 -> "${days}d left" to DueTone.WARNING
            else -> "${days}d left" to DueTone.MUTED
        }
    }
}
