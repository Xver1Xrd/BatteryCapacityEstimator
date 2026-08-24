package dev.xverlxrd.batterycapacity.domain.estimator

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.MeasurementMethod
import dev.xverlxrd.batterycapacity.domain.policy.SampleValidityPolicy
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Доверительный интервал оценки ёмкости ±X мА·ч.
 *
 * Источники ошибки (считаем независимыми, складываем дисперсии):
 *
 * 1. Дискретность SOC-отчётов. ОС репортит SOC шагом в 1% (иногда 5%),
 *    поэтому границы диапазона известны с ошибкой ±0.5% каждая:
 *       σ(ΔSOC) ≈ sqrt(2) · 0.5% / ΔSOC
 *    Относительная ошибка ёмкости равна относительной ошибке знаменателя.
 *
 * 2. Шум интегрируемого тока. Для метода B остаточный шум после медианы
 *    σ_I [мА] за время T [ч] даёт σ(Q) = σ_I · T; относим к Q.
 *
 * Итог: X = C · sqrt( (sqrt(2)/2 / ΔSOC_pct)² + (σ_I · T / Q)² ).
 */
object ConfidenceCalculator {

    const val SOC_QUANTIZATION_PCT = 1f

    fun confidenceMah(
        capacityMah: Double,
        deltaSocPct: Float,
        samples: List<BatterySnapshot>,
        method: MeasurementMethod,
    ): Double {
        // Член 1: ошибка границ диапазона из-за квантования SOC.
        val socErrorRel = (sqrt(2.0) * (SOC_QUANTIZATION_PCT / 2f)) / deltaSocPct.coerceAtLeast(1f)

        var noiseRel = 0.0
        if (method == MeasurementMethod.B_CURRENT_INTEGRATION) {
            val currents = samples.mapNotNull { s ->
                    s.currentMa?.let { abs(it).toDouble() }
                }.filter { SampleValidityPolicy.isCurrentPlausible(it.toFloat()) }
            if (currents.size >= 3) {
                val sigmaMa = stdDev(currents)
                val totalHours = (samples.last().timestampMs - samples.first().timestampMs) / 3_600_000.0
                val meanMa = currents.average().takeIf { it > 0 } ?: return capacityMah * socErrorRel
                noiseRel = sigmaMa * totalHours / (meanMa * totalHours)
            }
        }

        val relative = sqrt(socErrorRel * socErrorRel + noiseRel * noiseRel).coerceIn(0.02, 0.35)
        return capacityMah * relative
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }
}
