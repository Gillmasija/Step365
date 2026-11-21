package com.step365.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.step365.app.domain.*
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId

data class RepositorySnapshot(val records: List<DailyRecord> = emptyList(), val stride: Double = .70,
    val weightKg: Double? = null, val personalGoal: Int = 10_000, val enabled: Boolean = false,
    val tracker: TrackerEntity = TrackerEntity())

class StepRepository internal constructor(private val db: StepDatabase) {
    private val dao = db.dao()
    val updates = combine(dao.observeDays(), dao.observePreferences(), dao.observeTracker()) { days, prefs, tracker ->
        makeSnapshot(days, prefs, tracker)
    }
    private fun makeSnapshot(days: List<DayEntity>, p: PreferencesEntity?, t: TrackerEntity?) = RepositorySnapshot(
        days.map { DailyRecord(it.date, it.steps, it.complete, it.updatedAt) },
        p?.stride ?: .70, p?.weightKg, p?.personalGoal ?: 10_000, p?.enabled ?: false, t ?: TrackerEntity())

    suspend fun snapshot(): RepositorySnapshot = db.withTransaction {
        makeSnapshot(dao.days(), dao.preferences(), dao.tracker())
    }

    suspend fun saveSettings(stride: Double, weight: Double?, goal: Int) = db.withTransaction {
        require(stride.isFinite() && stride in .2..2.0)
        require(weight == null || (weight.isFinite() && weight in 20.0..350.0))
        require(goal in 1_000..100_000)
        dao.putPreferences((dao.preferences() ?: PreferencesEntity()).copy(stride = stride, weightKg = weight, personalGoal = goal))
    }
    suspend fun setEnabled(enabled: Boolean) = db.withTransaction {
        dao.putPreferences((dao.preferences() ?: PreferencesEntity()).copy(enabled = enabled))
    }

    suspend fun begin(date: LocalDate, zone: String, boot: Int, now: Long) = db.withTransaction {
        val old = dao.tracker()
        // Recovery after process death cannot certify an uninterrupted day.
        if (old != null && old.date.isNotEmpty()) markPartial(old.date, now)
        markPartial(date.toString(), now)
        dao.putTracker(TrackerEntity(date = date.toString(), zoneId = zone, bootCount = boot,
            heartbeat = now, active = true, status = "Waiting for your first step"))
    }

    suspend fun stop(message: String, now: Long) = db.withTransaction {
        val tracker = dao.tracker() ?: TrackerEntity()
        if (tracker.date.isNotEmpty()) markPartial(tracker.date, now)
        dao.putTracker(tracker.copy(raw = null, active = false, coverage = false, status = message, heartbeat = now))
    }

    private suspend fun markPartial(date: String, now: Long) {
        val day = dao.day(date) ?: DayEntity(date)
        dao.putDay(day.copy(complete = false, updatedAt = now))
    }

    /** Full-day reliability is sealed only on a live midnight rollover. */
    private suspend fun rollover(t: TrackerEntity, date: String, zone: String, now: Long): TrackerEntity {
        if (t.date == date && t.zoneId == zone) return t
        val normal = t.active && t.zoneId == zone && t.date.isNotEmpty() &&
            LocalDate.parse(t.date).plusDays(1).toString() == date && now - t.heartbeat in 0..90_000
        if (t.date.isNotEmpty()) {
            val old = dao.day(t.date) ?: DayEntity(t.date)
            dao.putDay(old.copy(complete = normal && t.coverage, updatedAt = now))
        }
        // New day is provisional until the following midnight seals its coverage.
        markPartial(date, now)
        return t.copy(date = date, zoneId = zone, coverage = normal,
            raw = if (normal) t.raw else null, status = if (normal) t.status else "Clock change: partial recording")
    }

    suspend fun heartbeat(date: LocalDate, zone: String, now: Long) = db.withTransaction {
        val t = dao.tracker() ?: return@withTransaction
        if (!t.active) return@withTransaction
        val next = rollover(t, date.toString(), zone, now)
        dao.putTracker(next.copy(heartbeat = now))
    }

    suspend fun counter(sample: CounterSample, now: Long) = db.withTransaction {
        val initial = dao.tracker() ?: return@withTransaction
        if (!initial.active) return@withTransaction
        if (initial.bootCount == sample.bootCount && sample.elapsedNanos <= initial.elapsedNanos) return@withTransaction
        if (sample.date < initial.date) {
            markPartial(sample.date, now)
            dao.putTracker(initial.copy(coverage = false))
            return@withTransaction
        }
        // A sensor reading spanning midnight is ambiguous. Discard its delta rather than backfill.
        val t = rollover(initial, sample.date, sample.zoneId, now)
        val previous = initial.raw?.let { CounterCheckpoint(it, initial.elapsedNanos, initial.sampleDate, initial.sampleZone, initial.bootCount) }
        val result = CounterReducer.reduce(previous, sample)
        if (result.reason == CounterReason.OUT_OF_ORDER || result.reason == CounterReason.DUPLICATE) return@withTransaction
        val c = result.checkpoint ?: return@withTransaction
        val day = dao.day(sample.date) ?: DayEntity(sample.date)
        dao.putDay(day.copy(steps = day.steps + result.delta, updatedAt = now))
        val cleanBoundary = result.reason == CounterReason.DATE_OR_ZONE_CHANGE &&
            initial.sampleZone == sample.zoneId && initial.raw == sample.raw
        if (result.reason == CounterReason.DATE_OR_ZONE_CHANGE && !cleanBoundary && initial.sampleDate.isNotEmpty()) markPartial(initial.sampleDate, now)
        dao.putTracker(t.copy(raw = c.raw, elapsedNanos = c.elapsedNanos, bootCount = c.bootCount,
            sampleDate = c.date, sampleZone = c.zoneId, heartbeat = now,
            coverage = t.coverage && (!result.gap || cleanBoundary), status = "Step counter connected"))
    }

    suspend fun detector(date: LocalDate, zone: String, boot: Int, timestamp: Long, now: Long) = db.withTransaction {
        val initial = dao.tracker() ?: return@withTransaction
        if (!initial.active || (initial.bootCount == boot && timestamp <= initial.elapsedNanos)) return@withTransaction
        if (date.toString() < initial.date) {
            markPartial(date.toString(), now)
            dao.putTracker(initial.copy(coverage = false))
            return@withTransaction
        }
        val t = rollover(initial, date.toString(), zone, now)
        val day = dao.day(date.toString()) ?: DayEntity(date.toString())
        dao.putDay(day.copy(steps = day.steps + 1, updatedAt = now))
        dao.putTracker(t.copy(elapsedNanos = timestamp, bootCount = boot, heartbeat = now,
            status = "Step detector connected (fallback)"))
    }

    companion object {
        @Volatile private var instance: StepRepository? = null
        fun get(context: Context): StepRepository = instance ?: synchronized(this) {
            instance ?: StepRepository(Room.databaseBuilder(context.applicationContext, StepDatabase::class.java, "step365.db")
                // Version 1 has no prior schema. Future versions must add explicit migrations.
                .build()).also { instance = it }
        }
    }
}
