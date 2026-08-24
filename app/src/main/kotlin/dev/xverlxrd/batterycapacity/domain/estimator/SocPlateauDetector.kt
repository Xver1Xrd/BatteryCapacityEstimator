package dev.xverlxrd.batterycapacity.domain.estimator

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.policy.SampleValidityPolicy

/**
 * Детектор «застрявшего» SOC. Если при ненулевом токе процент не менялся
 * дольше PLATEAU_MINUTES минут — калибровочный дрейф газгейджа: ОС думает,
 * что заряд не меняется, хотя счётчик заряда течёт. Лечится полным циклом
 * разряд→заряд без отключения (перекалибровка гейджа).
 */
class SocPlateauDetector(private val plateauMs: Long = SampleValidityPolicy.PLATEAU_MINUTES * 60_000) {

    data class Plateau(val sinceMs: Long, val lastSoc: Float)

    private var anchorSoc: Float? = null
    private var anchorTimeMs: Long = 0L
    private var activePlateau: Plateau? = null

    fun onSample(sample: BatterySnapshot): Plateau? {
        val soc = sample.socPercent ?: return activePlateau.also { resetIfStale(sample.timestampMs) }
        val now = sample.timestampMs

        val anchor = anchorSoc
        if (anchor == null) {
            anchorSoc = soc
            anchorTimeMs = now
            return null
        }

        if (kotlin.math.abs(soc - anchor) >= 1f) {
            // SOC сдвинулся — плато кончилось.
            anchorSoc = soc
            anchorTimeMs = now
            return null
        }

        return if (now - anchorTimeMs >= plateauMs) {
            val p = Plateau(sinceMs = anchorTimeMs, lastSoc = soc)
            activePlateau = p
            p
        } else {
            null
        }
    }

    private fun resetIfStale(now: Long) {
        if (now - anchorTimeMs >= plateauMs && anchorSoc != null) {
            activePlateau = activePlateau
        }
    }

    fun reset() {
        anchorSoc = null
        activePlateau = null
    }
}
