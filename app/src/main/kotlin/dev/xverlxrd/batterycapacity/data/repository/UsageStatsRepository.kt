package dev.xverlxrd.batterycapacity.data.repository

import dev.xverlxrd.batterycapacity.data.local.dao.UsageStatsDao
import dev.xverlxrd.batterycapacity.data.local.entity.UsageDailyStatEntity
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Долговременная статистика использования батареи: воркер каждые ~6 часов
 * вливает свежий снимок в строку текущего дня. Через пару недель набегает
 * фактическая картина: суточные разряды, температура, износ (циклы).
 */
@Singleton
class UsageStatsRepository @Inject constructor(
    private val dao: UsageStatsDao,
) {

    suspend fun mergeSample(snapshot: BatterySnapshot, nowMs: Long = System.currentTimeMillis()) {
        val today = LocalDate.now().toEpochDay()
        val soc = snapshot.socPercent
        val existing = dao.observeSince(today).first().lastOrNull { it.dateEpochDay == today }

        val merged = UsageDailyStatEntity(
            dateEpochDay = today,
            sampleCount = (existing?.sampleCount ?: 0) + 1,
            socSum = (existing?.socSum ?: 0.0) + (soc?.toDouble() ?: 0.0),
            socMin = listOfNotNull(existing?.socMin, soc).minOrNull(),
            socMax = listOfNotNull(existing?.socMax, soc).maxOrNull(),
            tempMaxC = listOfNotNull(existing?.tempMaxC, snapshot.temperatureC).maxOrNull(),
            cycleCountLast = snapshot.cycleCount ?: existing?.cycleCountLast,
            voltageMinMv = listOfNotNull(existing?.voltageMinMv, snapshot.voltageMv).minOrNull(),
            updatedAtMs = nowMs,
        )
        dao.upsert(merged)
    }

    fun observeSinceDays(days: Int): Flow<List<UsageDailyStatEntity>> =
        dao.observeSince(LocalDate.now().minusDays(days.toLong()).toEpochDay())

    suspend fun clear() = dao.clear()
}
