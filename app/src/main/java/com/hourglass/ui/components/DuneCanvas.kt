package com.hourglass.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.hourglass.core.Desert
import com.hourglass.core.Milestone
import com.hourglass.core.duneCurve
import com.hourglass.ui.theme.HourglassTheme
import kotlin.math.sin

/**
 * The dune your banked time has built.
 *
 * Every band is one session, in the colour of the timer that earned it, oldest at the
 * base. The shape is a real dune profile — a long windward slope into a steeper lee
 * face — so the pile reads as a landform rather than a bar chart lying on its side.
 *
 * Nothing here is decorative: the height is your total, the bands are your sessions,
 * and the landmarks are thresholds you actually crossed.
 */
@Composable
fun DuneCanvas(
    desert: Desert,
    nightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors

    val height by animateFloatAsState(
        targetValue = desert.height,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "dune_height"
    )

    val skyTop = if (nightMode) colors.duskTop else colors.backdropTop
    val skyBottom = if (nightMode) colors.duskBottom else colors.accentSoft
    val bandColours = desert.strata.map { colors.sand(it.sand) }

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(listOf(skyTop, skyBottom)),
            size = size
        )

        drawCelestialBody(
            nightMode = nightMode,
            tint = if (nightMode) colors.glassHighlight else colors.accent
        )

        if (desert.isEmpty) {
            drawFlatSand(colors.sand(com.hourglass.core.TimerSand.AMBER).copy(alpha = 0.35f))
            return@Canvas
        }

        // A second, smaller dune behind the main one gives the horizon some depth.
        drawDune(
            heightFraction = height * 0.55f,
            skew = 0.66f,
            bands = listOf(colors.sand(desert.strata.first().sand).copy(alpha = 0.35f)),
            bounds = listOf(0f to 1f)
        )

        drawDune(
            heightFraction = height,
            skew = 0.38f,
            bands = bandColours,
            bounds = desert.strata.map { it.start to it.end }
        )

        desert.reached.forEach { milestone ->
            drawLandmark(
                milestone = milestone,
                heightFraction = height,
                skew = 0.38f,
                tint = colors.frame
            )
        }
    }
}

private fun DrawScope.drawFlatSand(colour: Color) {
    val baseline = size.height * 0.88f
    drawRect(
        color = colour,
        topLeft = Offset(0f, baseline),
        size = androidx.compose.ui.geometry.Size(size.width, size.height - baseline)
    )
}

/**
 * Draws the dune as stacked bands. Each band follows the same silhouette scaled to its
 * own share of the height, so the strata bend with the landform instead of lying flat
 * across it.
 */
private fun DrawScope.drawDune(
    heightFraction: Float,
    skew: Float,
    bands: List<Color>,
    bounds: List<Pair<Float, Float>>
) {
    if (bands.isEmpty()) return
    val baseline = size.height
    val peak = size.height * heightFraction.coerceIn(0f, 1f)
    val steps = SILHOUETTE_STEPS

    bands.forEachIndexed { index, colour ->
        val (start, end) = bounds.getOrElse(index) { 0f to 1f }
        val path = Path()

        // Upper edge of the band, left to right.
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            val y = baseline - duneCurve(t, peak, skew) * end
            val x = t * size.width
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // Lower edge, back again.
        for (step in steps downTo 0) {
            val t = step.toFloat() / steps
            val y = baseline - duneCurve(t, peak, skew) * start
            path.lineTo(t * size.width, y)
        }
        path.close()

        drawPath(path, color = colour)
    }

    // A pale crest line catches the light along the ridge.
    val crest = Path()
    for (step in 0..steps) {
        val t = step.toFloat() / steps
        val y = baseline - duneCurve(t, peak, skew)
        val x = t * size.width
        if (step == 0) crest.moveTo(x, y) else crest.lineTo(x, y)
    }
    drawPath(
        crest,
        color = Color.White.copy(alpha = 0.18f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.006f)
    )
}

private fun DrawScope.drawCelestialBody(nightMode: Boolean, tint: Color) {
    val centre = Offset(size.width * 0.78f, size.height * 0.22f)
    val radius = size.minDimension * 0.07f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tint.copy(alpha = 0.35f), Color.Transparent),
            center = centre,
            radius = radius * 3f
        ),
        radius = radius * 3f,
        center = centre
    )
    drawCircle(
        color = tint.copy(alpha = if (nightMode) 0.85f else 0.95f),
        radius = radius,
        center = centre
    )
}

/**
 * Landmarks stand on the dune's surface at a fixed fraction along it, so they sit on
 * the slope rather than floating over it.
 */
private fun DrawScope.drawLandmark(
    milestone: Milestone,
    heightFraction: Float,
    skew: Float,
    tint: Color
) {
    val t = Milestone.offsetFor(milestone)
    val baseline = size.height
    val peak = size.height * heightFraction.coerceIn(0f, 1f)
    val groundY = baseline - duneCurve(t, peak, skew)
    val x = t * size.width
    val scale = size.minDimension

    when (milestone) {
        Milestone.FIRST_DUNE -> Unit // the dune itself is the landmark

        Milestone.GRASS -> repeat(3) { blade ->
            val offset = (blade - 1) * scale * 0.012f
            drawLine(
                color = tint.copy(alpha = 0.7f),
                start = Offset(x + offset, groundY),
                end = Offset(x + offset + sin(blade.toFloat()) * scale * 0.01f, groundY - scale * 0.035f),
                strokeWidth = scale * 0.005f
            )
        }

        Milestone.SHRUB -> drawCircle(
            color = tint.copy(alpha = 0.55f),
            radius = scale * 0.022f,
            center = Offset(x, groundY - scale * 0.018f)
        )

        Milestone.CACTUS -> {
            val h = scale * 0.075f
            val w = scale * 0.016f
            drawRoundRect(
                color = tint.copy(alpha = 0.8f),
                topLeft = Offset(x - w / 2, groundY - h),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)
            )
            drawRoundRect(
                color = tint.copy(alpha = 0.8f),
                topLeft = Offset(x + w * 0.6f, groundY - h * 0.75f),
                size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.42f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)
            )
        }

        Milestone.OASIS -> drawOval(
            color = Color(0xFF2F8F86).copy(alpha = 0.55f),
            topLeft = Offset(x - scale * 0.06f, groundY - scale * 0.012f),
            size = androidx.compose.ui.geometry.Size(scale * 0.12f, scale * 0.028f)
        )

        Milestone.PYRAMID -> {
            val h = scale * 0.1f
            val path = Path().apply {
                moveTo(x, groundY - h)
                lineTo(x + h * 0.8f, groundY)
                lineTo(x - h * 0.8f, groundY)
                close()
            }
            drawPath(path, color = tint.copy(alpha = 0.7f))
        }
    }
}

private const val SILHOUETTE_STEPS = 48
