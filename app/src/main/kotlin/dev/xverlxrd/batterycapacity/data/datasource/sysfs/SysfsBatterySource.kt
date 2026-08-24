package dev.xverlxrd.batterycapacity.data.datasource.sysfs

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import dev.xverlxrd.batterycapacity.domain.model.PowerSource
import javax.inject.Inject

/**
 * Чтение /sys/class/power_supply. Ядро экспортирует узлы по типам:
 *  - type == "Battery" — сами банки (их может быть несколько: складные,
 *    планшеты, отдельный bms/maxfg у Pixel);
 *  - type == "USB"/"Mains"/"Wireless" — источники.
 *
 * Единицы ядра (стандарт power_supply class):
 *  charge_full / charge_full_design — мкА·ч  → делим на 1000 → мА·ч
 *  energy_full / energy_full_design — мкВт·ч → для перевода в мА·ч делим на
 *      номинальное напряжение (3.85 В): µWh/1000/V = мА·ч;
 *  voltage_now — мкВ → мВ (/1000);
 *  current_now — мкА, знак платформозависимый → нормализуем по status;
 *  temp — десятые доли °C.
 */
class SysfsBatterySource @Inject constructor(
    private val reader: SysfsReader,
) {

    data class SysfsReading(
        val node: String,
        val capacityPercent: Float?,
        val chargeFullMah: Double?,
        val chargeFullDesignMah: Double?,
        val energyFullMah: Double?,
        val energyFullDesignMah: Double?,
        val voltageMv: Int?,
        /** Знак уже нормализован: >0 заряд, <0 разряд. */
        val currentMa: Double?,
        val tempC: Double?,
        val cycleCount: Int?,
        val statusText: String?,
    )

    /** Ищем все банки; приоритет отдаём узлам с charge_full (главный fuel gauge). */
    fun findBatteryNodes(): List<String> {
        return reader.listDirs(POWER_SUPPLY_ROOT).filter { dir ->
            reader.readText("$dir/type")?.equals("Battery", ignoreCase = true) == true
        }.sortedWith(
            compareByDescending<String> { dir -> hasChargeFull(dir) }
                .thenBy { it.substringAfterLast('/') },
        )
    }

    fun readNode(node: String, nowMs: Long): SysfsReading {
        fun num(name: String): Long? = reader.readText("$node/$name")?.toLongOrNull()

        val chargeFullUah = num("charge_full")
        val chargeFullDesignUah = num("charge_full_design")
        // Номинальное напряжение берём из voltage_min/max_design, иначе типовые 3.85 В.
        val nominalV = (num("voltage_max_design") ?: 3850000L) / 1_000_000.0

        val rawCurrent = num("current_now")?.toDouble()
        val status = reader.readText("$node/status")
        // Некоторые OEM репортят ток всегда положительным — ориентируемся на status.
        val signedCurrent = when {
            rawCurrent == null -> null
            status.equals("Discharging", true) && rawCurrent > 0 -> -rawCurrent
            else -> rawCurrent
        }

        return SysfsReading(
            node = node,
            capacityPercent = num("capacity")?.toFloat(),
            chargeFullMah = chargeFullUah?.div(1000.0),
            chargeFullDesignMah = chargeFullDesignUah?.div(1000.0),
            energyFullMah = num("energy_full")?.let { it / 1000.0 / nominalV },
            energyFullDesignMah = num("energy_full_design")?.let { it / 1000.0 / nominalV },
            voltageMv = num("voltage_now")?.div(1000)?.toInt(),
            currentMa = signedCurrent?.div(1000.0),
            tempC = num("temp")?.div(10.0),
            cycleCount = num("cycle_count")?.toInt(),
            statusText = status,
        )
    }

    /**
     * Агрегирует показания всех найденных банок в один снимок.
     * Для нескольких батарей заряды складываются, SOC усредняется по ёмкости.
     */
    fun readAll(nowMs: Long): BatterySnapshot? {
        val nodes = findBatteryNodes()
        if (nodes.isEmpty()) return null
        val readings = nodes.map { readNode(it, nowMs) }

        fun sum(select: (SysfsReading) -> Double?): Double? =
            readings.mapNotNull(select).takeIf { it.isNotEmpty() }?.sum()

        fun weightedSoc(): Float? {
            val pairs = readings.mapNotNull { r ->
                val cap = r.chargeFullMah ?: return@mapNotNull null
                val soc = r.capacityPercent ?: return@mapNotNull null
                cap to soc
            }
            if (pairs.isEmpty()) return readings.firstNotNullOfOrNull { it.capacityPercent }
            val totalCap = pairs.sumOf { it.first }
            return pairs.sumOf { it.first * it.second } .toFloat() / totalCap.toFloat()
        }

        val primary = readings.first()
        return BatterySnapshot(
            timestampMs = nowMs,
            socPercent = weightedSoc(),
            // Pixel: V/I/temp живут в узле battery, а charge_full — в maxfg.
            voltageMv = primary.voltageMv ?: readings.firstNotNullOfOrNull { it.voltageMv },
            currentMa = sum { it.currentMa }?.toFloat(),
            // sysfs не даёт накопленный счётчик ОС — он приходит из BatteryManager.
            chargeCounterUah = null,
            temperatureC = primary.tempC ?: readings.firstNotNullOfOrNull { it.tempC },
            status = parseStatus(primary.statusText ?: readings.firstNotNullOfOrNull { it.statusText }),
            plugged = PowerSource.NONE, // уточняется слоем BatteryManager (ACTION_BATTERY_CHANGED)
            cycleCount = primary.cycleCount ?: readings.firstNotNullOfOrNull { it.cycleCount },
            chargeFullMah = sum { it.chargeFullMah },
            chargeFullDesignMah = sum { it.chargeFullDesignMah },
            energyFullMah = sum { it.energyFullMah },
            energyFullDesignMah = sum { it.energyFullDesignMah },
        )
    }

    private fun parseStatus(text: String?): ChargeStatus = when (text?.lowercase()) {
        "charging" -> ChargeStatus.CHARGING
        "discharging" -> ChargeStatus.DISCHARGING
        "full" -> ChargeStatus.FULL
        "not charging" -> ChargeStatus.NOT_CHARGING
        else -> ChargeStatus.UNKNOWN
    }

    private fun hasChargeFull(dir: String) = reader.exists("$dir/charge_full")

    companion object {
        const val POWER_SUPPLY_ROOT = "/sys/class/power_supply"
    }
}
