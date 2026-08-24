package dev.xverlxrd.batterycapacity

import dev.xverlxrd.batterycapacity.domain.estimator.CoulombCounter
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoulombCounterTest {

    private val counter = CoulombCounter()

    /** Идеальный разряд 5000 мА·ч батареи со 100% до 15%. */
    @Test
    fun idealDischarge_fullSpan() {
        // Полный счётчик 5 000 000 мкА·ч; при 15% осталось 750 000 мкА·ч.
        val samples = listOf(
            snapshot(soc = 100f, counterUah = 5_000_000),
            snapshot(soc = 60f, counterUah = 3_000_000),
            snapshot(soc = 15f, counterUah = 750_000),
        )
        val result = counter.estimate(samples)

        assertNotNull(result)
        // C = ΔQ/ΔSOC = 4250 мА·ч / 0.85 = 5000 мА·ч.
        assertEquals(5000.0, result!!.capacityMah, 1.0)
        assertEquals(4250.0, result.deltaQMah, 1.0)
        assertEquals(85f, result.deltaSocPct)
    }

    /** Пропуски отсчётов не влияют: важны только крайние валидные точки. */
    @Test
    fun gapsBetweenSamples_areTolerated() {
        val samples = listOf(
            snapshot(soc = 90f, counterUah = 4_500_000, tMs = 0),
            // пропущено ~2 часа опроса (устройство спало)
            snapshot(soc = 40f, counterUah = 2_000_000, tMs = 7_200_000),
        )
        val result = counter.estimate(samples)
        assertNotNull(result)
        assertEquals(5000.0, result!!.capacityMah, 1.0)
    }

    /** Диапазон меньше 10% отклоняется: тонет в дискретности SOC. */
    @Test
    fun narrowSpan_rejected() {
        val samples = listOf(
            snapshot(soc = 50f, counterUah = 2_500_000),
            snapshot(soc = 47f, counterUah = 2_350_000),
        )
        assertNull(counter.estimate(samples))
    }

    /** OEM отдаёт нулевой счётчик — метод A недоступен. */
    @Test
    fun zeroCounter_returnsNull() {
        val samples = listOf(
            snapshot(soc = 100f, counterUah = 0L),
            snapshot(soc = 20f, counterUah = 0L),
        )
        assertNull(counter.estimate(samples))
    }

    /** Температура за пределами [0..45]°C отбрасывает оценку целиком. */
    @Test
    fun outOfRangeTemperature_rejected() {
        val samples = listOf(
            snapshot(soc = 100f, counterUah = 5_000_000, tempC = -3.0),
            snapshot(soc = 20f, counterUah = 1_000_000, tempC = -3.0),
        )
        assertNull(counter.estimate(samples))
    }

    /** Физически невозможная ёмкость (>20000 мА·ч) — мусорные данные. */
    @Test
    fun implausibleCapacity_rejected() {
        val samples = listOf(
            snapshot(soc = 100f, counterUah = 999_999_999),
            snapshot(soc = 99f, counterUah = 900_000_000),
        )
        assertNull(counter.estimate(samples))
    }

    private fun snapshot(
        soc: Float,
        counterUah: Long,
        tMs: Long = 0,
        tempC: Double = 25.0,
    ) = BatterySnapshot(
        timestampMs = tMs,
        socPercent = soc,
        voltageMv = 3900,
        currentMa = null,
        chargeCounterUah = counterUah,
        temperatureC = tempC,
        status = ChargeStatus.DISCHARGING,
    )
}
