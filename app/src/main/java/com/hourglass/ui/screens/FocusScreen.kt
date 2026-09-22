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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hourglass.R
import com.hourglass.core.Direction
import com.hourglass.core.HourglassSim
import com.hourglass.core.SandGrid
import com.hourglass.core.TimeFormat
import com.hourglass.ui.components.SandCanvas
import com.hourglass.ui.components.rememberTiltDirection
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
    val overtimeSand = colors.overtime

    val sim = remember(card.ref) {
        HourglassSim(SandGrid(GRID_WIDTH, GRID_HEIGHT), neckHalfWidth = 1)
            .also { it.fill(tint = SAND_TINT) }
    }

    val tilt by rememberTiltDirection(enabled = true)

    val glow by animateFloatAsState(
        targetValue = if (card.isRunning) 1f else 0.4f,
        animationSpec = tween(durationMillis = 600),
        label = "focus_glow"
    )

    BackHandler(onBack = onPause)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.backdropTop,
                        colors.backdrop,
                        sand.copy(alpha = 0.10f * glow)
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
                .padding(Spacing.xl),
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

            SandCanvas(
                grid = sim.grid,
                tints = listOf(sand, overtimeSand),
                wallColor = colors.glass.copy(alpha = 0.22f),
                running = card.isRunning,
                gravity = if (card.isRunning) tilt else Direction.S,
                onBeforeStep = { grid ->
                    if (card.isOvertime) {
                        // The glass does not stop when the allocation does: overtime
                        // keeps raining into the lower chamber, in its own colour.
                        grid.pour(
                            x = grid.width / 2,
                            y = sim.neckRow + 1,
                            amount = OVERTIME_POUR_PER_FRAME,
                            tint = OVERTIME_TINT,
                            spread = 2
                        )
                    } else {
                        sim.syncTo(card.progress)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .weight(1f)
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
        }

        // The one control. Quiet, because finishing should be a decision rather than
        // the thing your thumb lands on.
        TextButton(
            onClick = onFinish,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.finish_session),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary
            )
        }
    }
}

/**
 * Grid resolution for the focus glass. Coarse enough that a grain reads as a grain at
 * arm's length, fine enough that the heaps look like sand rather than gravel.
 */
private const val GRID_WIDTH = 54
private const val GRID_HEIGHT = 96
private const val SAND_TINT = 1
private const val OVERTIME_TINT = 2

/** A trickle, not a flood: overtime should nag, not bury the glass. */
private const val OVERTIME_POUR_PER_FRAME = 1
