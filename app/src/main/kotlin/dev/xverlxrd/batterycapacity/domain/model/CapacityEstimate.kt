package dev.xverlxrd.batterycapacity.domain.model

/** Каким методом получена оценка ёмкости. */
enum class MeasurementMethod(val displayName: String, val accuracyNote: String) {
    /** ΔQ из fuel-gauge счётчика / ΔSOC — самый точный программный метод. */
    A_COULOMB_COUNTER("Счётчик заряда", "Высокая точность"),

    /** Численное интегрирование тока (трапеции) между точками SOC — fallback. */
    B_CURRENT_INTEGRATION("Интегрирование тока", "Средняя точность"),

    /** charge_full / charge_full_design напрямую из sysfs/dumpsys. */
    C_SYSTEM_REPORTED("Отчёт системы", "Точность зависит от OEM"),
}

/**
 * Вердикт по проценту здоровья (фактическая/паспортная):
 *  >85% отлично; 70–85% норма; 50–70% износ; <50% замена.
 */
enum class HealthVerdict(val title: String) {
    EXCELLENT("Отлично"),
    GOOD("Норма"),
    WORN("Износ"),
    REPLACE("Рекомендуется замена");

    companion object {
        fun from(healthPercent: Double): HealthVerdict = when {
            healthPercent > 85.0 -> EXCELLENT
            healthPercent >= 70.0 -> GOOD
            healthPercent >= 50.0 -> WORN
            else -> REPLACE
        }
    }
}

/** Источник паспортной ёмкости. */
enum class DesignCapacitySource { SYSFS_DESIGN, DEVICE_TABLE, MANUAL, UNKNOWN }

data class DesignCapacity(val valueMah: Double?, val source: DesignCapacitySource)

/**
 * Итоговая оценка.
 * @param actualMah фактическая ёмкость, мА·ч
 * @param designMah паспортная ёмкость, мА·ч (null — пользователь не задал)
 * @param confidenceMah полуширина доверительного интервала ±X мА·ч
 * @param socSpanPct ширина диапазона SOC, по которому считали (чем шире — тем точнее)
 */
data class CapacityEstimate(
    val actualMah: Double,
    val designMah: Double?,
    val method: MeasurementMethod,
    val confidenceMah: Double,
    val socSpanPct: Float,
    val sampleCount: Int,
    val measuredAtMs: Long,
    val warnings: List<String> = emptyList(),
) {
    val healthPercent: Double?
        get() = designMah?.takeIf { it > 0 }?.let { actualMah / it * 100.0 }

    val verdict: HealthVerdict?
        get() = healthPercent?.let(HealthVerdict::from)
}
