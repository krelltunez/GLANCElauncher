package app.glance.launcher.ui.diagnostics

import app.glance.launcher.data.provider.RefreshEntry
import java.time.Duration
import java.time.Instant

/** How the diagnostics panel reads one refresh-log entry. Pure, tested on the JVM. */
object RefreshLogView {
    enum class Tone { OK, WARN, BAD, NEUTRAL }

    /**
     * A `queued` request older than this with no outcome was never answered: the
     * apps hold a backgrounded app awake for a minute (their LauncherAwakeWorker)
     * and mark a request they held for and lost `unanswered`, so one still
     * `queued` after that means the hold itself never ran (docs/decisions.md, decision 54).
     */
    val NO_ANSWER_AFTER: Duration = Duration.ofMinutes(2)

    /** A request still queued long past the keep-awake hold: the app's job never ran. */
    fun neverAnswered(e: RefreshEntry, now: Instant): Boolean =
        e.outcome == "queued" && e.finishedAt == null &&
            e.requestedAt != null && Duration.between(e.requestedAt, now) > NO_ANSWER_AFTER

    fun tone(e: RefreshEntry, now: Instant): Tone = when {
        neverAnswered(e, now) -> Tone.BAD
        e.outcome == "ran" -> Tone.OK
        // The page did not answer, but the app closed it and a headless run took over: handled.
        e.outcome == "unanswered" && e.pageClosed -> Tone.WARN
        e.outcome == "failed" || e.outcome == "unanswered" -> Tone.BAD
        // No cycle completed, for a reason the entry gives.
        e.outcome in setOf("deferred", "coalesced", "aborted", "throttled", "skipped") -> Tone.WARN
        else -> Tone.NEUTRAL
    }

    /** The headline: the outcome and what the app saw, plus "never answered" on a request left queued. */
    fun headline(e: RefreshEntry, now: Instant, formatHeld: (Long) -> String): String = buildString {
        append(e.outcome)
        if (neverAnswered(e, now)) append(" · never answered")
        if (e.pageClosed) append(" · page closed")
        if (e.headless) append(" · headless")
        e.liveness?.let { append(" · renderer $it") }
        e.waitMs?.let { append(" · held ${formatHeld(it)}") }
        e.durationMs?.let { append(" · $it ms") }
    }
}
