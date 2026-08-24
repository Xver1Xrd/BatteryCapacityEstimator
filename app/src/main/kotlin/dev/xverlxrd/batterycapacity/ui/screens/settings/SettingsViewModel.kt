package dev.xverlxrd.batterycapacity.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.CompletedMeasurementsRepository
import dev.xverlxrd.batterycapacity.domain.repository.SourceCapabilities
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val pollingIntervalSeconds: Int = 30,
    val manualDesignCapacityMah: Long? = null,
    val filterWindowSize: Int = 5,
    val useDarkTheme: Boolean? = null,
    val animationsEnabled: Boolean = true,
    val useDynamicColor: Boolean = false,
    val capabilities: SourceCapabilities = SourceCapabilities(false, false, false, emptyList()),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: UserSettingsRepository,
    private val historyRepository: CompletedMeasurementsRepository,
    batteryRepository: BatteryInfoRepository,
) : ViewModel() {

    private val capabilities = MutableStateFlow(SourceCapabilities(false, false, false, emptyList()))

    init {
        viewModelScope.launch {
            batteryRepository.observeCapabilities().collect { capabilities.value = it }
        }
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.preferences, capabilities.asStateFlow()) { prefs, caps ->
            SettingsUiState(
                pollingIntervalSeconds = prefs.pollingIntervalSeconds,
                manualDesignCapacityMah = prefs.manualDesignCapacityMah,
                filterWindowSize = prefs.filterWindowSize,
                useDarkTheme = prefs.useDarkTheme,
                animationsEnabled = prefs.animationsEnabled,
                useDynamicColor = prefs.useDynamicColor,
                capabilities = caps,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setPolling(seconds: Int) = viewModelScope.launch { settings.setPollingIntervalSeconds(seconds) }
    fun setFilterWindow(size: Int) = viewModelScope.launch { settings.setFilterWindowSize(size) }
    fun setManualCapacity(mah: Long?) = viewModelScope.launch { settings.setManualDesignCapacityMah(mah) }
    fun setTheme(dark: Boolean?) = viewModelScope.launch { settings.setUseDarkTheme(dark) }
    fun setAnimations(enabled: Boolean) = viewModelScope.launch { settings.setAnimationsEnabled(enabled) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setUseDynamicColor(enabled) }

    /** Очистка всей истории измерений. */
    fun clearHistory(onDone: () -> Unit = {}) = viewModelScope.launch {
        historyRepository.clearAll()
        onDone()
    }

    /** Экспорт истории в CSV через системный шэринг — данные не покидают устройство без ведома пользователя. */
    fun exportCsv() = viewModelScope.launch {
        val items = historyRepository.observeHistory().first()
        val csv = buildCsv(items)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Battery Capacity Estimator — история измерений")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        appContext.startActivity(
            Intent.createChooser(send, "Экспорт истории").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun buildCsv(items: List<CapacityEstimate>): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return buildString {
            appendLine("measured_at;actual_mah;design_mah;health_percent;confidence_mah;soc_span_pct;method")
            items.forEach { m ->
                appendLine(
                    listOf(
                        date.format(Date(m.measuredAtMs)),
                        m.actualMah.toInt(),
                        m.designMah?.toInt()?.toString() ?: "",
                        m.healthPercent?.let { "%.1f".format(it) } ?: "",
                        m.confidenceMah.toInt(),
                        m.socSpanPct.toInt(),
                        m.method.name,
                    ).joinToString(";"),
                )
            }
        }
    }
}
