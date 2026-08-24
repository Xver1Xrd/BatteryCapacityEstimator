package dev.xverlxrd.batterycapacity.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.ui.components.GroupSurface
import dev.xverlxrd.batterycapacity.ui.components.MetricRow
import dev.xverlxrd.batterycapacity.ui.components.SectionHeader
import dev.xverlxrd.batterycapacity.ui.components.SettingsSwitchRow
import dev.xverlxrd.batterycapacity.ui.theme.Spacing
import kotlin.math.roundToInt

private const val GITHUB_URL = "https://github.com/Xver1Xrd/BatteryCapacityEstimator"

/** Настройки: сгруппированные секции в духе inset-grouped списков. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var manualText by remember(state.manualDesignCapacityMah) {
        mutableStateOf(state.manualDesignCapacityMah?.toString() ?: "")
    }
    var showClearDialog by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenH, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge)

        // --- Оформление ---
        SectionHeader("Оформление")
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 0.dp)) {
                themeSegment("Системная", state.useDarkTheme == null, null) { viewModel.setTheme(null) }
                themeSegment("Светлая", state.useDarkTheme == false, false) { viewModel.setTheme(false) }
                themeSegment("Тёмная", state.useDarkTheme == true, true) { viewModel.setTheme(true) }
            }
            GroupSurface {
                SettingsSwitchRow(
                    label = "Динамический цвет",
                    supporting = "Палитра Material You (Android 12+)",
                    checked = state.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SettingsSwitchRow(
                    label = "Анимации",
                    supporting = "Плавные переходы и появление графиков",
                    checked = state.animationsEnabled,
                    onCheckedChange = viewModel::setAnimations,
                    showDivider = false,
                )
            }
        }

        // --- Измерения ---
        SectionHeader("Измерения")
        Spacer(Modifier.height(4.dp))
        GroupSurface {
            MetricRow(
                label = "Интервал фонового опроса",
                value = "${state.pollingIntervalSeconds} сек",
            )
            Slider(
                value = state.pollingIntervalSeconds.toFloat(),
                onValueChange = { viewModel.setPolling(it.roundToInt()) },
                valueRange = 30f..300f,
                steps = 8,
                modifier = Modifier.padding(horizontal = Spacing.l),
            )
            MetricRow(
                label = "Окно фильтра шума",
                value = "${state.filterWindowSize} отсчёта (медиана)",
            )
            Slider(
                value = state.filterWindowSize.toFloat(),
                onValueChange = { viewModel.setFilterWindow(it.roundToInt()) },
                valueRange = 3f..9f,
                steps = 2,
                modifier = Modifier.padding(horizontal = Spacing.l),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Паспортная ёмкость (мА·ч)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Перекрывает системное значение. Возьмите число из спецификации устройства; пусто — использовать данные системы.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = manualText,
                onValueChange = { input ->
                    manualText = input.filter { it.isDigit() }.take(5)
                    viewModel.setManualCapacity(manualText.toLongOrNull())
                },
                placeholder = { Text("например 5000") },
                singleLine = true,
                shape = RoundedCornerShape(Spacing.m),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // --- Данные ---
        SectionHeader("Данные")
        Spacer(Modifier.height(4.dp))
        GroupSurface {
            MetricRow("Экспорт истории", "CSV", onClick = viewModel::exportCsv)
            MetricRow(
                label = "Очистить историю",
                value = "",
                valueColor = MaterialTheme.colorScheme.error,
                onClick = { showClearDialog = true },
            )
            MetricRow("Счётчик заряда (A)", if (state.capabilities.hasChargeCounter) "доступен" else "нет")
            MetricRow("Ток (B)", if (state.capabilities.hasCurrentNow) "доступен" else "нет")
            MetricRow(
                "sysfs (C)",
                if (state.capabilities.hasSysfsAccess) "доступен" else "нет",
                showDivider = false,
            )
        }

        // --- О приложении ---
        SectionHeader("О приложении")
        Spacer(Modifier.height(4.dp))
        GroupSurface {
            VersionRow()
            MetricRow("GitHub", "открыть репозиторий", numericValue = false, onClick = { runCatching { uriHandler.openUri(GITHUB_URL) } })
            MetricRow("Лицензии", "", onClick = { showLicenses = true }, showDivider = false)
        }

        Text(
            "Приватность: приложение не отправляет данные наружу. Вся история хранится только на устройстве.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все сохранённые измерения будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearHistory()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            },
        )
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Используемые библиотеки") },
            text = {
                Text(
                    "Jetpack Compose • Hilt • Room • DataStore • WorkManager • Kotlin Coroutines\n\nВсе компоненты распространяются по лицензии Apache 2.0.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("Закрыть") }
            },
        )
    }
}

@androidx.compose.runtime.Composable
private fun androidx.compose.material3.SingleChoiceSegmentedButtonRowScope.themeSegment(
    text: String,
    selected: Boolean,
    dark: Boolean?,
    onClick: () -> Unit,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = SegmentedButtonDefaults.itemShape(index = when (dark) { null -> 0; false -> 1; true -> 2 }, count = 3),
    ) { Text(text) }
}

@androidx.compose.runtime.Composable
private fun VersionRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: Exception) {
            "—"
        }
    }
    MetricRow("Версия", version)
}
