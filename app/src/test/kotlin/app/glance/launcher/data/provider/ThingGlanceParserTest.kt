package app.glance.launcher.data.provider

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThingGlanceParserTest {
    private val meta = """{"version":1,"snapshotVersion":1,"updatedAt":"2026-11-15T16:00:00.000Z","checkedAt":1763222700000,"syncedAt":"2026-11-15T15:00:00.000Z",
        "rendererAlive":false,"headlessRunning":false,"today":"2026-11-15","currency":"CAD","locale":"en-CA",
        "counts":{"things":4,"subscriptions":3,"attention":2,"urgent":1,"covered":2},
        "burn":{"monthly":42.5,"yearly":510,"replacementMonthly":61},
        "charges":{"days":30,"total":129.99,"count":2,"months":[{"month":"2026-11-01","total":10},{"month":"2026-12-01","total":119.99},{"month":"bad","total":1}]},
        "canSend":true,"labels":{"verdicts":{"prime":"Prime","watch":"Watch"},"sent":"In dayGLANCE"},
        "handledWrites":[{"clientId":"w1","action":"decide","entityId":"coverage-end:c1:2026-11-24"},{"action":"send"}],
        "refreshLog":[{"clientId":"r1","requestedAt":1763222000000,"outcome":"ran","liveness":"dead","durationMs":900,"finishedAt":1763222009000,"waitMs":3000,"headless":true}]}"""
    private val rows = mapOf(
        "meta" to listOf(meta),
        "attention" to listOf(
            """{"key":"coverage-end:c1:2026-11-24","type":"coverage-end","date":"2026-11-24","days":9,"severity":"urgent","title":"AppleCare ends","detail":"MacBook Pro","thingId":"t1","handlerId":null,"state":"sent","link":"thingglance://event/coverage-end%3Ac1%3A2026-11-24"}""",
            """{"key":"renewal:s1:2026-12-01","type":"renewal","date":"2026-12-01","severity":"soon","title":"Netflix renews","detail":"Subscription","amount":22.99,"state":"open","link":"thingglance://event/x"}""",
            """{"title":"no key"}""", "not json",
        ),
        "upcoming" to listOf("""{"key":"replace:t2:2027-03-01","type":"replace","date":"2027-03-01","severity":"later","title":"Planned replacement","detail":"Pixel 8"}"""),
        "things" to listOf(
            """{"id":"t1","name":"MacBook Pro","category":"laptop","verdict":"solid","remaining":0.62,"lifeEnd":"2029-05-01","daysLeft":898,"lifeStart":"2025-05-01","coverageEnd":"2026-11-24","supportEnd":null,"lifeEndReason":"plan","ownerMemberId":null}""",
            """{"id":"t3","name":"Lamp","category":"other","verdict":"unknown","remaining":null,"lifeEnd":null,"daysLeft":null}""",
        ),
        "charges" to listOf("""{"subscriptionId":"s1","name":"Netflix","date":"2026-12-01","amount":22.99,"link":"thingglance://plan/s1"}""", """{"name":"no id"}"""),
    )

    @Test fun `every collection thingGLANCE serves is read`() {
        assertEquals(listOf("meta", "attention", "upcoming", "things", "charges"), GlanceApp.THINGGLANCE.collections)
        assertEquals(rows.keys, GlanceApp.THINGGLANCE.collections.toSet())
    }

    @Test fun `the snapshot is read field for field, and a bad row is skipped`() {
        val s = ThingGlanceParser.parse(rows)
        val d = s.data
        assertTrue(d.hasData)
        assertEquals(LocalDate.of(2026, 11, 15), d.builtFor)
        assertEquals("CAD", d.currency)
        assertEquals("en-CA", d.locale)
        assertEquals(4, d.counts.things)
        assertEquals(3, d.counts.plans)
        assertEquals(2, d.counts.attention)
        assertEquals(1, d.counts.urgent)
        assertEquals(2, d.counts.covered)
        assertEquals(42.5, d.monthlyPlans, 0.0)
        assertEquals(61.0, d.fundMonthly, 0.0)
        assertEquals(129.99, d.chargesTotal, 0.0)
        assertEquals(2, d.chargesCount)
        assertEquals(listOf(10.0, 119.99), d.months.map { it.total })
        assertTrue(d.canSend)
        assertEquals("Watch", d.verdictLabels["watch"])

        assertEquals(2, d.attention.size)
        val first = d.attention[0]
        assertEquals("coverage-end:c1:2026-11-24", first.key)
        assertEquals(LocalDate.of(2026, 11, 24), first.date)
        assertEquals("AppleCare ends", first.title)
        assertTrue(first.sent)
        assertNull(first.amount)
        assertEquals(22.99, d.attention[1].amount!!, 0.0)
        assertEquals("replace", d.upcoming.single().type)
        assertEquals(3, d.allEvents.size)

        val laptop = d.things[0]
        assertEquals(0.62f, laptop.remaining!!, 0.0001f)
        assertEquals(LocalDate.of(2025, 5, 1), laptop.lifeStart)
        assertEquals(LocalDate.of(2026, 11, 24), laptop.coverageEnd)
        assertNull(laptop.supportEnd)
        assertEquals("plan", laptop.lifeEndReason)
        assertNull(d.things[1].remaining)
        assertEquals(1, d.charges.size)
        assertEquals("thingglance://plan/s1", d.charges[0].link)
    }

    @Test fun `freshness is read through the parser the other two apps use`() {
        val s = ThingGlanceParser.parse(rows)
        assertEquals(Instant.parse("2026-11-15T16:00:00Z"), s.updatedAt)
        assertEquals(Instant.ofEpochMilli(1763222700000), s.meta.checkedAt)
        assertEquals(Instant.parse("2026-11-15T15:00:00Z"), s.meta.syncedAt)
        assertEquals(false, s.meta.rendererAlive)
        val run = s.meta.refreshLog.single()
        assertEquals("ran", run.outcome)
        assertTrue(run.headless)
        assertEquals(3000L, run.waitMs)
        // A ledger entry without an id is nothing a write can be matched to.
        assertEquals(setOf("w1"), s.reflected)
    }

    @Test fun `installed and never opened has a meta row and nothing to show`() {
        val s = ThingGlanceParser.parse(mapOf("meta" to listOf("""{"version":1,"empty":true,"rendererAlive":false,"headlessRunning":false,"syncedAt":null,"refreshLog":[]}""")))
        assertFalse(s.data.hasData)
        assertTrue(s.data.attention.isEmpty())
        assertNull(s.updatedAt)
        assertFalse(ThingGlanceParser.parse(emptyMap()).data.hasData)
    }
}
