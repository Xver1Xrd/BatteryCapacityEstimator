package dev.xverlxrd.batterycapacity.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.ui.components.HealthRing
import dev.xverlxrd.batterycapacity.ui.components.InsetCard
import dev.xverlxrd.batterycapacity.ui.components.ListRow
import dev.xverlxrd.batterycapacity.ui.components.VerdictBadge
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens
import dev.xverlxrd.batterycapacity.ui.theme.healthColor
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Обзор", style = MaterialTheme.typography.headlineLarge)

        StaggeredItem(index = 0, reduced) {
            HeroHealthCard(state)
        }
        StaggeredItem(index = 1, reduced) {
            InsetCard {
                ListRow(
                    title = "Фактическая ёмкость",
                    value = state.latestMeasurement?.let {
                        "${it.actualMah.roundToInt()} мА·ч ±${it.confidenceMah.roundToInt()}"
                    } ?: "—",
                )
                ListRow(
                    title = "Паспортная ёмкость",
                    value = state.latestMeasurement?.designMah?.roundToInt()?.let { "$it мА·ч" } ?: "—",
                )
                ListRow(
                    title = "Диапазон SOC",
                    value = state.latestMeasurement?.socSpanPct?.roundToInt()?.let { "$it%" } ?: "—",
                )
                ListRow(title = "Циклов зарядки", value = state.snapshot?.cycleCount?.toString() ?: "—")
                ListRow(
                    title = "Температура",
                    value = state.snapshot?.temperatureC?.let { "%.1f °C".format(it) } ?: "—",
                    showDivider = false,
                )
            }
        }
        StaggeredItem(index = 2, reduced) {
            InsetCard {
                ListRow(
                    title = "Напряжение",
                    value = state.snapshot?.voltageMv?.let { "%.2f В".format(it / 1000.0) } ?: "—",
                    showDivider = false,
                )
            }
        }
        StaggeredItem(index = 3, reduced) {
            Button(
                onClick = viewModel::startCalibration,
                enabled = state.activeSession == null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (state.activeSession == null) "Начать калибровочный тест" else "Тест идёт — вкладка «Тест»")
            }
        }
        if (state.latestMeasurement == null) {
            StaggeredItem(index = 4, reduced) {
                Text(
                    "Измерений ещё не было. Калибровка разрядит и зарядит батарею под контролем сервиса, чтобы посчитать реальную ёмкость.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroHealthCard(state: DashboardUiState) {
    val measurement = state.latestMeasurement
    val health = measurement?.healthPercent
    val ringColor = health?.let { healthColor(it) } ?: MaterialTheme.colorScheme.secondary

    InsetCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HealthRing(percent = health, ringColor = ringColor)
            val verdict = measurement?.verdict
            if (health != null && verdict != null) {
                VerdictBadge(verdict.title, healthColor(health))
            }
            Text(
                listOfNotNull(
                    measurement?.method?.displayName?.let { "Метод: $it" },
                    measurement?.warnings?.firstOrNull(),
                ).takeIf { it.isNotEmpty() }?.joinToString(" • ") ?: "Нет данных измерений",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Каскадный вход карточек; reduced-motion → только fade. */
@Composable
private fun StaggeredItem(index: Int, reduced: Boolean, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(MotionTokens.STAGGER_STEP_MS * index.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) {
            fadeIn(tween(MotionTokens.DURATION_QUICK))
        } else {
            fadeIn(tween(MotionTokens.DURATION_STANDARD, easing = MotionTokens.EaseOut)) +
                scaleIn(initialScale = 0.95f, animationSpec = tween(MotionTokens.DURATION_STANDARD, easing = MotionTokens.EaseOut))
        },
    ) { content() }
}
