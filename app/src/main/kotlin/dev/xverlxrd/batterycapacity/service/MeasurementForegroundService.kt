package dev.xverlxrd.batterycapacity.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.xverlxrd.batterycapacity.MainActivity
import dev.xverlxrd.batterycapacity.R
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.SessionState
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import dev.xverlxrd.batterycapacity.domain.usecase.FinalizeCalibrationUseCase
import dev.xverlxrd.batterycapacity.domain.usecase.ResolveDesignCapacityUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service калибровочного теста.
 *
 * Политика энергопотребления: опрос строго раз в pollingIntervalSeconds
 * (≥30 c), между опросами процесс спит. Сервис останавливается сразу после
 * завершения теста или его отмены. Doze переживаем связкой FGS(dataSync)
 * + WorkManager-контрольные точки (CheckPointWorker перезапустит сервис,
 * если система его убила).
 */
@AndroidEntryPoint
class MeasurementForegroundService : LifecycleService() {

    @Inject lateinit var sessions: MeasurementSessionRepository
    @Inject lateinit var batteryRepository: BatteryInfoRepository
    @Inject lateinit var settings: UserSettingsRepository
    @Inject lateinit var finalizeCalibration: FinalizeCalibrationUseCase
    @Inject lateinit var resolveDesignCapacity: ResolveDesignCapacityUseCase

    private var measureJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopMeasuring()
                return START_NOT_STICKY
            }
            else -> startMeasuring()
        }
        return START_STICKY
    }

    private fun startMeasuring() {
        if (measureJob?.isActive == true) return
        startForeground(NOTIFICATION_ID, buildNotification(null))
        measureJob = lifecycleScope.launch {
            val intervalMs = settings.preferences.first().pollingIntervalSeconds * 1000L
            loop@ while (coroutineContext.isActive) {
                val session = sessions.observeActiveSession().first()
                if (session == null || !session.isActive) break@loop
                val snapshot = batteryRepository.readNow()
                handleSnapshot(session, snapshot)
                kotlinx.coroutines.delay(intervalMs)
            }
            stopMeasuring()
        }
    }

    private suspend fun handleSnapshot(session: CalibrationSession, s: BatterySnapshot) {
        // Пишем все сэмплы активной сессии независимо от подфазы — больше данных,
        // шире диапазон SOC, точнее оценка.
        if (s.socPercent != null) sessions.recordSample(session.id, s)

        when (session.state) {
            SessionState.WAITING_DISCHARGE -> {
                if ((s.socPercent ?: 100f) < CalibrationSession.START_SOC_THRESHOLD) {
                    sessions.updateState(session.id, SessionState.MEASURING)
                    updateNotification("Разряд достиг ${s.socPercent?.toInt()}% — подключите зарядное устройство")
                } else {
                    updateNotification("Ожидание разряда ниже 15% (сейчас ${s.socPercent?.toInt()}%)")
                }
            }

            SessionState.MEASURING -> {
                val fullyCharged = s.status == dev.xverlxrd.batterycapacity.domain.model.ChargeStatus.FULL ||
                    (s.socPercent ?: 0f) >= COMPLETE_SOC_PCT && s.isCharging
                if (fullyCharged) {
                    val estimate = finalizeCalibration(session.id)
                    updateNotification(
                        if (estimate != null) {
                            "Готово: ${estimate.actualMah.toInt()} мА·ч (${estimate.method.displayName})"
                        } else {
                            "Тест завершён: данных недостаточно для оценки"
                        },
                    )
                    sessions.updateState(session.id, SessionState.COMPLETED)
                } else if (!s.isCharging) {
                    // Зарядку выдернули — пауза, а не сброс.
                    sessions.updateState(session.id, SessionState.PAUSED, PAUSE_REASON_UNPLUGGED)
                    updateNotification("Пауза: зарядка отключена. Подключите снова, чтобы продолжить")
                } else {
                    updateNotification(progressText(session, s))
                }
            }

            SessionState.PAUSED -> {
                if (s.isCharging) {
                    sessions.updateState(session.id, SessionState.MEASURING)
                    updateNotification(progressText(session, s))
                }
            }

            else -> Unit
        }
    }

    private fun progressText(session: CalibrationSession, s: BatterySnapshot): String =
        "Измерение: ${s.socPercent?.toInt()}% • ${session.collectedDeltaUah / 1000} мА·ч собрано"

    private fun stopMeasuring() {
        measureJob?.cancel()
        measureJob = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String?): Notification {
        val contentText = text ?: "Калибровка батареи выполняется"
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_MEASUREMENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(intent)
            .build()
    }

    override fun onDestroy() {
        measureJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_MEASUREMENT = "measurement"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "dev.xverlxrd.batterycapacity.STOP"
        const val PAUSE_REASON_UNPLUGGED = "charger_unplugged"
        /** Завершаем чуть раньше 100%: зона ≥95% исключена из расчёта как нелинейная. */
        const val COMPLETE_SOC_PCT = 99f

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MeasurementForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MeasurementForegroundService::class.java).setAction(ACTION_STOP),
            )
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MEASUREMENT,
                    "Калибровка батареи",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Прогресс измерения ёмкости аккумулятора" },
            )
        }
    }
}
