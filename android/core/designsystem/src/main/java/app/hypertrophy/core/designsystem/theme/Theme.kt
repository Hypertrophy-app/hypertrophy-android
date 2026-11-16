package app.hypertrophy.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.hypertrophy.core.designsystem.theme.PhoenixTypography

@Composable
fun PhoenixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val phoenix = if (darkTheme) DarkPhoenixColors else LightPhoenixColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = phoenix.crimsonPrimary,
            onPrimary = phoenix.textPrimary,
            secondary = phoenix.emberOrange,
            onSecondary = phoenix.canvas,
            tertiary = phoenix.solarGold,
            onTertiary = phoenix.canvas,
            background = phoenix.canvas,
            onBackground = phoenix.textPrimary,
            surface = phoenix.surface,
            onSurface = phoenix.textPrimary,
            surfaceVariant = phoenix.elevated,
            onSurfaceVariant = phoenix.textSecondary,
            error = phoenix.formErrorCrimson,
        )
    } else {
        lightColorScheme(
            primary = phoenix.crimsonPrimary,
            onPrimary = phoenix.surface,
            secondary = phoenix.emberOrange,
            onSecondary = phoenix.surface,
            tertiary = phoenix.solarGold,
            onTertiary = phoenix.textPrimary,
            background = phoenix.canvas,
            onBackground = phoenix.textPrimary,
            surface = phoenix.surface,
            onSurface = phoenix.textPrimary,
            surfaceVariant = phoenix.elevated,
            onSurfaceVariant = phoenix.textSecondary,
            error = phoenix.formErrorCrimson,
        )
    }

    CompositionLocalProvider(
        LocalPhoenixColors provides phoenix,
        LocalPhoenixTypography provides PhoenixTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PhoenixTypography,
            content = content,
        )
    }
}

@Composable
fun HypertrophyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) = PhoenixTheme(darkTheme = darkTheme, content = content)
