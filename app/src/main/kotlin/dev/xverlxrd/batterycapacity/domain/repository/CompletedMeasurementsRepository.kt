package dev.xverlxrd.batterycapacity.domain.repository

import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.domain.model.DesignCapacity
import kotlinx.coroutines.flow.Flow

/** История завершённых измерений — по ней строится график деградации. */
interface CompletedMeasurementsRepository {
    fun observeHistory(): Flow<List<CapacityEstimate>>
    suspend fun save(estimate: CapacityEstimate)
    suspend fun delete(estimate: CapacityEstimate)
    suspend fun clearAll()
}

interface UserSettingsRepository {
    val preferences: Flow<UserPreferencesSnapshot>
    suspend fun setPollingIntervalSeconds(seconds: Int)
    suspend fun setManualDesignCapacityMah(mah: Long?)
    suspend fun setFilterWindowSize(size: Int)
    suspend fun setUseDarkTheme(dark: Boolean?)
    suspend fun setAnimationsEnabled(enabled: Boolean)
    suspend fun setUseDynamicColor(enabled: Boolean)

    data class UserPreferencesSnapshot(
        val pollingIntervalSeconds: Int = 30,
        val manualDesignCapacityMah: Long? = null,
        val filterWindowSize: Int = 5,
        val useDarkTheme: Boolean? = null,
        val animationsEnabled: Boolean = true,
        val useDynamicColor: Boolean = false,
    ) {
        fun toDesign(): DesignCapacity = manualDesignCapacityMah
            ?.let { DesignCapacity(it.toDouble(), dev.xverlxrd.batterycapacity.domain.model.DesignCapacitySource.MANUAL) }
            ?: DesignCapacity(null, dev.xverlxrd.batterycapacity.domain.model.DesignCapacitySource.UNKNOWN)
    }
}
