package dev.xverlxrd.batterycapacity.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Нажатие на карточку/кнопку даёт мгновенный отклик scale→0.97 за 150 мс.
 * При reduced-motion масштаб не трогаем — остаётся только ripple.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = tween(durationMillis = MotionTokens.DURATION_QUICK, easing = MotionTokens.EaseOut),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Белая скруглённая карточка (inset-grouped стиль iOS): радиус 10, без теней.
 * Фундамент всех группировок контента в приложении.
 */
@Composable
fun InsetCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Card(
            modifier = modifier.fillMaxWidth().pressScale(interaction),
            shape = shape,
            interactionSource = interaction,
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(content = content)
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(content = content)
        }
    }
}

/**
 * Строка «параметр — значение» как в Настройках iOS: заголовок слева,
 * значение справа серым (или цветным), волосяной разделитель с отступом слева.
 */
@Composable
fun ListRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    supporting: String? = null,
    showDivider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor ?: MaterialTheme.colorScheme.secondary,
                )
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Карточка метрики: крупное число, подпись, вспомогательный текст. */
@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    supportingText: String? = null,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    InsetCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent ?: MaterialTheme.colorScheme.onSurface,
                )
                if (!unit.isNullOrEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (!supportingText.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Компактный бейдж вердикта здоровья — мягкая пилюля с тонированным фоном. */
@Composable
fun VerdictBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/**
 * Кольцевой индикатор здоровья батареи в стиле «колец активности»:
 * трек по кругу + дуга процента с круглыми окончаниями, число в центре.
 */
@Composable
fun HealthRing(percent: Double?, ringColor: Color, modifier: Modifier = Modifier, sizeDp: Dp = 200.dp) {
    val track = MaterialTheme.colorScheme.outline
    val reduced = LocalReducedMotion.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val sweepFraction by animateFloatAsState(
        targetValue = if (started) ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = if (reduced) {
            tween(1)
        } else {
            tween(MotionTokens.DURATION_SLOW + 200, easing = MotionTokens.EaseOut)
        },
        label = "ringSweep",
    )

    Box(modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2 + 1.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (percent != null && sweepFraction > 0f) {
                // Небольшой зазор между началом и концом дуги, как в Activity rings.
                val sweep = sweepFraction * 358f
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                percent?.let { "${it.roundToInt()}%" } ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                color = if (percent != null) ringColor else MaterialTheme.colorScheme.secondary,
            )
            Text(
                "здоровье",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

data class ChartSeries(val points: List<Pair<Float, Float>>, val color: Color, val label: String)

/**
 * Линейный график «напряжение/ток vs время» на Canvas.
 * Без внешних зависимостей; сетка едва заметна — данные на первом плане.
 */
@Composable
fun MultiLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(height)) {
        val allY = series.flatMap { it.points.map { p -> p.second } }
        if (allY.isEmpty()) return@Canvas
        val minY = allY.min()
        val maxY = allY.max()
        val yRange = (maxY - minY).takeIf { abs(it) > 1e-6 } ?: 1f
        val maxX = series.maxOfOrNull { s -> s.points.maxOfOrNull { it.first } ?: 0f } ?: 1f
        val xRange = maxX.takeIf { abs(it) > 1e-6 } ?: 1f

        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(outline.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
        }

        series.forEach { s ->
            if (s.points.size < 2) return@forEach
            val path = Path()
            s.points.forEachIndexed { i, (x, y) ->
                val px = (x / xRange) * size.width
                val py = size.height - ((y - minY) / yRange) * size.height
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
