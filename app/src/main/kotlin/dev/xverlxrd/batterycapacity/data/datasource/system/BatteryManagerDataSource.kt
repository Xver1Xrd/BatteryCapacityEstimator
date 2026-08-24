package dev.xverlxrd.batterycapacity.data.datasource.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import dev.xverlxrd.batterycapacity.domain.model.PowerSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Чистое сопоставление extras ACTION_BATTERY_CHANGED → доменные поля.
 * Вынесено из источника для Robolectric-теста на реальных фикстурах.
 */
object BatteryStatusIntentMapper {

    fun map(
        level: Int,
        scale: Int,
        voltageMv: Int,
        tempTenthsC: Int,
        statusCode: Int,
        pluggedCode: Int,
        healthCode: Int,
        nowMs: Long,
    ): BatterySnapshot {
        val status = when (statusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
            else -> ChargeStatus.UNKNOWN
        }
        return BatterySnapshot(
            timestampMs = nowMs,
            socPercent = if (scale > 0) level.toFloat() / scale * 100f else null,
            voltageMv = voltageMv.takeIf { it > 0 },
            // ACTION_BATTERY_CHANGED тока не несёт — придёт из BATTERY_PROPERTY_CURRENT_NOW.
            currentMa = null,
            chargeCounterUah = null,
            temperatureC = tempTenthsC.takeIf { it > 0 }?.div(10.0),
            status = status,
            plugged = PowerSource.fromAndroidConstant(pluggedCode),
            osHealthCode = healthCode,
        )
    }
}

/**
 * Источник на официальном API: BatteryManager properties + sticky broadcast.
 * Доступен на всех устройствах, но OEM-зависимо:
 *  - CHARGE_COUNTER может отсутствовать или всегда быть 0 (тогда метод A недоступен);
 *  - CURRENT_NOW может не поддерживаться (метод B тоже) — проверяем capability при старте.
 */
@Singleton
class BatteryManagerDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun readIntentSnapshot(nowMs: Long): BatterySnapshot {
        // Sticky broadcast читаем без регистрации ресивера — ноль накладных расходов.
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        if (intent == null) return BatterySnapshot(timestampMs = nowMs, socPercent = null, voltageMv = null, currentMa = null, chargeCounterUah = null, temperatureC = null)

        val mapped = BatteryStatusIntentMapper.map(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
            tempTenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE),
            statusCode = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            pluggedCode = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
            nowMs = nowMs,
        )
        // Обогащаем из properties API.
        return mapped.copy(
            currentMa = readCurrentMa(bm)?.let { normalizeSign(it, mapped.status) },
            chargeCounterUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                .takeIf { it > 0 },
        )
    }

    /** CURRENT_NOW в мА (µA/1000); null если свойство не поддерживается. */
    fun readCurrentMa(bm: BatteryManager): Double? {
        val ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (ua == Int.MIN_VALUE || ua == 0 && !hasCurrentProperty(bm)) return null
        return ua / 1000.0
    }

    private fun hasCurrentProperty(bm: BatteryManager): Boolean =
        runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) != Int.MIN_VALUE }
            .getOrDefault(false)

    /**
     * Знак тока платформозависим: документация говорит «+ при заряде», но часть
     * OEM инвертирует. Нормализуем по статусу зарядки из broadcast'а.
     */
    private fun normalizeSign(currentMa: Double, status: ChargeStatus): Float = when (status) {
        ChargeStatus.DISCHARGING -> -kotlin.math.abs(currentMa).toFloat()
        ChargeStatus.CHARGING, ChargeStatus.FULL -> kotlin.math.abs(currentMa).toFloat()
        else -> currentMa.toFloat()
    }

    /** Быстрая capability-проверка без чтения значений. */
    fun probeCapabilities(): Pair<Boolean, Boolean> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val counterOk = runCatching { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) > 0 }
            .getOrDefault(false)
        val currentOk = runCatching {
            val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            v != Int.MIN_VALUE && v != 0
        }.getOrDefault(false)
        return counterOk to currentOk
    }
}
