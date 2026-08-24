package dev.xverlxrd.batterycapacity.domain.estimator

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.domain.model.MeasurementMethod
import dev.xverlxrd.batterycapacity.domain.policy.SampleValidityPolicy
import kotlin.math.abs
import javax.inject.Inject

/**
 * Оркестратор оценки ёмкости. Приоритет методов:
 *
 *  C — системный отчёт: если fuel gauge экспортирует charge_full(_design)
 *      через sysfs/dumpsys, это прямое показание износа от контроллера
 *      батареи. Точность ограничена честностью OEM.
 *  A — coulomb counting по charge_counter: ΔQ/ΔSOC между двумя точками.
 *  B — интегрирование CURRENT_NOW трапециями, когда счётчика нет/нулевой.
 *
 * Фазы разряда и заряда считаются раздельно: смешивать нельзя, т.к. во время
 * заряда часть тока уходит не в банку (нагрев, CV-фаза), что занижает оценку.
 */
class CapacityEstimator @Inject constructor() {

    private val coulombCounter = CoulombCounter()
    private val integrator = CurrentIntegrator()

    fun estimate(samples: List<BatterySnapshot>, measuredAtMs: Long): CapacityEstimate? {
        if (samples.isEmpty()) return null
        val warnings = mutableListOf<String>()
        val last = samples.last()

        // --- Метод C: прямые показания контроллера ---
        if (last.hasSystemCapacityPair) {
            val full = (last.chargeFullMah ?: last.energyFullMah)!!
            val design = (last.chargeFullDesignMah ?: last.energyFullDesignMah)!!
            return buildEstimate(
                actualMah = full,
                method = MeasurementMethod.C_SYSTEM_REPORTED,
                confidenceMah = full * SYSTEM_REPORT_RELATIVE_UNCERTAINTY,
                socSpanPct = 100f,
                sampleCount = samples.size,
                measuredAtMs = measuredAtMs,
                warnings = warnings,
            )
        }

        // --- Разбивка на фазы по знаку тока/статусу зарядки ---
        val phases = splitIntoPhases(samples)

        // --- Метод A ---
        val bestCoulomb = phases.mapNotNull { phase ->
            coulombCounter.estimate(phase.samples)?.let { phase to it }
        }.maxByOrNull { (_, r) -> abs(r.deltaSocPct) }?.second

        if (bestCoulomb != null) {
            val spanPhases = phases.mapNotNull { p -> coulombCounter.estimate(p.samples)?.let { p to it } }
            val chosenPhase = spanPhases.first { it.second === bestCoulomb }.first
            if (phases.any { it.isCharging }) {
                warnings += "Учтена фаза заряда: точность ниже, чем при чистом разряде"
            }
            return buildEstimate(
                actualMah = bestCoulomb.capacityMah,
                method = MeasurementMethod.A_COULOMB_COUNTER,
                confidenceMah = ConfidenceCalculator.confidenceMah(
                    bestCoulomb.capacityMah, bestCoulomb.deltaSocPct, chosenPhase.samples,
                    MeasurementMethod.A_COULOMB_COUNTER,
                ),
                socSpanPct = abs(bestCoulomb.deltaSocPct),
                sampleCount = bestCoulomb.sampleCount,
                measuredAtMs = measuredAtMs,
                warnings = warnings,
            )
        }

        // --- Метод B ---
        val wireless = phases.any { it.plugged == dev.xverlxrd.batterycapacity.domain.model.PowerSource.WIRELESS }
        if (wireless) warnings += "Беспроводная зарядка: окно фильтра расширено из-за шума тока"
        val bestIntegrated = phases.mapNotNull { phase ->
            integrator.integrate(
                phase.samples,
                chargingSinceMs = phase.chargingSinceMs,
                isWirelessCharging = wireless,
            )?.let { phase to it }
        }.maxByOrNull { (_, r) -> abs(r.deltaSocPct) }

        val (phase, integrated) = bestIntegrated ?: run {
            if (samples.all { it.chargeCounterUah == null || it.chargeCounterUah == 0L }) {
                warnings += "Счётчик заряда скрыт OEM — использовано интегрирование тока, точность снижена"
            }
            return null
        }
        if (integrated.rejectedSpikes > 0) {
            warnings += "Отброшено выбросов тока: ${integrated.rejectedSpikes}"
        }
        return buildEstimate(
            actualMah = integrated.capacityMah,
            method = MeasurementMethod.B_CURRENT_INTEGRATION,
            confidenceMah = ConfidenceCalculator.confidenceMah(
                integrated.capacityMah, integrated.deltaSocPct, phase.samples,
                MeasurementMethod.B_CURRENT_INTEGRATION,
            ),
            socSpanPct = abs(integrated.deltaSocPct),
            sampleCount = integrated.sampleCount,
            measuredAtMs = measuredAtMs,
            warnings = warnings,
        )
    }

    private fun buildEstimate(
        actualMah: Double,
        method: MeasurementMethod,
        confidenceMah: Double,
        socSpanPct: Float,
        sampleCount: Int,
        measuredAtMs: Long,
        warnings: List<String>,
    ) = CapacityEstimate(
        actualMah = actualMah,
        designMah = null, // подставляется репозиторием из DesignCapacityResolver
        method = method,
        confidenceMah = confidenceMah,
        socSpanPct = socSpanPct,
        sampleCount = sampleCount,
        measuredAtMs = measuredAtMs,
        warnings = warnings,
    )

    /** Разбивает поток сэмплов на непрерывные фазы разряда/заряда. */
    private fun splitIntoPhases(samples: List<BatterySnapshot>): List<Phase> {
        val phases = mutableListOf<Phase>()
        var current = mutableListOf<BatterySnapshot>()
        var currentCharging = samples.firstOrNull()?.isCharging ?: false
        var chargingSinceMs: Long? = if (currentCharging) samples.first().timestampMs else null

        for (s in samples.drop(1)) {
            val charging = s.isCharging
            if (charging != currentCharging) {
                phases += Phase(current.toList(), currentCharging, chargingSinceMs)
                current = mutableListOf()
                currentCharging = charging
                chargingSinceMs = if (charging) s.timestampMs else null
            }
            current += s
        }
        if (current.isNotEmpty()) {
            phases += Phase(current.toList(), currentCharging, chargingSinceMs)
        }
        return phases.filter { SampleValidityPolicy.isTemperatureValid(it.samples.first().temperatureC) }
    }

    data class Phase(
        val samples: List<BatterySnapshot>,
        val isCharging: Boolean,
        val chargingSinceMs: Long?,
    ) {
        val plugged: dev.xverlxrd.batterycapacity.domain.model.PowerSource
            get() = samples.lastOrNull()?.plugged ?: dev.xverlxrd.batterycapacity.domain.model.PowerSource.NONE
    }

    companion object {
        /** Системные charge_full врут на 3–8% в зависимости от OEM — фиксируем это в интервале. */
        const val SYSTEM_REPORT_RELATIVE_UNCERTAINTY = 0.08
    }
}
