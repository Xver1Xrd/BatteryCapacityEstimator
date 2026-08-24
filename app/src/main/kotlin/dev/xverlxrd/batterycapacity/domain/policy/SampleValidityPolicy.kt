package dev.xverlxrd.batterycapacity.domain.policy

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.PowerSource

/**
 * Политика отбора отсчётов для расчёта ёмкости. Всё, что попадает под фильтры,
 * выбрасывается ДО интегрирования — нелинейные участки кривой заряда и
 * температурный дрейф иначе дают систематическую ошибку до ±20%.
 */
object SampleValidityPolicy {

    /** Вне [-0..45]°C газгейдж врёт: литий-ион теряет доступную ёмкость на холоде. */
    const val TEMP_MIN_C = 0.0
    const val TEMP_MAX_C = 45.0

    /**
     * Верхняя зона SOC ≥ 95% нелинейна: контроллер растягивает последние проценты
     * (CV-фаза, gas gauge quantization) — в расчёт ΔQ/ΔSOC её не включаем.
     */
    const val TOP_SOC_ZONE_PCT = 95f

    /** Выброс датчика тока: >5 А нереально для смартфона вне всплесков модема. */
    const val MAX_PLAUSIBLE_CURRENT_MA = 5000f

    /** Первые минуты быстрой зарядки ток нелинеен (CC-CV ramp-up). */
    const val FAST_CHARGE_GRACE_MS = 3 * 60 * 1000L

    /** Резкий скачок напряжения — признак смены нагрузки или глюка датчика. */
    const val VOLTAGE_JUMP_MV_PER_S = 200.0

    /** Порог «застрявшего» SOC для детектора плато. */
    const val PLATEAU_MINUTES = 20L

    fun isTemperatureValid(tempC: Double?): Boolean =
        tempC == null || (tempC in TEMP_MIN_C..TEMP_MAX_C)

    /**
     * Валиден ли переход prev→cur для накопления заряда.
     * @param chargingSinceMs когда подключилась зарядка (для grace-периода), null если разряд.
     */
    fun isTransitionValid(prev: BatterySnapshot, cur: BatterySnapshot, chargingSinceMs: Long?): Boolean {
        if (!isTemperatureValid(prev.temperatureC) || !isTemperatureValid(cur.temperatureC)) return false
        // Верхняя нелинейная зона исключается только на заряде; при разряде через неё можно.
        if (cur.isCharging && (prev.socPercent ?: 0f) >= TOP_SOC_ZONE_PCT) return false
        if (chargingSinceMs != null &&
            cur.timestampMs - chargingSinceMs < FAST_CHARGE_GRACE_MS &&
            (prev.plugged == PowerSource.AC || cur.plugged == PowerSource.AC)
        ) return false
        val dv = (cur.voltageMv ?: return true) - (prev.voltageMv ?: return true)
        val dtSec = ((cur.timestampMs - prev.timestampMs).coerceAtLeast(1)) / 1000.0
        if (kotlin.math.abs(dv) / dtSec > VOLTAGE_JUMP_MV_PER_S) return false
        return true
    }

    /** Фильтр выбросов тока: |I| > 5 А — мусор датчика. */
    fun isCurrentPlausible(currentMa: Float): Boolean =
        kotlin.math.abs(currentMa) <= MAX_PLAUSIBLE_CURRENT_MA
}
