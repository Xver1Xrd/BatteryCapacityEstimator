package dev.xverlxrd.batterycapacity.domain.usecase

import dev.xverlxrd.batterycapacity.domain.estimator.CapacityEstimator
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.domain.repository.CompletedMeasurementsRepository
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import javax.inject.Inject

/**
 * Финализация калибровки: собранные за тест сэмплы прогоняются через
 * оркестратор методов A/B/C, к результату прикрепляется паспортная ёмкость
 * и оценка сохраняется в историю деградации.
 */
class FinalizeCalibrationUseCase @Inject constructor(
    private val sessions: MeasurementSessionRepository,
    private val history: CompletedMeasurementsRepository,
    private val resolveDesignCapacity: ResolveDesignCapacityUseCase,
    private val estimator: CapacityEstimator,
) {
    suspend operator fun invoke(sessionId: Long): CapacityEstimate? {
        val samples = sessions.loadSamples(sessionId)
        val estimate = estimator.estimate(samples, System.currentTimeMillis()) ?: return null
        val design = resolveDesignCapacity()
        val complete = estimate.copy(designMah = design.valueMah)
        history.save(complete)
        sessions.updateState(sessionId, dev.xverlxrd.batterycapacity.domain.model.SessionState.COMPLETED)
        return complete
    }
}
