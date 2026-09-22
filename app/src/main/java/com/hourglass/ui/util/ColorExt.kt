package com.hourglass.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/** Parses a stored `#AARRGGBB` / `#RRGGBB` swatch, falling back when it is unreadable. */
fun String?.toTimerColour(fallback: Color): Color {
    val hex = this?.trim()?.removePrefix("#") ?: return fallback
    val value = when (hex.length) {
        6 -> hex.toLongOrNull(16)?.or(0xFF000000L)
        8 -> hex.toLongOrNull(16)
        else -> null
    } ?: return fallback
    return Color(value.toInt())
}

/** Serialises a swatch in the `#AARRGGBB` form the database stores. */
fun Color.toHex(): String = String.format(Locale.US, "#%08X", toArgb())
