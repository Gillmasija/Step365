package com.step365.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Test

class ChallengeTest {
    private val first = Challenge.start
    private fun record(offset: Long, steps: Long = 10_000, complete: Boolean = true) =
        DailyRecord(first.plusDays(offset).toString(), steps, complete)

    @Test fun `challenge has exactly 365 dates and original target`() {
        assertEquals(365L, ChronoUnit.DAYS.between(Challenge.start, Challenge.end) + 1)
        assertEquals(3_650_000L, Challenge.goal * Challenge.duration)
        assertEquals(LocalDate.of(2026, 11, 22), Challenge.start)
        assertEquals(LocalDate.of(2027, 11, 21), Challenge.end)
    }

    @Test fun `day number clamps outside fixed challenge`() {
        assertEquals(0, Challenge.dayNumber(first.minusDays(1)))
        assertEquals(1, Challenge.dayNumber(first))
        assertEquals(365, Challenge.dayNumber(Challenge.end))
        assertEquals(365, Challenge.dayNumber(Challenge.end.plusYears(1)))
        assertFalse(Challenge.contains(first.minusDays(1)))
        assertTrue(Challenge.contains(Challenge.end))
        assertFalse(Challenge.contains(Challenge.end.plusDays(1)))
    }

    @Test fun `distance and net calories respond to personal settings`() {
        assertEquals(7.0, distance(10_000, 0.70), 0.000001)
        assertEquals(8.0, distance(10_000, 0.80), 0.000001)
        assertEquals(245.0, calories(10_000, 0.70, 70.0)!!, 0.000001)
        assertEquals(280.0, calories(10_000, 0.80, 70.0)!!, 0.000001)
        assertEquals(0.0, calories(0, 0.70, 70.0)!!, 0.000001)
        assertNull(calories(10_000, 0.70, null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative steps rejected`() { distance(-1, 0.70) }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid stride rejected`() { distance(1, Double.NaN) }

    @Test(expected = IllegalArgumentException::class)
    fun `zero weight rejected`() { calories(1, 0.70, 0.0) }

    @Test fun `today stays purple whether completed or below goal`() {
        assertEquals(DayStatus.TODAY, status(first, first, record(0, 2)))
        assertEquals(DayStatus.TODAY, status(first, first, record(0)))
    }

    @Test fun `failure requires finished day and reliable coverage`() {
        val tomorrow = first.plusDays(1)
        assertEquals(DayStatus.MISSED, status(first, tomorrow, record(0, 9_999)))
        assertEquals(DayStatus.MISSING, status(first, tomorrow, record(0, 9_999, false)))
        assertEquals(DayStatus.MISSING, status(first, tomorrow, null))
        assertEquals(DayStatus.COMPLETE, status(first, tomorrow, record(0, 10_000, false)))
    }

    @Test fun `future and outside are never completed by malformed records`() {
        assertEquals(DayStatus.FUTURE, status(first.plusDays(1), first, record(1)))
        assertEquals(DayStatus.OUTSIDE, status(first.minusDays(1), first, null))
        assertEquals(DayStatus.OUTSIDE, status(Challenge.end.plusDays(1), Challenge.end.plusDays(2), null))
        assertEquals(DayStatus.MISSING, status(first, first.plusDays(1), record(1)))
    }

    @Test fun `empty before challenge is pleasant and has no missed days`() {
        val stats = statistics(emptyList(), first.minusDays(1))
        assertEquals(365, stats.remaining)
        assertEquals(0, stats.completed)
        assertEquals(0, stats.missed)
        assertEquals(0, stats.missing)
        assertEquals(0.0, stats.overallProgress, 0.0)
        assertEquals(0.0, stats.completionRate, 0.0)
        assertEquals(0.0, stats.averageSteps, 0.0)
    }

    @Test fun `first current day cannot be missed and remains available`() {
        val stats = statistics(listOf(record(0, 500)), first)
        assertEquals(365, stats.remaining)
        assertEquals(0, stats.missed)
        assertEquals(0, stats.missing)
        assertEquals(500.0, stats.averageSteps, 0.0)
        assertEquals(0.0, stats.overallProgress, 0.0)
    }

    @Test fun `missing days reduce completion rate without becoming missed`() {
        val stats = statistics(listOf(record(0), record(1, 5_000), record(2, 3_000, false)), first.plusDays(4))
        assertEquals(1, stats.completed)
        assertEquals(1, stats.missed)
        assertEquals(2, stats.missing)
        assertEquals(361, stats.remaining)
        assertEquals(0.25, stats.completionRate, 0.000001)
        assertEquals(4.0 / 365, stats.overallProgress, 0.000001)
        assertEquals(18_000L, stats.totalSteps)
        assertEquals(3_600.0, stats.averageSteps, 0.000001)
    }

    @Test fun `unfinished today preserves yesterday streak`() {
        val stats = statistics(listOf(record(0), record(1), record(2, 200)), first.plusDays(2))
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
        assertEquals(1.0, stats.completionRate, 0.0)
    }

    @Test fun `completed today extends current and longest streak`() {
        val stats = statistics(listOf(record(0), record(1), record(2)), first.plusDays(2))
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
        assertEquals(3, stats.completed)
        assertEquals(1.0, stats.completionRate, 0.0)
    }

    @Test fun `missed yesterday resets current streak without erasing earlier completion`() {
        val stats = statistics(listOf(record(0), record(1), record(2, 500)), first.plusDays(3))
        assertEquals(0, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
        assertEquals(2, stats.completed)
        assertEquals(1, stats.missed)
    }

    @Test fun `missing yesterday also breaks verifiable streak`() {
        val stats = statistics(listOf(record(0), record(2)), first.plusDays(2))
        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
        assertEquals(1, stats.missing)
    }

    @Test fun `challenge never restarts after final date`() {
        val records = (0L until 365L).map { record(it) }
        val stats = statistics(records, Challenge.end.plusYears(2))
        assertEquals(365, stats.completed)
        assertEquals(365, stats.currentStreak)
        assertEquals(365, stats.longestStreak)
        assertEquals(0, stats.remaining)
        assertEquals(0, stats.missing)
        assertEquals(1.0, stats.completionRate, 0.0)
        assertEquals(1.0, stats.overallProgress, 0.0)
        assertEquals(3_650_000L, stats.totalSteps)
    }

    @Test fun `end day not elapsed until it finishes`() {
        val stats = statistics(listOf(DailyRecord(Challenge.end.toString(), 10_000)), Challenge.end)
        assertEquals(1, stats.remaining)
        assertEquals(1, stats.completed)
        assertEquals(364, stats.missing)
        assertEquals(0.0, stats.completionRate, 0.0)
        assertEquals(364.0 / 365, stats.overallProgress, 0.000001)
    }

    @Test fun `outside future invalid and duplicate rows do not inflate totals`() {
        val records = listOf(record(0, 100), record(0, 200).copy(updatedAt = 5), record(1),
            DailyRecord(first.minusDays(1).toString(), 100_000), DailyRecord("invalid", 100_000))
        val stats = statistics(records, first)
        assertEquals(200L, stats.totalSteps)
        assertEquals(0, stats.completed)
    }
}
