package dev.xverlxrd.batterycapacity.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.ui.components.InsetCard
import dev.xverlxrd.batterycapacity.ui.components.ListRow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var manualText by remember(state.manualDesignCapacityMah) {
        mutableStateOf(state.manualDesignCapacityMah?.toString() ?: "")
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge)

        // --- Тема: сегментированный контрол как в iOS ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Тема", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.useDarkTheme == null,
                    onClick = { viewModel.setTheme(null) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                ) { Text("Системная") }
                SegmentedButton(
                    selected = state.useDarkTheme == false,
                    onClick = { viewModel.setTheme(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) { Text("Светлая") }
                SegmentedButton(
                    selected = state.useDarkTheme == true,
                    onClick = { viewModel.setTheme(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) { Text("Тёмная") }
            }
        }

        // --- Ручной ввод паспортной ёмкости ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Паспортная ёмкость (мА·ч)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Возьмите значение с коробки или из спецификации. Перекрывает таблицу известных моделей; если оставить пустым, используется charge_full_design из системы.",
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // --- Интервал опроса ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Интервал фонового опроса", style = MaterialTheme.typography.titleMedium)
            Text(
                "${state.pollingIntervalSeconds} сек — чем реже, тем меньше расход батареи приложением",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.pollingIntervalSeconds.toFloat(),
                onValueChange = { viewModel.setPolling(it.roundToInt()) },
                valueRange = 30f..300f,
                steps = 8,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        // --- Окно медианного фильтра ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Окно фильтра шума тока", style = MaterialTheme.typography.titleMedium)
            Text(
                "${state.filterWindowSize} отсчёта (медиана) — расширяйте при беспроводной зарядке",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.filterWindowSize.toFloat(),
                onValueChange = { viewModel.setFilterWindow(it.roundToInt()) },
                valueRange = 3f..9f,
                steps = 2,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        // --- Диагностика источников ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Доступность источников", style = MaterialTheme.typography.titleMedium)
            InsetCard {
                CapabilityRow("Счётчик заряда (метод A)", state.capabilities.hasChargeCounter)
                CapabilityRow("Мгновенный ток (метод B)", state.capabilities.hasCurrentNow)
                CapabilityRow("sysfs power_supply (метод C)", state.capabilities.hasSysfsAccess, showDivider = false)
            }
            if (state.capabilities.batteryNodesFound.isNotEmpty()) {
                Text(
                    "Найдено узлов батареи: ${state.capabilities.batteryNodesFound.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "Приватность: приложение не отправляет данные наружу. Вся история хранится локально.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CapabilityRow(name: String, available: Boolean, showDivider: Boolean = true) {
    ListRow(
        title = name,
        value = if (available) "доступен" else "недоступен",
        valueColor = if (available) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        showDivider = showDivider,
    )
}
