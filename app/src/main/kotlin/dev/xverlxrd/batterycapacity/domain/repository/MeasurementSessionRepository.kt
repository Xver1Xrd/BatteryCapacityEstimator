package dev.xverlxrd.batterycapacity.domain.repository

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.SessionState
import kotlinx.coroutines.flow.Flow

/**
 * Состояние калибровочной сессии живёт в Room:
 *  - пауза при выдёргивании зарядки (не сброс);
 *  - восстановление после перезагрузки;
 *  - прерывание/возобновление из UI.
 */
interface MeasurementSessionRepository {
    fun observeActiveSession(): Flow<CalibrationSession?>
    fun observeSamples(sessionId: Long): Flow<List<BatterySnapshot>>

    /** Создаёт сессию и возвращает её id. Вызывается из мастера калибровки. */
    suspend fun start(socNow: Float?, nowMs: Long): Long

    suspend fun recordSample(sessionId: Long, snapshot: BatterySnapshot)

    /** Все сэмплы сессии разом (для финализации). */
    suspend fun loadSamples(sessionId: Long): List<BatterySnapshot>

    suspend fun updateState(sessionId: Long, state: SessionState, pauseReason: String? = null)

    suspend fun touch(sessionId: Long, socNow: Float?)

    /** Помечает активные сессии как PAUSED (вызов при загрузке ОС / старте приложения). */
    suspend fun pauseAllActive(reason: String)

    suspend fun cancelActive()

    /** Возвращает незавершённую сессию, если есть (после перезагрузки). */
    suspend fun findResumable(): CalibrationSession?

    suspend fun delete(sessionId: Long)
}
