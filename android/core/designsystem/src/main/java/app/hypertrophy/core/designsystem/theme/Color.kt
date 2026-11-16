package app.hypertrophy.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PhoenixColors(
    val canvas: Color,
    val surface: Color,
    val elevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val crimsonPrimary: Color,
    val emberOrange: Color,
    val solarGold: Color,
    val formErrorCrimson: Color,
    val isDark: Boolean,
)

/** Obsidian Forge, the dark theme. */
val DarkPhoenixColors = PhoenixColors(
    canvas = Color(0xFF0D0D0F),
    surface = Color(0xFF18181C),
    elevated = Color(0xFF222228),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF9E9EA8),
    crimsonPrimary = Color(0xFFD91E36),
    emberOrange = Color(0xFFFF6B1A),
    solarGold = Color(0xFFFFB800),
    formErrorCrimson = Color(0xFFFF2A4B),
    isDark = true,
)

/** Daylight Ash, the light theme. */
val LightPhoenixColors = PhoenixColors(
    canvas = Color(0xFFF8F7F4),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFEFECE6),
    textPrimary = Color(0xFF111114),
    textSecondary = Color(0xFF5C5C66),
    crimsonPrimary = Color(0xFFD91E36),
    emberOrange = Color(0xFFFF6B1A),
    solarGold = Color(0xFFFFB800),
    formErrorCrimson = Color(0xFFFF2A4B),
    isDark = false,
)

val LocalPhoenixColors = staticCompositionLocalOf { DarkPhoenixColors }
