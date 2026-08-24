package dev.xverlxrd.batterycapacity.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.xverlxrd.batterycapacity.ui.screens.calibration.CalibrationWizardScreen
import dev.xverlxrd.batterycapacity.ui.screens.dashboard.DashboardScreen
import dev.xverlxrd.batterycapacity.ui.screens.history.HistoryScreen
import dev.xverlxrd.batterycapacity.ui.screens.live.LiveMonitorScreen
import dev.xverlxrd.batterycapacity.ui.screens.settings.SettingsScreen
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Обзор", Icons.Outlined.Battery5Bar),
    Live("live", "Сейчас", Icons.Outlined.MonitorHeart),
    Calibration("calibration", "Тест", Icons.Outlined.Bolt),
    History("history", "История", Icons.Filled.History),
    Settings("settings", "Настройки", Icons.Filled.Settings),
}

/** Каркас приложения: нижняя навигация из пяти экранов. */
@Composable
fun BatteryEstimatorApp() {
    val navController = rememberNavController()
    val reducedMotion = LocalReducedMotion.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Плавающий округлый док: отступы от краёв, тень, скругление 32.
            val dockShape = RoundedCornerShape(32.dp)
            NavigationBar(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .shadow(elevation = 8.dp, shape = dockShape)
                    .clip(dockShape),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                val entry by navController.currentBackStackEntryAsState()
                val currentDestination = entry?.destination
                Destination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    // Стиль iOS tab bar: выбор подсвечивает только цвет, без пилюли.
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(dest.route) {
                                    popUpTo(Destination.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.secondary,
                            unselectedTextColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding),
            enterTransition = {
                if (reducedMotion) fadeIn(tween(1)) else fadeIn(tween(MotionTokens.DURATION_STANDARD)) +
                    scaleIn(initialScale = 0.95f, animationSpec = tween(MotionTokens.DURATION_STANDARD, easing = MotionTokens.EaseOut))
            },
            exitTransition = { fadeOut(tween(if (reducedMotion) 1 else MotionTokens.DURATION_QUICK)) },
            popEnterTransition = { fadeIn(tween(if (reducedMotion) 1 else MotionTokens.DURATION_STANDARD)) },
            popExitTransition = { fadeOut(tween(if (reducedMotion) 1 else MotionTokens.DURATION_QUICK)) },
        ) {
            composable(Destination.Dashboard.route) { DashboardScreen() }
            composable(Destination.Live.route) { LiveMonitorScreen() }
            composable(Destination.Calibration.route) { CalibrationWizardScreen() }
            composable(Destination.History.route) { HistoryScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
