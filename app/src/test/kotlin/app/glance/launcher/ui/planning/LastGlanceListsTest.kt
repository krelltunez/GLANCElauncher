package app.glance.launcher.ui.planning

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The heatmap's grid and the full lists' grouping on lastGLANCE's page (D82). */
class LastGlanceListsTest {
    // A Sunday.
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `as many weeks as the width holds at the app's cell size, and no more than its 52`() {
        // 9 dp cells with 3 dp between them: 25 columns need 297 dp.
        assertEquals(25, LastGlanceLists.weeksThatFit(297f))
        assertEquals(24, LastGlanceLists.weeksThatFit(296f))
        assertEquals(52, LastGlanceLists.weeksThatFit(621f))
        assertEquals(52, LastGlanceLists.weeksThatFit(2000f))
        assertEquals(1, LastGlanceLists.weeksThatFit(4f))
    }

    @Test
    fun `the last column is the week today is in, and each column runs from the week's first day`() {
        val weeks = LastGlanceLists.heatWeeks(today, DayOfWeek.SUNDAY, 3)
        assertEquals(3, weeks.size)
        assertEquals(List(3) { 7 }, weeks.map { it.size })
        assertEquals(today, weeks.last().first())
        assertEquals(LocalDate.of(2026, 11, 1), weeks.first().first())
        assertEquals(LocalDate.of(2026, 11, 21), weeks.last().last())
        // A Monday start puts this Sunday at the foot of the last column.
        val monday = LastGlanceLists.heatWeeks(today, DayOfWeek.MONDAY, 2)
        assertEquals(today, monday.last().last())
        assertEquals(LocalDate.of(2026, 11, 2), monday.first().first())
    }

    @Test
    fun `the greens step as the app's do`() {
        assertEquals(listOf(0, 0, 1, 2, 3, 3, 4, 4), listOf(-1, 0, 1, 2, 3, 4, 5, 40).map { LastGlanceLists.heatLevel(it) })
    }

    @Test
    fun `chores and people fall under their category or circle in the order the app sent them`() {
        val items = listOf("Kitchen" to "Vacuum", "Garden" to "Mow", "Kitchen" to "Dishes", "" to "Loose")
        val groups = LastGlanceLists.grouped(items) { it.first }
        assertEquals(listOf("Kitchen", "Garden", ""), groups.map { it.first })
        assertEquals(listOf("Vacuum", "Dishes"), groups[0].second.map { it.second })
        assertEquals(emptyList(), LastGlanceLists.grouped(emptyList<String>()) { it })
        // A category and a circle of one name fold separately.
        assertEquals("chores:Family", LastGlanceLists.choresKey("Family"))
        assertEquals("people:Family", LastGlanceLists.peopleKey("Family"))
    }
}
