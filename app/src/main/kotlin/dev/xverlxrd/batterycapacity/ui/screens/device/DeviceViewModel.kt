package dev.xverlxrd.batterycapacity.ui.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xverlxrd.batterycapacity.data.datasource.sysfs.SysfsHarvester
import dev.xverlxrd.batterycapacity.data.repository.UsageStatsRepository
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.LiveBatteryInfo
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.usecase.ObserveLiveBatteryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Сводка использования за период — то, что показывает экран. */
data class UsageSummary(
    val rows: List<DayRow>,
    val weekTempMaxC: Double?,
    val cycleCount: Int?,
) {
    data class DayRow(val label: String, val socAvg: Int?, val socMin: Int?, val socMax: Int?)
}

@HiltViewModel
class DeviceViewModel @Inject constructor(
    observeLive: ObserveLiveBatteryUseCase,
    batteryRepository: BatteryInfoRepository,
    private val harvester: SysfsHarvester,
    usageStats: UsageStatsRepository,
) : ViewModel() {

    /** Живые данные каждые 2 секунды, пока экран открыт. */
    val live: StateFlow<LiveBatteryInfo?> = observeLive(LIVE_INTERVAL_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), null)

    /**
     * Роллинг-окно последних WINDOW отсчётов для спарклайна напряжения.
     * Явная цепочка map→scan→stateIn фиксирует типы за инференсом.
     */
    val snapshots: StateFlow<List<BatterySnapshot>> =
        batteryRepository.observeSnapshots(LIVE_INTERVAL_MS)
            .mapToSingle()
            .scan(emptyList<BatterySnapshot>()) { acc, next -> (acc + next).takeLast(WINDOW) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    /** Полный дамп узлов /sys/class/power_supply — обновление раз в 10 с. */
    val sysfsNodes: StateFlow<List<SysfsHarvester.Node>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(runCatching { harvester.harvest() }.getOrDefault(emptyList()))
            delay(SYSFS_REFRESH_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Дневная статистика за 30 дней (накапливается фоновым воркером). */
    val usageSummary: StateFlow<UsageSummary?> = usageStats.observeSinceDays(30)
        .map { stats ->
            if (stats.isEmpty()) return@map null
            UsageSummary(
                rows = stats.takeLast(14).map { s ->
                    val date = LocalDate.ofEpochDay(s.dateEpochDay)
                    val n = s.sampleCount.coerceAtLeast(1)
                    UsageSummary.DayRow(
                        label = date.format(DAY_FORMAT),
                        socAvg = if (s.socSum > 0) (s.socSum / n).toInt() else null,
                        socMin = s.socMin?.toInt(),
                        socMax = s.socMax?.toInt(),
                    )
                },
                weekTempMaxC = stats.takeLast(7).mapNotNull { it.tempMaxC }.maxOrNull(),
                cycleCount = stats.mapNotNull { it.cycleCountLast }.lastOrNull(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun Flow<BatterySnapshot>.mapToSingle(): Flow<BatterySnapshot> = this

    companion object {
        const val LIVE_INTERVAL_MS = 2000L
        const val WINDOW = 60
        const val SYSFS_REFRESH_MS = 10_000L
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
    }
}
