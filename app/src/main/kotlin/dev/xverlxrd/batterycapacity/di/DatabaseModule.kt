package dev.xverlxrd.batterycapacity.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.xverlxrd.batterycapacity.data.local.AppDatabase
import dev.xverlxrd.batterycapacity.data.local.dao.CompletedMeasurementDao
import dev.xverlxrd.batterycapacity.data.local.dao.SampleDao
import dev.xverlxrd.batterycapacity.data.local.dao.SessionDao
import dev.xverlxrd.batterycapacity.data.local.dao.UsageStatsDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideSampleDao(db: AppDatabase): SampleDao = db.sampleDao()

    @Provides
    fun provideCompletedMeasurementDao(db: AppDatabase): CompletedMeasurementDao =
        db.completedMeasurementDao()

    @Provides
    fun provideUsageStatsDao(db: AppDatabase): UsageStatsDao = db.usageStatsDao()
}
