package dev.xverlxrd.batterycapacity

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.xverlxrd.batterycapacity.service.CheckpointWorker
import dev.xverlxrd.batterycapacity.service.MeasurementForegroundService
import javax.inject.Inject

@HiltAndroidApp
class BatteryEstimatorApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        MeasurementForegroundService.ensureChannel(this)
        createServiceStatusChannel()
    }

    private fun createServiceStatusChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "service_status",
                "Статус сервиса",
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
    }
}
