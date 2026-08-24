package dev.xverlxrd.batterycapacity.domain.estimator

import dev.xverlxrd.batterycapacity.domain.filter.MedianNoiseFilter
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.MeasurementMethod
import dev.xverlxrd.batterycapacity.domain.model.PowerSource
import dev.xverlxrd.batterycapacity.domain.policy.SampleValidityPolicy

/**
 * Метод B (fallback). Интегрирование тока между известными точками SOC.
 *
 * Физика: заряд Q — интеграл тока по времени. Для пары соседних отсчётов
 * применяется формула трапеций:
 *
 *     ΔQ [мА·ч] = (I_i + I_{i+1}) / 2 · Δt [часы]
 *
 * Ток читается из BATTERY_PROPERTY_CURRENT_NOW в мкА → переводим /1000 в мА.
 * Знак нормализуем к физическому смыслу: при разряде берём модуль снятого
 * заряда, при заряде — модуль принятого. Ёмкость:
 *
 *     C [мА·ч] = ΣΔQ / ΔSOC [доля]
 *
 * Внутреннее сопротивление батареи (типично 30–150 мОм) даёт падение
 * V = I·R на датчике тока, но не влияет на интеграл заряда — метод устойчив,
 * пока fuel gauge честно репортит ток. Шум давится медианным фильтром
 * (окно 3–5; при беспроводной зарядке окно расширяется — там ток шумный).
 */
class CurrentIntegrator(
    private val filterWindow: Int = DEFAULT_FILTER_WINDOW,
) {

    data class Result(
        val capacityMah: Double,
        val integratedQMah: Double,
        val deltaSocPct: Float,
        val sampleCount: Int,
        val rejectedSpikes: Int,
    )

    /**
     * @param samples хронологические снимки одной фазы (разряд ИЛИ заряд).
     * @param chargingSinceMs момент подключения зарядки для grace-периода быстрой зарядки.
     */
    fun integrate(
        samples: List<BatterySnapshot>,
        chargingSinceMs: Long? = null,
        isWirelessCharging: Boolean = false,
    ): Result? {
        // 1. Отсекаем сэмплы без SOC/тока и невалидные переходы (температура, скачки V).
        val window = if (isWirelessCharging) filterWindow.coerceAtLeast(WIRELESS_FILTER_WINDOW) else filterWindow
        val prepared = samples
            .filter { it.socPercent != null && it.currentMa != null }
            .filterIndexed { i, cur ->
                val prev = samples.getOrNull(i - 1) ?: return@filterIndexed true
                SampleValidityPolicy.isTransitionValid(prev, cur, chargingSinceMs)
            }
        if (prepared.size < 2) return null

        // 2. Медианный фильтр по модулю тока — глушит импульсные выбросы (>5 А уже отброшены).
        var spikes = 0
        val currents = prepared.map { s ->
            val raw = kotlin.math.abs(s.currentMa!!)
            if (!SampleValidityPolicy.isCurrentPlausible(raw)) {
                spikes++
                Float.NaN
            } else {
                raw
            }
        }
        val smoothed = MedianNoiseFilter.smooth(currents, window)
        // Отброшенные выбросы не «выкидывают» время из интеграла: пробелы
        // заполняются линейной интерполяцией между соседними валидными точками.
        val filled = interpolateNans(smoothed)

        // 3. Трапеции по валидным точкам.
        var totalQMah = 0.0
        for (i in 0 until prepared.lastIndex) {
            val a = filled[i]
            val b = filled[i + 1]
            if (a.isNaN() || b.isNaN()) continue
            val dtHours = (prepared[i + 1].timestampMs - prepared[i].timestampMs) / 3_600_000.0
            if (dtHours <= 0) continue
            totalQMah += (a + b) / 2.0 * dtHours
        }

        val start = prepared.first()
        val end = prepared.last()
        val deltaSocPct = start.socPercent!! - end.socPercent!!
        if (kotlin.math.abs(deltaSocPct) < CoulombCounter.MIN_SPAN_PCT || totalQMah <= 0) return null

        val capacity = totalQMah / (deltaSocPct / 100f)
        if (capacity > CoulombCounter.IMPLAUSIBLE_CAPACITY_MAH) return null

        return Result(
            capacityMah = capacity,
            integratedQMah = totalQMah,
            deltaSocPct = deltaSocPct,
            sampleCount = prepared.size,
            rejectedSpikes = spikes,
        )
    }

    /**
     * Заполняет NaN-последовательности линейной интерполяцией между крайними
     * валидными соседями; на краях списка — константное продолжение.
     */
    private fun interpolateNans(values: List<Float>): List<Float> {
        if (values.none { it.isNaN() }) return values
        val out = values.toMutableList()
        var i = 0
        while (i < out.size) {
            if (!out[i].isNaN()) {
                i++
                continue
            }
            val runStart = i
            while (i < out.size && out[i].isNaN()) i++
            val left = out.getOrNull(runStart - 1)?.takeUnless { it.isNaN() }
            val right = out.getOrNull(i)?.takeUnless { it.isNaN() }
            when {
                left != null && right != null -> {
                    val span = i - runStart + 1
                    for (k in runStart until i) {
                        val frac = (k - runStart + 1).toFloat() / span
                        out[k] = left + (right - left) * frac
                    }
                }
                left != null -> for (k in runStart until i) out[k] = left
                right != null -> for (k in runStart until i) out[k] = right
            }
        }
        return out
    }

    companion object {
        val METHOD = MeasurementMethod.B_CURRENT_INTEGRATION
        const val DEFAULT_FILTER_WINDOW = 5
        const val WIRELESS_FILTER_WINDOW = 9
        const val MAX_PLAUSIBLE_CURRENT_MA = SampleValidityPolicy.MAX_PLAUSIBLE_CURRENT_MA
    }
}
