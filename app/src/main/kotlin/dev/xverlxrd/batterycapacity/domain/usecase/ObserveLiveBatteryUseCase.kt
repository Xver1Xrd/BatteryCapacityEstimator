package dev.xverlxrd.batterycapacity.domain.usecase

import dev.xverlxrd.batterycapacity.domain.estimator.SocPlateauDetector
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.LiveBatteryInfo
import dev.xverlxrd.batterycapacity.domain.repository.BatteryInfoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * Live-мониторинг «Сейчас»: поток снимков каждые ~2 c + производные метрики.
 *
 * Скорость разряда %/час = ΔSOC / Δt[ч] по скользящему окну WINDOW минут —
 * короткое окно живо реагирует на смену нагрузки, длинное точнее;
 * компромисс 10 минут. ETA до отключения = остаток SOC / |rate|.
 */
class ObserveLiveBatteryUseCase @Inject constructor(
    private val batteryRepository: BatteryInfoRepository,
) {
    operator fun invoke(intervalMs: Long = LIVE_INTERVAL_MS): Flow<LiveBatteryInfo> =
        batteryRepository.observeSnapshots(intervalMs).map { latest ->
            val history = ringBuffer.apply { add(latest) }
            val rate = computeRatePerHour(history)
            val eta = rate?.takeIf { it < -MIN_RATE_FOR_ETA }?.let {
                ((latest.socPercent ?: return@let null) / abs(it) * 60).toInt()
            }
            val plateau = plateauDetector.onSample(latest)
            LiveBatteryInfo(latest, rate, eta, plateau != null)
        }

    private fun computeRatePerHour(history: List<BatterySnapshot>): Double? {
        if (history.size < 2) return null
        val oldest = history.first()
        val newest = history.last()
        val socStart = oldest.socPercent ?: return null
        val socEnd = newest.socPercent ?: return null
        val dtHours = (newest.timestampMs - oldest.timestampMs) / 3_600_000.0
        if (dtHours < MIN_WINDOW_HOURS || newest.isCharging) return null
        // Отрицательное значение = разряд (SOC падает).
        return (socEnd - socStart) / dtHours
    }

    private companion object {
        const val LIVE_INTERVAL_MS = 2000L
        const val MIN_RATE_FOR_ETA = 1.0 // %/час — ниже этого ETA бессмысленен
        const val MIN_WINDOW_HOURS = 1 / 60.0 * 5 // минимум 5 минут наблюдения
        const val WINDOW_SIZE = 32 // ~10 минут при опросе раз в 20 c
    }

    private val ringBuffer = object : AbstractList<BatterySnapshot>() {
        private val deque = ArrayDeque<BatterySnapshot>(WINDOW_SIZE)
        override val size: Int get() = deque.size
        override fun get(index: Int): BatterySnapshot = deque.elementAt(index)

        fun add(s: BatterySnapshot) {
            if (deque.size == WINDOW_SIZE) deque.removeFirst()
            deque.addLast(s)
        }
    }

    private val plateauDetector = SocPlateauDetector()
}
