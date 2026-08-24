package dev.xverlxrd.batterycapacity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.xverlxrd.batterycapacity.data.local.entity.BatterySampleEntity
import dev.xverlxrd.batterycapacity.data.local.entity.CompletedMeasurementEntity
import dev.xverlxrd.batterycapacity.data.local.entity.MeasurementSessionEntity
import dev.xverlxrd.batterycapacity.data.local.entity.UsageDailyStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: MeasurementSessionEntity): Long

    @Query("UPDATE measurement_sessions SET state = :state, pause_reason = :pauseReason, updated_at_ms = :nowMs WHERE id = :id")
    suspend fun updateState(id: Long, state: String, pauseReason: String?, nowMs: Long)

    @Query("UPDATE measurement_sessions SET last_soc = :soc, lowest_soc = MIN(COALESCE(lowest_soc, :soc), :soc), collected_delta_uah = :deltaUah, sample_count = :sampleCount, updated_at_ms = :nowMs WHERE id = :id")
    suspend fun touchProgress(
        id: Long,
        soc: Float?,
        deltaUah: Long,
        sampleCount: Int,
        nowMs: Long,
    )

    /** Активные сессии: MEASURING / WAITING_DISCHARGE / PAUSED. */
    @Query(
        "SELECT * FROM measurement_sessions WHERE state IN ('MEASURING', 'WAITING_DISCHARGE', 'PAUSED') ORDER BY started_at_ms DESC LIMIT 1",
    )
    fun observeActive(): Flow<MeasurementSessionEntity?>

    @Query(
        "SELECT * FROM measurement_sessions WHERE state IN ('MEASURING', 'WAITING_DISCHARGE', 'PAUSED') ORDER BY started_at_ms DESC LIMIT 1",
    )
    suspend fun findActive(): MeasurementSessionEntity?

    @Query("SELECT * FROM measurement_sessions WHERE id = :id")
    suspend fun byId(id: Long): MeasurementSessionEntity?

    @Query("DELETE FROM measurement_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sample: BatterySampleEntity): Long

    @Query("SELECT * FROM battery_samples WHERE session_id = :sessionId ORDER BY timestamp_ms ASC")
    fun observeBySession(sessionId: Long): Flow<List<BatterySampleEntity>>

    @Query("SELECT * FROM battery_samples WHERE session_id = :sessionId ORDER BY timestamp_ms ASC")
    suspend fun loadBySession(sessionId: Long): List<BatterySampleEntity>

    @Query("SELECT COUNT(*) FROM battery_samples WHERE session_id = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    @Query("DELETE FROM battery_samples WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}

@Dao
interface CompletedMeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CompletedMeasurementEntity): Long

    @Query("SELECT * FROM completed_measurements ORDER BY measured_at_ms DESC")
    fun observeAll(): Flow<List<CompletedMeasurementEntity>>

    @Query("DELETE FROM completed_measurements WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM completed_measurements WHERE measured_at_ms = :measuredAtMs")
    suspend fun deleteByMeasuredAt(measuredAtMs: Long)

    @Query("DELETE FROM completed_measurements")
    suspend fun clear()
}

@Dao
interface UsageStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: UsageDailyStatEntity)

    @Query("SELECT * FROM usage_daily_stats WHERE date_epoch_day >= :sinceEpochDay ORDER BY date_epoch_day ASC")
    fun observeSince(sinceEpochDay: Long): Flow<List<UsageDailyStatEntity>>

    @Query("DELETE FROM usage_daily_stats")
    suspend fun clear()
}
