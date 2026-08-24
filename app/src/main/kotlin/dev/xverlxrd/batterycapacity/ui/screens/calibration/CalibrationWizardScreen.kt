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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.SessionState
import dev.xverlxrd.batterycapacity.service.MeasurementForegroundService
import dev.xverlxrd.batterycapacity.ui.components.InsetCard
import dev.xverlxrd.batterycapacity.ui.components.ListRow
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens
import kotlin.math.roundToInt

/**
 * Пошаговый мастер калибровки в стиле iOS:
 *  интро → «разрядите до <15%» → «заряжайте до 100%» → результат.
 * Переходы между шагами: fade + scale(0.95→1), 220 мс; reduced-motion — fade.
 */
@Composable
fun CalibrationWizardScreen(viewModel: CalibrationViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current

    CalibrationContent(
        state = state,
        reducedMotion = reduced,
        onStart = viewModel::start,
        onResume = { session -> viewModel.resume(session.id) },
        onCancel = viewModel::cancel,
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
                    scaleIn(initialScale = 0.95f, animationSpec = tween(MotionTokens.DURATION_STANDARD, easing = MotionTokens.EaseOut)))
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
            WizardStep.DONE -> DoneStep()
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

@Composable
private fun IntroStep(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Калибровка ёмкости", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Тест занимает несколько часов и проходит в три шага:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InsetCard {
            ListRow(title = "01 · Разряд", value = "ниже 15%", supporting = "Обычное использование телефона")
            ListRow(title = "02 · Заряд", value = "до 100%", supporting = "Не используйте телефон во время зарядки")
            ListRow(
                title = "03 · Результат",
                value = "мА·ч и износ",
                supporting = "Чем шире диапазон заряда — тем точнее оценка",
                showDivider = false,
            )
        }
        Text(
            "Во время теста работает фоновый сервис с уведомлением. Данные остаются только на устройстве.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Начать тест")
        }
    }
}

@Composable
private fun DischargeStep(state: CalibrationUiState, onCancel: () -> Unit) {
    val soc = state.session?.lastSoc?.roundToInt()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Шаг 1 · Разряд", style = MaterialTheme.typography.headlineLarge)
        LinearProgressIndicator(
            progress = { ((soc ?: 100) / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        InsetCard {
            ListRow(
                title = "Текущий заряд",
                value = "${soc ?: "—"}%",
                supporting = "Нужно опуститься ниже ${CalibrationSession.START_SOC_THRESHOLD.roundToInt()}%",
            )
            if (state.etaMinutesToFifteen != null) {
                ListRow(
                    title = "Осталось примерно",
                    value = "${state.etaMinutesToFifteen} мин",
                    showDivider = false,
                )
            } else {
                ListRow(title = "Пользуйтесь телефоном как обычно", value = "", showDivider = false)
            }
        }
        CancelFooter(onCancel)
    }
}

@Composable
private fun ChargeStep(state: CalibrationUiState, onCancel: () -> Unit) {
    val soc = state.session?.lastSoc?.roundToInt() ?: 0
    val preview = state.previewEstimate
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Шаг 2 · Заряд до полного", style = MaterialTheme.typography.headlineLarge)
        LinearProgressIndicator(
            progress = { (soc / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        InsetCard {
            ListRow(title = "Заряд", value = "$soc%")
            ListRow(title = "Собрано данных", value = "${state.samples.size} отсч.")
            if (state.etaMinutesToFull != null) {
                ListRow(title = "До 100% примерно", value = "${state.etaMinutesToFull} мин")
            }
            ListRow(
                title = "Предварительная оценка",
                value = preview?.actualMah?.roundToInt()?.let { "$it мА·ч" } ?: "—",
                supporting = preview?.let {
                    "±${it.confidenceMah.roundToInt()} мА·ч • метод: ${it.method.displayName}"
                },
                showDivider = false,
            )
        }
        if (preview?.method == dev.xverlxrd.batterycapacity.domain.model.MeasurementMethod.B_CURRENT_INTEGRATION) {
            Text(
                "Счётчик заряда недоступен на этом устройстве — используется интегрирование тока, точность снижена.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "Не используйте телефон во время зарядки: фоновая нагрузка искажает интеграл тока.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CancelFooter(onCancel)
    }
}

@Composable
private fun PausedStep(state: CalibrationUiState, onResume: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Пауза", style = MaterialTheme.typography.headlineLarge)
        Text(
            when (state.session?.pauseReason) {
                MeasurementForegroundService.PAUSE_REASON_UNPLUGGED ->
                    "Зарядка отключена. Подключите зарядное устройство снова — тест продолжится автоматически."
                else ->
                    "Сессия приостановлена (возможно, после перезагрузки). Данные сохранены."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onResume,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Продолжить") }
        CancelFooter(onCancel)
    }
}

@Composable
private fun DoneStep() {
    // Финальные цифры показывает Обзор; здесь — подтверждение.
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Готово", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Измерение завершено и сохранено в историю. Результат — на вкладке «Обзор».",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun CancelFooter(onCancel: () -> Unit) {
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Прервать тест", color = MaterialTheme.colorScheme.error)
    }
}
