package app.glance.launcher.ui.search

import java.time.LocalTime

/**
 * A command the launcher carries out itself, read from what was said (or typed). No AI: a fixed
 * set of leading verbs, the way a voice remote works. Anything that is not one of these stays an
 * ordinary search.
 */
sealed class VoiceCommand {
    /** "open Spotify": the app whose name matches. */
    data class Open(val appName: String) : VoiceCommand()
    /** "call Mom": the contact's number in the dialer, one tap from calling. */
    data class Call(val name: String) : VoiceCommand()
    /** "text Mom": a new message to the contact. */
    data class Text(val name: String) : VoiceCommand()
    /** "timer 10 minutes": the clock app's timer, [label] when one was given ("…for pasta"). */
    data class Timer(val seconds: Int, val label: String?) : VoiceCommand()
    /** "alarm 6:30": the clock app's alarm, at the next time of day that matches. */
    data class Alarm(val hour: Int, val minute: Int) : VoiceCommand()
    /** "navigate to Whole Foods": directions in the maps app. */
    data class Navigate(val place: String) : VoiceCommand()
    /** "add dentist friday 3pm", "remind me to…": a dayGLANCE task, through quick add. */
    data class AddTask(val text: String) : VoiceCommand()
    /** "talked to Sam": an interaction logged in lastGLANCE. */
    data class TalkedTo(val name: String) : VoiceCommand()
    /** "search for…", "google…", "look up…": the web search. */
    data class WebSearch(val query: String) : VoiceCommand()
}

/** Reads a [VoiceCommand] from a phrase. Pure: tested on the JVM. */
object VoiceCommands {
    fun parse(raw: String, now: LocalTime = LocalTime.now()): VoiceCommand? {
        val text = normalise(raw)
        if (text.isEmpty()) return null
        // Timers and alarms first: "start a timer" and "set an alarm" begin with other verbs.
        timer(text)?.let { return it }
        alarm(text, now)?.let { return it }
        for ((pattern, build) in RULES) {
            val m = pattern.matchEntire(text) ?: continue
            val rest = m.groupValues.last().trim()
            if (rest.isNotEmpty()) return build(rest)
        }
        return null
    }

    private val RULES: List<Pair<Regex, (String) -> VoiceCommand>> = listOf(
        Regex("""(?:navigate|directions|get directions|take me|drive|route)(?: me)?(?: to)? (.+)""") to { s -> VoiceCommand.Navigate(s) },
        Regex("""(?:call|phone|ring|dial) (.+)""") to { s -> VoiceCommand.Call(s) },
        Regex("""(?:text|message|sms|send (?:a )?(?:text|message) to) (.+)""") to { s -> VoiceCommand.Text(s) },
        Regex("""(?:i )?(?:talked|spoke|chatted|caught up|met up|had coffee) (?:to|with) (.+)""") to { s -> VoiceCommand.TalkedTo(s) },
        Regex("""(?:remind me to|remind me|add (?:a )?task|new task|create (?:a )?task|add|to ?do|task) (.+)""") to { s -> VoiceCommand.AddTask(s) },
        Regex("""(?:search for|search|google|look up|web search for|web search) (.+)""") to { s -> VoiceCommand.WebSearch(s) },
        Regex("""(?:open|launch|start|run|go to|show me|show) (?:the )?(.+?)(?: app)?""") to { s -> VoiceCommand.Open(s) },
    )

    /** Lower case, filler and trailing punctuation dropped, spaces collapsed. */
    private fun normalise(raw: String): String {
        var s = raw.lowercase().replace(",", " ").replace(Regex("""[.!?]+$"""), "").replace(Regex("""\s+"""), " ").trim()
        s = s.replace(Regex("""^(?:(?:hey|ok|okay|please|can you|could you|would you)\s+)+"""), "")
        return s.removeSuffix(" please").trim()
    }

    private fun timer(text: String): VoiceCommand.Timer? {
        if (!Regex("""\btimer\b""").containsMatchIn(text)) return null
        // A label after the duration: "timer for 10 minutes for pasta", "a 10 minute pasta timer" is not attempted.
        val labelMatch = Regex("""\b(?:for|called|named) ([a-z][a-z ]*)$""").find(text)
        val label = labelMatch?.groupValues?.get(1)?.trim()?.takeIf { durationSeconds(it) == null && !it.contains("timer") }
        val body = if (label != null) text.substring(0, labelMatch.range.first) else text
        val seconds = durationSeconds(body) ?: return null
        return if (seconds in 1..(24 * 3600)) VoiceCommand.Timer(seconds, label) else null
    }

    /** The total of every "N unit" in [text] ("1 hour 30 minutes", "half an hour"), or null without one. */
    internal fun durationSeconds(text: String): Int? {
        var total = 0
        var found = false
        val halfHour = Regex("""\bhalf (?:an )?hour\b""")
        if (halfHour.containsMatchIn(text)) { total += 1800; found = true }
        val words = numbersToDigits(text.replace(halfHour, " "))
        // "10 minutes", "1 hour 30 minutes", "an hour and a half", "2 and a half minutes".
        for (m in Regex("""(\d+(?:\.\d+)?)( and a half)? ?(hours?|hrs?|minutes?|mins?|seconds?|secs?)\b( and a half)?""").findAll(words)) {
            val half = m.groupValues[2].isNotEmpty() || m.groupValues[4].isNotEmpty()
            val n = m.groupValues[1].toDouble() + if (half) 0.5 else 0.0
            val unit = when (m.groupValues[3].first()) { 'h' -> 3600; 'm' -> 60; else -> 1 }
            total += (n * unit).toInt()
            found = true
        }
        return if (found) total else null
    }

    private fun alarm(text: String, now: LocalTime): VoiceCommand.Alarm? {
        val asks = Regex("""\balarm\b""").containsMatchIn(text) || text.startsWith("wake me up") || text.startsWith("wake me")
        if (!asks) return null
        val words = numbersToDigits(text)
        // "alarm in 10 minutes" is a duration, not a time of day: not read as one.
        if (Regex("""\bin \d+ ?(?:hours?|minutes?|mins?)\b""").containsMatchIn(words)) return null
        val m = Regex("""\b(\d{1,2})(?:[: ](\d{2}))? ?(a\.? ?m\.?|p\.? ?m\.?|in the morning|in the evening|at night|tonight)?(?=\s|$)""")
            .findAll(words).lastOrNull { it.groupValues[1].toInt() in 0..23 } ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        if (minute !in 0..59) return null
        val meridiem = m.groupValues[3]
        val pm = meridiem.startsWith("p") || meridiem == "in the evening" || meridiem == "at night" || meridiem == "tonight"
        val am = meridiem.startsWith("a") || meridiem == "in the morning"
        when {
            pm && hour in 1..11 -> hour += 12
            am && hour == 12 -> hour = 0
            // "Wake me up at 7:30" is the morning, whatever the time now.
            !pm && !am && hour in 1..11 && text.startsWith("wake me") -> Unit
            !pm && !am && hour in 1..12 -> {
                // No a.m. or p.m.: the next time of day that matches, as a clock app reads "6:30".
                val morning = LocalTime.of(hour % 12, minute)
                val evening = LocalTime.of(hour % 12 + 12, minute)
                hour = when {
                    morning.isAfter(now) -> morning.hour
                    evening.isAfter(now) -> evening.hour
                    else -> morning.hour
                }
            }
        }
        return VoiceCommand.Alarm(hour, minute)
    }

    private val UNITS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "ninety" to 90)

    /** Spoken numbers up to ninety-nine as digits ("twenty five minutes" to "25 minutes"); "a"/"an" only before a unit. */
    internal fun numbersToDigits(text: String): String {
        val tokens = text.split(" ")
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i].replace("-", " ").split(" ")
            val tens = TENS[t[0]]
            when {
                tens != null && t.size == 2 && UNITS[t[1]] != null -> out += (tens + UNITS.getValue(t[1])).toString()
                tens != null && i + 1 < tokens.size && UNITS[tokens[i + 1]]?.takeIf { tokens[i + 1] !in setOf("a", "an") } != null -> {
                    out += (tens + UNITS.getValue(tokens[i + 1])).toString(); i++
                }
                tens != null -> out += tens.toString()
                t[0] in setOf("a", "an") -> {
                    // "a minute", "an hour": a number only before a unit.
                    val next = tokens.getOrNull(i + 1).orEmpty()
                    out += if (Regex("""^(hours?|minutes?|seconds?|hrs?|mins?|secs?)$""").matches(next)) "1" else t[0]
                }
                UNITS[t[0]] != null && t.size == 1 -> out += UNITS.getValue(t[0]).toString()
                else -> out += tokens[i]
            }
            i++
        }
        return out.joinToString(" ")
    }
}
