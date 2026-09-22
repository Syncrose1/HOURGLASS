package com.hourglass.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.hourglass.core.TimerSand

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

/*
 * Timer sands.
 *
 * Eight hues, stepped separately for each surface rather than flipped between them.
 * Both sets were checked with a colour-vision validator: adjacent swatches clear
 * ΔE 15 for normal vision and ΔE 15 under protanopia, deuteranopia and tritanopia.
 *
 * Several sands sit below 3:1 against their surface, which is fine here and only
 * because of how they are used: a sand is always accompanied by the timer's name in
 * a text colour, never asked to carry identity by itself.
 */

private val LightSands = mapOf(
    TimerSand.AMBER to Color(0xFFD9A949),
    TimerSand.TERRACOTTA to Color(0xFFA14122),
    TimerSand.ROSE to Color(0xFFEB8D8A),
    TimerSand.PLUM to Color(0xFF84467F),
    TimerSand.LAVENDER to Color(0xFFAAA1F6),
    TimerSand.INDIGO to Color(0xFF3063A6),
    TimerSand.AQUA to Color(0xFF33C1C2),
    TimerSand.OLIVE to Color(0xFF3D701E)
)

private val DarkSands = mapOf(
    TimerSand.AMBER to Color(0xFFB48B39),
    TimerSand.TERRACOTTA to Color(0xFF94472F),
    TimerSand.ROSE to Color(0xFFCD7673),
    TimerSand.PLUM to Color(0xFF82477E),
    TimerSand.LAVENDER to Color(0xFF8F87D2),
    TimerSand.INDIGO to Color(0xFF33619D),
    TimerSand.AQUA to Color(0xFF12A7A8),
    TimerSand.OLIVE to Color(0xFF426E2A)
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
    val scrim: Color,
    /** Chart ink one step off the surface: gridlines, baselines, empty tracks. */
    val chartGrid: Color,
    /** Columns that are present but not the subject — the de-emphasis step. */
    val chartMuted: Color,
    val sands: Map<TimerSand, Color>
) {
    /** The step for [sand] on this theme's surface. */
    fun sand(sand: TimerSand): Color = sands.getValue(sand)

    /** Every sand in picker order. */
    fun allSands(): List<Pair<TimerSand, Color>> =
        TimerSand.entries.map { it to sands.getValue(it) }
}

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
    scrim = Color(0x14000000),
    chartGrid = Sand200,
    chartMuted = Sand300,
    sands = LightSands
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
    scrim = Color(0x33000000),
    chartGrid = Color(0xFF362D24),
    chartMuted = Color(0xFF4A3D30),
    sands = DarkSands
)

internal fun hourglassColors(darkTheme: Boolean): HourglassColors =
    if (darkTheme) DarkColors else LightColors

val LocalHourglassColors = staticCompositionLocalOf { LightColors }
