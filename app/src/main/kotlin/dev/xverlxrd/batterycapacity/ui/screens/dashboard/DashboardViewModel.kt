package dev.xverlxrd.batterycapacity.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.CompletedMeasurementsRepository
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import dev.xverlxrd.batterycapacity.domain.repository.SourceCapabilities
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import dev.xverlxrd.batterycapacity.service.CheckpointWorker
import dev.xverlxrd.batterycapacity.service.MeasurementForegroundService
import dev.xverlxrd.batterycapacity.domain.usecase.StartCalibrationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val snapshot: BatterySnapshot? = null,
    val capabilities: SourceCapabilities = SourceCapabilities(false, false, false, emptyList()),
    val latestMeasurement: CapacityEstimate? = null,
    val previousMeasurement: CapacityEstimate? = null,
    val activeSession: CalibrationSession? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val batteryRepository: BatteryInfoRepository,
    private val sessions: MeasurementSessionRepository,
    private val history: CompletedMeasurementsRepository,
    private val settings: UserSettingsRepository,
    private val startCalibration: StartCalibrationUseCase,
) : ViewModel() {

    private val capabilities = MutableStateFlow(SourceCapabilities(false, false, false, emptyList()))

    init {
        viewModelScope.launch {
            batteryRepository.readNow()
            batteryRepository.observeCapabilities().collect { capabilities.value = it }
        }
        // Незавершённая сессия после смерти процесса — помечаем паузой.
        viewModelScope.launch {
            if (sessions.findResumable() != null) sessions.pauseAllActive("Приложение было перезапущено")
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        batteryRepository.observeSnapshots(REFRESH_MS),
        history.observeHistory(),
        combine(sessions.observeActiveSession(), capabilities.asStateFlow()) { s, c -> s to c },
    ) { snapshot, measurements, (session, caps) ->
        DashboardUiState(
            snapshot = snapshot,
            capabilities = caps,
            latestMeasurement = measurements.firstOrNull(),
            previousMeasurement = measurements.getOrNull(1),
            activeSession = session,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    /** Запуск калибровки: создаём сессию, поднимаем FGS и контрольные точки. */
    fun startCalibration() {
        viewModelScope.launch {
            startCalibration()
            MeasurementForegroundService.start(appContext)
            CheckpointWorker.schedule(appContext)
        }
    }

    companion object {
        private const val REFRESH_MS = 30_000L
    }
}
