package dev.xverlxrd.batterycapacity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.provider.Settings
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Токены движения: тихие фейды и мягкий scale, как в системных приложениях iOS;
 * reduced-motion оставляет только opacity/color.
 */
object MotionTokens {
    // cubic-bezier(0.23, 1, 0.32, 1) — сильный ease-out для UI.
    val EaseOut = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    // cubic-bezier(0.77, 0, 0.175, 1) — сильный ease-in-out для on-screen морфинга.
    val EaseInOut = androidx.compose.animation.core.CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val DURATION_QUICK = 150       // press feedback, мелкие элементы
    const val DURATION_STANDARD = 220    // экраны, карточки
    const val DURATION_SLOW = 280        // крупные панели (всё ещё <300)
    const val STAGGER_STEP_MS = 45       // 30–80 мс между элементами списка
}

/** Глобальный флаг reduced-motion: читается из системной шкалы анимаций. */
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

/**
 * Типографика в духе iOS HIG: Large Title 34 bold с отрицательным трекингом,
 * headline 17 semibold для заголовков строк, body 17, footnote 13.
 */
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 12.sp),
)

/** Скругления как у inset-grouped списков iOS: карточки 10, кнопки 12, листы 16. */
private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    outline = LightOutline,
    error = LightError,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
)

@Composable
fun BatteryEstimatorTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(LocalReducedMotion provides !rememberSystemAnimationsEnabled()) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
