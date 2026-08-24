package dev.xverlxrd.batterycapacity.domain.filter

/**
 * Медианный фильтр (скользящее окно 3–9 отсчётов).
 *
 * Выбран именно медианный, а не скользящее среднее: он полностью подавляет
 * одиночные импульсные выбросы (глитчи датчика тока), не «размазывая» их,
 * и сохраняет фронты сигнала. Краевые окна усечённые.
 */
object MedianNoiseFilter {

    /** Синхронная обработка массива. NaN-значения пропускаются как есть. */
    fun smooth(samples: List<Float>, window: Int): List<Float> {
        if (window < 3 || samples.size < 2) return samples
        val half = window / 2
        return samples.indices.map { i ->
            val v = samples[i]
            if (v.isNaN()) {
                Float.NaN
            } else {
                val from = (i - half).coerceAtLeast(0)
                val toExclusive = (i + half + 1).coerceAtMost(samples.size)
                val neighborhood = samples.subList(from, toExclusive).filter { !it.isNaN() }
                if (neighborhood.isEmpty()) v else median(neighborhood)
            }
        }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            // Чётное окно: среднее двух центральных элементов.
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }
}
