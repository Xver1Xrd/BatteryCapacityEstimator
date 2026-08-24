package dev.xverlxrd.batterycapacity.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens
import kotlin.math.abs

/** Точка тренда здоровья. */
data class HealthPoint(
    val timeMs: Long,
    val label: String,
    val healthPercent: Double,
    val capacityMah: Int?,
)

/**
 * Минималистичный график «здоровье во времени»: гладкая кривая без сетки,
 * появление слева направо, выбор точки касанием. Вся навигация по точкам —
 * через hoisted-состояние selectedIndex.
 */
@Composable
fun HealthTrendChart(
    points: List<HealthPoint>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    lineColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
) {
    val reduced = LocalReducedMotion.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val reveal by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = if (reduced) tween(1) else tween(MotionTokens.DURATION_SLOW + 240, easing = MotionTokens.EaseOut),
        label = "chartReveal",
    )
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baselineColor = MaterialTheme.colorScheme.outline
    val selectionLineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectTapGestures { tap ->
                    if (points.isEmpty()) return@detectTapGestures
                    val step = size.width / (points.size - 1).coerceAtLeast(1)
                    val index = ((tap.x + step / 2f) / step).toInt().coerceIn(0, points.lastIndex)
                    onSelect(if (index == selectedIndex) null else index)
                }
            },
    ) {
        if (points.size < 2) {
            // Одна точка — рисуем одиночный маркер по центру.
            points.firstOrNull()?.let {
                drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
            }
            return@Canvas
        }

        val minY = points.minOf { it.healthPercent }.toFloat()
        val maxY = points.maxOf { it.healthPercent }.toFloat()
        val pad = ((maxY - minY).takeIf { it > 6.0 } ?: 12.0).toFloat() * 0.35f
        val lo = minY - pad
        val hi = maxY + pad
        fun y(v: Double): Float {
            val t = ((v.toFloat() - lo) / (hi - lo)).coerceIn(0f, 1f)
            return size.height - t * size.height
        }
        fun x(i: Int): Float = size.width * i / (points.size - 1)

        // Базовая линия снизу — единственная «сетка».
        drawLine(baselineColor.copy(alpha = 0.7f), Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())

        // Гладкая кривая через средние точки (quadratic midpoint smoothing).
        val path = Path()
        points.forEachIndexed { i, p ->
            val px = x(i); val py = y(p.healthPercent)
            if (i == 0) path.moveTo(px, py) else {
                val prevX = x(i - 1); val prevY = y(points[i - 1].healthPercent)
                val midX = (prevX + px) / 2f
                path.cubicTo(midX, prevY, midX, py, px, py)
            }
        }
        clipRect(right = size.width * reveal) {
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }

        // Точки данных.
        points.forEachIndexed { i, _ ->
            clipRect(right = size.width * reveal) {
                drawCircle(dotColor.copy(alpha = 0.55f), radius = 3.dp.toPx(), center = Offset(x(i), y(points[i].healthPercent)))
            }
        }

        // Выделение выбранной точки.
        selectedIndex?.takeIf { it in points.indices }?.let { i ->
            val cx = x(i); val cy = y(points[i].healthPercent)
            drawLine(selectionLineColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(lineColor, radius = 5.5.dp.toPx(), center = Offset(cx, cy))
            drawCircle(surfaceColor, radius = 2.5.dp.toPx(), center = Offset(cx, cy))
        }
    }
}
