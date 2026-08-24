package dev.xverlxrd.batterycapacity.ui.screens.calibration

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xverlxrd.batterycapacity.domain.estimator.CapacityEstimator
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import dev.xverlxrd.batterycapacity.service.CheckpointWorker
import dev.xverlxrd.batterycapacity.service.MeasurementForegroundService
import dev.xverlxrd.batterycapacity.domain.usecase.CancelCalibrationUseCase
import dev.xverlxrd.batterycapacity.domain.usecase.ResumeCalibrationUseCase
import dev.xverlxrd.batterycapacity.domain.usecase.StartCalibrationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import javax.inject.Inject

data class CalibrationUiState(
    val session: CalibrationSession? = null,
    val samples: List<BatterySnapshot> = emptyList(),
    /** Предварительная оценка по уже собранным данным — растёт в точности с диапазоном SOC. */
    val previewEstimate: CapacityEstimate? = null,
    val designCapacityMah: Double? = null,
    val etaMinutesToFull: Int? = null,
    val etaMinutesToFifteen: Int? = null,
    /** Последний сохранённый результат — показывается на финальном экране. */
    val latestResult: CapacityEstimate? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalibrationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessions: MeasurementSessionRepository,
    private val batteryRepository: BatteryInfoRepository,
    private val settings: UserSettingsRepository,
    private val estimator: CapacityEstimator,
    historyRepository: dev.xverlxrd.batterycapacity.domain.repository.CompletedMeasurementsRepository,
    private val startCalibration: StartCalibrationUseCase,
    private val resumeCalibration: ResumeCalibrationUseCase,
    private val cancelCalibration: CancelCalibrationUseCase,
) : ViewModel() {

    private val activeSession = sessions.observeActiveSession()

    private val sessionSamples = activeSession.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else sessions.observeSamples(session.id)
    }

    val uiState: StateFlow<CalibrationUiState> = combine(
        combine(activeSession, sessionSamples) { s, samples -> s to samples },
        batteryRepository.observeSnapshots(PREVIEW_INTERVAL_MS).map { it.socPercent },
        settings.preferences,
        historyRepository.observeHistory(),
    ) { (session, samples), _, prefs, measurements ->
        var state = CalibrationUiState(session = session, samples = samples, latestResult = measurements.firstOrNull())

        if (samples.size >= 2 && session != null && session.isActive) {
            // Предварительная оценка пересчитывается на каждом опросе.
            estimator.estimate(samples, System.currentTimeMillis())?.let { preview ->
                state = state.copy(
                    previewEstimate = preview.copy(designMah = prefs.manualDesignCapacityMah?.toDouble()),
                )
            }
            state = state.copy(designCapacityMah = prefs.manualDesignCapacityMah?.toDouble())
            state = state.copy(etaMinutesToFull = etaMinutes(samples, target = 100f))
            state = state.copy(etaMinutesToFifteen = etaMinutes(samples, target = 15f))
        }
        state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalibrationUiState())

    /**
     * ETA до целевого SOC: скорость из последних WINDOW_MINUTES минут наблюдения,
     * линейная экстраполяция |ΔSOC| / rate * 60 минут.
     */
    private fun etaMinutes(samples: List<BatterySnapshot>, target: Float): Int? {
        if (samples.size < 3) return null
        val window = samples.takeLast(WINDOW_SAMPLES)
        val first = window.first()
        val last = window.last()
        val socStart = first.socPercent ?: return null
        val socEnd = last.socPercent ?: return null
        val dtHours = (last.timestampMs - first.timestampMs) / 3_600_000.0
        if (dtHours < MIN_WINDOW_HOURS) return null
        val ratePerHour = (socEnd - socStart) / dtHours // отрицательный при разряде
        val remaining = abs(target - socEnd)
        val minutes = remaining / abs(ratePerHour) * 60.0
        return if (minutes.isFinite() && minutes in 0.0..24 * 60.0) minutes.toInt() else null
    }

    fun start() {
        viewModelScope.launch {
            startCalibration()
            MeasurementForegroundService.start(appContext)
            CheckpointWorker.schedule(appContext)
        }
    }

    fun resume(sessionId: Long) {
        viewModelScope.launch {
            resumeCalibration(sessionId)
            MeasurementForegroundService.start(appContext)
        }
    }

    fun cancel() {
        viewModelScope.launch {
            cancelCalibration()
            MeasurementForegroundService.stop(appContext)
            CheckpointWorker.cancel(appContext)
        }
    }

    companion object {
        private const val PREVIEW_INTERVAL_MS = 30_000L
        private const val WINDOW_SAMPLES = 20
        private const val MIN_WINDOW_HOURS = 10.0 / 60.0
    }
}
