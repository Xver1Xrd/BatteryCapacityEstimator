package dev.xverlxrd.batterycapacity.domain.usecase

import dev.xverlxrd.batterycapacity.domain.estimator.DeviceCapacityTable
import dev.xverlxrd.batterycapacity.domain.model.DesignCapacity
import dev.xverlxrd.batterycapacity.domain.model.DesignCapacitySource
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Паспортная (design) ёмкость. Приоритет:
 *  1. charge_full_design / energy_full_design из sysfs — сказал контроллер;
 *  2. ручной ввод пользователя — он знает свой номинал из документации;
 *  3. таблица известных моделей.
 */
class ResolveDesignCapacityUseCase @Inject constructor(
    private val batteryRepository: BatteryInfoRepository,
    private val settings: UserSettingsRepository,
) {
    suspend operator fun invoke(): DesignCapacity {
        val snapshot = batteryRepository.readNow()
        val sysfsDesign = snapshot.chargeFullDesignMah ?: snapshot.energyFullDesignMah
        if (sysfsDesign != null && sysfsDesign > 0) {
            return DesignCapacity(sysfsDesign, DesignCapacitySource.SYSFS_DESIGN)
        }
        val prefs = settings.preferences.first()
        val manual = prefs.manualDesignCapacityMah?.takeIf { it > 0 }
        if (manual != null) {
            return DesignCapacity(manual.toDouble(), DesignCapacitySource.MANUAL)
        }
        val fromTable = DeviceCapacityTable.designCapacityMah(
            batteryRepository.deviceManufacturer,
            batteryRepository.deviceModel,
        )
        return if (fromTable != null) {
            DesignCapacity(fromTable, DesignCapacitySource.DEVICE_TABLE)
        } else {
            DesignCapacity(null, DesignCapacitySource.UNKNOWN)
        }
    }

    /** Округлённое значение для UI. */
    fun displayValue(capacity: DesignCapacity): Long? =
        capacity.valueMah?.roundToLong()
}
