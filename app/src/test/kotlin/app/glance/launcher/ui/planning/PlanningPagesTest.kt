package app.glance.launcher.ui.planning

import app.glance.launcher.data.Availability
import app.glance.launcher.data.provider.GlanceApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Home is the rightmost page and index 0; each app is one swipe further left (D80). */
class PlanningPagesTest {
    private val both = listOf(GlanceApp.DAYGLANCE, GlanceApp.LASTGLANCE)

    @Test
    fun `dayGLANCE is one swipe from Home and lastGLANCE two, installed or not`() {
        assertEquals(both, PlanningPages.apps(Availability(dayGlanceInstalled = true, lastGlanceInstalled = true)))
        // An app that is missing keeps its page: it shows the demo data there.
        assertEquals(both, PlanningPages.apps(Availability(dayGlanceInstalled = false, lastGlanceInstalled = false)))
        assertEquals(1, PlanningPages.indexOf(GlanceApp.DAYGLANCE, both))
        assertEquals(2, PlanningPages.indexOf(GlanceApp.LASTGLANCE, both))
        assertEquals(3, PlanningPages.count(both))
    }

    @Test
    fun `thingGLANCE has a page only while its own data can be read, and it is the last`() {
        val installed = Availability(dayGlanceInstalled = true, lastGlanceInstalled = true, thingGlanceInstalled = true)
        // Installed but unreadable (too old, or it does not trust this build): no demo page for it.
        assertEquals(both, PlanningPages.apps(installed))
        val live = installed.copy(thingGlance = app.glance.launcher.data.DataSource.LIVE)
        assertEquals(both + GlanceApp.THINGGLANCE, PlanningPages.apps(live))
        assertEquals(3, PlanningPages.indexOf(GlanceApp.THINGGLANCE, PlanningPages.apps(live)))
        // The screenshot demo shows its seeded page.
        assertEquals(both + GlanceApp.THINGGLANCE, PlanningPages.apps(Availability(dayGlanceInstalled = false, lastGlanceInstalled = false, demoForced = true)))
        assertEquals("thingGLANCE", PlanningPages.name(GlanceApp.THINGGLANCE))
    }

    @Test
    fun `an index maps back to its app, and Home and anything past the ends to none`() {
        assertNull(PlanningPages.appAt(PlanningPages.HOME, both))
        assertEquals(GlanceApp.DAYGLANCE, PlanningPages.appAt(1, both))
        assertEquals(GlanceApp.LASTGLANCE, PlanningPages.appAt(2, both))
        assertNull(PlanningPages.appAt(3, both))
        assertNull(PlanningPages.appAt(-1, both))
        for (app in both) assertEquals(app, PlanningPages.appAt(PlanningPages.indexOf(app, both)!!, both))
    }

    @Test
    fun `on screen the pages read furthest first, with Home after the last`() {
        assertEquals(listOf(GlanceApp.LASTGLANCE, GlanceApp.DAYGLANCE), PlanningPages.leftToRight(both))
        assertEquals(emptyList(), PlanningPages.leftToRight(emptyList()))
    }

    @Test
    fun `an app joining the end moves no page that was there`() {
        val one = listOf(GlanceApp.DAYGLANCE)
        assertEquals(2, PlanningPages.count(one))
        assertNull(PlanningPages.indexOf(GlanceApp.LASTGLANCE, one))
        // Home is index 0 before and after, and so is every page already in the list.
        assertEquals(PlanningPages.indexOf(GlanceApp.DAYGLANCE, one), PlanningPages.indexOf(GlanceApp.DAYGLANCE, both))
        for (index in 0 until PlanningPages.count(one)) assertEquals(PlanningPages.key(index, one), PlanningPages.key(index, both))
    }

    @Test
    fun `a page keeps its key when an app before it leaves`() {
        val lastOnly = listOf(GlanceApp.LASTGLANCE)
        assertEquals("home", PlanningPages.key(PlanningPages.HOME, both))
        assertEquals("home", PlanningPages.key(PlanningPages.HOME, lastOnly))
        // lastGLANCE moves from index 2 to 1; its key is what the pager follows it by.
        assertEquals(PlanningPages.key(2, both), PlanningPages.key(1, lastOnly))
        assertEquals(both.size + 1, (0 until PlanningPages.count(both)).map { PlanningPages.key(it, both) }.toSet().size)
    }

    @Test
    fun `no apps leaves Home alone`() {
        assertEquals(1, PlanningPages.count(emptyList()))
        assertNull(PlanningPages.appAt(PlanningPages.HOME, emptyList()))
        assertNull(PlanningPages.indexOf(GlanceApp.DAYGLANCE, emptyList()))
    }

    @Test
    fun `each app has the name its wordmark spells`() {
        assertEquals("dayGLANCE", PlanningPages.name(GlanceApp.DAYGLANCE))
        assertEquals("lastGLANCE", PlanningPages.name(GlanceApp.LASTGLANCE))
    }
}
