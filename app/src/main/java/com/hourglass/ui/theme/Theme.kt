package com.hourglass.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * HOURGLASS deliberately does not opt into Material You dynamic colour: the whole point
 * of the app is the sand palette, and letting the wallpaper repaint it was why the UI
 * drifted away from its own identity.
 */
@Composable
fun HourglassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = hourglassColors(darkTheme)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accent,
            secondary = Sand400,
            onSecondary = Sand950,
            secondaryContainer = colors.surfaceMuted,
            onSecondaryContainer = colors.textPrimary,
            tertiary = DuskViolet,
            onTertiary = Sand50,
            background = colors.backdrop,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outlineStrong,
            outlineVariant = colors.outline,
            error = colors.overtime,
            onError = Sand950,
            errorContainer = Sand800,
            onErrorContainer = colors.overtime,
            inverseSurface = Sand100,
            inverseOnSurface = Sand900,
            scrim = Sand950
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = Sand800,
            secondary = Sand600,
            onSecondary = Sand50,
            secondaryContainer = Sand100,
            onSecondaryContainer = Sand800,
            tertiary = DuskIndigo,
            onTertiary = Sand50,
            background = colors.backdrop,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outlineStrong,
            outlineVariant = colors.outline,
            error = colors.overtime,
            onError = Sand50,
            errorContainer = OvertimeContainerLight,
            onErrorContainer = OvertimeLight,
            inverseSurface = Sand800,
            inverseOnSurface = Sand50,
            scrim = Sand900
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalHourglassColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HourglassTypography,
            shapes = HourglassShapes,
            content = content
        )
    }
}

/** Access point for the sand-specific colours that Material's scheme does not name. */
object HourglassTheme {
    val colors: HourglassColors
        @Composable @ReadOnlyComposable get() = LocalHourglassColors.current
}
