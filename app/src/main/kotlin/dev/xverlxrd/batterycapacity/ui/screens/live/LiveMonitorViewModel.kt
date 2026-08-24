package dev.xverlxrd.batterycapacity.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.LiveBatteryInfo
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.usecase.ObserveLiveBatteryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LiveMonitorViewModel @Inject constructor(
    observeLive: ObserveLiveBatteryUseCase,
    batteryRepository: BatteryInfoRepository,
) : ViewModel() {

    /** Живые данные каждые 2 секунды, пока экран открыт. */
    val live: StateFlow<LiveBatteryInfo?> = observeLive(LIVE_INTERVAL_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), null)

    /**
     * Роллинг-окно последних WINDOW отсчётов для графика V/I.
     * Явная цепочка map→scan→stateIn фиксирует типы за инференсом.
     */
    val history: StateFlow<List<BatterySnapshot>> =
        batteryRepository.observeSnapshots(LIVE_INTERVAL_MS)
            .mapToList()
            .scan(emptyList<BatterySnapshot>()) { acc, next -> (acc + next).takeLast(WINDOW) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    private fun Flow<BatterySnapshot>.mapToList(): Flow<List<BatterySnapshot>> =
        map { snapshot -> listOf(snapshot) }

    companion object {
        const val LIVE_INTERVAL_MS = 2000L
        const val WINDOW = 60
    }
}
