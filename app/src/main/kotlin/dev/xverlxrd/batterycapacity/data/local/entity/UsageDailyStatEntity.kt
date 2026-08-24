package dev.xverlxrd.batterycapacity.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Агрегированная статистика использования батареи за один день.
 * Наполняется фоновым воркером (UsageStatsWorker) каждые ~6 часов:
 * каждая выборка вливается в строку текущего дня (min/max/бегущее среднее).
 */
@Entity(tableName = "usage_daily_stats")
data class UsageDailyStatEntity(
    /** LocalDate.toEpochDay() — стабильный ключ дня по локальному времени. */
    @PrimaryKey @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    /** Сумма SOC всех выборок дня — для точного среднего при слиянии. */
    @ColumnInfo(name = "soc_sum") val socSum: Double,
    @ColumnInfo(name = "soc_min") val socMin: Float?,
    @ColumnInfo(name = "soc_max") val socMax: Float?,
    @ColumnInfo(name = "temp_max_c") val tempMaxC: Double?,
    @ColumnInfo(name = "cycle_count_last") val cycleCountLast: Int?,
    @ColumnInfo(name = "voltage_min_mv") val voltageMinMv: Int?,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)
