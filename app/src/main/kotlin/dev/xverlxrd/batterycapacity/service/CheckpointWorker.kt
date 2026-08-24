package dev.xverlxrd.batterycapacity.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import java.util.concurrent.TimeUnit

/**
 * Контрольная точка в Doze: раз в ~15 минут проверяем, что при активной
 * сессии сервис жив. Если система убила FGS — перезапускаем.
 * Минимальный период WorkManager — 15 минут, чаще нельзя.
 */
@HiltWorker
class CheckpointWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sessions: MeasurementSessionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val session = sessions.findResumable() ?: return Result.success()
        MeasurementForegroundService.start(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "battery_checkpoint"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CheckpointWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
