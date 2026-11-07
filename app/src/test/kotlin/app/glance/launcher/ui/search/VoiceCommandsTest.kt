package app.glance.launcher.ui.search

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceCommandsTest {
    private val morning = LocalTime.of(9, 0)
    private fun parse(s: String, now: LocalTime = morning) = VoiceCommands.parse(s, now)

    @Test
    fun `open, call, text, navigate, search and talked-to read their object`() {
        assertEquals(VoiceCommand.Open("spotify"), parse("Open Spotify"))
        assertEquals(VoiceCommand.Open("google maps"), parse("launch the Google Maps app"))
        assertEquals(VoiceCommand.Call("mom"), parse("call Mom."))
        assertEquals(VoiceCommand.Text("sam"), parse("send a message to Sam"))
        assertEquals(VoiceCommand.Text("sam"), parse("text Sam"))
        assertEquals(VoiceCommand.Navigate("whole foods"), parse("navigate to Whole Foods"))
        assertEquals(VoiceCommand.Navigate("the airport"), parse("take me to the airport"))
        assertEquals(VoiceCommand.WebSearch("weather tomorrow"), parse("search for weather tomorrow"))
        assertEquals(VoiceCommand.TalkedTo("sam"), parse("I talked to Sam"))
        assertEquals(VoiceCommand.TalkedTo("alex"), parse("had coffee with Alex"))
    }

    @Test
    fun `add and remind me make a task of the rest`() {
        assertEquals(VoiceCommand.AddTask("dentist friday 3pm"), parse("add dentist Friday 3pm"))
        assertEquals(VoiceCommand.AddTask("call the plumber tomorrow"), parse("remind me to call the plumber tomorrow"))
        assertEquals(VoiceCommand.AddTask("buy milk"), parse("new task buy milk"))
    }

    @Test
    fun `filler before and after the command is ignored`() {
        assertEquals(VoiceCommand.Open("spotify"), parse("Hey, please open Spotify please"))
        assertEquals(VoiceCommand.Call("mom"), parse("can you call mom?"))
    }

    @Test
    fun `timers read the duration in digits or words, with an optional label`() {
        assertEquals(VoiceCommand.Timer(600, null), parse("set a timer for 10 minutes"))
        assertEquals(VoiceCommand.Timer(600, null), parse("timer ten minutes"))
        assertEquals(VoiceCommand.Timer(5400, null), parse("start a timer for 1 hour 30 minutes"))
        assertEquals(VoiceCommand.Timer(1500, null), parse("twenty five minute timer"))
        assertEquals(VoiceCommand.Timer(1800, null), parse("timer for half an hour"))
        assertEquals(VoiceCommand.Timer(5400, null), parse("timer for an hour and a half"))
        assertEquals(VoiceCommand.Timer(60, null), parse("set a timer for a minute"))
        assertEquals(VoiceCommand.Timer(720, "pasta"), parse("timer for 12 minutes for pasta"))
        assertEquals(VoiceCommand.Timer(45, null), parse("45 second timer"))
        // "timer" with no duration is no command: it stays a search (the Clock app, say).
        assertNull(parse("timer"))
    }

    @Test
    fun `alarms take am and pm, words, and otherwise the next matching time of day`() {
        assertEquals(VoiceCommand.Alarm(6, 30), parse("set an alarm for 6:30 am"))
        assertEquals(VoiceCommand.Alarm(18, 0), parse("alarm 6 pm"))
        assertEquals(VoiceCommand.Alarm(7, 30), parse("wake me up at seven thirty"))
        assertEquals(VoiceCommand.Alarm(0, 15), parse("alarm 12:15 am"))
        assertEquals(VoiceCommand.Alarm(21, 0), parse("alarm 9 tonight"))
        // No a.m. or p.m.: at 9:00, "6:30" is this evening; at 5:00 it is this morning.
        assertEquals(VoiceCommand.Alarm(18, 30), parse("alarm 6:30", LocalTime.of(9, 0)))
        assertEquals(VoiceCommand.Alarm(6, 30), parse("alarm 6:30", LocalTime.of(5, 0)))
        // After 6:30 p.m., tomorrow morning.
        assertEquals(VoiceCommand.Alarm(6, 30), parse("alarm 6:30", LocalTime.of(20, 0)))
        assertEquals(VoiceCommand.Alarm(14, 5), parse("alarm 14:05"))
        // "in 10 minutes" is a duration, not a time of day.
        assertNull(parse("alarm in 10 minutes"))
    }

    @Test
    fun `anything else is no command`() {
        assertNull(parse("spotify"))
        assertNull(parse("dentist friday 3pm"))
        assertNull(parse(""))
        assertNull(parse("open"))
    }

    @Test
    fun `spoken numbers become digits, and a or an only before a unit`() {
        assertEquals("25 minutes", VoiceCommands.numbersToDigits("twenty five minutes"))
        assertEquals("set a timer for 1 hour", VoiceCommands.numbersToDigits("set a timer for an hour"))
        assertEquals(90, VoiceCommands.durationSeconds("a minute and a half"))
    }
}
