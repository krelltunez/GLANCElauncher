package app.glance.launcher.data.provider

import app.glance.launcher.data.Chore
import app.glance.launcher.data.Person
import app.glance.launcher.data.Routine
import app.glance.launcher.data.Task
import app.glance.launcher.data.TaskSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxTest {
    private val now = Instant.parse("2026-10-19T15:00:00Z")
    private val today = LocalDate.of(2026, 10, 19)
    private fun entry(patch: Patch, app: GlanceApp = GlanceApp.DAYGLANCE, id: String = "w", createdAt: Instant = now) =
        OutboxEntry(id, app, app.action("X"), """{"a":1}""", createdAt, patch)

    @Test
    fun `patches apply over a snapshot and leave everything else alone`() {
        val tasks = listOf(
            Task("t1", "Run", today, 7 * 60, 45),
            Task("i1", "Inbox", null, null, 15, priority = 2, deadline = today.plusDays(2)),
            Task("e1", "Standup", today, 10 * 60, 15, source = TaskSource.CALENDAR),
        )
        val created = Task("w-new", "From launcher", today, 14 * 60, 30)
        val out = Outbox.applyToTasks(tasks, listOf(
            entry(Patch.CompleteTask("t1", now)),
            entry(Patch.CompleteTask("e1", now)),            // a calendar event cannot be completed
            entry(Patch.MoveTask("i1", today, 9 * 60)),      // scheduling an Inbox task drops its inbox-only fields
            entry(Patch.ResizeTask("t1", 3)),                // never below five minutes
            entry(Patch.CreateTask(created)),
            entry(Patch.CreateTask(created)),                // idempotent
        ))
        assertEquals(4, out.size)
        assertTrue(out[0].completed)
        assertEquals(now, out[0].completedAt)
        val reopened = Outbox.applyToTasks(out, listOf(entry(Patch.UncompleteTask("t1"))))
        assertFalse(reopened[0].completed)
        assertNull(reopened[0].completedAt)
        assertEquals(5, out[0].durationMin)
        assertFalse(out[2].completed)
        assertEquals(today to 9 * 60, out[1].date to out[1].startMin)
        assertEquals(0, out[1].priority)
        assertNull(out[1].deadline)
        assertEquals("w-new", out[3].id)

        val routines = Outbox.applyToRoutines(listOf(Routine("r1", "Stretch", 750, 765, setOf(today))), listOf(entry(Patch.ToggleRoutine("r1", today))))
        assertTrue(routines[0].completedDates.isEmpty())
        // A tap that asked for a state sets it: a snapshot that already shows it is left alone,
        // where a flip would undo it (dayGLANCE's snapshot carries the change before the ledger
        // catches up, or another device ticked it first).
        val done = listOf(Routine("r1", "Stretch", 750, 765, setOf(today)))
        assertEquals(setOf(today), Outbox.applyToRoutines(done, listOf(entry(Patch.ToggleRoutine("r1", today, completed = true))))[0].completedDates)
        assertEquals(emptySet(), Outbox.applyToRoutines(done, listOf(entry(Patch.ToggleRoutine("r1", today, completed = false))))[0].completedDates)
        val open = listOf(Routine("r1", "Stretch", 750, 765, emptySet()))
        assertEquals(setOf(today), Outbox.applyToRoutines(open, listOf(entry(Patch.ToggleRoutine("r1", today, completed = true))))[0].completedDates)

        val chores = Outbox.applyToChores(listOf(Chore("c1", "Vacuum", "Kitchen", 7, now.minus(Duration.ofDays(9)))), listOf(
            entry(Patch.LogChore("c1", now), GlanceApp.LASTGLANCE),
            entry(Patch.AddChore(Chore("w-c", "Dust", "Inbox")), GlanceApp.LASTGLANCE),
        ))
        assertEquals(now, chores[0].lastDoneAt)
        assertEquals("w-c", chores[1].id)
        // A snapshot that already moved past the optimistic stamp wins.
        val later = Outbox.applyToChores(listOf(Chore("c1", "Vacuum", "Kitchen", 7, now.plusSeconds(60))), listOf(entry(Patch.LogChore("c1", now), GlanceApp.LASTGLANCE)))
        assertEquals(now.plusSeconds(60), later[0].lastDoneAt)

        val people = Outbox.applyToPeople(listOf(Person("p1", "Ana", unreturned = true)), listOf(entry(Patch.LogInteraction("p1", now), GlanceApp.LASTGLANCE)))
        assertEquals(now, people[0].lastInteractionAt)
        assertFalse(people[0].unreturned)
    }

    @Test
    fun `the outbox survives a round trip through its JSON form`() {
        val entries = listOf(
            entry(Patch.CompleteTask("t1", now), id = "a"),
            entry(Patch.UncompleteTask("t1"), id = "a2"),
            entry(Patch.CreateTask(Task("b", "New", today, 9 * 60, 30, priority = 1, deadline = today.plusDays(3), colorHex = "#ec4899", projectId = "p1")), id = "b"),
            entry(Patch.MoveTask("t1", today.plusDays(1), null), id = "c"),
            entry(Patch.ResizeTask("t1", 60), id = "d"),
            entry(Patch.ToggleRoutine("r1", today), id = "e"),
            entry(Patch.ToggleRoutine("r1", today, completed = true), id = "e2"),
            entry(Patch.LogChore("c1", now), GlanceApp.LASTGLANCE, "f"),
            entry(Patch.AddChore(Chore("g", "Dust", "Inbox")), GlanceApp.LASTGLANCE, "g"),
            entry(Patch.LogInteraction("p1", now), GlanceApp.LASTGLANCE, "h"),
        )
        val back = Outbox.decode(Outbox.encode(entries))
        assertEquals(entries, back)
        assertTrue(Outbox.decode(null).isEmpty())
        assertTrue(Outbox.decode("nope").isEmpty())
        // An entry of an unknown shape is dropped, the rest kept.
        val mixed = """[{"clientId":"z","app":"DAYGLANCE","action":"x","payload":"{}","createdAt":1,"patch":{"type":"future"}},""" +
            Outbox.encode(entries.take(1)).trim('[', ']') + "]"
        assertEquals(listOf("a"), Outbox.decode(mixed).map { it.clientId })
    }

    @Test
    fun `an entry expires after a day`() {
        assertFalse(entry(Patch.CompleteTask("t", now), createdAt = now.minus(Duration.ofHours(23))).expired(now))
        assertTrue(entry(Patch.CompleteTask("t", now), createdAt = now.minus(Duration.ofHours(25))).expired(now))
    }
}
