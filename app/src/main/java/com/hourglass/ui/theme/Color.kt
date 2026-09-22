package com.hourglass.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Raw palette.
 *
 * One warm neutral ramp carries every surface, border and text colour; the accents
 * only ever appear as sand, glow or state. Keeping the ramp continuous is what stops
 * the app looking like a set of unrelated cards.
 */

val Sand50 = Color(0xFFFCF8F1)
val Sand100 = Color(0xFFF5EEE1)
val Sand200 = Color(0xFFEBE0CC)
val Sand300 = Color(0xFFDACBB0)
val Sand400 = Color(0xFFC2AE8E)
val Sand500 = Color(0xFFA18A69)
val Sand600 = Color(0xFF7E6A4F)
val Sand700 = Color(0xFF5B4B37)
val Sand800 = Color(0xFF3B3125)
val Sand900 = Color(0xFF262019)
val Sand950 = Color(0xFF171310)

/** Accent — the gold of falling sand. */
val Gold = Color(0xFFE0A63C)
val GoldSoft = Color(0xFFF2CA7C)
val GoldDeep = Color(0xFFB07E23)

/** Dusk — the bedtime card's gradient, evening light turning over into night. */
val DuskIndigo = Color(0xFF3B3E6E)
val DuskViolet = Color(0xFF5C4A72)
val DuskAmber = Color(0xFF9A6A3C)

/** State. */
val OvertimeLight = Color(0xFFC0503F)
val OvertimeDark = Color(0xFFE98267)
val OvertimeContainerLight = Color(0xFFF7E1DB)

/**
 * The eight sand colours a timer can be given. Ordered as a warm-to-cool sweep so
 * the picker reads as a single ribbon rather than a bag of swatches.
 */
val TimerPalette: List<Color> = listOf(
    Color(0xFFE0A63C), // amber
    Color(0xFFE08A4B), // apricot
    Color(0xFFD4604A), // terracotta
    Color(0xFFC2506E), // rose
    Color(0xFF8A5E9E), // plum
    Color(0xFF52689E), // indigo
    Color(0xFF2F8F86), // teal
    Color(0xFF6F8F45)  // olive
)

/**
 * Semantic colours for everything the Material scheme does not name well — glass,
 * sand, glow, hairline borders. Provided through [LocalHourglassColors] so a composable
 * never has to reach for a literal and never renders light-on-light in dark mode.
 */
@Immutable
data class HourglassColors(
    val isDark: Boolean,
    /** Page background, bottom of the ambient gradient. */
    val backdrop: Color,
    /** Page background, top of the ambient gradient. */
    val backdropTop: Color,
    /** Card fill. */
    val surface: Color,
    /** Card fill for the quieter quicksand cards. */
    val surfaceMuted: Color,
    /** Hairline card outline at rest. */
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val overtime: Color,
    /** Glass body of the hourglass drawing. */
    val glass: Color,
    val glassHighlight: Color,
    /** Caps at the top and bottom of the hourglass drawing. */
    val frame: Color,
    val duskTop: Color,
    val duskBottom: Color,
    val scrim: Color
)

private val LightColors = HourglassColors(
    isDark = false,
    backdrop = Sand100,
    backdropTop = Sand50,
    surface = Color(0xFFFFFCF6),
    surfaceMuted = Sand100,
    outline = Sand200,
    outlineStrong = Sand300,
    textPrimary = Sand900,
    textSecondary = Sand600,
    textMuted = Sand500,
    accent = GoldDeep,
    accentSoft = Color(0xFFF7E3BC),
    onAccent = Color(0xFFFFFBF2),
    overtime = OvertimeLight,
    glass = Sand400,
    glassHighlight = Color(0xFFFFFFFF),
    frame = Sand600,
    duskTop = DuskIndigo,
    duskBottom = DuskAmber,
    scrim = Color(0x14000000)
)

private val DarkColors = HourglassColors(
    isDark = true,
    backdrop = Sand950,
    backdropTop = Color(0xFF221B15),
    surface = Color(0xFF221C16),
    surfaceMuted = Color(0xFF1C1712),
    outline = Color(0xFF362D24),
    outlineStrong = Color(0xFF4A3D30),
    textPrimary = Sand100,
    textSecondary = Sand400,
    textMuted = Sand500,
    accent = Gold,
    accentSoft = Color(0xFF4A3617),
    onAccent = Sand950,
    overtime = OvertimeDark,
    glass = Sand600,
    glassHighlight = Color(0xFFFFE8BC),
    frame = Sand500,
    duskTop = Color(0xFF232544),
    duskBottom = Color(0xFF5E3F26),
    scrim = Color(0x33000000)
)

internal fun hourglassColors(darkTheme: Boolean): HourglassColors =
    if (darkTheme) DarkColors else LightColors

val LocalHourglassColors = staticCompositionLocalOf { LightColors }
