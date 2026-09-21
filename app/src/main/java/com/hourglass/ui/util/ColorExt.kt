package com.hourglass.ui.util

import androidx.compose.ui.graphics.Color
import android.graphics.Color as AColor

fun String.toColorOrNull(): Color? {
    return try {
        Color(AColor.parseColor(this))
    } catch (_: Exception) {
        null
    }
}
