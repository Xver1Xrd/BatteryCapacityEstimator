package dev.xverlxrd.batterycapacity.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.SourceCapabilities
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val pollingIntervalSeconds: Int = 30,
    val manualDesignCapacityMah: Long? = null,
    val filterWindowSize: Int = 5,
    val useDarkTheme: Boolean? = null,
    val capabilities: SourceCapabilities = SourceCapabilities(false, false, false, emptyList()),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: UserSettingsRepository,
    batteryRepository: BatteryInfoRepository,
) : ViewModel() {

    private val capabilities = MutableStateFlow(SourceCapabilities(false, false, false, emptyList()))

    init {
        viewModelScope.launch {
            batteryRepository.observeCapabilities().collect { capabilities.value = it }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(settings.preferences, capabilities.asStateFlow()) { prefs, caps ->
        SettingsUiState(
            pollingIntervalSeconds = prefs.pollingIntervalSeconds,
            manualDesignCapacityMah = prefs.manualDesignCapacityMah,
            filterWindowSize = prefs.filterWindowSize,
            useDarkTheme = prefs.useDarkTheme,
            capabilities = caps,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setPolling(seconds: Int) = viewModelScope.launch { settings.setPollingIntervalSeconds(seconds) }
    fun setFilterWindow(size: Int) = viewModelScope.launch { settings.setFilterWindowSize(size) }
    fun setManualCapacity(mah: Long?) = viewModelScope.launch { settings.setManualDesignCapacityMah(mah) }
    fun setTheme(dark: Boolean?) = viewModelScope.launch { settings.setUseDarkTheme(dark) }
}
