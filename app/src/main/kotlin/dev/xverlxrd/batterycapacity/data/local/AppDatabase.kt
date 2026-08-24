package dev.xverlxrd.batterycapacity.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.xverlxrd.batterycapacity.data.local.dao.CompletedMeasurementDao
import dev.xverlxrd.batterycapacity.data.local.dao.SampleDao
import dev.xverlxrd.batterycapacity.data.local.dao.SessionDao
import dev.xverlxrd.batterycapacity.data.local.dao.UsageStatsDao
import dev.xverlxrd.batterycapacity.data.local.entity.BatterySampleEntity
import dev.xverlxrd.batterycapacity.data.local.entity.CompletedMeasurementEntity
import dev.xverlxrd.batterycapacity.data.local.entity.MeasurementSessionEntity
import dev.xverlxrd.batterycapacity.data.local.entity.UsageDailyStatEntity

@Database(
    entities = [
        MeasurementSessionEntity::class,
        BatterySampleEntity::class,
        CompletedMeasurementEntity::class,
        UsageDailyStatEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun sampleDao(): SampleDao
    abstract fun completedMeasurementDao(): CompletedMeasurementDao
    abstract fun usageStatsDao(): UsageStatsDao

    companion object {
        const val NAME = "battery_estimator.db"

        /** v2: таблица дневной статистики использования. Данные не трогаем. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS usage_daily_stats (
                        date_epoch_day INTEGER NOT NULL PRIMARY KEY,
                        sample_count INTEGER NOT NULL,
                        soc_sum REAL NOT NULL,
                        soc_min REAL,
                        soc_max REAL,
                        temp_max_c REAL,
                        cycle_count_last INTEGER,
                        voltage_min_mv INTEGER,
                        updated_at_ms INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
