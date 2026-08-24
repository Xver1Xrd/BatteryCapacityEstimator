package dev.xverlxrd.batterycapacity.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * После перезагрузки: помечаем активную сессию паузой (данные не теряем)
 * и, если тест шёл, поднимаем сервис заново — сессия продолжится.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var sessions: MeasurementSessionRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (sessions.findResumable() != null) {
                    sessions.pauseAllActive("Перезагрузка устройства")
                    MeasurementForegroundService.start(context)
                    CheckpointWorker.schedule(context)
                }
                UsageStatsWorker.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
