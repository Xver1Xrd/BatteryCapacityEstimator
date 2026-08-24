package dev.xverlxrd.batterycapacity.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сессия калибровки — переживает перезагрузку и выгрузку процесса. */
@Entity(tableName = "measurement_sessions")
data class MeasurementSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    /** SessionState.name */
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "soc_at_start") val socAtStart: Float?,
    @ColumnInfo(name = "lowest_soc") val lowestSoc: Float?,
    @ColumnInfo(name = "last_soc") val lastSoc: Float?,
    @ColumnInfo(name = "collected_delta_uah") val collectedDeltaUah: Long,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    @ColumnInfo(name = "pause_reason") val pauseReason: String?,
)

@Entity(tableName = "battery_samples", indices = [Index("session_id"), Index("timestamp_ms")])
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    @ColumnInfo(name = "soc_percent") val socPercent: Float,
    @ColumnInfo(name = "voltage_mv") val voltageMv: Int?,
    @ColumnInfo(name = "current_ma") val currentMa: Float?,
    @ColumnInfo(name = "charge_counter_uah") val chargeCounterUah: Long?,
    /** Десятые доли °C как отдаёт ОС. */
    @ColumnInfo(name = "temp_tenths_c") val tempTenthsC: Int?,
)

@Entity(tableName = "completed_measurements")
data class CompletedMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "measured_at_ms") val measuredAtMs: Long,
    @ColumnInfo(name = "actual_mah") val actualMah: Double,
    @ColumnInfo(name = "design_mah") val designMah: Double?,
    /** MeasurementMethod.name */
    @ColumnInfo(name = "method") val method: String,
    @ColumnInfo(name = "confidence_mah") val confidenceMah: Double,
    @ColumnInfo(name = "soc_span_pct") val socSpanPct: Float,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    @ColumnInfo(name = "warnings") val warnings: String?,
)
