package dev.xverlxrd.batterycapacity.data.datasource.dumpsys

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import dev.xverlxrd.batterycapacity.domain.model.PowerSource

/**
 * Парсер вывода `dumpsys battery`. Чистая функция над строкой — тестируется
 * на фикстурах Pixel/Samsung/Xiaomi без Robolectric.
 *
 * Замечание о доступности: обычное приложение не имеет permission DUMP,
 * поэтому exec обычно падает с SecurityException; источник используется
 * опционально (например, при отладке через adb shell appops). Основные
 * данные всегда приходят из BatteryManager/sysfs.
 */
object DumpsysOutputParser {

    data class Fields(
        val levelPercent: Float?,
        val voltageMv: Int?,
        val chargeCounterUah: Long?,
        val tempTenthsC: Int?,
        val statusCode: Int?,
        val healthCode: Int?,
        val acPowered: Boolean?,
        val usbPowered: Boolean?,
        val wirelessPowered: Boolean?,
        val cycleCount: Int?,
        val technology: String?,
    )

    fun parse(output: String): Fields? {
        if (!output.contains("Battery Service", ignoreCase = true) &&
            !output.contains("level:", ignoreCase = true)
        ) return null

        val kv = output.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
            }
            .toMap()

        fun int(key: String): Int? = kv[key]?.toIntOrNull()
        fun bool(key: String): Boolean? = kv[key]?.lowercase()?.let { it == "true" }

        return Fields(
            levelPercent = int("level")?.toFloat(),
            voltageMv = int("voltage"),
            chargeCounterUah = kv["charge counter"]?.toLongOrNull(),
            tempTenthsC = int("temperature"),
            statusCode = int("status"),
            healthCode = int("health"),
            acPowered = bool("ac powered"),
            usbPowered = bool("usb powered"),
            wirelessPowered = bool("wireless powered"),
            cycleCount = int("cycle_count") ?: int("cycle count"),
            technology = kv["technology"],
        )
    }

    /** Преобразование полей в доменный снимок. */
    fun toSnapshot(fields: Fields, nowMs: Long): BatterySnapshot {
        val plugged = when {
            fields.acPowered == true -> PowerSource.AC
            fields.usbPowered == true -> PowerSource.USB
            fields.wirelessPowered == true -> PowerSource.WIRELESS
            else -> PowerSource.NONE
        }
        // status: 1=unknown 2=charging 3=discharging 4=not charging 5=full
        val status = when (fields.statusCode) {
            2 -> ChargeStatus.CHARGING
            3 -> ChargeStatus.DISCHARGING
            4 -> ChargeStatus.NOT_CHARGING
            5 -> ChargeStatus.FULL
            else -> ChargeStatus.UNKNOWN
        }
        return BatterySnapshot(
            timestampMs = nowMs,
            socPercent = fields.levelPercent,
            voltageMv = fields.voltageMv,
            currentMa = null, // dumpsys battery тока не отдаёт
            chargeCounterUah = fields.chargeCounterUah,
            temperatureC = fields.tempTenthsC?.div(10.0),
            status = status,
            plugged = plugged,
            osHealthCode = fields.healthCode,
            cycleCount = fields.cycleCount,
        )
    }
}
