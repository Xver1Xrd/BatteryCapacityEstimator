package dev.xverlxrd.batterycapacity.data.repository

import android.os.Build
import dev.xverlxrd.batterycapacity.data.datasource.dumpsys.DumpsysBatterySource
import dev.xverlxrd.batterycapacity.data.datasource.sysfs.SysfsBatterySource
import dev.xverlxrd.batterycapacity.data.datasource.system.BatteryManagerDataSource
import dev.xverlxrd.batterycapacity.di.IoDispatcher
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.PowerSource
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.SourceCapabilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Слияние источников по приоритету:
 *  - SOC/ток/счётчик — BatteryManager API (метод A/B);
 *  - charge_full/design, cycle_count, temp — sysfs (метод C), если узлы есть;
 *  - dumpsys — последний fallback для счётчика (нужен DUMP, обычно недоступен).
 */
@Singleton
class BatteryInfoRepositoryImpl @Inject constructor(
    private val batteryManagerSource: BatteryManagerDataSource,
    private val sysfsSource: SysfsBatterySource,
    private val dumpsysSource: DumpsysBatterySource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BatteryInfoRepository {

    override var latestSnapshot: BatterySnapshot? = null
        private set

    private val capabilitiesFlow = MutableStateFlow(SourceCapabilities(false, false, false, emptyList()))
    override fun observeCapabilities(): Flow<SourceCapabilities> = capabilitiesFlow.asStateFlow()

    init {
        probe()
    }

    private fun probe() {
        val (counter, current) = runCatching { batteryManagerSource.probeCapabilities() }
            .getOrDefault(false to false)
        val nodes = runCatching { sysfsSource.findBatteryNodes() }.getOrDefault(emptyList())
        capabilitiesFlow.value = SourceCapabilities(
            hasChargeCounter = counter,
            hasCurrentNow = current,
            hasSysfsAccess = nodes.isNotEmpty(),
            batteryNodesFound = nodes.map { it.substringAfterLast('/') },
        )
    }

    override suspend fun readNow(): BatterySnapshot = withContext(io) {
        val now = System.currentTimeMillis()
        val base = batteryManagerSource.readIntentSnapshot(now)
        val sysfs = runCatching { sysfsSource.readAll(now) }.getOrNull()

        // Сливаем: sysfs богаче по ёмкостным полям, BatteryManager — по SOC и счётчику.
        val merged = if (sysfs != null) {
            base.copy(
                voltageMv = base.voltageMv ?: sysfs.voltageMv,
                temperatureC = base.temperatureC ?: sysfs.temperatureC,
                cycleCount = base.cycleCount ?: sysfs.cycleCount,
                chargeFullMah = sysfs.chargeFullMah,
                chargeFullDesignMah = sysfs.chargeFullDesignMah,
                energyFullMah = sysfs.energyFullMah,
                energyFullDesignMah = sysfs.energyFullDesignMah,
            )
        } else {
            base
        }

        // Счётчик заряда: сначала официальный API, затем dumpsys.
        val counterUah = merged.chargeCounterUah
            ?: dumpsysSource.read(now)?.chargeCounterUah?.takeIf { it > 0 }

        latestSnapshot = merged.copy(chargeCounterUah = counterUah)
        latestSnapshot!!
    }

    override fun observeSnapshots(intervalMs: Long): Flow<BatterySnapshot> = flow {
        while (true) {
            emit(readNow())
            kotlinx.coroutines.delay(intervalMs)
        }
    }.flowOn(io)


    override val deviceManufacturer: String get() = Build.MANUFACTURER.orEmpty()
    override val deviceModel: String get() = Build.MODEL.orEmpty()
}
