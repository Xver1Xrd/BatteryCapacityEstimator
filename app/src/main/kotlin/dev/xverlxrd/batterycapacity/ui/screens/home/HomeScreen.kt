package dev.xverlxrd.batterycapacity.ui.screens.home

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import dev.xverlxrd.batterycapacity.ui.components.BatteryHealthHero
import dev.xverlxrd.batterycapacity.ui.components.EmptyState
import dev.xverlxrd.batterycapacity.ui.components.ErrorState
import dev.xverlxrd.batterycapacity.ui.components.GroupSurface
import dev.xverlxrd.batterycapacity.ui.components.MetricRow
import dev.xverlxrd.batterycapacity.ui.components.PulseDot
import dev.xverlxrd.batterycapacity.ui.components.PrimaryButton
import dev.xverlxrd.batterycapacity.ui.components.SectionHeader
import dev.xverlxrd.batterycapacity.ui.components.StaggeredItem
import dev.xverlxrd.batterycapacity.ui.components.StatusPill
import dev.xverlxrd.batterycapacity.ui.theme.LocalStatusColors
import dev.xverlxrd.batterycapacity.ui.theme.healthColor
import dev.xverlxrd.batterycapacity.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Главный экран: ответ на вопрос «в каком состоянии аккумулятор?» за пару секунд.
 * Hero → ёмкость → человеческий вывод → действие → последнее измерение.
 */
@Composable
fun HomeScreen(
    onStartMeasurement: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Header(state)

        StaggeredItem(0) { HeroSection(state) }

        val measurement = state.latestMeasurement
        if (measurement == null) {
            StaggeredItem(1) {
                EmptyState(
                    title = "Измерений ещё не было",
                    body = "Тест разрядит и зарядит батарею под контролем фонового сервиса, чтобы вычислить реальную ёмкость.",
                )
            }
        } else {
            StaggeredItem(1) {
                SectionHeader("Ёмкость")
                Spacer(Modifier.height(6.dp))
                GroupSurface {
                    MetricRow(
                        label = "Фактическая",
                        value = "${measurement.actualMah.roundToInt()} мА·ч",
                        supporting = "±${measurement.confidenceMah.roundToInt()} мА·ч · ${measurement.method.displayName}",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                    MetricRow(
                        label = "Паспортная",
                        value = measurement.designMah?.roundToInt()?.let { "$it мА·ч" } ?: "не задана",
                    )
                    MetricRow(
                        label = "Потеря ёмкости",
                        value = lossText(measurement.actualMah, measurement.designMah),
                        valueColor = if (measurement.designMah != null && measurement.designMah > measurement.actualMah) {
                            statusColors.warning
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        showDivider = false,
                    )
                }
            }
            StaggeredItem(2) {
                Text(
                    insightSentence(measurement.healthPercent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        if (state.capabilities.let { !it.hasChargeCounter && !it.hasCurrentNow && !it.hasSysfsAccess }) {
            StaggeredItem(3) {
                ErrorState(
                    title = "Данные батареи недоступны",
                    message = "Android не предоставляет достаточно информации для точной оценки на этом устройстве.",
                )
            }
        }

        StaggeredItem(4) {
            PrimaryButton(
                text = if (state.activeSession == null) "Измерить ёмкость" else "Идёт измерение…",
                onClick = onStartMeasurement,
                enabled = state.activeSession == null,
            )
        }

        if (measurement != null) {
            StaggeredItem(5) {
                GroupSurface(onClick = onOpenHistory) {
                    MetricRow(
                        label = "Последнее измерение",
                        value = formatMeasuredAt(measurement.measuredAtMs),
                        numericValue = false,
                        showDivider = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Header(state: HomeUiState) {
    val statusColors = LocalStatusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Батарея", style = MaterialTheme.typography.headlineLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                Build.MODEL?.removePrefix("google ") ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.snapshot?.let { snap ->
                val charging = snap.isCharging
                PulseDot(if (charging) MaterialTheme.colorScheme.tertiary else statusColors.success)
                Text(
                    when {
                        snap.status == ChargeStatus.FULL -> "Полный"
                        charging -> "Заряжается"
                        else -> "Разряжается"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroSection(state: HomeUiState) {
    val statusColors = LocalStatusColors.current
    val health = state.latestMeasurement?.healthPercent
    GroupSurface(modifier = Modifier.padding(top = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BatteryHealthHero(
                percent = health,
                accentColor = health?.let(statusColors::healthColor) ?: MaterialTheme.colorScheme.secondary,
            )
            val verdict = state.latestMeasurement?.verdict
            if (health != null && verdict != null) {
                StatusPill(verdict.title, statusColors.healthColor(health))
            } else {
                StatusPill("Нет данных", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun insightSentence(health: Double?): String = when {
    health == null -> "Запустите первое измерение, чтобы оценить состояние аккумулятора."
    else -> "Батарея сохраняет примерно ${"%.0f".format(health)} % исходной ёмкости."
}

private fun lossText(actual: Double, design: Double?): String {
    design ?: return "—"
    val loss = (design - actual).roundToInt()
    return if (loss > 0) "$loss мА·ч" else "в пределах нормы"
}

private fun formatMeasuredAt(ms: Long): String {
    val now = System.currentTimeMillis()
    return when {
        ms > now - 60_000 -> "только что"
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms)) ==
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now)) ->
            "сегодня, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(ms))
    }
}
