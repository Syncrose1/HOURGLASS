package com.hourglass.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hourglass.ui.theme.HourglassTheme
import kotlin.math.sin

/**
 * The sandglass.
 *
 * Everything is derived from the canvas size, so one drawing serves the 132dp timer
 * face, the 56dp empty-state mark and the 24dp wordmark glyph without any of the
 * hard-coded pixel constants that used to make it shrink into a corner on dense screens.
 */
@Composable
fun HourglassGlass(
    /** Fraction of the allocation consumed: 0f is a full upper bulb, 1f a drained one. */
    progress: Float,
    sandColor: Color,
    running: Boolean,
    overtime: Boolean,
    modifier: Modifier = Modifier,
    showFrame: Boolean = true
) {
    val colors = HourglassTheme.colors

    // Smooth the drain so a one-second tick reads as sand settling, not a jump.
    val drawnProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "sand_progress"
    )

    // Grain phase only animates while the timer runs — a paused card costs nothing.
    val grainPhase = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (running) {
            grainPhase.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            grainPhase.snapTo(0f)
        }
    }

    val breath = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (running) {
            breath.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            breath.snapTo(0f)
        }
    }

    val sand = if (overtime) colors.overtime else sandColor

    Canvas(modifier = modifier) {
        val geometry = HourglassGeometry(size)

        if (running) {
            // Warm halo behind the glass; pulses gently so a live timer draws the eye.
            val pulse = 0.5f + 0.5f * sin(breath.value * 2f * Math.PI).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(sand.copy(alpha = 0.20f + 0.06f * pulse), Color.Transparent),
                    center = geometry.center,
                    radius = geometry.haloRadius
                ),
                radius = geometry.haloRadius,
                center = geometry.center
            )
        }

        drawGlassBody(geometry, colors.glass, colors.isDark)
        drawSand(geometry, drawnProgress, sand, grainPhase.value, running, overtime)
        drawGlassHighlight(geometry, colors.glassHighlight)
        if (showFrame) drawFrame(geometry, colors.frame)
    }
}

/** Static mark for the wordmark, empty state and screen headers. */
@Composable
fun HourglassMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color? = null,
    animated: Boolean = false
) {
    val colors = HourglassTheme.colors
    val sand = tint ?: colors.accent

    val fall = remember { Animatable(0f) }
    LaunchedEffect(animated) {
        if (animated) {
            fall.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            fall.snapTo(0.55f)
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val geometry = HourglassGeometry(this.size)
        drawGlassBody(geometry, sand.copy(alpha = 0.35f), colors.isDark)
        drawSand(geometry, fall.value, sand, fall.value, animated, overtime = false)
        drawFrame(geometry, sand.copy(alpha = 0.85f))
    }
}

/**
 * Every measurement the drawing needs, resolved once per frame from the canvas box.
 *
 * The silhouette is a classic sandglass: near-vertical bulb walls that sweep into a
 * narrow waist, capped top and bottom by a frame.
 */
private class HourglassGeometry(canvas: Size) {
    /** The drawing is 3:4; letterbox inside whatever box we were given. */
    private val boxHeight = minOf(canvas.height, canvas.width * 4f / 3f)
    private val boxWidth = boxHeight * 3f / 4f
    private val originX = (canvas.width - boxWidth) / 2f
    private val originY = (canvas.height - boxHeight) / 2f

    val centerX = originX + boxWidth / 2f
    val capHeight = boxHeight * 0.055f
    val capWidth = boxWidth * 0.96f
    val topY = originY + capHeight
    val bottomY = originY + boxHeight - capHeight
    val midY = (topY + bottomY) / 2f

    val bulbHalfWidth = boxWidth * 0.40f
    val neckHalfWidth = boxWidth * 0.045f
    val bulbHeight = midY - topY

    val center = Offset(centerX, originY + boxHeight / 2f)
    val haloRadius = boxWidth * 0.95f
    val rimWidth = (boxWidth * 0.018f).coerceAtLeast(1f)
    val grainRadius = (boxWidth * 0.018f).coerceAtLeast(0.8f)
    val capRadius = capHeight / 2f
    val capLeft = centerX - capWidth / 2f
    val capTop = originY
    val capBottom = originY + boxHeight - capHeight

    /** Outline of one bulb. [top] selects the upper chamber. */
    fun bulb(top: Boolean): Path {
        val outerY = if (top) topY else bottomY
        val left = centerX - bulbHalfWidth
        val right = centerX + bulbHalfWidth
        val neckLeft = centerX - neckHalfWidth
        val neckRight = centerX + neckHalfWidth
        // Control points keep the wall vertical where it meets the cap, then pull it
        // hard into the waist — that late bend is what makes the shape read as glass.
        val c1 = if (top) outerY + bulbHeight * 0.52f else outerY - bulbHeight * 0.52f
        val c2 = if (top) outerY + bulbHeight * 0.86f else outerY - bulbHeight * 0.86f
        return Path().apply {
            moveTo(left, outerY)
            cubicTo(left, c1, neckLeft, c2, neckLeft, midY)
            lineTo(neckRight, midY)
            cubicTo(neckRight, c2, right, c1, right, outerY)
            close()
        }
    }
}

private fun DrawScope.drawGlassBody(geometry: HourglassGeometry, glass: Color, isDark: Boolean) {
    val top = geometry.bulb(top = true)
    val bottom = geometry.bulb(top = false)

    val fill = Brush.verticalGradient(
        colors = listOf(
            glass.copy(alpha = if (isDark) 0.16f else 0.10f),
            glass.copy(alpha = if (isDark) 0.06f else 0.04f)
        ),
        startY = geometry.topY,
        endY = geometry.bottomY
    )
    drawPath(top, brush = fill)
    drawPath(bottom, brush = fill)

    val rim = Stroke(width = geometry.rimWidth)
    drawPath(top, color = glass.copy(alpha = 0.55f), style = rim)
    drawPath(bottom, color = glass.copy(alpha = 0.55f), style = rim)
}

private fun DrawScope.drawSand(
    geometry: HourglassGeometry,
    progress: Float,
    sand: Color,
    grainPhase: Float,
    running: Boolean,
    overtime: Boolean
) {
    val p = progress.coerceIn(0f, 1f)
    val topBulb = geometry.bulb(top = true)
    val bottomBulb = geometry.bulb(top = false)

    // Upper chamber: the sand surface descends, dipping into a funnel above the neck.
    if (p < 1f) {
        val surfaceY = geometry.topY + geometry.bulbHeight * p
        val dip = (geometry.midY - surfaceY).coerceAtLeast(0f) * 0.22f
        val left = geometry.centerX - geometry.bulbHalfWidth
        val right = geometry.centerX + geometry.bulbHalfWidth
        val upperSand = Path().apply {
            moveTo(left, surfaceY)
            quadraticBezierTo(geometry.centerX, surfaceY + dip * 1.6f, right, surfaceY)
            lineTo(right, geometry.midY + 1f)
            lineTo(left, geometry.midY + 1f)
            close()
        }
        clipPath(topBulb) {
            drawPath(
                upperSand,
                brush = Brush.verticalGradient(
                    colors = listOf(sand.copy(alpha = 0.95f), sand),
                    startY = surfaceY,
                    endY = geometry.midY
                )
            )
        }
    }

    // Lower chamber: a heap that grows, peaking under the neck.
    if (p > 0f) {
        val moundHeight = geometry.bulbHeight * p
        val edgeHeight = moundHeight * 0.42f
        val left = geometry.centerX - geometry.bulbHalfWidth
        val right = geometry.centerX + geometry.bulbHalfWidth
        val lowerSand = Path().apply {
            moveTo(left, geometry.bottomY)
            lineTo(left, geometry.bottomY - edgeHeight)
            quadraticBezierTo(
                geometry.centerX,
                geometry.bottomY - moundHeight * 1.5f,
                right,
                geometry.bottomY - edgeHeight
            )
            lineTo(right, geometry.bottomY)
            close()
        }
        clipPath(bottomBulb) {
            drawPath(
                lowerSand,
                brush = Brush.verticalGradient(
                    colors = listOf(sand, sand.copy(alpha = 0.82f)),
                    startY = geometry.bottomY - moundHeight,
                    endY = geometry.bottomY
                )
            )
        }
    }

    // The stream through the waist, plus a few grains chasing each other down it.
    val streaming = running && (p < 1f || overtime)
    if (streaming) {
        val streamTop = geometry.midY - geometry.bulbHeight * 0.05f
        val streamBottom = geometry.bottomY - geometry.bulbHeight * (p * 0.9f)
        if (streamBottom > streamTop) {
            clipPath(bottomBulb) {
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(sand, sand.copy(alpha = 0.35f)),
                        startY = streamTop,
                        endY = streamBottom
                    ),
                    start = Offset(geometry.centerX, streamTop),
                    end = Offset(geometry.centerX, streamBottom),
                    strokeWidth = geometry.rimWidth * 1.4f
                )
                val span = streamBottom - streamTop
                repeat(GRAIN_COUNT) { index ->
                    val offset = (grainPhase + index.toFloat() / GRAIN_COUNT) % 1f
                    val y = streamTop + span * offset
                    val drift = sin((offset * 6f).toDouble()).toFloat() * geometry.neckHalfWidth * 0.6f
                    drawCircle(
                        color = sand,
                        radius = geometry.grainRadius * (1f - offset * 0.35f),
                        center = Offset(geometry.centerX + drift, y),
                        alpha = (1f - offset).coerceIn(0.15f, 1f)
                    )
                }
            }
        }
    }
}

/** A soft specular streak down the left of the glass, and a brighter kiss on the waist. */
private fun DrawScope.drawGlassHighlight(geometry: HourglassGeometry, highlight: Color) {
    val shine = Path().apply {
        addOval(
            Rect(
                offset = Offset(
                    geometry.centerX - geometry.bulbHalfWidth * 0.78f,
                    geometry.topY + geometry.bulbHeight * 0.14f
                ),
                size = Size(geometry.bulbHalfWidth * 0.30f, geometry.bulbHeight * 0.52f)
            )
        )
    }
    val upper = geometry.bulb(top = true)
    val lower = geometry.bulb(top = false)
    val clip = Path().apply { op(upper, lower, PathOperation.Union) }
    clipPath(clip) {
        drawPath(shine, color = highlight.copy(alpha = 0.22f))
    }
}

private fun DrawScope.drawFrame(geometry: HourglassGeometry, frame: Color) {
    val brush = Brush.horizontalGradient(
        colors = listOf(
            frame.copy(alpha = 0.55f),
            frame.copy(alpha = 0.95f),
            frame.copy(alpha = 0.55f)
        ),
        startX = geometry.capLeft,
        endX = geometry.capLeft + geometry.capWidth
    )
    listOf(geometry.capTop, geometry.capBottom).forEach { y ->
        translate(left = geometry.capLeft, top = y) {
            drawRoundRect(
                brush = brush,
                size = Size(geometry.capWidth, geometry.capHeight),
                cornerRadius = CornerRadius(geometry.capRadius, geometry.capRadius)
            )
        }
    }
}

private const val GRAIN_COUNT = 5
