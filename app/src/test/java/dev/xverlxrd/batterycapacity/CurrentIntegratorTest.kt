package dev.xverlxrd.batterycapacity

import dev.xverlxrd.batterycapacity.domain.estimator.CurrentIntegrator
import dev.xverlxrd.batterycapacity.domain.filter.MedianNoiseFilter
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CurrentIntegratorTest {

    private val integrator = CurrentIntegrator()

    /**
     * Идеальный разряд: постоянный ток 500 мА, SOC 100→50 за 5 часов.
     * Q = 0.5 А * 5 ч = 2500 мА·ч; C = 2500 / 0.5 = 5000 мА·ч.
     */
    @Test
    fun idealConstantDischarge() {
        val samples = buildList {
            for (soc in 100 downTo 50 step 5) {
                add(snapshot(soc.toFloat(), currentMa = -500f, tMs = (100 - soc) * 360_000L))
            }
        }
        val result = integrator.integrate(samples)

        assertNotNull(result)
        assertEquals(5000.0, result!!.capacityMah, 25.0) // допуск ~0.5% на трапеции
        assertEquals(50f, result.deltaSocPct)
    }

    /**
     * Линейно растущий ток (трапеции обязаны дать точный интеграл):
     * I от 100 до 300 мА линейно, среднее 200 мА, ровно 4 часа (40 интервалов
     * по 0.1 ч) → Q = 800 мА·ч, ΔSOC = 20% → C = 4000 мА·ч.
     */
    @Test
    fun linearRamp_trapezoidExact() {
        val n = 41
        val samples = List(n) { i ->
            val fraction = i / (n - 1.0)
            val current = 100.0 + 200.0 * fraction
            BatterySnapshot(
                timestampMs = (i * 360_000L),
                socPercent = (100f - 20f * fraction).toFloat(),
                voltageMv = 3900,
                currentMa = -current.toFloat(),
                chargeCounterUah = null,
                temperatureC = 25.0,
                status = ChargeStatus.DISCHARGING,
            )
        }
        val result = integrator.integrate(samples)
        assertNotNull(result)
        assertEquals(4000.0, result!!.capacityMah, 15.0)
    }

    /**
     * Зашумлённый сигнал с выбросами ±8 А (датчик глючит) и нулями в простое:
     * медиана должна вычистить выбросы, оценка остаётся близкой к эталону.
     * Эталон: 500 мА × 2.95 ч ≈ 1475 мА·ч, ΔSOC = 29.5% → C = 5000 мА·ч.
     */
    @Test
    fun noisySignalWithSpikes_medianFilterRescues() {
        val rng = Random(42)
        val n = 60
        var spikes = 0
        val samples = List(n) { i ->
            val fraction = i / (n - 1.0)
            // База 500 мА + шум ±40 мА; каждые 7-й отсчёт — всплеск 8000 мА.
            val baseCurrent = 500.0 + rng.nextDouble(-40.0, 40.0)
            val current = if (i % 7 == 0 && i != 0 && i != n - 1) {
                spikes++
                8000.0
            } else {
                baseCurrent
            }
            BatterySnapshot(
                timestampMs = (i * 180_000L), // шаг 3 минуты
                socPercent = (100f - 29.5f * fraction).toFloat(),
                voltageMv = 3900,
                currentMa = -current.toFloat(),
                chargeCounterUah = null,
                temperatureC = 25.0,
                status = ChargeStatus.DISCHARGING,
            )
        }
        val result = integrator.integrate(samples)
        assertNotNull(result)
        assertTrue("Выбросы должны быть отброшены", result!!.rejectedSpikes > 0)
        // Эталон 5000 мА·ч; шум после медианы даёт единицы процентов погрешности.
        assertTrue("Оценка ${result.capacityMah} далеко от 5000", abs(result.capacityMah - 5000.0) < 350.0)
    }

    /** Пропуск отсчётов (сон устройства): интеграл по времени между соседями корректен. */
    @Test
    fun missingSamples_integrateAcrossGap() {
        val samples = listOf(
            snapshot(100f, -500f, 0),
            snapshot(95f, -500f, 1_800_000),
            // разрыв 2 часа (глубокий сон): SOC упал с 95 до 75
            snapshot(75f, -500f, 9_000_000),
            snapshot(70f, -500f, 10_800_000),
        )
        val result = integrator.integrate(samples)
        assertNotNull(result)
        // ΔQ = 30% * 500 мА / 100% = 1500 мА·ч за 3 ч → C = 5000 мА·ч.
        assertEquals(5000.0, result!!.capacityMah, 30.0)
    }

    /** Узкий диапазон — отказ, как у метода A. */
    @Test
    fun narrowSpan_rejected() {
        val samples = listOf(
            snapshot(50f, -500f, 0),
            snapshot(49f, -500f, 600_000),
        )
        assertNull(integrator.integrate(samples))
    }

    /** Знак тока нормализуется: положительные значения при статусе DISCHARGING тоже считаются. */
    @Test
    fun signConvention_positiveDuringDischarge_treatedAsDischargeMagnitude() {
        val samples = listOf(
            snapshot(80f, +400f, 0, ChargeStatus.DISCHARGING),
            snapshot(60f, +400f, 7_200_000),
        )
        val result = integrator.integrate(samples)
        assertNotNull(result)
        // |I|=400 мА, 2 ч, ΔSOC=20% → 800/0.2 = 4000 мА·ч.
        assertEquals(4000.0, result!!.capacityMah, 20.0)
    }

    private fun snapshot(
        soc: Float,
        currentMa: Float,
        tMs: Long,
        status: ChargeStatus = ChargeStatus.DISCHARGING,
    ) = BatterySnapshot(
        timestampMs = tMs,
        socPercent = soc,
        voltageMv = 3900,
        currentMa = currentMa,
        chargeCounterUah = null,
        temperatureC = 25.0,
        status = status,
    )
}

/** Медианный фильтр на известном входе/выходе. */
class MedianNoiseFilterTest {

    @Test
    fun singleImpulse_isSuppressed() {
        val input = listOf(100f, 100f, 9000f, 100f, 100f)
        val output = MedianNoiseFilter.smooth(input, window = 3)
        // Импульс заменяется соседним значением.
        assertEquals(listOf(100f, 100f, 100f, 100f, 100f), output)
    }

    @Test
    fun constantSignal_unchanged() {
        val input = List(10) { 420f }
        assertEquals(input, MedianNoiseFilter.smooth(input, 5))
    }

    @Test
    fun ramp_frontsArePreserved() {
        val input = listOf(10f, 10f, 10f, 50f, 50f, 50f, 90f, 90f, 90f)
        val output = MedianNoiseFilter.smooth(input, 3)
        // Фронт не размывается медианой (в отличие от скользящего среднего).
        assertEquals(10f, output[2])
        assertEquals(50f, output[4])
        assertEquals(90f, output[8])
    }

    @Test
    fun evenWindow_averageOfTwoMiddle() {
        val input = listOf(1f, 2f, 3f, 100f, 5f)
        val output = MedianNoiseFilter.smooth(input, window = 4)
        // Окно [1,2,3,100]: медиана чётного окна = (2+3)/2 = 2.5.
        assertEquals(2.5f, output[1])
    }

    @Test
    fun nanValues_passThroughAndSkippedByNeighborhood() {
        val input = listOf(Float.NaN, 5f, Float.NaN)
        val output = MedianNoiseFilter.smooth(input, 3)
        assertEquals(Float.NaN, output[0])
        assertEquals(5f, output[1]) // окрестность из одного валидного значения
        assertEquals(Float.NaN, output[2])
    }
}
