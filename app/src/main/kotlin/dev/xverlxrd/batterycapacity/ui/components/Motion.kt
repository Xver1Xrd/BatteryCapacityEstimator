package dev.xverlxrd.batterycapacity.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.xverlxrd.batterycapacity.ui.theme.LocalReducedMotion
import dev.xverlxrd.batterycapacity.ui.theme.MotionTokens
import kotlinx.coroutines.delay

/** Нажатие → мягкий scale 0.97; reduced-motion оставляет только ripple. */
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
 * «Дышащая» точка активного процесса: тихий пульс alpha.
 * Полностью статична при reduced-motion.
 */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val reduced = LocalReducedMotion.current
    val alpha = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "pulse")
        val pulsing by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900, easing = MotionTokens.EaseInOut), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
        pulsing
    }
    Box(modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Surface(modifier = Modifier.size(size), shape = CircleShape, color = color.copy(alpha = alpha)) {}
    }
}

/**
 * Каскадный вход секций экрана: задержка index × шаг + fade (+лёгкий scale).
 * При reduced-motion — только мгновенный fade.
 */
@Composable
fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MotionTokens.STAGGER_STEP_MS * index.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) {
            fadeIn(tween(MotionTokens.DURATION_QUICK))
        } else {
            fadeIn(tween(MotionTokens.DURATION_STANDARD, easing = MotionTokens.EaseOut)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(MotionTokens.DURATION_STANDARD, easing = MotionTokens.EaseOut))
        },
    ) { content() }
}
