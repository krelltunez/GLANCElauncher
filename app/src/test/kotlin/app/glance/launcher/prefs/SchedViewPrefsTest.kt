package app.glance.launcher.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class SchedViewPrefsTest {
    @Test
    fun `filters and toggles survive a round trip`() {
        val view = SchedViewPrefs(
            colors = setOf("#22c55e", "#6366f1"), tags = setOf("training", "home"), projectIds = setOf("p1", "none"),
            showEmptyDays = true, hideCompleted = true, hidePastEvents = false,
        )
        assertEquals(view, SchedViewPrefs.parse(view.serialise()))
        assertEquals(SchedViewPrefs(hidePastEvents = true), SchedViewPrefs.parse(SchedViewPrefs(hidePastEvents = true).serialise()))
    }

    @Test
    fun `nothing stored, or anything unreadable, is the defaults`() {
        assertEquals(SchedViewPrefs(), SchedViewPrefs.parse(null))
        assertEquals(SchedViewPrefs(), SchedViewPrefs.parse(""))
        assertEquals(SchedViewPrefs(), SchedViewPrefs.parse(SchedViewPrefs().serialise()))
        // An unknown line is ignored; a flag that is not a boolean is off; blank values are dropped.
        assertEquals(
            SchedViewPrefs(tags = setOf("work"), showEmptyDays = true),
            SchedViewPrefs.parse("future\tx\ntags\twork\t\nshowEmptyDays\ttrue\nhideCompleted\tyes"),
        )
    }
}
