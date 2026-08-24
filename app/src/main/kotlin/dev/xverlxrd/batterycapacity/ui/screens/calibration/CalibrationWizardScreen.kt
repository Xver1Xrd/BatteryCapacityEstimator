package dev.xverlxrd.batterycapacity.ui.screens.calibration

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.MeasurementMethod
import dev.xverlxrd.batterycapacity.domain.model.SessionState
import dev.xverlxrd.batterycapacity.service.MeasurementForegroundService
import dev.xverlxrd.batterycapacity.ui.components.GroupSurface
import dev.xverlxrd.batterycapacity.ui.components.MetricRow
import dev.xverlxrd.batterycapacity.ui.components.PrimaryButton
import dev.xverlxrd.batterycapacity.ui.components.PulseDot
import dev.xverlxrd.batterycapacity.ui.components.SectionHeader
import dev.xverlxrd.batterycapacity.ui.components.SecondaryButton
import dev.xverlxrd.batterycapacity.ui.components.StatusPill
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.LocalStatusColors
import dev.xverlxrd.batterycapacity.ui.theme.healthColor
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens
import dev.xverlxrd.batterycapacity.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Мастер измерения: интро → разряд → заряд → премиальный результат.
 * Переходы шагов: fade + scale(0.96→1); reduced-motion — только fade.
 */
@Composable
fun CalibrationWizardScreen(
    onDone: () -> Unit,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current

    CalibrationContent(
        state = state,
        reducedMotion = reduced,
        onStart = viewModel::start,
        onResume = { session -> viewModel.resume(session.id) },
        onCancel = viewModel::cancel,
        onDone = onDone,
    )
}

/** Stateless-содержимое мастера — отдельно, чтобы покрывалось Compose UI-тестами без Hilt. */
@Composable
fun CalibrationContent(
    state: CalibrationUiState,
    reducedMotion: Boolean,
    onStart: () -> Unit,
    onResume: (CalibrationSession) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit = {},
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    val step = when {
        state.session == null -> WizardStep.INTRO
        state.session.state == SessionState.WAITING_DISCHARGE -> WizardStep.DISCHARGE
        state.session.state == SessionState.PAUSED -> WizardStep.PAUSED
        state.session.state == SessionState.MEASURING -> WizardStep.CHARGE
        else -> WizardStep.DONE
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(1)) togetherWith fadeOut(tween(1))
            } else {
                (fadeIn(tween(MotionTokens.DURATION_STANDARD)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(MotionTokens.DURATION_STANDARD, easing = MotionTokens.EaseOut)))
                    .togetherWith(fadeOut(tween(MotionTokens.DURATION_QUICK)))
            }
        },
        label = "wizard",
        contentAlignment = Alignment.TopStart,
    ) { current ->
        when (current) {
            WizardStep.INTRO -> IntroStep(onStart = onStart)
            WizardStep.DISCHARGE -> DischargeStep(state, onCancel = { showCancelDialog = true })
            WizardStep.CHARGE -> ChargeStep(state, onCancel = { showCancelDialog = true })
            WizardStep.PAUSED -> PausedStep(state, onResume = { state.session?.let(onResume) }, onCancel = { showCancelDialog = true })
            WizardStep.DONE -> DoneStep(state, onDone = onDone)
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Отменить тест?") },
            text = { Text("Собранные данные этой сессии будут удалены. Прерванный тест можно возобновить позже из состояния паузы.") },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; onCancel() }) {
                    Text("Отменить тест", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Продолжить измерение") }
            },
        )
    }
}

private enum class WizardStep { INTRO, DISCHARGE, CHARGE, PAUSED, DONE }

// ---------------------------------------------------------------- INTRO

@Composable
private fun IntroStep(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenH, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Измерение ёмкости", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Тест оценивает, сколько энергии батарея реально держит сейчас.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionHeader("Как проходит")
        GroupSurface {
            MetricRow("01 · Разряд", "до 15 %", supporting = "Пользуйтесь телефоном как обычно")
            MetricRow("02 · Заряд", "до 100 %", supporting = "Не используйте телефон во время зарядки")
            MetricRow("03 · Результат", "", supporting = "Ёмкость в мА·ч и процент износа", showDivider = false)
        }
        SectionHeader("Условия")
        GroupSurface {
            MetricRow("Длительность", "≈ 3–6 часов")
            MetricRow("Уровень заряда", "любой на старте", numericValue = false)
            MetricRow("Точность растёт", "шире диапазон SOC", numericValue = false, showDivider = false)
        }
        Text(
            "Во время теста работает фоновый сервис с уведомлением. Данные остаются только на устройстве.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = "Начать тест", onClick = onStart)
        Spacer(Modifier.height(Spacing.m))
    }
}

// ---------------------------------------------------------------- DISCHARGE

@Composable
private fun DischargeStep(state: CalibrationUiState, onCancel: () -> Unit) {
    val soc = state.session?.lastSoc?.roundToInt()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenH, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatusPill("Шаг 1 из 3", MaterialTheme.colorScheme.primary)
        }
        Text("Шаг 1 · Разряд", style = MaterialTheme.typography.headlineLarge)

        ThinProgress(soc?.toFloat() ?: 100f, MaterialTheme.colorScheme.primary)

        // Главный числовой объект шага.
        Text(
            "${soc ?: "—"}%",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Нужно опуститься ниже ${CalibrationSession.START_SOC_THRESHOLD.roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GroupSurface {
            if (state.etaMinutesToFifteen != null) {
                MetricRow("Осталось примерно", "~${state.etaMinutesToFifteen} мин")
            } else {
                MetricRow("Оценка времени", "собираем данные", numericValue = false)
            }
            MetricRow("Отсчётов собрано", state.samples.size.toString(), showDivider = false)
        }
        CancelFooter(onCancel)
        Spacer(Modifier.height(Spacing.m))
    }
}

// ---------------------------------------------------------------- CHARGE

@Composable
private fun ChargeStep(state: CalibrationUiState, onCancel: () -> Unit) {
    val soc = state.session?.lastSoc?.roundToInt() ?: 0
    val preview = state.previewEstimate
    val statusColors = LocalStatusColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenH, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        StatusPill("Шаг 2 из 3", statusColors.success)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PulseDot(statusColors.success)
            Text("Измерение идёт", style = MaterialTheme.typography.titleMedium)
        }

        ThinProgress(soc.toFloat(), statusColors.success)

        Text("$soc%", style = MaterialTheme.typography.displayMedium)

        SectionHeader("Предварительная оценка")
        GroupSurface {
            MetricRow(
                label = "Ёмкость",
                value = preview?.actualMah?.roundToInt()?.let { "$it мА·ч" } ?: "—",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
            MetricRow("Доверительный интервал", preview?.let { "±${it.confidenceMah.roundToInt()} мА·ч" } ?: "—")
            MetricRow("До полного", state.etaMinutesToFull?.let { "~$it мин" } ?: "—", showDivider = false)
        }
        if (preview?.method == MeasurementMethod.B_CURRENT_INTEGRATION) {
            Text(
                "Счётчик заряда недоступен — используется интегрирование тока, точность снижена.",
                style = MaterialTheme.typography.bodySmall,
                color = statusColors.warning,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Text(
            "Не используйте телефон во время зарядки: фоновая нагрузка искажает результат.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        CancelFooter(onCancel)
        Spacer(Modifier.height(Spacing.m))
    }
}

// ---------------------------------------------------------------- PAUSED

@Composable
private fun PausedStep(state: CalibrationUiState, onResume: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenH, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        StatusPill("Пауза", MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            when (state.session?.pauseReason) {
                MeasurementForegroundService.PAUSE_REASON_UNPLUGGED ->
                    "Зарядка отключена. Подключите зарядное устройство снова — тест продолжится."
                else ->
                    "Измерение приостановлено (возможно, после перезагрузки). Данные сохранены."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Продолжить", onClick = onResume)
        CancelFooter(onCancel)
    }
}

// ---------------------------------------------------------------- RESULT

@Composable
private fun DoneStep(state: CalibrationUiState, onDone: () -> Unit) {
    val result = state.latestResult
    val statusColors = LocalStatusColors.current
    val health = result?.healthPercent
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenH, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        StatusPill("Готово", statusColors.success)
        Text("Результат", style = MaterialTheme.typography.headlineLarge)

        // Главный числовой объект результата.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                health?.let { "%.0f%%".format(it) } ?: "—",
                style = MaterialTheme.typography.displayLarge,
                color = health?.let(statusColors::healthColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "здоровье батареи",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        health?.let {
            Text(insightText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionHeader("Детали")
        GroupSurface {
            if (result != null) {
                MetricRow("Фактическая ёмкость", "${result.actualMah.roundToInt()} мА·ч", valueColor = MaterialTheme.colorScheme.onSurface)
                MetricRow("Паспортная ёмкость", result.designMah?.roundToInt()?.let { "$it мА·ч" } ?: "не задана")
                MetricRow("Точность", "±${result.confidenceMah.roundToInt()} мА·ч")
                MetricRow("Диапазон SOC", "${result.socSpanPct.roundToInt()} %")
                MetricRow("Метод", result.method.displayName, numericValue = false, showDivider = false)
            } else {
                MetricRow(
                    label = "Результат сохранён",
                    value = "",
                    supporting = "Полная сводка доступна в истории и на главном экране.",
                    showDivider = false,
                )
            }
        }
        PrimaryButton(text = "Готово", onClick = onDone)
    }
}

private fun insightText(health: Double): String =
    "Батарея держит примерно ${"%.0f".format(health)} % исходной ёмкости."

// ---------------------------------------------------------------- shared

/** Тихий прогресс-бар: высота 5, скругление пилюлей, без мигания. */
@Composable
private fun ThinProgress(value: Float, color: androidx.compose.ui.graphics.Color) {
    LinearProgressIndicator(
        progress = { (value / 100f).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(5.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        drawStopIndicator = {},
    )
}

@Composable
private fun CancelFooter(onCancel: () -> Unit) {
    Spacer(Modifier.height(Spacing.xs))
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Прервать тест", color = MaterialTheme.colorScheme.error)
    }
}
