package com.step365.app.domain

/** Persist this checkpoint atomically with the daily step increment. */
data class CounterCheckpoint(
    val raw: Long,
    val elapsedNanos: Long,
    val date: String,
    val zoneId: String,
    val bootCount: Int,
)

data class CounterSample(
    val raw: Long,
    val elapsedNanos: Long,
    val date: String,
    val zoneId: String,
    val bootCount: Int,
)

enum class CounterReason { BASELINE, ADVANCED, DUPLICATE, OUT_OF_ORDER, REBOOT, RESET, DATE_OR_ZONE_CHANGE, INVALID }

data class CounterUpdate(
    val checkpoint: CounterCheckpoint?,
    val delta: Long,
    /** The observation interval cannot establish reliable full-day coverage. */
    val gap: Boolean,
    val reason: CounterReason,
)

object CounterReducer {
    /**
     * A cumulative sensor count is never treated as today's total. Boot identity and the sensor's
     * monotonic timestamp prevent old samples, persisted checkpoints and resets from double counting.
     * A delta across local dates or time zones cannot safely be assigned, so it is discarded and the
     * caller must mark the affected interval as incomplete. No steps are invented or backdated.
     */
    fun reduce(previous: CounterCheckpoint?, sample: CounterSample): CounterUpdate {
        if (sample.raw < 0 || sample.elapsedNanos < 0 ||
            runCatching { java.time.LocalDate.parse(sample.date) }.isFailure ||
            runCatching { java.time.ZoneId.of(sample.zoneId) }.isFailure
        ) return CounterUpdate(previous, 0, true, CounterReason.INVALID)

        val next = CounterCheckpoint(sample.raw, sample.elapsedNanos, sample.date, sample.zoneId, sample.bootCount)
        if (previous == null) return CounterUpdate(next, 0, true, CounterReason.BASELINE)
        if (previous.bootCount != sample.bootCount) return CounterUpdate(next, 0, true, CounterReason.REBOOT)
        if (sample.elapsedNanos < previous.elapsedNanos) {
            return CounterUpdate(previous, 0, false, CounterReason.OUT_OF_ORDER)
        }
        if (sample.elapsedNanos == previous.elapsedNanos) {
            return CounterUpdate(previous, 0, false, CounterReason.DUPLICATE)
        }
        if (sample.date != previous.date || sample.zoneId != previous.zoneId) {
            return CounterUpdate(next, 0, true, CounterReason.DATE_OR_ZONE_CHANGE)
        }
        if (sample.raw < previous.raw) return CounterUpdate(next, 0, true, CounterReason.RESET)
        return CounterUpdate(next, sample.raw - previous.raw, false, CounterReason.ADVANCED)
    }
}
