package app.glance.launcher.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class FakeRepositoryTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun `reseed rebuilds the demo around the new today and drops earlier taps`() = runBlocking {
        val started = LocalDate.of(2026, 11, 2)
        val clock = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), zone)
        // Built the day the launcher started, four days before the clock's today.
        val repo = FakeRepository(clock, FakeSeed(started, started.atStartOfDay(zone).toInstant(), zone))
        assertTrue(repo.tasks.value.any { it.date == started })
        val tapped = repo.tasks.value.first { !it.completed && it.completable }
        repo.setTaskCompleted(tapped.id, true)

        repo.reseed()

        val today = LocalDate.of(2026, 11, 6)
        val fresh = FakeSeed(today, clock.instant(), zone)
        assertEquals(fresh.tasks.map { it.date }, repo.tasks.value.map { it.date })
        assertTrue(repo.tasks.value.any { it.date == today })
        assertEquals(fresh.tasks.map { it.completed }, repo.tasks.value.map { it.completed })
        assertEquals(fresh.chores.map { it.lastDoneAt }, repo.chores.value.map { it.lastDoneAt })
    }
}
