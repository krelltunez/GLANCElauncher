package app.glance.launcher.ui.diagnostics

import app.glance.launcher.data.provider.RefreshEntry
import app.glance.launcher.ui.diagnostics.RefreshLogView.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RefreshLogViewTest {
    private val now = Instant.parse("2026-10-23T13:47:20Z")
    private fun entry(outcome: String, agoSec: Long = 10, finished: Boolean = false, reason: String? = null) = RefreshEntry(
        clientId = "c1", requestedAt = now.minusSeconds(agoSec), outcome = outcome, reason = reason,
        liveness = "alive", durationMs = null, finishedAt = if (finished) now else null,
    )
    private val held: (Long) -> String = { "${it} ms" }

    @Test fun `a completed cycle reads ok, a skipped one a warning, an unanswered or failed one bad`() {
        assertEquals(Tone.OK, RefreshLogView.tone(entry("ran", finished = true), now))
        assertEquals(Tone.WARN, RefreshLogView.tone(entry("skipped", reason = "a vault sync has been running since 22:58:24"), now))
        assertEquals(Tone.BAD, RefreshLogView.tone(entry("unanswered", finished = true), now))
        assertEquals(Tone.BAD, RefreshLogView.tone(entry("failed"), now))
        assertEquals(Tone.WARN, RefreshLogView.tone(entry("throttled"), now))
    }

    @Test fun `an unanswered request the app recovered from by closing its page is a warning, not a failure`() {
        val closed = entry("unanswered", finished = true).copy(pageClosed = true)
        assertEquals(Tone.WARN, RefreshLogView.tone(closed, now))
        assertEquals("unanswered · page closed · renderer alive", RefreshLogView.headline(closed, now, held))
        // Left open (a check that failed, or a build that does not close it): still bad.
        assertEquals(Tone.BAD, RefreshLogView.tone(entry("unanswered", finished = true), now))
    }

    @Test fun `a request just queued is still on its way`() {
        val e = entry("queued", agoSec = 30)
        assertFalse(RefreshLogView.neverAnswered(e, now))
        assertEquals(Tone.NEUTRAL, RefreshLogView.tone(e, now))
        assertEquals("queued · renderer alive", RefreshLogView.headline(e, now, held))
    }

    @Test fun `a request left queued past the keep-awake hold was never answered`() {
        val e = entry("queued", agoSec = 8 * 3600)
        assertTrue(RefreshLogView.neverAnswered(e, now))
        assertEquals(Tone.BAD, RefreshLogView.tone(e, now))
        assertEquals("queued · never answered · renderer alive", RefreshLogView.headline(e, now, held))
    }

    @Test fun `a scheduled headless run waiting on the OS is not called unanswered`() {
        val e = entry("scheduled", agoSec = 8 * 3600)
        assertFalse(RefreshLogView.neverAnswered(e, now))
        assertEquals(Tone.NEUTRAL, RefreshLogView.tone(e, now))
    }
}
