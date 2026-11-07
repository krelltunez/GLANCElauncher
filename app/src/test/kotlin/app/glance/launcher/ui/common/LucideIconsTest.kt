package app.glance.launcher.ui.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The spellings tried for a chore's icon, since the name it stores may not be the drawable's. */
class LucideIconsTest {
    private val aliases = LucideIcons.parseAliases("# header\nHome House\n\nCheckCircle CircleCheck\n")

    @Test
    fun `the stored name is tried first, then the other prefix`() {
        assertEquals(listOf("ShowerHead", "LucideShowerHead"), LucideIcons.candidates("ShowerHead", aliases))
        assertEquals(listOf("LucideShowerHead", "ShowerHead"), LucideIcons.candidates("LucideShowerHead", aliases))
    }

    @Test
    fun `an alias is tried with and without the prefix`() {
        assertEquals(listOf("CircleCheck", "LucideCircleCheck", "CheckCircle", "LucideCheckCircle"), LucideIcons.candidates("CircleCheck", aliases))
        assertEquals(listOf("LucideHouse", "House", "Home", "LucideHome"), LucideIcons.candidates("LucideHouse", aliases))
    }

    @Test
    fun `comments and blank lines are not names`() {
        assertEquals(setOf("Home", "House", "CheckCircle", "CircleCheck"), aliases.keys)
    }

    @Test
    fun `the shipped table parses, and names the aliases the picker relies on`() {
        val shipped = LucideIcons.parseAliases(File("src/main/assets/lucide-aliases.txt").readText())
        assertTrue(shipped.size > 400, "only ${shipped.size} names")
        assertTrue("House" in shipped.getValue("Home"))
        assertTrue("CheckCircle2" in shipped.getValue("CircleCheck"))
        assertTrue(shipped.values.all { it.isNotEmpty() })
    }
}
