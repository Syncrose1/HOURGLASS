package com.hourglass.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = HourglassGold,
    secondary = AmberGlass,
    background = Color(0xFF1A1814),
    surface = Color(0xFF252220),
    surfaceVariant = Color(0xFF33302C),
    onPrimary = Color(0xFF1A1814),
    onSecondary = Color(0xFF1A1814),
    onBackground = SandWhite,
    onSurface = SandWhite,
    error = OvertimeRed,
    inverseSurface = SandWhite,
    inverseOnSurface = Color(0xFF1A1814)
)

private val LightColorScheme = lightColorScheme(
    primary = HourglassGold,
    secondary = AmberGlass,
    background = SandWhite,
    surface = GlassBackground,
    surfaceVariant = SandCream,
    onPrimary = Color(0xFF1A1814),
    onSecondary = Color(0xFF1A1814),
    onBackground = Color(0xFF2C2418),
    onSurface = Color(0xFF2C2418),
    error = OvertimeRed,
    inverseSurface = SandDeep,
    inverseOnSurface = SandWhite
)

@Composable
fun HourglassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
