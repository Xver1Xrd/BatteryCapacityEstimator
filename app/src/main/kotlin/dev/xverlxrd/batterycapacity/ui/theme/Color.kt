package dev.xverlxrd.batterycapacity.ui.theme

import androidx.compose.ui.graphics.Color

// Светлая тема: системные цвета iOS.
// background = systemGroupedBackground, surface = secondarySystemGroupedBackground,
// primary = systemBlue, outline = separator, onSurfaceVariant = secondaryLabel.
val LightBackground = Color(0xFFF2F2F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFEFF4)
val LightPrimary = Color(0xFF007AFF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFF8E8E93)
val LightTertiary = Color(0xFF34C759)
val LightOutline = Color(0xFFE5E5EA)
val LightError = Color(0xFFFF3B30)

// Тёмная тема: чёрный фон, приподнятые поверхности, светлые акценты iOS.
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF1C1C1E)
val DarkSurfaceVariant = Color(0xFF2C2C2E)
val DarkPrimary = Color(0xFF0A84FF)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkSecondary = Color(0xFF98989F)
val DarkTertiary = Color(0xFF30D158)
val DarkOutline = Color(0xFF38383A)
val DarkError = Color(0xFFFF453A)

// Семантика здоровья батареи — палитра iOS: зелёный/бирюза/оранжевый/красный.
val HealthExcellent = Color(0xFF34C759)
val HealthGood = Color(0xFF00C7BE)
val HealthWorn = Color(0xFFFF9500)
val HealthReplace = Color(0xFFFF3B30)

fun healthColor(percent: Double): Color = when {
    percent > 85.0 -> HealthExcellent
    percent >= 70.0 -> HealthGood
    percent >= 50.0 -> HealthWorn
    else -> HealthReplace
}
