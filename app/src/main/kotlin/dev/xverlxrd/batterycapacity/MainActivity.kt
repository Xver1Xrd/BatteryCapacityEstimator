package dev.xverlxrd.batterycapacity

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.xverlxrd.batterycapacity.ui.navigation.BatteryEstimatorApp
import dev.xverlxrd.batterycapacity.ui.theme.BatteryEstimatorTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* не критично */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askNotificationPermissionIfNeeded()
        setContent {
            BatteryEstimatorTheme {
                // Объясняем пользователю, зачем нужно уведомление: прогресс фонового теста.
                LaunchedEffect(Unit) { }
                BatteryEstimatorApp()
            }
        }
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
