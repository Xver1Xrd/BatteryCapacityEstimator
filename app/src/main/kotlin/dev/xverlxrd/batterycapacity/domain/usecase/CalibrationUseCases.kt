package dev.xverlxrd.batterycapacity.domain.usecase

import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.SessionState
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import javax.inject.Inject

class StartCalibrationUseCase @Inject constructor(
    private val sessions: MeasurementSessionRepository,
    private val batteryRepository: BatteryInfoRepository,
) {
    /** Создаёт сессию; если SOC уже < 15% — сразу в фазу измерения. */
    suspend operator fun invoke(): Long {
        val soc = batteryRepository.readNow().socPercent
        val state = if (soc != null && soc < CalibrationSession.START_SOC_THRESHOLD) {
            SessionState.MEASURING
        } else {
            SessionState.WAITING_DISCHARGE
        }
        val id = sessions.start(soc, System.currentTimeMillis())
        sessions.updateState(id, state)
        return id
    }
}

class PauseCalibrationUseCase @Inject constructor(
    private val sessions: MeasurementSessionRepository,
) {
    suspend operator fun invoke(reason: String) = sessions.pauseAllActive(reason)
}

class ResumeCalibrationUseCase @Inject constructor(
    private val sessions: MeasurementSessionRepository,
    private val batteryRepository: BatteryInfoRepository,
) {
    /** Возобновление после паузы: зарядка снова подключена или приложение перезапущено. */
    suspend operator fun invoke(sessionId: Long) {
        val soc = batteryRepository.readNow().socPercent
        sessions.touch(sessionId, soc)
        sessions.updateState(sessionId, SessionState.MEASURING)
    }
}

class CancelCalibrationUseCase @Inject constructor(
    private val sessions: MeasurementSessionRepository,
) {
    suspend operator fun invoke() = sessions.cancelActive()
}
