package com.step365.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Raw daily observations. [complete] means reliable coverage, not goal achievement. */
data class DailyRecord(
    val date: String,
    val steps: Long = 0,
    val complete: Boolean = false,
    val updatedAt: Long = 0,
)

object Challenge {
    val start: LocalDate = LocalDate.of(2026, 11, 22)
    val end: LocalDate = LocalDate.of(2027, 11, 21)
    const val goal: Long = 10_000L
    const val duration: Int = 365

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** Zero before the challenge, one on its first day, capped after its last day. */
    fun dayNumber(date: LocalDate): Int =
        (ChronoUnit.DAYS.between(start, date) + 1).coerceIn(0L, duration.toLong()).toInt()
}

/** Estimated distance; inputs must describe nonnegative steps and a positive stride. */
fun distance(steps: Long, stride: Double): Double {
    require(steps >= 0) { "Steps must be nonnegative" }
    require(stride.isFinite() && stride > 0.0) { "Stride must be positive and finite" }
    return steps.toDouble() * stride / 1000.0
}

/**
 * Net walking activity energy approximation: 0.5 kcal × kg × km.
 * This is a level-walking estimate excluding resting energy, not a medical measurement.
 * No assumed body weight is substituted when the optional weight has not been supplied.
 */
fun calories(steps: Long, stride: Double, weightKg: Double?): Double? {
    val km = distance(steps, stride)
    if (weightKg == null) return null
    require(weightKg.isFinite() && weightKg > 0.0) { "Weight must be positive and finite" }
    return 0.5 * weightKg * km
}

enum class DayStatus { COMPLETE, MISSED, TODAY, MISSING, FUTURE, OUTSIDE }

fun status(date: LocalDate, today: LocalDate, record: DailyRecord?): DayStatus = when {
    !Challenge.contains(date) -> DayStatus.OUTSIDE
    date.isAfter(today) -> DayStatus.FUTURE
    date == today -> DayStatus.TODAY
    record != null && record.date == date.toString() && record.steps >= Challenge.goal -> DayStatus.COMPLETE
    record != null && record.date == date.toString() && record.complete -> DayStatus.MISSED
    else -> DayStatus.MISSING
}

data class ChallengeStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val completed: Int,
    val missed: Int,
    val missing: Int,
    val remaining: Int,
    val totalSteps: Long,
    val averageSteps: Double,
    val completionRate: Double,
    val overallProgress: Double,
)

/**
 * The completion-rate denominator is all finished challenge days, including missing days.
 * Today's success is counted in completed/streak totals, but not in that finished-day rate.
 * Average steps uses every elapsed challenge day, including today, to avoid hiding data gaps.
 * Remaining includes today while it remains available to walk; time progress counts finished days.
 */
fun statistics(records: List<DailyRecord>, today: LocalDate): ChallengeStats {
    val byDate = records.mapNotNull { record ->
        val date = runCatching { LocalDate.parse(record.date) }.getOrNull()
        if (date != null && Challenge.contains(date) && !date.isAfter(today)) date to record else null
    }.groupBy({ it.first }, { it.second })
        .mapValues { (_, duplicates) -> duplicates.maxWith(compareBy<DailyRecord> { it.updatedAt }.thenBy { it.steps }) }

    val finished = ChronoUnit.DAYS.between(Challenge.start, today).coerceIn(0L, 365L).toInt()
    val elapsed = if (today.isBefore(Challenge.start)) 0 else Challenge.dayNumber(today)
    val lastElapsed = when {
        today.isAfter(Challenge.end) -> Challenge.end
        else -> today
    }
    var longest = 0
    var run = 0
    var completed = 0
    var missed = 0
    var missing = 0
    var completedFinished = 0
    var total = 0L
    var date = Challenge.start
    while (!date.isAfter(lastElapsed)) {
        val record = byDate[date]
        val steps = (record?.steps ?: 0L).coerceAtLeast(0L)
        total = if (Long.MAX_VALUE - total < steps) Long.MAX_VALUE else total + steps
        if (steps >= Challenge.goal) {
            completed++
            run++
            longest = maxOf(longest, run)
            if (date.isBefore(today)) completedFinished++
        } else {
            run = 0
            if (date.isBefore(today)) {
                if (record?.complete == true) missed++ else missing++
            }
        }
        date = date.plusDays(1)
    }

    // An unfinished current day does not erase yesterday's streak before midnight.
    var streakDate = when {
        today.isAfter(Challenge.end) -> Challenge.end
        byDate[today]?.steps?.let { it >= Challenge.goal } == true -> today
        else -> today.minusDays(1)
    }
    var current = 0
    while (Challenge.contains(streakDate) && byDate[streakDate]?.steps?.let { it >= Challenge.goal } == true) {
        current++
        streakDate = streakDate.minusDays(1)
    }
    return ChallengeStats(
        currentStreak = current,
        longestStreak = longest,
        completed = completed,
        missed = missed,
        missing = missing,
        remaining = Challenge.duration - finished,
        totalSteps = total,
        averageSteps = if (elapsed == 0) 0.0 else total.toDouble() / elapsed,
        completionRate = if (finished == 0) 0.0 else completedFinished.toDouble() / finished,
        overallProgress = finished.toDouble() / Challenge.duration,
    )
}
