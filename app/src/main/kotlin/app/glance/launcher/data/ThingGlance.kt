package app.glance.launcher.data

import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * thingGLANCE as the launcher sees it (its `docs/launcher.md`): the dates that need a decision,
 * what is still ahead, each thing in service with how much of its window is left, and what plans
 * are about to charge. The app has already decided all of it; the launcher only draws it, and
 * works out the countdowns itself so they are right at midnight without the app running.
 */
data class ThingGlanceData(
    /** False when thingGLANCE is installed and has never been opened: there is nothing to show yet. */
    val hasData: Boolean,
    /** The day the app built its lists for. */
    val builtFor: LocalDate?,
    /** ISO 4217 code of every amount; nothing is converted. */
    val currency: String = "USD",
    /** The BCP 47 tag the app's text is written for; amounts are formatted for it too. */
    val locale: String? = null,
    val counts: ThingCounts = ThingCounts(),
    /** Plan cost a month, and the upgrade fund's monthly target. */
    val monthlyPlans: Double = 0.0,
    val fundMonthly: Double = 0.0,
    /** What plans will charge over the next [chargesDays] days: the total and how many charges. */
    val chargesDays: Int = 30,
    val chargesTotal: Double = 0.0,
    val chargesCount: Int = 0,
    /** The year ahead, a total per calendar month. */
    val months: List<ThingMonth> = emptyList(),
    /** What needs a decision, most pressing first. */
    val attention: List<ThingEvent> = emptyList(),
    /** What is still ahead, soonest first. */
    val upcoming: List<ThingEvent> = emptyList(),
    val things: List<ThingItem> = emptyList(),
    val charges: List<ThingCharge> = emptyList(),
    /** True when thingGLANCE is connected to dayGLANCE: only then is "Send to dayGLANCE" offered. */
    val canSend: Boolean = false,
    /** The app's own names for its verdicts, by code; empty from a build that sends none. */
    val verdictLabels: Map<String, String> = emptyMap(),
) {
    /** Things closest to the end of their window first, as the app's Fleet card runs. */
    val fleet: List<ThingItem> get() = things.sortedBy { it.lifeEnd ?: LocalDate.MAX }

    /** Every dated event the launcher knows of, once each: the year strip draws from it. */
    val allEvents: List<ThingEvent> get() = (attention + upcoming).distinctBy { it.key }
}

data class ThingCounts(val things: Int = 0, val plans: Int = 0, val attention: Int = 0, val urgent: Int = 0, val covered: Int = 0)

/** One date: a coverage ending, support ending, a planned replacement, a renewal, a trial converting, a plan ending. */
data class ThingEvent(
    /** `type:entityId:date`, the id a write names it by. */
    val key: String,
    val type: String,
    val date: LocalDate,
    /** `past`, `urgent`, `soon` or `later`, as the app judged it when it built the list. */
    val severity: String,
    /** Two lines of text in the app's own words. */
    val title: String,
    val detail: String,
    val amount: Double? = null,
    /** `open`, `sent` (handed to dayGLANCE), `done` or `dismissed`. */
    val state: String = "open",
    /** What a tap opens: a `thingglance://` link, handed to the app as it is. */
    val link: String = "",
) {
    val sent: Boolean get() = state == "sent"
}

data class ThingItem(
    val id: String,
    val name: String,
    val category: String = "",
    /** `prime`, `solid`, `watch`, `replace`, `overdue` or `unknown`. */
    val verdict: String = "unknown",
    /** Share of its window left, 0..1; null when it has no dates yet. */
    val remaining: Float? = null,
    val lifeEnd: LocalDate? = null,
    val lifeStart: LocalDate? = null,
    val coverageEnd: LocalDate? = null,
    val supportEnd: LocalDate? = null,
    /** `plan` when the planned replacement closes its window, `support` when support running out does. */
    val lifeEndReason: String? = null,
)

data class ThingCharge(val planId: String, val name: String, val date: LocalDate, val amount: Double, val link: String = "")

data class ThingMonth(val month: LocalDate, val total: Double)

/** thingGLANCE's six tones (its `components/kit.ts`); the theme turns one into a colour. */
enum class ThingTone { OK, ACCENT, WARN, BAD, SUB, DIM }

/**
 * The app's own rules for how a date or a thing reads, ported from thingGLANCE's `src/core`
 * (`dates.ts`, `horizon.ts`, `health.ts`, `money.ts`) so the launcher's page says what the app
 * says. English only, as the launcher is.
 */
object ThingGlance {
    fun daysUntil(date: LocalDate, today: LocalDate): Int = ChronoUnit.DAYS.between(today, date).toInt()

    /** The countdown badge: `T-12d` before a date, `T+3d` after it, `T-0` on the day (`tMinus`). */
    fun tMinus(days: Int): String {
        if (days == 0) return "T-0"
        val n = abs(days)
        val sign = if (days > 0) "-" else "+"
        return when {
            n < 60 -> "T$sign${n}d"
            n < 730 -> "T$sign${(n / 30.44).roundToInt()}mo"
            else -> "T$sign${decimal(n / 365.25)}y"
        }
    }

    /** "tomorrow", "in 4 weeks", "3 days ago": the app's `relative`, which leans on Intl's `numeric: auto`. */
    fun relative(days: Int): String {
        val n = abs(days)
        val (count, unit) = when {
            n < 14 -> n.toDouble() to "day"
            n < 60 -> (n / 7.0).roundToInt().toDouble() to "week"
            n < 730 -> (n / 30.44).roundToInt().toDouble() to "month"
            else -> ((n / 365.25) * 10).roundToLong() / 10.0 to "year"
        }
        if (unit == "day") when (days) { 0 -> return "today"; 1 -> return "tomorrow"; -1 -> return "yesterday" }
        val amount = "${decimal(count)} $unit${if (count == 1.0) "" else "s"}"
        return if (days < 0) "$amount ago" else "in $amount"
    }

    /** 2.5 stays 2.5, and 2.0 reads 2. */
    private fun decimal(n: Double): String {
        val rounded = (n * 10).roundToLong() / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    /**
     * How pressing a date is today. The app's word stands, except that a date which has passed
     * since the list was built is past, and one inside two weeks is urgent (`severity()`): the
     * launcher redraws at midnight, and the app may not have run by then.
     */
    fun severity(event: ThingEvent, days: Int): String = when {
        event.severity == "later" -> "later"
        days < 0 -> "past"
        days <= 14 -> "urgent"
        else -> event.severity
    }

    fun severityTone(severity: String): ThingTone = when (severity) {
        "past", "urgent" -> ThingTone.BAD
        "soon" -> ThingTone.WARN
        else -> ThingTone.DIM
    }

    /** The colour of a kind of date (`EVENT_META`). */
    fun eventTone(type: String): ThingTone = when (type) {
        "coverage-end" -> ThingTone.OK
        "support-end" -> ThingTone.ACCENT
        "replace" -> ThingTone.WARN
        "trial-end" -> ThingTone.BAD
        else -> ThingTone.SUB
    }

    /** The one-word name of a kind of date, for the year strip's legend. */
    fun eventKind(type: String): String = when (type) {
        "coverage-end" -> "Coverage"
        "support-end" -> "Support"
        "replace" -> "Replace"
        "renewal" -> "Renewal"
        "trial-end" -> "Trial"
        else -> "Ends"
    }

    /** Which of the year strip's three lanes a kind of date runs in. */
    fun eventLane(type: String): Int = when (type) {
        "coverage-end" -> 0
        "support-end", "replace" -> 1
        else -> 2
    }

    fun verdictTone(verdict: String): ThingTone = when (verdict) {
        "prime" -> ThingTone.OK
        "solid" -> ThingTone.ACCENT
        "watch" -> ThingTone.WARN
        "replace", "overdue" -> ThingTone.BAD
        else -> ThingTone.DIM
    }

    /** The app's name for a verdict when it sent one, else the English it ships with. */
    fun verdictLabel(verdict: String, labels: Map<String, String>): String = labels[verdict] ?: when (verdict) {
        "prime" -> "Prime"
        "solid" -> "Solid"
        "watch" -> "Watch"
        "replace" -> "Replace soon"
        "overdue" -> "Past its window"
        "retired" -> "Retired"
        else -> "No dates yet"
    }

    /**
     * How far along its life a thing is today, 0..1, for the life bar: from [ThingItem.lifeStart]
     * to [ThingItem.lifeEnd]. Null when either is unknown.
     */
    fun lifePosition(thing: ThingItem, on: LocalDate): Float? {
        val start = thing.lifeStart ?: return null
        val end = thing.lifeEnd ?: return null
        val total = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
        return (ChronoUnit.DAYS.between(start, on).toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * The share of a thing's window left today. The app's own figure when the day has not moved
     * on since it built the list; otherwise worked out from its dates, so the ring does not sit
     * still for days while the app is closed.
     */
    fun remaining(thing: ThingItem, today: LocalDate, builtFor: LocalDate?): Float? {
        if (thing.remaining == null) return null
        if (builtFor == null || builtFor == today) return thing.remaining
        return lifePosition(thing, today)?.let { 1f - it } ?: thing.remaining
    }

    /** [data] with the date cleared: gone from what needs a decision, and done where it is still listed ahead. */
    fun decided(data: ThingGlanceData, key: String): ThingGlanceData {
        val gone = data.attention.any { it.key == key }
        return data.copy(
            attention = data.attention.filterNot { it.key == key },
            upcoming = data.upcoming.map { if (it.key == key) it.copy(state = "done") else it },
            counts = if (gone) data.counts.copy(attention = (data.counts.attention - 1).coerceAtLeast(0)) else data.counts,
        )
    }

    /** [data] with a cleared date put back among what needs a decision, in date order. */
    fun restored(data: ThingGlanceData, event: ThingEvent): ThingGlanceData {
        val open = event.copy(state = "open")
        val back = data.attention.none { it.key == event.key }
        return data.copy(
            attention = if (back) (data.attention + open).sortedBy { it.date } else data.attention,
            upcoming = data.upcoming.map { if (it.key == event.key) it.copy(state = "open") else it },
            counts = if (back) data.counts.copy(attention = data.counts.attention + 1) else data.counts,
        )
    }

    /** [data] with the date marked as handed to dayGLANCE; it stays in view. */
    fun sent(data: ThingGlanceData, key: String): ThingGlanceData {
        val mark = { e: ThingEvent -> if (e.key == key && e.state == "open") e.copy(state = "sent") else e }
        return data.copy(attention = data.attention.map(mark), upcoming = data.upcoming.map(mark))
    }

    /** The app's headline: "All clear." or how many dates want a decision. */
    fun headline(waiting: Int): String = when (waiting) {
        0 -> "All clear. Nothing needs you right now."
        1 -> "1 thing wants a decision."
        else -> "$waiting things want a decision."
    }

    /** The status word beside the date: how many are urgent, else "heads up", else "nominal". */
    fun statusWord(urgent: Int, waiting: Int): String = when {
        urgent > 0 -> "$urgent urgent"
        waiting > 0 -> "heads up"
        else -> "nominal"
    }

    /** The sentence under the headline about the first date in the queue, or what is being watched. */
    fun nextUp(first: ThingEvent?, today: LocalDate, counts: ThingCounts): String {
        if (first == null) {
            val things = if (counts.things == 1) "1 thing" else "${counts.things} things"
            val plans = if (counts.plans == 1) "1 plan" else "${counts.plans} plans"
            return "You are watching $things and $plans. The next date is a way off."
        }
        val days = daysUntil(first.date, today)
        val lead = if (days < 0) "Overdue" else "Next up"
        return "$lead: ${first.title.lowercase()} for ${first.detail}, ${relative(days)}."
    }

    /**
     * An amount as the app writes it (`money`): no cents for a whole amount or one of 100 and
     * over, and a compact form ("$12K") from 10,000 when [compact].
     */
    fun money(amount: Double, currency: String, localeTag: String? = null, compact: Boolean = false): String {
        val locale = localeTag?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }?.takeIf { it.language.isNotEmpty() } ?: Locale.getDefault()
        val cents = abs(amount) < 100 && amount % 1.0 != 0.0
        return try {
            if (compact && abs(amount) >= 10_000) {
                val symbol = Currency.getInstance(currency).getSymbol(locale)
                val (value, suffix) = if (abs(amount) >= 1_000_000) amount / 1_000_000 to "M" else amount / 1_000 to "K"
                "$symbol${decimal(value)}$suffix"
            } else {
                NumberFormat.getCurrencyInstance(locale).apply {
                    this.currency = Currency.getInstance(currency)
                    minimumFractionDigits = if (cents) 2 else 0
                    maximumFractionDigits = if (cents) 2 else 0
                }.format(amount)
            }
        } catch (e: Exception) {
            "$currency ${if (cents) "%.2f".format(amount) else amount.roundToLong().toString()}"
        }
    }
}
