package app.glance.launcher.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsPagesTest {
    private fun labels(q: String) = SettingsPages.matches(q).map { it.label }

    @Test
    fun `a page is found by its name or a keyword, names first`() {
        assertEquals("Bluetooth", labels("blue").first())
        assertEquals(listOf("Battery", "Battery saver"), labels("batt"))
        assertEquals("Display", labels("brightness").single())
        assertEquals("Do Not Disturb", labels("dnd").single())
        assertEquals("Wi-Fi", labels("wifi").first())
        assertTrue("Home app" in labels("launcher"))
    }

    @Test
    fun `every word must match, one letter matches nothing, and at most three come back`() {
        assertEquals(listOf("Battery saver"), labels("battery saver"))
        assertEquals(emptyList(), labels("b"))
        assertEquals(emptyList(), labels("battery zebra"))
        assertTrue(labels("s").isEmpty() && labels("se").size <= SettingsPages.MAX)
    }
}
