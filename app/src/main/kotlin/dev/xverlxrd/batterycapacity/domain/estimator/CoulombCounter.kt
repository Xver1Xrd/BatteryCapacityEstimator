package dev.xverlxrd.batterycapacity.domain.estimator

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.MeasurementMethod
import dev.xverlxrd.batterycapacity.domain.policy.SampleValidityPolicy

/**
 * Метод A. Coulomb counting по топливомерному счётчику ОС.
 *
 * Физика: BATTERY_PROPERTY_CHARGE_COUNTER — накопленный счётчик заряда в мкА·ч,
 * который сам fuel gauge ведёт интегрированием тока. Ёмкость батареи C равна
 * количеству электричества, снятому с полного до известного уровня SOC:
 *
 *     C [мА·ч] = ΔQ [мА·ч] / ΔSOC [доля]
 *
 * где ΔQ = |CC_start − CC_end| / 1000 (перевод мкА·ч → мА·ч),
 *     ΔSOC = (SOC_start − SOC_end) / 100 (проценты → доля).
 *
 * Пример: разряд со 100% до 15% снял 3200 мА·ч → C = 3200 / 0.85 ≈ 3765 мА·ч.
 *
 * Метод не требует знания номинала и не накапливает дрейф нуля, т.к.
 * опирается на абсолютные показания счётчика в двух точках.
 */
class CoulombCounter {

    data class Result(
        val capacityMah: Double,
        val deltaQMah: Double,
        val deltaSocPct: Float,
        val sampleCount: Int,
    )

    /**
     * @param samples хронологический список снимков одной фазы
     * (только разряд или только заряд), уже прошедший политику валидности.
     */
    fun estimate(samples: List<BatterySnapshot>): Result? {
        val valid = samples.filter { it.socPercent != null && it.chargeCounterUah != null && it.chargeCounterUah > 0 }
        // Нужно минимум 2 точки с заметной дельтой SOC, иначе ΔSOC ~ дискретности отчётов.
        if (valid.size < 2) return null

        val start = valid.first()
        val end = valid.last()
        val deltaSocPct = (start.socPercent!! - end.socPercent!!)
        if (kotlin.math.abs(deltaSocPct) < MIN_SPAN_PCT) return null

        // µА·ч → мА·ч: делим на 1000. Знак не важен — берём модуль перенесённого заряда.
        val deltaQUah = kotlin.math.abs(end.chargeCounterUah!! - start.chargeCounterUah!!)
        if (!SampleValidityPolicy.isTemperatureValid(start.temperatureC) ||
            !SampleValidityPolicy.isTemperatureValid(end.temperatureC)
        ) return null

        val deltaQMah = deltaQUah / 1000.0
        val capacity = deltaQMah / (deltaSocPct / 100f)
        if (capacity <= 0 || capacity > IMPLAUSIBLE_CAPACITY_MAH) return null

        return Result(
            capacityMah = capacity,
            deltaQMah = deltaQMah,
            deltaSocPct = deltaSocPct,
            sampleCount = valid.size,
        )
    }

    companion object {
        val METHOD = MeasurementMethod.A_COULOMB_COUNTER

        /** Ниже 10% диапазона оценка тонет в дискретности SOC-отчётов (±0.5%). */
        const val MIN_SPAN_PCT = 10f

        /** Разумный верх для телефона: 20000 мА·ч (планшеты/складные с двумя банками). */
        const val IMPLAUSIBLE_CAPACITY_MAH = 20_000.0
    }
}
