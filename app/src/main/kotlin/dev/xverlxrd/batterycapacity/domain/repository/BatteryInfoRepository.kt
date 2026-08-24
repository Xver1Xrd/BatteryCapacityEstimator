package dev.xverlxrd.batterycapacity.domain.repository

import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import kotlinx.coroutines.flow.Flow

/** Возможности источников на данном устройстве — определяются один раз при старте. */
data class SourceCapabilities(
    val hasChargeCounter: Boolean,
    val hasCurrentNow: Boolean,
    val hasSysfsAccess: Boolean,
    val batteryNodesFound: List<String>,
)

interface BatteryInfoRepository {
    /** Горячий поток снимков с заданным интервалом опроса. */
    fun observeSnapshots(intervalMs: Long): Flow<BatterySnapshot>

    suspend fun readNow(): BatterySnapshot

    fun observeCapabilities(): Flow<SourceCapabilities>


    /** Доступ к последнему снимку для сервисов без подписки. */
    val latestSnapshot: BatterySnapshot?

    /** Для таблицы паспортных ёмкостей и диагностики. */
    val deviceManufacturer: String
    val deviceModel: String
}
