package app.glance.launcher.data

import app.glance.launcher.data.provider.GlanceApp
import app.glance.launcher.data.provider.Outbox
import app.glance.launcher.data.provider.OutboxEntry
import app.glance.launcher.data.provider.Patch
import app.glance.launcher.data.provider.SnapshotParser
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A chore's season, held to lastGLANCE's `isInSeasonalWindow`, and the heatmap's counts as the app sends them (D82). */
class ChoreSeasonTest {
    private fun chore(start: MonthDay?, end: MonthDay?) = Chore("c", "Mow", "Garden", seasonalStart = start, seasonalEnd = end)
    private fun day(month: Int, d: Int) = LocalDate.of(2026, month, d)

    @Test
    fun `a chore is in season from its first day to its last, both counted`() {
        val mow = chore(MonthDay.of(5, 1), MonthDay.of(10, 31))
        assertFalse(mow.inSeason(day(4, 30)))
        assertTrue(mow.inSeason(day(5, 1)))
        assertTrue(mow.inSeason(day(10, 31)))
        assertFalse(mow.inSeason(day(11, 1)))
        // The report this came from: due now on 15 November, with a season that ended in October.
        assertFalse(mow.inSeason(day(11, 15)))
    }

    @Test
    fun `a season that starts after it ends wraps the new year`() {
        val shovel = chore(MonthDay.of(11, 1), MonthDay.of(2, 28))
        assertTrue(shovel.inSeason(day(12, 25)))
        assertTrue(shovel.inSeason(day(1, 10)))
        assertFalse(shovel.inSeason(day(3, 1)))
        assertFalse(shovel.inSeason(day(10, 31)))
    }

    @Test
    fun `no season, or half of one, is always in season`() {
        assertTrue(chore(null, null).inSeason(day(1, 1)))
        assertTrue(chore(MonthDay.of(5, 1), null).inSeason(day(1, 1)))
        assertTrue(chore(null, MonthDay.of(10, 31)).inSeason(day(12, 1)))
    }

    @Test
    fun `seasons and the heatmap's counts are read from lastGLANCE's rows`() {
        val rows = mapOf(
            "meta" to listOf("""{"version":1,"updatedAt":1760000000000,"today":"2026-10-19","handledWrites":[],"completionsByDay":{"2026-10-18":2,"2026-10-19":1,"soon":4,"2026-10-01":0}}"""),
            "chores" to listOf(
                """{"id":"c1","name":"Mow","category":"Garden","targetCadenceDays":7,"lastDoneAt":null,"seasonalStart":"05-01","seasonalEnd":"10-31"}""",
                """{"id":"c2","name":"Vacuum","category":"Kitchen","targetCadenceDays":7,"lastDoneAt":null,"seasonalStart":null,"seasonalEnd":null}""",
                """{"id":"c3","name":"Dust","category":"Kitchen","targetCadenceDays":7,"lastDoneAt":null,"seasonalStart":"13-40","seasonalEnd":"10-31"}""",
            ),
            "people" to emptyList(),
        )
        val s = SnapshotParser.lastGlance(rows, LocalDate.of(2026, 10, 19))
        assertEquals(MonthDay.of(5, 1), s.chores[0].seasonalStart)
        assertEquals(MonthDay.of(10, 31), s.chores[0].seasonalEnd)
        assertNull(s.chores[1].seasonalStart)
        // A season the launcher cannot read is no season: the chore stays listed.
        assertNull(s.chores[2].seasonalStart)
        assertTrue(s.chores[2].inSeason(LocalDate.of(2026, 12, 1)))
        assertEquals(mapOf(LocalDate.of(2026, 10, 18) to 2, LocalDate.of(2026, 10, 19) to 1), s.completionsByDay)
        // A lastGLANCE from before either: no seasons, no counts, nothing broken.
        val older = SnapshotParser.lastGlance(mapOf("meta" to listOf("""{"version":1}"""), "chores" to listOf("""{"id":"c1","name":"Mow","category":"Garden"}""")), LocalDate.of(2026, 10, 19))
        assertTrue(older.completionsByDay.isEmpty())
        assertNull(older.chores.single().seasonalEnd)
    }

    @Test
    fun `a chore logged from the launcher counts on its day before the app has seen it`() {
        val at = Instant.parse("2026-10-19T15:00:00Z")
        val entry = OutboxEntry("w1", GlanceApp.LASTGLANCE, "x", "{}", at, Patch.LogChore("c1", at))
        val other = OutboxEntry("w2", GlanceApp.LASTGLANCE, "x", "{}", at, Patch.LogInteraction("p1", at))
        val today = LocalDate.of(2026, 10, 19)
        assertEquals(mapOf(today to 3), Outbox.applyToActivity(mapOf(today to 2), listOf(entry, other), ZoneOffset.UTC))
        assertEquals(mapOf(today to 1), Outbox.applyToActivity(emptyMap(), listOf(entry), ZoneOffset.UTC))
        // The day is the device's own. Mid-morning in Denver is the 19th there too,
        assertEquals(mapOf(LocalDate.of(2026, 10, 19) to 1), Outbox.applyToActivity(emptyMap(), listOf(entry), java.time.ZoneId.of("America/Denver")))
        // but a chore logged late in the evening there is still the 18th.
        val late = OutboxEntry("w3", GlanceApp.LASTGLANCE, "x", "{}", at, Patch.LogChore("c1", Instant.parse("2026-10-19T04:30:00Z")))
        assertEquals(mapOf(LocalDate.of(2026, 10, 18) to 1), Outbox.applyToActivity(emptyMap(), listOf(late), java.time.ZoneId.of("America/Denver")))
        assertEquals(emptyMap(), Outbox.applyToActivity(emptyMap(), listOf(other), ZoneOffset.UTC))
    }
}
