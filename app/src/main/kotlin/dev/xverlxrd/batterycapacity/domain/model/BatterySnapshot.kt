package dev.xverlxrd.batterycapacity.domain.model

/**
 * Мгновенный снимок состояния батареи. Единая точка входа для всех методов
 * оценки; поля nullable — на конкретном устройстве источник может отсутствовать
 * (OEM скрыл charge_counter и т.п.), тогда estimator переходит на fallback.
 *
 * Единицы:
 *  - socPercent: 0..100 (дробное, если ОС отдаёт charge_counter/10000);
 *  - voltageMv: мВ;
 *  - currentMa: мА, знак нормализуем: >0 — заряд, <0 — разряд;
 *  - chargeCounterUah: мкА·ч (BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).
 */
data class BatterySnapshot(
    val timestampMs: Long,
    val socPercent: Float?,
    val voltageMv: Int?,
    val currentMa: Float?,
    val chargeCounterUah: Long?,
    val temperatureC: Double?,
    val status: ChargeStatus = ChargeStatus.UNKNOWN,
    val plugged: PowerSource = PowerSource.NONE,
    /** «health» из ACTION_BATTERY_CHANGED — это НЕ % ёмкости, а класс состояния. */
    val osHealthCode: Int? = null,
    val cycleCount: Int? = null,
    /** sysfs: фактическая полная ёмкость, мА·ч. */
    val chargeFullMah: Double? = null,
    /** sysfs: паспортная ёмкость, мА·ч. */
    val chargeFullDesignMah: Double? = null,
    /** sysfs energy_full / energy_full_design, пересчитанные в мА·ч (µWh / V_nominal). */
    val energyFullMah: Double? = null,
    val energyFullDesignMah: Double? = null,
) {
    val isCharging: Boolean get() = status == ChargeStatus.CHARGING || plugged != PowerSource.NONE

    /** Метод C: обе величины из sysfs → точная пара (фактическая, паспортная). */
    val hasSystemCapacityPair: Boolean
        get() {
            val full = chargeFullMah ?: energyFullMah ?: return false
            val design = chargeFullDesignMah ?: energyFullDesignMah ?: return false
            return full > 0 && design > 0
        }
}

enum class ChargeStatus { UNKNOWN, CHARGING, DISCHARGING, FULL, NOT_CHARGING }

enum class PowerSource {
    NONE, AC, USB, WIRELESS, DOCK;

    companion object {
        fun fromAndroidConstant(plugged: Int): PowerSource = when (plugged) {
            1 -> AC      // BatteryManager.BATTERY_PLUGGED_AC
            2 -> USB     // BATTERY_PLUGGED_USB
            4 -> WIRELESS // BATTERY_PLUGGED_WIRELESS
            8 -> DOCK    // BATTERY_PLUGGED_DOCK
            else -> NONE
        }
    }
}
