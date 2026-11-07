package app.glance.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SystemAppsTest {
    private val denver = ZoneId.of("America/Denver")

    @Test fun `the calendar is asked for local noon on the dial's date`() {
        assertEquals(Instant.parse("2026-10-23T18:00:00Z").toEpochMilli(), SystemApps.calendarTimeMillis(LocalDate.of(2026, 10, 23), denver))
    }

    @Test fun `noon keeps the day across a daylight-saving change and a zone an hour or so away`() {
        // Nov 1 2026: Denver falls back at 02:00, so the day's start and noon differ from the usual offset.
        val millis = SystemApps.calendarTimeMillis(LocalDate.of(2026, 11, 1), denver)
        assertEquals(LocalDate.of(2026, 11, 1), Instant.ofEpochMilli(millis).atZone(denver).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 1), Instant.ofEpochMilli(millis).atZone(ZoneId.of("America/Los_Angeles")).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 1), Instant.ofEpochMilli(millis).atZone(ZoneId.of("America/New_York")).toLocalDate())
    }
}
