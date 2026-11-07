package app.glance.launcher.quickadd

import app.glance.launcher.data.TaskDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Natural-language quick capture, a Kotlin cut of dayGLANCE's `quickAddParser.js`.
 *
 * Supported, in the reference's stage order (tags, time range, time, date, duration; each
 * stage masks what it claimed, right-most match wins):
 * - tags `#word` (kept in the title, as dayGLANCE stores them inline);
 * - times `noon`, `midnight`, `3pm`, `3:30 pm`, `at 3` (8 to 12 as written, 1 to 7 as PM), `15:00`;
 * - ranges `3-4pm`, `3pm - 5pm`, `from 3 to 4:30`, `9am to 11` (a bare `3-4` is not a range);
 * - dates `today`, `tomorrow`/`tmrw`/`tmr`/`tom`, `tonight`, `this morning`, day-parts,
 *   `next week`, `[on|next] friday` (always the next future one), `in N days|weeks|months`,
 *   `june 3`, `6/3`;
 * - durations `for 30 min`, `for 1h`, `for 1.5 hours`, `for 1h 30m`, `for an hour`,
 *   `for half an hour`, glued `90m`, `2h`, `1h30m`;
 * - sigils `@date`, `~time`, `$deadline`, `%30` (minutes), `!`, `!!`, `!!!` (priority).
 *
 * Not ported: recurrence. The launcher creates one-off tasks only; a recurring phrase stays
 * in the title for dayGLANCE to handle when the real write path lands.
 */
object QuickAddParser {

    data class Result(
        val draft: TaskDraft,
        /** Human-readable chips describing what was detected, for the search bar preview. */
        val chips: List<String>,
    )

    private val dayParts = mapOf("morning" to 9 * 60, "afternoon" to 14 * 60, "evening" to 18 * 60, "night" to 21 * 60)

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): Result {
        var text = " ${input.trim()} "
        val chips = ArrayList<String>()
        var date: LocalDate? = null
        var deadline: LocalDate? = null
        var startMin: Int? = null
        var impliedTime: Int? = null
        var duration: Int? = null
        var priority = 0

        // Sigils first; explicit syntax wins.
        Regex("(?<=\\s)(!{1,3})(?=\\s|$)").find(text)?.let { m ->
            priority = m.groupValues[1].length
            text = text.removeRange(m.range)
            chips += "priority $priority"
        }
        Regex("(?<=\\s)%(\\d{1,4})(?=\\s|$)").find(text)?.let { m ->
            duration = m.groupValues[1].toInt().coerceIn(1, 1440)
            text = text.removeRange(m.range)
        }
        Regex("(?<=\\s)~(\\S+)").find(text)?.let { m ->
            parseTimeToken(m.groupValues[1])?.let { startMin = it; text = text.removeRange(m.range) }
        }
        Regex("(?<=\\s)\\$(\\S+)").find(text)?.let { m ->
            parseDateToken(m.groupValues[1], now.toLocalDate(), locale)?.let { deadline = it; text = text.removeRange(m.range) }
        }
        Regex("(?<=\\s)@(\\S+)").find(text)?.let { m ->
            parseDateToken(m.groupValues[1], now.toLocalDate(), locale)?.let { date = it; text = text.removeRange(m.range) }
        }

        // Tags stay in the title; they are only reported.
        Regex("(?<=\\s)#([\\p{L}][\\p{L}\\p{N}_/-]*)").findAll(text).forEach { chips += "#${it.groupValues[1].lowercase()}" }

        // Time range.
        val range = Regex(
            "(?<=\\s)(?:from\\s+|at\\s+)?(\\d{1,2}(?::\\d{2})?)\\s*(am|pm)?\\s*(?:-|–|to)\\s*(\\d{1,2}(?::\\d{2})?)\\s*(am|pm)?(?=\\s|$)",
            RegexOption.IGNORE_CASE,
        ).findAll(text).lastOrNull { m ->
            val explicit = m.value.trim().startsWith("from", true) || m.value.trim().startsWith("at", true)
            explicit || m.groupValues[2].isNotEmpty() || m.groupValues[4].isNotEmpty() || m.groupValues[1].contains(':') || m.groupValues[3].contains(':')
        }
        if (range != null && startMin == null) {
            val startMer = range.groupValues[2].lowercase().ifEmpty { null }
            val endMer = range.groupValues[4].lowercase().ifEmpty { null }
            val resolved = resolveRange(range.groupValues[1], startMer, range.groupValues[3], endMer)
            if (resolved != null) {
                startMin = resolved.first
                if (duration == null) duration = (resolved.second - resolved.first).coerceIn(5, 1440)
                text = text.removeRange(range.range)
            }
        }

        // Single time.
        if (startMin == null) {
            val timeMatch = Regex(
                "(?<=\\s)(?:at\\s+)?(noon|midnight|(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)|(\\d{1,2}):(\\d{2})|at\\s+(\\d{1,2}))(?=\\s|$)",
                RegexOption.IGNORE_CASE,
            ).findAll(text).lastOrNull()
            if (timeMatch != null) {
                val parsed = parseTimeToken(timeMatch.value.trim().removePrefix("at ").removePrefix("At ").trim())
                if (parsed != null) {
                    startMin = parsed
                    text = text.removeRange(timeMatch.range)
                }
            }
        }

        // Date.
        if (date == null) {
            val today = now.toLocalDate()
            val weekdayNames = "sun(?:day)?|mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?"
            val monthNames = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
            val patterns = listOf(
                Regex("(?<=\\s)(today|tod|tomorrow|tmrw|tmr|tom|yesterday)(?:\\s+(morning|afternoon|evening|night))?(?=\\s|$)", RegexOption.IGNORE_CASE),
                Regex("(?<=\\s)(tonight|this\\s+(morning|afternoon|evening|night))(?=\\s|$)", RegexOption.IGNORE_CASE),
                Regex("(?<=\\s)next\\s+week(?=\\s|$)", RegexOption.IGNORE_CASE),
                Regex("(?<=\\s)(?:on\\s+|next\\s+)?($weekdayNames)(?=\\s|$)", RegexOption.IGNORE_CASE),
                Regex("(?<=\\s)in\\s+(a|an|\\d+)\\s+(day|days|week|weeks|month|months)(?=\\s|$)", RegexOption.IGNORE_CASE),
                Regex("(?<=\\s)($monthNames)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?(?=\\s|$)", RegexOption.IGNORE_CASE),
                Regex("(?<=\\s)(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?(?=\\s|$)"),
            )
            var best: Pair<MatchResult, LocalDate>? = null
            for (p in patterns) {
                for (m in p.findAll(text)) {
                    val d = resolveDate(m, today) ?: continue
                    if (best == null || m.range.first > best!!.first.range.first) best = Pair(m, d)
                }
            }
            if (best != null) {
                val (m, d) = best!!
                date = d
                val part = m.groups.filterNotNull().drop(1).map { it.value.lowercase() }.firstOrNull { it in dayParts }
                if (m.value.trim().lowercase() == "tonight") impliedTime = dayParts.getValue("night")
                else if (part != null) impliedTime = dayParts.getValue(part)
                text = text.removeRange(m.range)
            }
        }

        // Duration.
        if (duration == null) {
            val dur = Regex(
                "(?<=\\s)(?:for\\s+)?(?:(an?\\s+hour)|(half\\s+an\\s+hour)|(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hour|hours)(?:\\s*(\\d+)\\s*(m|min|mins|minutes)?)?|(\\d+)\\s*(m|min|mins|minutes))(?=\\s|$)",
                RegexOption.IGNORE_CASE,
            ).findAll(text).lastOrNull()
            if (dur != null) {
                val g = dur.groupValues
                val minutes = when {
                    g[1].isNotEmpty() -> 60
                    g[2].isNotEmpty() -> 30
                    g[3].isNotEmpty() -> (g[3].toDouble() * 60).toInt() + (g[5].toIntOrNull() ?: 0)
                    else -> g[7].toInt()
                }
                if (minutes >= 5) {
                    duration = minutes.coerceIn(5, 1440)
                    text = text.removeRange(dur.range)
                }
            }
        }

        if (startMin == null && impliedTime != null) startMin = impliedTime
        if (startMin != null && date == null) date = now.toLocalDate()

        val title = text.replace(Regex("\\s{2,}"), " ").trim().trim(',', '.', ';').trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

        date?.let { chips += if (it == now.toLocalDate()) "today" else if (it == now.toLocalDate().plusDays(1)) "tomorrow" else it.toString() }
        startMin?.let { chips += formatTimeChip(it) }
        duration?.let { chips += "${it}m" }
        deadline?.let { chips += "due $it" }

        return Result(
            TaskDraft(
                title = title.ifEmpty { input.trim() },
                date = date,
                startMin = startMin,
                durationMin = duration ?: 30,
                priority = priority,
                deadline = deadline,
            ),
            chips,
        )
    }

    private fun formatTimeChip(min: Int): String {
        val h = min / 60
        val m = min % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (m == 0) "$h12 ${if (h < 12) "AM" else "PM"}" else "$h12:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
    }

    /**
     * Start and end minutes for a range. The meridiem back-propagates from the end to the
     * start (`11-1pm` is 11 AM to 1 PM); an end before the start rolls forward (`11pm to 1am`
     * crosses midnight, `from 3 to 4:30` reads as 15:00 to 16:30).
     */
    internal fun resolveRange(startClock: String, startMer: String?, endClock: String, endMer: String?): Pair<Int, Int>? {
        val start: Int
        val end: Int
        when {
            startMer != null -> {
                start = clockToMinutes(startClock, startMer, bareHourRule = false) ?: return null
                end = clockToMinutes(endClock, endMer ?: startMer, bareHourRule = false) ?: return null
            }
            endMer != null -> {
                end = clockToMinutes(endClock, endMer, bareHourRule = false) ?: return null
                val same = clockToMinutes(startClock, endMer, bareHourRule = false) ?: return null
                start = if (same < end) same else clockToMinutes(startClock, if (endMer == "pm") "am" else "pm", bareHourRule = false) ?: return null
            }
            else -> {
                start = clockToMinutes(startClock, null, bareHourRule = true) ?: return null
                end = clockToMinutes(endClock, null, bareHourRule = false) ?: return null
            }
        }
        var e = end
        while (e <= start) e += 12 * 60
        if (e - start > 1440) return null
        return Pair(start, e)
    }

    /** `3pm`, `3:30pm`, `15:00`, `noon`, `midnight`, `3` (working-hours rule). */
    fun parseTimeToken(token: String): Int? {
        val t = token.trim().lowercase().replace(".", "")
        if (t == "noon") return 12 * 60
        if (t == "midnight") return 0
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$").find(t) ?: return null
        val mer = m.groupValues[3].ifEmpty { null }
        return clockToMinutes(m.groupValues[1] + (if (m.groupValues[2].isNotEmpty()) ":" + m.groupValues[2] else ""), mer, bareHourRule = mer == null && m.groupValues[2].isEmpty())
    }

    /**
     * `HH[:MM]` with an optional meridiem. With [bareHourRule], a lone hour follows dayGLANCE's
     * working-hours rule: 8 to 12 stay as written, 1 to 7 become PM, 13 to 23 are already 24h.
     */
    private fun clockToMinutes(clock: String, meridiem: String?, bareHourRule: Boolean): Int? {
        val parts = clock.split(':')
        var h = parts[0].toIntOrNull() ?: return null
        val m = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else 0
        if (h > 23 || m > 59) return null
        when (meridiem) {
            "am" -> if (h == 12) h = 0 else if (h > 12) return null
            "pm" -> if (h < 12) h += 12
            null -> if (bareHourRule && h in 1..7) h += 12
        }
        return h * 60 + m
    }

    private fun parseDateToken(token: String, today: LocalDate, locale: Locale): LocalDate? {
        val t = token.lowercase().trim()
        val quick = mapOf("today" to 0L, "tod" to 0L, "tomorrow" to 1L, "tmrw" to 1L, "tmr" to 1L, "tom" to 1L, "yesterday" to -1L)
        quick[t]?.let { return today.plusDays(it) }
        weekdayFor(t)?.let { return nextWeekday(today, it) }
        Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?$").find(t)?.let { m ->
            return monthDay(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull(), today)
        }
        return runCatching { LocalDate.parse(t) }.getOrNull()
    }

    private fun resolveDate(m: MatchResult, today: LocalDate): LocalDate? {
        val raw = m.value.trim().lowercase()
        val g = m.groupValues
        return when {
            raw.startsWith("tonight") || raw.startsWith("this ") -> today
            raw == "next week" -> today.plusDays(7)
            raw.startsWith("in ") -> {
                val n = if (g[1] == "a" || g[1] == "an") 1L else g[1].toLongOrNull() ?: return null
                when {
                    g[2].startsWith("day") -> today.plusDays(n)
                    g[2].startsWith("week") -> today.plusWeeks(n)
                    else -> today.plusMonths(n)
                }
            }
            g.size > 1 && g[1].lowercase() in setOf("today", "tod", "tomorrow", "tmrw", "tmr", "tom", "yesterday") ->
                today.plusDays(when (g[1].lowercase()) { "yesterday" -> -1L; "today", "tod" -> 0L; else -> 1L })
            g.size > 1 && weekdayFor(g[1].lowercase()) != null -> nextWeekday(today, weekdayFor(g[1].lowercase())!!)
            g.size > 2 && monthFor(g[1].lowercase()) != null -> {
                val month = monthFor(g[1].lowercase())!!
                monthDay(month.value, g[2].toInt(), g.getOrNull(3)?.toIntOrNull(), today)
            }
            g.size > 2 && g[1].all { it.isDigit() } && g[2].all { it.isDigit() } ->
                monthDay(g[1].toInt(), g[2].toInt(), g.getOrNull(3)?.toIntOrNull(), today)
            else -> null
        }
    }

    private fun monthDay(month: Int, day: Int, year: Int?, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return runCatching {
            if (year != null) LocalDate.of(year, month, day)
            else {
                val candidate = LocalDate.of(today.year, month, day)
                if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }
        }.getOrNull()
    }

    private fun weekdayFor(t: String): DayOfWeek? = when {
        t.startsWith("sun") -> DayOfWeek.SUNDAY
        t.startsWith("mon") -> DayOfWeek.MONDAY
        t.startsWith("tue") -> DayOfWeek.TUESDAY
        t.startsWith("wed") -> DayOfWeek.WEDNESDAY
        t.startsWith("thu") -> DayOfWeek.THURSDAY
        t.startsWith("fri") -> DayOfWeek.FRIDAY
        t.startsWith("sat") -> DayOfWeek.SATURDAY
        else -> null
    }

    private fun monthFor(t: String): Month? = Month.values().firstOrNull { month ->
        val full = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        t == full || (t.length >= 3 && full.startsWith(t) && (t.length == 3 || t == "sept"))
    }

    /** The next future occurrence: if today is Friday, "friday" means a week from now. */
    private fun nextWeekday(today: LocalDate, day: DayOfWeek): LocalDate {
        var delta = (day.value - today.dayOfWeek.value + 7) % 7
        if (delta == 0) delta = 7
        return today.plusDays(delta.toLong())
    }
}
