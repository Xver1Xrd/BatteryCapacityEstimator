package dev.xverlxrd.batterycapacity.domain.model

import kotlin.math.roundToInt

/**
 * Состояние калибровочной сессии. Хранится в Room, поэтому переживает
 * перезагрузку телефона и выгрузку процесса.
 */
enum class SessionState {
    /** Ждём разряда ниже порога (<15%), чтобы начать сбор данных. */
    WAITING_DISCHARGE,

    /** Активный сбор данных (разряд или заряд). */
    MEASURING,

    /** Зарядка выдернута / телефон перезагрузился — сессия возобновляема. */
    PAUSED,

    /** Дошли до 100%, считаем результат. */
    COMPLETED,

    /** Пользователь отменил тест. */
    CANCELLED,
}

data class CalibrationSession(
    val id: Long,
    val startedAtMs: Long,
    val updatedAtMs: Long,
    val state: SessionState,
    val socAtStart: Float?,
    val lowestSoc: Float?,
    val lastSoc: Float?,
    val collectedDeltaUah: Long,
    val sampleCount: Int,
    val pauseReason: String? = null,
) {
    val isActive: Boolean get() = state == SessionState.MEASURING || state == SessionState.WAITING_DISCHARGE || state == SessionState.PAUSED

    companion object {
        const val START_SOC_THRESHOLD = 15f
    }
}

/**
 * Live-информация для экрана «Сейчас» (обновление каждые ~2 c).
 *
 * dischargeRatePerHour — скорость разряда, %/час = (ΔSOC / Δt_часы),
 * отрицательная при разряде. ETA до выключения оценивается как
 * remaining_soc / |rate| — грубая линейная экстраполяция текущего потребления.
 */
data class LiveBatteryInfo(
    val snapshot: BatterySnapshot,
    val dischargeRatePerHour: Double?,
    val etaMinutesToEmpty: Int?,
    val plateauDetected: Boolean,
) {
    val socRounded: Int get() = snapshot.socPercent?.roundToInt() ?: -1
}

/** Настройки пользователя (DataStore). */
data class UserPreferences(
    val pollingIntervalSeconds: Int = 30,
    val manualDesignCapacityMah: Long? = null,
    val filterWindowSize: Int = 5,
    val useDarkTheme: Boolean? = null,
)
