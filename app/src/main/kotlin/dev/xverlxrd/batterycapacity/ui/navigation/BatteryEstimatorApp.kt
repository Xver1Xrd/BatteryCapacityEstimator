package dev.xverlxrd.batterycapacity.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.xverlxrd.batterycapacity.ui.components.pressScale
import dev.xverlxrd.batterycapacity.ui.screens.calibration.CalibrationWizardScreen
import dev.xverlxrd.batterycapacity.ui.screens.device.DeviceScreen
import dev.xverlxrd.batterycapacity.ui.screens.history.HistoryScreen
import dev.xverlxrd.batterycapacity.ui.screens.home.HomeScreen
import dev.xverlxrd.batterycapacity.ui.screens.settings.SettingsScreen
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens

/** Четыре таба; измерение — отдельный полноэкранный маршрут вне дока. */
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
        bottomBar = { Dock(navController) },
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

/**
 * Плавающий док-капсула вместо стандартного бара: полностью скруглённые торцы,
 * компактная высота, мягкая тень. Без индикатора-пилюли.
 */
@Composable
private fun Dock(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val dockShape = RoundedCornerShape(50)

    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(62.dp)
            .shadow(10.dp, dockShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(dockShape),
        shape = dockShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(Modifier.fillMaxSize()) {
            Tab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                val interaction = remember { MutableInteractionSource() }
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(MotionTokens.DURATION_QUICK),
                    label = "dockTint",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .pressScale(interaction)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            if (!selected) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) tab.iconActive else tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
