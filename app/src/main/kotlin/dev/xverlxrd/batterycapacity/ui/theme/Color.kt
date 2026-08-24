package dev.xverlxrd.batterycapacity.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Собственная семантическая палитра: нейтральный графит + кобальтовый акцент.
 * UI никогда не ссылается на hex напрямую — только на эти токены или слоты M3.
 */

// --- Светлая тема ---
private val LightBackground = Color(0xFFF6F6F4)      // тёплая «бумага»
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFEFEFEC)  // мягкая заливка
private val LightOutline = Color(0xFFE7E7E3)         // волосяные линии
private val LightOutlineVariant = Color(0xFFD9D9D4)  // заметные границы
private val LightOnSurface = Color(0xFF17191C)
private val LightSecondaryText = Color(0xFF585D66)
private val LightTertiaryText = Color(0xFF8A8F98)
private val LightPrimary = Color(0xFF2E5BE3)         // кобальт
private val LightOnPrimary = Color(0xFFFFFFFF)

// Статусы (светлые)
private val LightSuccess = Color(0xFF1E9E56)
private val LightInfo = Color(0xFF0E9384)            // «good» бирюза
private val LightWarning = Color(0xFFC77E14)
private val LightCritical = Color(0xFFD93B41)

// --- Тёмная тема: система глубины из нескольких уровней поверхности ---
private val DarkBackground = Color(0xFF0C0D10)
private val DarkSurface = Color(0xFF15171B)
private val DarkSurfaceVariant = Color(0xFF1D2026)   // приподнятая поверхность
private val DarkOutline = Color(0xFF272A31)          // волосяные линии
private val DarkOutlineVariant = Color(0xFF33373F)   // заметные границы
private val DarkOnSurface = Color(0xFFECEDF0)
private val DarkSecondaryText = Color(0xFFAFB4BD)
private val DarkTertiaryText = Color(0xFF7C828C)
private val DarkPrimary = Color(0xFF93ACFF)
private val DarkOnPrimary = Color(0xFF0A1440)

// Статусы (тёмные, приглушённо-светящиеся)
private val DarkSuccess = Color(0xFF54C584)
private val DarkInfo = Color(0xFF45BFAE)
private val DarkWarning = Color(0xFFEBAD55)
private val DarkCritical = Color(0xFFFF6B69)

/**
 * Семантические статусные цвета вне слотов M3: успех/инфо/предупреждение/критично.
 * Доступны через LocalStatusColors — ни один экран не хардкодит их.
 */
data class StatusColors(
    val success: Color,
    val info: Color,
    val warning: Color,
    val critical: Color,
)

val LightStatusColors = StatusColors(LightSuccess, LightInfo, LightWarning, LightCritical)
val DarkStatusColors = StatusColors(DarkSuccess, DarkInfo, DarkWarning, DarkCritical)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Цвет здоровья по проценту: >85 отлично, 70–85 норма, 50–70 износ, <50 замена. */
fun StatusColors.healthColor(percent: Double): Color = when {
    percent > 85.0 -> success
    percent >= 70.0 -> info
    percent >= 50.0 -> warning
    else -> critical
}

internal fun lightScheme(): ColorScheme = androidx.compose.material3.lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightOnSurface,
    secondary = LightSecondaryText,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,
    tertiary = LightInfo,
    onTertiary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondaryText,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightCritical,
    onError = LightOnPrimary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightSurfaceVariant,
)

internal fun darkScheme(): ColorScheme = androidx.compose.material3.darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkOnSurface,
    secondary = DarkSecondaryText,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = DarkInfo,
    onTertiary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkCritical,
    onError = Color(0xFF3A090B),
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceVariant,
)
