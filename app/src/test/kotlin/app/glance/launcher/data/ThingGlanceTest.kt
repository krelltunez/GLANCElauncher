package app.glance.launcher.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The launcher's port of thingGLANCE's wording rules, held to what the app itself writes. */
class ThingGlanceTest {
    private val today = LocalDate.of(2026, 11, 15)
    private fun event(severity: String = "soon", date: LocalDate = today.plusDays(20)) =
        ThingEvent("coverage-end:c1:$date", "coverage-end", date, severity, "AppleCare ends", "MacBook Pro")

    @Test fun `the countdown badge reads as the app's tMinus`() {
        assertEquals("T-0", ThingGlance.tMinus(0))
        assertEquals("T-12d", ThingGlance.tMinus(12))
        assertEquals("T+3d", ThingGlance.tMinus(-3))
        assertEquals("T-59d", ThingGlance.tMinus(59))
        assertEquals("T-2mo", ThingGlance.tMinus(60))
        assertEquals("T-24mo", ThingGlance.tMinus(729))
        assertEquals("T-2y", ThingGlance.tMinus(730))
        assertEquals("T-2.5y", ThingGlance.tMinus(913))
        assertEquals("T+3mo", ThingGlance.tMinus(-91))
    }

    @Test fun `a distance in words reads as the app's relative`() {
        assertEquals("today", ThingGlance.relative(0))
        assertEquals("tomorrow", ThingGlance.relative(1))
        assertEquals("yesterday", ThingGlance.relative(-1))
        assertEquals("in 9 days", ThingGlance.relative(9))
        assertEquals("3 days ago", ThingGlance.relative(-3))
        assertEquals("in 2 weeks", ThingGlance.relative(14))
        assertEquals("in 4 weeks", ThingGlance.relative(30))
        assertEquals("in 2 months", ThingGlance.relative(60))
        assertEquals("5 months ago", ThingGlance.relative(-150))
        assertEquals("in 2 years", ThingGlance.relative(730))
        assertEquals("in 2.5 years", ThingGlance.relative(913))
    }

    @Test fun `a date that has passed since the list was built is past`() {
        assertEquals("soon", ThingGlance.severity(event("soon"), 20))
        assertEquals("urgent", ThingGlance.severity(event("soon"), 14))
        assertEquals("past", ThingGlance.severity(event("urgent"), -1))
        // Outside its window the app calls it "later" whatever the distance, and so does the launcher.
        assertEquals("later", ThingGlance.severity(event("later"), 3))
        assertEquals(ThingTone.BAD, ThingGlance.severityTone("past"))
        assertEquals(ThingTone.WARN, ThingGlance.severityTone("soon"))
        assertEquals(ThingTone.DIM, ThingGlance.severityTone("later"))
    }

    @Test fun `kinds of date and verdicts take the app's tones and words`() {
        assertEquals(ThingTone.OK, ThingGlance.eventTone("coverage-end"))
        assertEquals(ThingTone.SUB, ThingGlance.eventTone("renewal"))
        assertEquals(ThingTone.BAD, ThingGlance.eventTone("trial-end"))
        assertEquals(1, ThingGlance.eventLane("replace"))
        assertEquals(2, ThingGlance.eventLane("sub-end"))
        assertEquals(ThingTone.ACCENT, ThingGlance.verdictTone("solid"))
        assertEquals(ThingTone.BAD, ThingGlance.verdictTone("overdue"))
        assertEquals("Replace soon", ThingGlance.verdictLabel("replace", emptyMap()))
        // The app's own word wins when it sent one.
        assertEquals("Bald ersetzen", ThingGlance.verdictLabel("replace", mapOf("replace" to "Bald ersetzen")))
    }

    @Test fun `the ring moves on with the days while the app is closed`() {
        val thing = ThingItem("t1", "Laptop", remaining = 0.5f, lifeStart = today.minusDays(100), lifeEnd = today.plusDays(100))
        assertEquals(0.5f, ThingGlance.remaining(thing, today, builtFor = today)!!, 0.0001f)
        assertEquals(0.25f, ThingGlance.remaining(thing, today.plusDays(50), builtFor = today)!!, 0.0001f)
        assertEquals(0f, ThingGlance.remaining(thing, today.plusDays(500), builtFor = today)!!, 0.0001f)
        // No dates: no ring, whatever the day.
        assertNull(ThingGlance.remaining(ThingItem("t2", "Lamp"), today.plusDays(5), today))
        // The app's figure stands where the dates to redo it from are missing.
        assertEquals(0.4f, ThingGlance.remaining(ThingItem("t3", "Phone", remaining = 0.4f), today.plusDays(5), today)!!, 0.0001f)
        assertEquals(0.5f, ThingGlance.lifePosition(thing, today)!!, 0.0001f)
    }

    @Test fun `the headline panel says what the app's does`() {
        assertEquals("All clear. Nothing needs you right now.", ThingGlance.headline(0))
        assertEquals("1 thing wants a decision.", ThingGlance.headline(1))
        assertEquals("3 things want a decision.", ThingGlance.headline(3))
        assertEquals("2 urgent", ThingGlance.statusWord(2, 5))
        assertEquals("heads up", ThingGlance.statusWord(0, 5))
        assertEquals("nominal", ThingGlance.statusWord(0, 0))
        assertEquals("Next up: applecare ends for MacBook Pro, in 3 weeks.", ThingGlance.nextUp(event(), today, ThingCounts()))
        assertEquals("Overdue: applecare ends for MacBook Pro, 2 days ago.", ThingGlance.nextUp(event(date = today.minusDays(2)), today, ThingCounts()))
        assertEquals("You are watching 1 thing and 3 plans. The next date is a way off.", ThingGlance.nextUp(null, today, ThingCounts(things = 1, plans = 3)))
    }

    @Test fun `amounts drop the cents the app drops`() {
        assertEquals("$9.99", ThingGlance.money(9.99, "USD", "en-US"))
        assertEquals("$10", ThingGlance.money(10.0, "USD", "en-US"))
        assertEquals("$120", ThingGlance.money(119.5, "USD", "en-US"))
        assertEquals("$1,200", ThingGlance.money(1200.0, "USD", "en-US"))
        assertEquals("$12K", ThingGlance.money(12_000.0, "USD", "en-US", compact = true))
        // Compact only from 10,000, as in the app.
        assertEquals("$1,200", ThingGlance.money(1200.0, "USD", "en-US", compact = true))
    }

    @Test fun `the fleet runs closest to the end of its window first, undated things last`() {
        val data = ThingGlanceData(hasData = true, builtFor = today, things = listOf(
            ThingItem("a", "A", lifeEnd = today.plusDays(300)), ThingItem("b", "B"), ThingItem("c", "C", lifeEnd = today.minusDays(5)),
        ))
        assertEquals(listOf("c", "a", "b"), data.fleet.map { it.id })
    }
}
