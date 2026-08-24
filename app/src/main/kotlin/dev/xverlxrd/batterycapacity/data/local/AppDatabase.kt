package dev.xverlxrd.batterycapacity.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.xverlxrd.batterycapacity.data.local.dao.CompletedMeasurementDao
import dev.xverlxrd.batterycapacity.data.local.dao.SampleDao
import dev.xverlxrd.batterycapacity.data.local.dao.SessionDao
import dev.xverlxrd.batterycapacity.data.local.entity.BatterySampleEntity
import dev.xverlxrd.batterycapacity.data.local.entity.CompletedMeasurementEntity
import dev.xverlxrd.batterycapacity.data.local.entity.MeasurementSessionEntity

@Database(
    entities = [
        MeasurementSessionEntity::class,
        BatterySampleEntity::class,
        CompletedMeasurementEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun sampleDao(): SampleDao
    abstract fun completedMeasurementDao(): CompletedMeasurementDao

    companion object {
        const val NAME = "battery_estimator.db"
    }
}
