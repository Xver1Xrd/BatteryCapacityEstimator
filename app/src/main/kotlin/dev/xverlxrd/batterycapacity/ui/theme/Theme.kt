package dev.xverlxrd.batterycapacity.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Токены движения: короткие, намеренные анимации; reduced-motion → только фейды. */
object MotionTokens {
    // cubic-bezier(0.23, 1, 0.32, 1) — мягкий ease-out.
    val EaseOut = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    // cubic-bezier(0.77, 0, 0.175, 1) — плавный ease-in-out для смены состояний.
    val EaseInOut = androidx.compose.animation.core.CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val DURATION_QUICK = 150       // press-отклик, мелкие элементы
    const val DURATION_STANDARD = 220    // экраны, карточки
    const val DURATION_SLOW = 320        // hero-визуализации
    const val STAGGER_STEP_MS = 40       // каскад входа секций
}

/** Глобальный флаг reduced-motion: системная шкала анимаций + настройка в приложении. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale > 0f
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.s),
    small = RoundedCornerShape(Radius.s),
    medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.l),
)

/**
 * @param darkTheme тёмная тема (обычно из настроек пользователя)
 * @param dynamicColor динамический цвет Material You (Android 12+)
 */
@Composable
fun BatteryEstimatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = if (darkTheme) darkScheme() else lightScheme()
    val scheme = if (dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (darkTheme) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
    } else {
        baseScheme
    }
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(
        LocalReducedMotion provides (!animationsEnabled || !rememberSystemAnimationsEnabled()),
        LocalStatusColors provides statusColors,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
