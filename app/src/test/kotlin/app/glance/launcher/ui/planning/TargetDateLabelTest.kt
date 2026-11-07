package app.glance.launcher.ui.planning

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Planning screen's target-date line, shared by goals and (dayGLANCE #2160) projects. */
class TargetDateLabelTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test
    fun `overdue, due today, soon and later`() {
        assertEquals("overdue" to DueTone.DANGER, TargetDateLabel.of(today.minusDays(1), today))
        assertEquals("due today" to DueTone.WARNING, TargetDateLabel.of(today, today))
        assertEquals("1d left" to DueTone.WARNING, TargetDateLabel.of(today.plusDays(1), today))
        assertEquals("3d left" to DueTone.WARNING, TargetDateLabel.of(today.plusDays(3), today))
        assertEquals("4d left" to DueTone.MUTED, TargetDateLabel.of(today.plusDays(4), today))
    }
}
