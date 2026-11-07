package app.glance.launcher.ui.common

import app.glance.launcher.data.Availability
import app.glance.launcher.data.DataSource
import app.glance.launcher.data.Freshness
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStatusTest {
    private val now = Instant.parse("2026-10-19T15:00:00Z")
    private fun live(updatedAt: Instant? = null, checkedAt: Instant? = null, syncedAt: Instant? = null) =
        Freshness(DataSource.LIVE, true, updatedAt, checkedAt, syncedAt)
    private fun avail(day: Freshness, last: Freshness) =
        Availability(day.installed, last.installed, day.source, last.source, day.updatedAt, last.updatedAt, day, last)

    @Test
    fun `live data confirmed within the hour says nothing`() {
        assertNull(dataStatusText(avail(live(syncedAt = now.minusSeconds(600)), live(checkedAt = now.minusSeconds(60))), now))
    }

    @Test
    fun `staleness is measured from the last sync, else the last look, else the last change, per app`() {
        // A snapshot that changed days ago but synced an hour ago is an hour old, not days.
        assertEquals("dayGLANCE 1h", appStatusWord(live(updatedAt = now.minus(Duration.ofDays(3)), checkedAt = now.minus(Duration.ofHours(2)), syncedAt = now.minus(Duration.ofHours(1))), now)?.let { "dayGLANCE $it" })
        assertEquals("2h", appStatusWord(live(updatedAt = now.minus(Duration.ofDays(3)), checkedAt = now.minus(Duration.ofHours(2))), now))
        assertEquals("3d", appStatusWord(live(updatedAt = now.minus(Duration.ofDays(3))), now))
        assertEquals("unconfirmed", appStatusWord(live(), now))
        assertEquals("demo", appStatusWord(Freshness(DataSource.DEMO), now))
        assertEquals(
            "dayGLANCE demo · lastGLANCE 2d",
            dataStatusText(avail(Freshness(DataSource.DEMO), live(syncedAt = now.minus(Duration.ofDays(2)))), now),
        )
        assertEquals("lastGLANCE 3h", dataStatusText(avail(live(syncedAt = now.minusSeconds(30)), live(checkedAt = now.minus(Duration.ofHours(3)))), now))
    }

    @Test
    fun `the Settings line says live, stale, demo, unconfirmed or not installed`() {
        assertEquals("live, confirmed just now", appStatusLine(live(syncedAt = now.minusSeconds(20)), now))
        assertEquals("live, confirmed 12m ago", appStatusLine(live(checkedAt = now.minusSeconds(720)), now))
        assertEquals("last confirmed 3h ago", appStatusLine(live(syncedAt = now.minus(Duration.ofHours(3))), now))
        assertEquals("demo data", appStatusLine(Freshness(DataSource.DEMO, true), now))
        assertEquals("not confirmed yet", appStatusLine(live(), now))
        assertEquals("not installed", appStatusLine(Freshness(DataSource.DEMO, false), now))
    }

    @Test
    fun `ages read naturally`() {
        assertEquals("now", formatAge(Duration.ofSeconds(10)))
        assertEquals("under 1 s", formatHeld(400))
        assertEquals("12 s", formatHeld(12_400))
        assertEquals("3m 20s", formatHeld(200_000))
        assertEquals("2h 5m", formatHeld(7_500_000))
        assertEquals("59m", formatAge(Duration.ofMinutes(59)))
        assertEquals("1h", formatAge(Duration.ofMinutes(61)))
        assertEquals("47h", formatAge(Duration.ofHours(47)))
        assertEquals("2d", formatAge(Duration.ofHours(48)))
        assertEquals("just now", formatAgo(Duration.ofSeconds(10)))
        assertEquals("12m ago", formatAgo(Duration.ofMinutes(12)))
    }
}
