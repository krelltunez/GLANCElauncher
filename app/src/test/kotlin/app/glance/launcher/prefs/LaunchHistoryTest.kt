package app.glance.launcher.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchHistoryTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun `recent is newest first and respects eligibility`() {
        val h = LaunchHistory().record("a", now - 3 * day).record("b", now - day).record("c", now - 2 * day)
        assertEquals(listOf("b", "c", "a"), h.recent(4))
        assertEquals(listOf("c", "a"), h.recent(4) { it != "b" })
        assertEquals(listOf("b"), h.recent(1))
    }

    @Test
    fun `frequent weighs count against age, this week's habit beating last month's`() {
        var h = LaunchHistory()
        repeat(10) { h = h.record("old-habit", now - 35 * day) }
        repeat(3) { h = h.record("this-week", now - day) }
        h = h.record("once-today", now)
        assertEquals(listOf("this-week", "once-today", "old-habit"), h.frequent(3, now))
    }

    @Test
    fun `the history stays bounded, dropping the least recent`() {
        var h = LaunchHistory()
        for (i in 0 until LaunchHistory.MAX_ENTRIES + 5) h = h.record("app$i", now + i)
        assertEquals(LaunchHistory.MAX_ENTRIES, h.entries.size)
        assertEquals(false, "app0" in h.entries)
        assertEquals(true, "app${LaunchHistory.MAX_ENTRIES + 4}" in h.entries)
    }

    @Test
    fun `it round-trips and skips malformed lines`() {
        val h = LaunchHistory().record("pkg/.Main", now).record("pkg/.Main", now + 1).record("x/.Y", now - 5)
        assertEquals(h, LaunchHistory.parse(h.serialise()))
        assertEquals(LaunchHistory(mapOf("ok/.A" to LaunchHistory.Stat(2, 9))), LaunchHistory.parse("ok/.A\t2\t9\nbad line\n\tx\t1\nz\tnope\t1"))
        assertEquals(LaunchHistory(), LaunchHistory.parse(null))
    }

    @Test
    fun `recent searches dedupe case-insensitively, newest first, ignore trivial queries`() {
        var l = emptyList<String>()
        for (q in listOf("dentist", "Groceries", "a", "  ", "DENTIST", "mail", "x1", "x2", "x3", "x4")) l = RecentSearches.add(l, q)
        assertEquals(listOf("x4", "x3", "x2", "x1", "mail", "DENTIST"), l)
    }
}
