package app.glance.launcher.ui.home

import app.glance.launcher.data.Chore
import app.glance.launcher.data.Person
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LastGlanceRowsTest {
    @Test
    fun `a busy card lists what is due, up to its rows`() {
        val p = LastGlanceRows.plan(listOf("a", "b", "c"), listOf("x"), rows = 2, otherDue = 0)
        assertEquals(listOf("a", "b"), p.items)
        assertFalse(p.caughtUp)
    }

    @Test
    fun `a caught-up card never lists more up-next rows than the busy card beside it`() {
        assertEquals(listOf("x"), LastGlanceRows.plan(emptyList(), listOf("x", "y"), rows = 2, otherDue = 1).items)
        assertEquals(listOf("x", "y"), LastGlanceRows.plan(emptyList(), listOf("x", "y", "z"), rows = 2, otherDue = 2).items)
        assertTrue(LastGlanceRows.plan(emptyList(), listOf("x"), rows = 2, otherDue = 1).caughtUp)
    }

    @Test
    fun `beside another caught-up card, or stacked, it takes the rows a busy card would`() {
        assertEquals(listOf("x", "y"), LastGlanceRows.plan(emptyList(), listOf("x", "y", "z"), rows = 2, otherDue = 0).items)
        assertEquals(listOf("x", "y"), LastGlanceRows.plan(emptyList(), listOf("x", "y", "z"), rows = 2, otherDue = null).items)
    }

    @Test
    fun `no room for rows lists none, and nothing coming due is still caught up`() {
        assertEquals(emptyList(), LastGlanceRows.plan(emptyList(), listOf("x"), rows = 0, otherDue = null).items)
        val none = LastGlanceRows.plan(emptyList<String>(), emptyList(), rows = 2, otherDue = 2)
        assertEquals(emptyList(), none.items)
        assertTrue(none.caughtUp)
    }

    @Test
    fun `up next is the on-track chores and the not-yet-due people, soonest due first`() {
        val now = Instant.parse("2026-10-29T12:00:00Z")
        fun ago(d: Long) = now.minus(Duration.ofDays(d))
        val chores = listOf(
            Chore("c1", "Mop", "Home", targetCadenceDays = 10, lastDoneAt = ago(2)), // 0.2
            Chore("c2", "Dust", "Home", targetCadenceDays = 10, lastDoneAt = ago(6)), // 0.6
            Chore("c3", "Oven", "Home", targetCadenceDays = 10, lastDoneAt = ago(9)), // due soon: not up next
            Chore("c4", "Bulbs", "Home"), // no cadence
        )
        val people = listOf(
            Person("p1", "Ana", targetCadenceDays = 30, lastInteractionAt = ago(20)), // 0.67
            Person("p2", "Ben", targetCadenceDays = 30, lastInteractionAt = ago(3)), // 0.1
            Person("p3", "Cy", targetCadenceDays = 30, lastInteractionAt = ago(28)), // reach out: not up next
            Person("p4", "Dee", targetCadenceDays = 30, lastInteractionAt = ago(15), snoozedUntil = now.plus(Duration.ofDays(3))),
            Person("p5", "Eve"), // no cadence
        )
        val s = lastGlanceStats(chores, people, now)
        assertEquals(listOf("Dust", "Mop"), s.upcomingChores.map { it.chore.name })
        assertEquals(listOf("Ana", "Ben"), s.upcomingPeople.map { it.person.name })
    }
}
