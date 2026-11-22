package com.step365.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.step365.app.domain.Challenge
import com.step365.app.domain.CounterSample
import com.step365.app.domain.DayStatus
import com.step365.app.domain.calories
import com.step365.app.domain.distance
import com.step365.app.domain.status
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StepRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: StepDatabase
    private lateinit var repository: StepRepository
    private val first = Challenge.start
    private val zone = "Africa/Nairobi"
    private val boot = 14

    @Before fun createDatabase() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StepDatabase::class.java).build()
        repository = StepRepository(database)
    }

    @After fun closeDatabase() { database.close() }

    private fun time(date: LocalDate = first, hour: Int = 12, minute: Int = 0, second: Int = 0): Long =
        date.atTime(hour, minute, second).atZone(ZoneId.of(zone)).toInstant().toEpochMilli()

    private fun sample(raw: Long, timestamp: Long, date: LocalDate = first, bootCount: Int = boot) =
        CounterSample(raw, timestamp, date.toString(), zone, bootCount)

    @Test fun `empty installation has real defaults and no fabricated records`() = runBlocking {
        val value = repository.snapshot()
        assertTrue(value.records.isEmpty())
        assertEquals(.70, value.stride, 0.0)
        assertNull(value.weightKg)
        assertEquals(10_000, value.personalGoal)
        assertFalse(value.enabled)
        assertFalse(value.tracker.active)
    }

    @Test fun `counter baseline duplicate and next event update atomically`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.counter(sample(72_000, 1_000), time())
        assertEquals(0L, repository.snapshot().records.single().steps)
        repository.counter(sample(72_018, 2_000), time() + 1)
        repository.counter(sample(72_018, 2_000), time() + 2)
        val value = repository.snapshot()
        assertEquals(18L, value.records.single().steps)
        assertEquals(72_018L, value.tracker.raw)
        assertEquals(2_000L, value.tracker.elapsedNanos)
        assertFalse(value.records.single().complete)
    }

    @Test fun `concurrent identical counter callbacks commit the increment exactly once`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.counter(sample(10_000, 1_000), time())
        val event = sample(10_125, 2_000)
        List(20) { index ->
            async(Dispatchers.Default) { repository.counter(event, time() + index + 1) }
        }.awaitAll()
        val value = repository.snapshot()
        assertEquals(125L, value.records.single().steps)
        assertEquals(10_125L, value.tracker.raw)
        assertEquals(2_000L, value.tracker.elapsedNanos)
    }

    @Test fun `recreated repository retains checkpoint and never repeats earlier steps`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.counter(sample(50_000, 1_000), time())
        repository.counter(sample(50_015, 2_000), time() + 1)
        val recreated = StepRepository(database)
        recreated.counter(sample(50_015, 2_000), time() + 2)
        recreated.counter(sample(50_019, 3_000), time() + 3)
        assertEquals(19L, recreated.snapshot().records.single().steps)
    }

    @Test fun `service restart preserves daily total while discarding unavailable baseline`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.counter(sample(40_000, 1_000), time())
        repository.counter(sample(40_060, 2_000), time() + 1)
        repository.stop("Tracking stopped", time() + 2)
        repository.begin(first, zone, boot, time() + 10_000)
        repository.counter(sample(40_900, 3_000), time() + 10_001)
        repository.counter(sample(40_910, 4_000), time() + 10_002)
        val day = repository.snapshot().records.single()
        assertEquals(70L, day.steps)
        assertFalse(day.complete)
    }

    @Test fun `stale service shutdown cannot stop its replacement session`() = runBlocking {
        repository.begin(first, zone, boot, time(), session = "old")
        repository.counter(sample(40_000, 1_000), time())
        repository.counter(sample(40_010, 2_000), time() + 1)
        repository.begin(first, zone, boot, time() + 2, session = "new")
        val beforeStaleStop = repository.snapshot()
        repository.stop("Old service destroyed", time() + 3, expectedSession = "old")
        val current = repository.snapshot()
        assertTrue(current.tracker.active)
        assertEquals("new", current.tracker.session)
        assertEquals(beforeStaleStop.tracker, current.tracker)
        assertEquals(beforeStaleStop.records, current.records)
        assertEquals(10L, current.records.single().steps)
        repository.stop("Current service stopped", time() + 4, expectedSession = "new")
        val stopped = repository.snapshot()
        assertFalse(stopped.tracker.active)
        assertEquals("new", stopped.tracker.session)
        assertEquals("Current service stopped", stopped.tracker.status)
        assertEquals(10L, stopped.records.single().steps)
    }

    @Test fun `counter reset preserves daily total without counting new raw baseline`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.counter(sample(100_000, 1_000), time())
        repository.counter(sample(100_020, 2_000), time() + 1)
        repository.counter(sample(4, 3_000), time() + 2)
        repository.counter(sample(7, 4_000), time() + 3)
        assertEquals(23L, repository.snapshot().records.single().steps)
        assertFalse(repository.snapshot().tracker.coverage)
    }

    @Test fun `reboot baseline cannot inject its cumulative total`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.counter(sample(100, 1_000), time())
        repository.counter(sample(110, 2_000), time() + 1)
        repository.begin(first, zone, boot + 1, time() + 100)
        repository.counter(sample(6_000, 50, bootCount = boot + 1), time() + 101)
        repository.counter(sample(6_004, 60, bootCount = boot + 1), time() + 102)
        assertEquals(14L, repository.snapshot().records.single().steps)
    }

    @Test fun `live midnight seals prior full day and starts fresh visible count`() = runBlocking {
        val second = first.plusDays(1)
        val third = first.plusDays(2)
        repository.begin(first, zone, boot, time(first, 23, 58))
        repository.counter(sample(40_000, 1_000), time(first, 23, 58))
        repository.heartbeat(first, zone, time(first, 23, 59, 30))
        repository.heartbeat(second, zone, time(second, 0, 0, 1))
        var value = repository.snapshot()
        assertEquals(0L, value.records.first { it.date == second.toString() }.steps)
        assertFalse(value.records.first { it.date == first.toString() }.complete)
        assertTrue(value.tracker.coverage)
        repository.counter(sample(40_000, 2_000, second), time(second, 0, 0, 2))
        assertTrue(repository.snapshot().tracker.coverage)
        repository.counter(sample(40_125, 3_000, second), time(second, 0, 1))
        // Simulate continuous service liveness, including long periods with no walking.
        for (minute in 2 until 24 * 60) {
            repository.heartbeat(second, zone, time(second, minute / 60, minute % 60))
        }
        repository.heartbeat(second, zone, time(second, 23, 59, 30))
        repository.heartbeat(third, zone, time(third, 0, 0, 1))
        value = repository.snapshot()
        val finished = value.records.first { it.date == second.toString() }
        assertEquals(125L, finished.steps)
        assertTrue(finished.complete)
        assertEquals(DayStatus.MISSED, status(second, third, finished))
        assertEquals(0L, value.records.first { it.date == third.toString() }.steps)
    }

    @Test fun `unobserved midnight cannot certify a failure or allocate ambiguous delta`() = runBlocking {
        val next = first.plusDays(1)
        repository.begin(first, zone, boot, time())
        repository.counter(sample(1_000, 1_000), time())
        repository.counter(sample(1_200, 2_000), time() + 1)
        repository.counter(sample(2_000, 3_000, next), time(next))
        val value = repository.snapshot()
        val prior = value.records.first { it.date == first.toString() }
        assertEquals(200L, prior.steps)
        assertFalse(prior.complete)
        assertEquals(DayStatus.MISSING, status(first, next, prior))
        assertEquals(0L, value.records.first { it.date == next.toString() }.steps)
        assertFalse(value.tracker.coverage)
    }

    @Test fun `heartbeat cannot assign a positive cross midnight counter delta to the new day`() = runBlocking {
        val second = first.plusDays(1)
        repository.begin(first, zone, boot, time(first, 23, 58))
        repository.counter(sample(40_000, 1_000), time(first, 23, 58))
        repository.heartbeat(first, zone, time(first, 23, 59, 30))
        repository.heartbeat(second, zone, time(second, 0, 0, 1))
        assertTrue(repository.snapshot().tracker.coverage)
        repository.counter(sample(40_025, 2_000, second), time(second, 0, 0, 2))
        var value = repository.snapshot()
        assertEquals(second.toString(), value.tracker.date)
        assertEquals(second.toString(), value.tracker.sampleDate)
        assertFalse(value.tracker.coverage)
        assertEquals(0L, value.records.first { it.date == first.toString() }.steps)
        assertFalse(value.records.first { it.date == first.toString() }.complete)
        assertEquals(0L, value.records.first { it.date == second.toString() }.steps)
        repository.counter(sample(40_030, 3_000, second), time(second, 0, 0, 3))
        value = repository.snapshot()
        assertEquals(5L, value.records.first { it.date == second.toString() }.steps)
        assertFalse(value.tracker.coverage)
    }

    @Test fun `delayed prior day callback cannot regress active date after heartbeat`() = runBlocking {
        val second = first.plusDays(1)
        repository.begin(first, zone, boot, time(first, 23, 58))
        repository.counter(sample(40_000, 1_000), time(first, 23, 58))
        repository.heartbeat(first, zone, time(first, 23, 59, 30))
        repository.heartbeat(second, zone, time(second, 0, 0, 1))
        // A newly delivered event with a prior-day sensor timestamp must not roll the UI backward.
        repository.counter(sample(40_005, 1_500, first), time(second, 0, 0, 2))
        val value = repository.snapshot()
        assertEquals(second.toString(), value.tracker.date)
        assertEquals(40_000L, value.tracker.raw)
        assertEquals(1_000L, value.tracker.elapsedNanos)
        assertEquals(0L, value.records.first { it.date == first.toString() }.steps)
        assertEquals(0L, value.records.first { it.date == second.toString() }.steps)
        assertFalse(value.tracker.coverage)
    }

    @Test fun `date jump keeps skipped dates missing rather than fabricating misses`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.counter(sample(1_000, 1_000), time())
        repository.heartbeat(first.plusDays(3), zone, time(first.plusDays(3)))
        val value = repository.snapshot()
        assertEquals(2, value.records.size)
        assertNull(value.records.find { it.date == first.plusDays(1).toString() })
        assertFalse(value.records.first().complete)
        assertFalse(value.tracker.coverage)
    }

    @Test fun `timezone change discards ambiguous counter delta and invalidates coverage`() = runBlocking {
        val second = first.plusDays(1)
        val changedZone = "Europe/London"
        repository.begin(first, zone, boot, time(first, 23, 58))
        repository.counter(sample(40_000, 1_000), time(first, 23, 58))
        repository.heartbeat(first, zone, time(first, 23, 59, 30))
        repository.heartbeat(second, zone, time(second, 0, 0, 1))
        repository.counter(sample(40_000, 2_000, second), time(second, 0, 0, 2))
        assertTrue(repository.snapshot().tracker.coverage)
        repository.counter(CounterSample(40_100, 3_000, second.toString(), changedZone, boot), time(second, 0, 1))
        var value = repository.snapshot()
        assertEquals(changedZone, value.tracker.zoneId)
        assertEquals(changedZone, value.tracker.sampleZone)
        assertEquals(40_100L, value.tracker.raw)
        assertFalse(value.tracker.coverage)
        assertEquals(0L, value.records.first { it.date == second.toString() }.steps)
        assertFalse(value.records.first { it.date == second.toString() }.complete)
        repository.counter(CounterSample(40_105, 4_000, second.toString(), changedZone, boot), time(second, 0, 2))
        value = repository.snapshot()
        assertEquals(5L, value.records.first { it.date == second.toString() }.steps)
        assertFalse(value.tracker.coverage)
    }

    @Test fun `detector fallback rejects duplicate and out of order events`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.detector(first, zone, boot, 1_000, time())
        repository.detector(first, zone, boot, 1_000, time() + 1)
        repository.detector(first, zone, boot, 900, time() + 2)
        repository.detector(first, zone, boot, 2_000, time() + 3)
        assertEquals(2L, repository.snapshot().records.single().steps)
        assertEquals(2_000L, repository.snapshot().tracker.elapsedNanos)
    }

    @Test fun `detector callback from prior day cannot regress active day after heartbeat`() = runBlocking {
        val second = first.plusDays(1)
        repository.begin(first, zone, boot, time(first, 23, 58))
        repository.detector(first, zone, boot, 1_000, time(first, 23, 58))
        repository.heartbeat(first, zone, time(first, 23, 59, 30))
        repository.heartbeat(second, zone, time(second, 0, 0, 1))
        repository.detector(first, zone, boot, 1_500, time(second, 0, 0, 2))
        val value = repository.snapshot()
        assertEquals(second.toString(), value.tracker.date)
        assertEquals(1_000L, value.tracker.elapsedNanos)
        assertEquals(1L, value.records.first { it.date == first.toString() }.steps)
        assertEquals(0L, value.records.first { it.date == second.toString() }.steps)
        assertFalse(value.tracker.coverage)
    }

    @Test fun `settings recalculate estimates without changing challenge scoring`() = runBlocking {
        repository.begin(first, zone, boot, time())
        repository.counter(sample(10_000, 1_000), time())
        repository.counter(sample(20_000, 2_000), time() + 1)
        repository.setEnabled(true)
        repository.saveSettings(.80, 70.0, 12_000)
        val value = repository.snapshot()
        assertTrue(value.enabled)
        assertEquals(12_000, value.personalGoal)
        assertEquals(10_000L, value.records.single().steps)
        assertEquals(8.0, distance(value.records.single().steps, value.stride), 0.000001)
        assertEquals(280.0, calories(value.records.single().steps, value.stride, value.weightKg)!!, 0.000001)
        assertEquals(10_000L, Challenge.goal)
        repository.saveSettings(.70, null, 10_000)
        assertNull(repository.snapshot().weightKg)
    }

    @Test fun `invalid settings are rejected without partially committing preferences`() = runBlocking {
        repository.saveSettings(.75, 65.0, 9_000)
        val invalid = listOf(Triple(Double.NaN, 65.0, 9_000), Triple(.1, 65.0, 9_000),
            Triple(.8, 0.0, 9_000), Triple(.8, Double.POSITIVE_INFINITY, 9_000), Triple(.8, 65.0, 1))
        invalid.forEach { (stride, weight, goal) ->
            var rejected = false
            try { repository.saveSettings(stride, weight, goal) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Expected invalid preferences to be rejected", rejected)
            val value = repository.snapshot()
            assertEquals(.75, value.stride, 0.0)
            assertEquals(65.0, value.weightKg!!, 0.0)
            assertEquals(9_000, value.personalGoal)
        }
    }

    @Test fun `file database survives close and reopen with steps preferences and checkpoint`() = runBlocking {
        val name = "step365-test-${UUID.randomUUID()}.db"
        var fileDatabase = Room.databaseBuilder(context, StepDatabase::class.java, name).build()
        try {
            var fileRepository = StepRepository(fileDatabase)
            fileRepository.saveSettings(.72, 80.0, 11_000)
            fileRepository.setEnabled(true)
            fileRepository.begin(first, zone, boot, time())
            fileRepository.counter(sample(70_000, 1_000), time())
            fileRepository.counter(sample(70_250, 2_000), time() + 1)
            fileDatabase.close()
            fileDatabase = Room.databaseBuilder(context, StepDatabase::class.java, name).build()
            fileRepository = StepRepository(fileDatabase)
            val restored = fileRepository.snapshot()
            assertEquals(250L, restored.records.single().steps)
            assertEquals(.72, restored.stride, 0.0)
            assertEquals(80.0, restored.weightKg!!, 0.0)
            assertEquals(11_000, restored.personalGoal)
            assertTrue(restored.enabled)
            assertEquals(70_250L, restored.tracker.raw)
            fileRepository.counter(sample(70_275, 3_000), time() + 2)
            assertEquals(275L, fileRepository.snapshot().records.single().steps)
        } finally {
            fileDatabase.close()
            context.deleteDatabase(name)
        }
    }
}
