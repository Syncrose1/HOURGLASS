package com.hourglass.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing

/**
 * The card every timer sits in.
 *
 * A resting card is a hairline outline on a flat surface. A running one lifts, takes
 * on the timer's own colour and gets a slow shimmer travelling around its edge — the
 * only thing on screen that moves, so a live timer is unmistakable at a glance.
 */
@Composable
fun TimerCardShell(
    accent: Color,
    active: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    surface: Color = HourglassTheme.colors.surface,
    contentPadding: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = HourglassTheme.colors

    val elevation by animateDpAsState(
        targetValue = if (active) 12.dp else 1.dp,
        animationSpec = tween(durationMillis = 420),
        label = "card_elevation"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (active) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 420),
        label = "card_border_width"
    )
    val borderBrush = rememberBorderBrush(
        accent = accent,
        resting = colors.outline,
        active = active
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = accent,
                spotColor = accent
            )
            .clip(shape)
            .background(surface)
            .border(width = borderWidth, brush = borderBrush, shape = shape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * A flat hairline at rest, a travelling gradient while running. The infinite
 * transition is only composed for an active card, so an idle screen does no work.
 */
@Composable
private fun rememberBorderBrush(accent: Color, resting: Color, active: Boolean): Brush {
    if (!active) return SolidColor(resting)

    val shimmer = rememberInfiniteTransition(label = "card_shimmer")
    val sweep by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "card_shimmer_offset"
    )

    val travel = (sweep * 2f - 0.5f) * BORDER_TRAVEL
    return Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = 0.35f),
            accent,
            accent.copy(alpha = 0.35f)
        ),
        start = Offset(travel, 0f),
        end = Offset(travel + BORDER_TRAVEL, BORDER_TRAVEL)
    )
}

/** Gradient travel in pixels; comfortably wider than any card so the sweep reads. */
private const val BORDER_TRAVEL = 900f
