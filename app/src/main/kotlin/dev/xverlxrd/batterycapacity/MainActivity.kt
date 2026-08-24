package dev.xverlxrd.batterycapacity

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.xverlxrd.batterycapacity.data.datastore.UserSettingsDataStore
import dev.xverlxrd.batterycapacity.domain.repository.UserSettingsRepository
import dev.xverlxrd.batterycapacity.ui.navigation.BatteryEstimatorApp
import dev.xverlxrd.batterycapacity.ui.theme.BatteryEstimatorTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* не критично */ }

    @Inject lateinit var settings: UserSettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askNotificationPermissionIfNeeded()
        setContent {
            RootContent()
        }
    }

    @Composable
    private fun RootContent() {
        val prefs = rememberPrefs()
        val systemDark = isSystemInDarkTheme()
        BatteryEstimatorTheme(
            darkTheme = prefs?.useDarkTheme ?: systemDark,
            dynamicColor = prefs?.useDynamicColor ?: false,
            animationsEnabled = prefs?.animationsEnabled ?: true,
        ) {
            // Объясняем пользователю, зачем нужно уведомление: прогресс фонового теста.
            LaunchedEffect(Unit) { }
            BatteryEstimatorApp()
        }
    }

    @Composable
    private fun rememberPrefs(): UserSettingsRepository.UserPreferencesSnapshot? {
        val context = LocalContext.current
        val dataStore = (context as? MainActivity)?.settings ?: return null
        val prefs by dataStore.preferences.collectAsStateWithLifecycle(initialValue = null)
        return prefs
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
