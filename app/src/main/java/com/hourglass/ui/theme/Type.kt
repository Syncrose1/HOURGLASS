package com.hourglass.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Two voices only.
 *
 * Numbers are thin and wide open — a timer face should feel like glass, not a receipt.
 * Labels are small, bold and letter-spaced, so section headings read as quiet captions
 * rather than competing with the numbers they introduce.
 */

private fun display(size: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.12).sp,
    letterSpacing = tracking.sp
)

private fun label(size: Int, tracking: Double) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = (size * 1.35).sp,
    letterSpacing = tracking.sp
)

val HourglassTypography = Typography(
    displayLarge = display(60, FontWeight.ExtraLight, -1.5),
    displayMedium = display(46, FontWeight.ExtraLight, -1.0),
    displaySmall = display(36, FontWeight.Light, -0.5),

    headlineLarge = display(30, FontWeight.Light, -0.25),
    headlineMedium = display(26, FontWeight.Light, 0.0),
    headlineSmall = display(22, FontWeight.Normal, 0.0),

    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),

    labelLarge = label(14, 0.6),
    labelMedium = label(11, 1.6),
    labelSmall = label(10, 1.8)
)

/** The wordmark: wide-tracked and airy, used for "HOURGLASS" and for section captions. */
val WordmarkStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 17.sp,
    letterSpacing = 5.sp
)
