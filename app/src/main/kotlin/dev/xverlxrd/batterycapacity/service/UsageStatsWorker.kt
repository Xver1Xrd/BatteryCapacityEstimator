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
import dev.xverlxrd.batterycapacity.data.repository.UsageStatsRepository
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import java.util.concurrent.TimeUnit

/**
 * Фоновая телеметрия: раз в ~6 часов снимает показания батареи и вливает их
 * в дневную статистику (SOC min/max/avg, температура, циклы, напряжение).
 * Работает независимо от измерений — так копятся данные для графиков износа.
 */
@HiltWorker
class UsageStatsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val battery: BatteryInfoRepository,
    private val stats: UsageStatsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        stats.mergeSample(battery.readNow())
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "usage_stats_sample"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UsageStatsWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
