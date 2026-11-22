package com.step365.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Steps are the source of truth. Estimates are derived with the current user preferences.
@Entity(tableName = "days")
data class DayEntity(@PrimaryKey val date: String, val steps: Long = 0,
    val complete: Boolean = false, val updatedAt: Long = 0)

@Entity(tableName = "preferences")
data class PreferencesEntity(@PrimaryKey val id: Int = 1, val stride: Double = .70,
    val weightKg: Double? = null, val personalGoal: Int = 10_000, val enabled: Boolean = false)

@Entity(tableName = "tracker")
data class TrackerEntity(@PrimaryKey val id: Int = 1, val raw: Long? = null,
    val elapsedNanos: Long = 0, val date: String = "", val zoneId: String = "",
    val sampleDate: String = "", val sampleZone: String = "",
    val bootCount: Int = -1, val heartbeat: Long = 0, val active: Boolean = false,
    val coverage: Boolean = false, val status: String = "Ready to start", val session: String = "")

@Dao
interface StepDao {
    @Query("SELECT * FROM days ORDER BY date") fun observeDays(): Flow<List<DayEntity>>
    @Query("SELECT * FROM days ORDER BY date") suspend fun days(): List<DayEntity>
    @Query("SELECT * FROM days WHERE date = :date") suspend fun day(date: String): DayEntity?
    @Upsert suspend fun putDay(day: DayEntity)
    @Query("SELECT * FROM preferences WHERE id = 1") suspend fun preferences(): PreferencesEntity?
    @Query("SELECT * FROM preferences WHERE id = 1") fun observePreferences(): Flow<PreferencesEntity?>
    @Upsert suspend fun putPreferences(value: PreferencesEntity)
    @Query("SELECT * FROM tracker WHERE id = 1") suspend fun tracker(): TrackerEntity?
    @Query("SELECT * FROM tracker WHERE id = 1") fun observeTracker(): Flow<TrackerEntity?>
    @Upsert suspend fun putTracker(value: TrackerEntity)
}

@Database(entities = [DayEntity::class, PreferencesEntity::class, TrackerEntity::class], version = 1, exportSchema = true)
abstract class StepDatabase : RoomDatabase() { abstract fun dao(): StepDao }
