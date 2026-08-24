package dev.xverlxrd.batterycapacity.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserSettingsRepository {

    private object Keys {
        val POLLING_SEC = intPreferencesKey("polling_interval_seconds")
        val MANUAL_DESIGN_MAH = longPreferencesKey("manual_design_capacity_mah")
        val FILTER_WINDOW = intPreferencesKey("filter_window_size")
        val DARK_THEME = intPreferencesKey("dark_theme") // -1 system, 0 light, 1 dark
    }

    override val preferences: Flow<UserSettingsRepository.UserPreferencesSnapshot> =
        context.settingsStore.data.map { p ->
            UserSettingsRepository.UserPreferencesSnapshot(
                pollingIntervalSeconds = (p[Keys.POLLING_SEC] ?: DEFAULT_POLLING_SECONDS).coerceIn(30, 300),
                manualDesignCapacityMah = p[Keys.MANUAL_DESIGN_MAH]?.takeIf { it > 0 },
                filterWindowSize = (p[Keys.FILTER_WINDOW] ?: 5).coerceIn(3, 9),
                useDarkTheme = when (p[Keys.DARK_THEME]) {
                    0 -> false
                    1 -> true
                    else -> null
                },
            )
        }

    override suspend fun setPollingIntervalSeconds(seconds: Int) {
        context.settingsStore.edit { it[Keys.POLLING_SEC] = seconds.coerceIn(30, 300) }
    }

    override suspend fun setManualDesignCapacityMah(mah: Long?) {
        context.settingsStore.edit { prefs ->
            if (mah == null || mah <= 0) prefs.remove(Keys.MANUAL_DESIGN_MAH) else prefs[Keys.MANUAL_DESIGN_MAH] = mah
        }
    }

    override suspend fun setFilterWindowSize(size: Int) {
        context.settingsStore.edit { it[Keys.FILTER_WINDOW] = size.coerceIn(3, 9) }
    }

    override suspend fun setUseDarkTheme(dark: Boolean?) {
        context.settingsStore.edit { prefs ->
            if (dark == null) prefs.remove(Keys.DARK_THEME) else prefs[Keys.DARK_THEME] = if (dark) 1 else 0
        }
    }

    companion object {
        const val DEFAULT_POLLING_SECONDS = 30
    }
}
