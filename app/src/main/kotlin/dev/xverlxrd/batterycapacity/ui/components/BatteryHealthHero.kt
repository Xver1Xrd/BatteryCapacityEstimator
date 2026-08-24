package dev.xverlxrd.batterycapacity.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens
import kotlin.math.roundToInt

/**
 * Центральный hero-компонент приложения: дуга здоровья на 270°.
 * Кастомная визуализация — не Material progress: тонкий трек, круглая шапка
 * дуги, анимированный count-up числа в центре, семантика для TalkBack.
 */
@Composable
fun BatteryHealthHero(
    percent: Double?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 248.dp,
    caption: String = "здоровье батареи",
    animateOnAppear: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val trackColor = MaterialTheme.colorScheme.outline
    val valueColor = if (percent != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val targetFraction = ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val sweepFraction by animateFloatAsState(
        targetValue = if (!animateOnAppear || started) targetFraction else 0f,
        animationSpec = if (reduced) tween(1) else tween(MotionTokens.DURATION_SLOW + 160, easing = MotionTokens.EaseOut),
        label = "heroSweep",
    )
    // Count-up числа следует за дугой.
    val shownPercent = if (percent == null) null else (sweepFraction * 100.0).roundToInt()

    Box(
        modifier
            .size(sizeDp)
            .semantics {
                contentDescription = if (percent != null) {
                    "$caption: $shownPercent процентов"
                } else {
                    "$caption: нет данных"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            // Трек: спокойные 270° с разрывом снизу.
            drawArc(
                color = trackColor,
                startAngle = -225f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (sweepFraction > 0.001f) {
                drawArc(
                    color = valueColor,
                    startAngle = -225f,
                    sweepAngle = sweepFraction * 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                shownPercent?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.displayMedium,
                color = if (percent != null) valueColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                caption.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
