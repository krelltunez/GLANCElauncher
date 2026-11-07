package app.glance.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [Overnight] held to dayGLANCE's own numbers (#2278). The vectors are generated from the
 * app's functions (src/utils/overnightVectors.js, `npm run ios:vectors`) and copied here from
 * dayglance-ios/TestFixtures/overnight.vectors.json; when the app's rule changes, copy the new
 * file over this one and this test says whether the port still agrees.
 *
 * The `frameSlots` section is not checked: the launcher reads each frame's free time from the
 * snapshot (`frames[].slots`), which the app already nets of the night before's pieces.
 */
class OvernightVectorsTest {
    private val vectors: JSONObject by lazy { JSONObject(load()) }

    /** On the classpath under Gradle; otherwise found by path from the working directory up. */
    private fun load(): String {
        javaClass.classLoader?.getResource(FILE)?.let { return it.readText() }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/test/resources/$FILE")
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("$FILE not found on the classpath or above ${File("").absolutePath}")
    }

    private fun minutesOf(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt() // seconds, if any, are ignored, as in the app
    }

    /** A snapshot task row as the vectors write it, as the launcher's model holds it. */
    private fun row(o: JSONObject): Task = Task(
        id = o.getString("id"),
        title = o.optString("title"),
        date = LocalDate.parse(o.getString("date")),
        startMin = if (o.isNull("startTime")) null else minutesOf(o.getString("startTime")),
        durationMin = o.optInt("duration", 0),
        isAllDay = o.optBoolean("isAllDay", false),
        completed = o.optBoolean("completed", false),
    )

    private fun cases(section: String): List<JSONObject> {
        val arr = vectors.getJSONObject(section).getJSONArray("cases")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    @Test
    fun `the vectors are the format this port reads`() {
        assertEquals("overnight.vectors/1", vectors.getString("format"))
        assertTrue(cases("spill").isNotEmpty() && cases("continuations").isNotEmpty())
    }

    @Test
    fun `each row's spill and real end match the app`() {
        for (c in cases("spill")) {
            val name = c.getString("name")
            val task = row(c.getJSONObject("input"))
            val expected = c.getJSONObject("expected")
            assertEquals(expected.getInt("spillMinutes"), Overnight.spillMinutes(task), "$name: spill")
            if (expected.isNull("end")) {
                assertNull(Overnight.end(task), "$name: end")
            } else {
                val end = expected.getJSONObject("end")
                val want = LocalDate.parse(end.getString("date")).atStartOfDay().plusMinutes(end.getInt("minutes").toLong())
                assertEquals(want, Overnight.end(task), "$name: end")
            }
        }
    }

    @Test
    fun `a date's pieces from the previous date's rows match the app, in row order`() {
        for (c in cases("continuations")) {
            val name = c.getString("name")
            val rows: JSONArray = c.getJSONObject("input").getJSONArray("prevDayRows")
            val got = Overnight.continuationsFromPreviousDay((0 until rows.length()).map { row(rows.getJSONObject(it)) })
            val want = c.getJSONArray("expected")
            assertEquals(want.length(), got.size, "$name: count")
            for ((i, piece) in got.withIndex()) {
                val w = want.getJSONObject(i)
                assertEquals(w.getString("id"), piece.task.id, "$name[$i]: id")
                assertEquals(LocalDate.parse(w.getString("fromDate")), piece.fromDate, "$name[$i]: fromDate")
                assertEquals(w.getInt("startMinutes"), piece.startMinutes, "$name[$i]: start")
                assertEquals(w.getInt("endMinutes"), piece.endMinutes, "$name[$i]: end")
                assertEquals(w.getInt("minutes"), piece.minutes, "$name[$i]: minutes")
            }
        }
    }

    @Test
    fun `continuationsFor reads the date before`() {
        val d = LocalDate.of(2026, 11, 3)
        val night = Task("night", "Night", d.minusDays(1), 23 * 60, 120)
        val other = Task("other", "Two days ago", d.minusDays(2), 23 * 60, 120)
        val today = Task("today", "Today", d, 23 * 60, 120)
        assertEquals(listOf("night"), Overnight.continuationsFor(d, listOf(other, night, today)).map { it.task.id })
    }

    private companion object { const val FILE = "overnight.vectors.json" }
}
