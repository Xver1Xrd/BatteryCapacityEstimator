package dev.xverlxrd.batterycapacity.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.xverlxrd.batterycapacity.data.datasource.sysfs.RealSysfsReader
import dev.xverlxrd.batterycapacity.data.datasource.sysfs.SysfsReader
import dev.xverlxrd.batterycapacity.data.datastore.UserSettingsDataStore
import dev.xverlxrd.batterycapacity.data.repository.BatteryInfoRepositoryImpl
import dev.xverlxrd.batterycapacity.data.repository.CompletedMeasurementsRepositoryImpl
import dev.xverlxrd.batterycapacity.data.repository.MeasurementSessionRepositoryImpl
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.CompletedMeasurementsRepository
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    @Singleton
    fun batteryInfoRepository(impl: BatteryInfoRepositoryImpl): BatteryInfoRepository

    @Binds
    @Singleton
    fun measurementSessionRepository(impl: MeasurementSessionRepositoryImpl): MeasurementSessionRepository

    @Binds
    @Singleton
    fun completedMeasurementsRepository(impl: CompletedMeasurementsRepositoryImpl): CompletedMeasurementsRepository

    @Binds
    @Singleton
    fun userSettingsRepository(impl: UserSettingsDataStore): UserSettingsRepository

    @Binds
    @Singleton
    fun sysfsReader(impl: RealSysfsReader): SysfsReader
}
