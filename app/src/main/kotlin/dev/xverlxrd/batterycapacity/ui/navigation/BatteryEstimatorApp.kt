package dev.xverlxrd.batterycapacity.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.xverlxrd.batterycapacity.ui.screens.calibration.CalibrationWizardScreen
import dev.xverlxrd.batterycapacity.ui.screens.device.DeviceScreen
import dev.xverlxrd.batterycapacity.ui.screens.history.HistoryScreen
import dev.xverlxrd.batterycapacity.ui.screens.home.HomeScreen
import dev.xverlxrd.batterycapacity.ui.screens.settings.SettingsScreen
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens

/** Четыре таба; измерение — отдельный полноэкранный маршрут вне бара. */
enum class Tab(val route: String, val label: String, val icon: ImageVector, val iconActive: ImageVector) {
    Home("home", "Обзор", Icons.Outlined.Battery5Bar, Icons.Filled.Battery5Bar),
    History("history", "История", Icons.Outlined.History, Icons.Filled.History),
    Device("device", "Устройство", Icons.Outlined.Smartphone, Icons.Filled.Smartphone),
    Settings("settings", "Настройки", Icons.Outlined.Settings, Icons.Filled.Settings),
}

private const val ROUTE_CALIBRATION = "calibration"

@Composable
fun BatteryEstimatorApp() {
    val navController = rememberNavController()
    val reducedMotion = LocalReducedMotion.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Плавающий округлый док: мягкая тень, без пилюли индикатора.
            val dockShape = RoundedCornerShape(32.dp)
            NavigationBar(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .shadow(8.dp, dockShape)
                    .clip(dockShape),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                val entry by navController.currentBackStackEntryAsState()
                val currentDestination = entry?.destination
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(if (selected) tab.iconActive else tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding),
            enterTransition = {
                if (reducedMotion) {
                    fadeIn(tween(1))
                } else {
                    fadeIn(tween(MotionTokens.DURATION_STANDARD)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(MotionTokens.DURATION_STANDARD, easing = MotionTokens.EaseOut))
                }
            },
            exitTransition = { fadeOut(tween(if (reducedMotion) 1 else MotionTokens.DURATION_QUICK)) },
            popEnterTransition = { fadeIn(tween(if (reducedMotion) 1 else MotionTokens.DURATION_STANDARD)) },
            popExitTransition = { fadeOut(tween(if (reducedMotion) 1 else MotionTokens.DURATION_QUICK)) },
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onStartMeasurement = { navController.navigate(ROUTE_CALIBRATION) },
                    onOpenHistory = {
                        navController.navigate(Tab.History.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Tab.History.route) {
                HistoryScreen(onStartMeasurement = { navController.navigate(ROUTE_CALIBRATION) })
            }
            composable(Tab.Device.route) { DeviceScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(ROUTE_CALIBRATION) {
                CalibrationWizardScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
