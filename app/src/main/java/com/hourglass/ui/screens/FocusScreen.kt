package com.hourglass.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.ui.components.lerp
import com.hourglass.ui.world.WorldRegistry
import com.hourglass.ui.world.WorldView
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.TimerCard

/**
 * A running timer, and nothing else.
 *
 * The screen has one gesture: tap to pause and go back to the wall. There is no way to
 * rename, recolour or reschedule anything from here, on purpose — time spent
 * rearranging the app while the clock runs is time the clock should not be counting.
 * If you want to change something, stop the timer first.
 *
 * The sand is a live simulation metered by the timer's own progress, so what you are
 * watching is the elapsed time, not a loop.
 */
@Composable
fun FocusScreen(
    card: TimerCard,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val sand = colors.sand(card.sand)

    val glow by animateFloatAsState(
        targetValue = if (card.isRunning) 1f else 0.4f,
        animationSpec = tween(durationMillis = 600),
        label = "focus_glow"
    )

    BackHandler(onBack = onPause)

    Box(
        modifier = modifier
            .fillMaxSize()
            // Every stop is opaque: an alpha here let the tile wall show straight
            // through the focus view, which is the one screen that must be alone.
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.backdropTop,
                        colors.backdrop,
                        lerp(colors.backdrop, sand, 0.10f * glow)
                    )
                )
            )
            .clickable(
                // No ripple: the whole screen is the target, and a full-bleed splash
                // on every tap would be louder than anything else in the app.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPause
            )
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.lg))

            // The world the timer is building. It fills the screen: this is the
            // thing to watch, and the only thing on it.
            val session = remember(card.ref, card.sessionStartedAt, card.durationMillis, card.world) {
                WorldRegistry.obtain(card.ref, card.sessionStartedAt, card.durationMillis, card.world)
            }
            WorldView(
                session = session,
                mineral = sand,
                running = card.isRunning,
                timerProgress = card.timerProgress,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = session.world.objective,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = TimeFormat.signedClock(card.remainingMillis),
                style = MaterialTheme.typography.displayLarge,
                color = if (card.isOvertime) colors.overtime else colors.textPrimary,
                maxLines = 1
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = stringResource(
                    if (card.isRunning) R.string.tap_to_pause else R.string.tap_to_resume
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )

            Spacer(Modifier.height(Spacing.lg))

            // The one control, and the last thing in the column rather than floating
            // over it — absolute positioning had it landing on top of the clock.
            TextButton(onClick = onFinish) {
                Text(
                    text = stringResource(R.string.finish_session),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
            }
        }
    }
}

/**
 * Grid resolution for the focus glass. Coarse enough that a grain reads as a grain at
 * arm's length, fine enough that the heaps look like sand rather than gravel.
 */
private const val GRID_WIDTH = 62
private const val GRID_HEIGHT = 108
