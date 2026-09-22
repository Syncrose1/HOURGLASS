package com.hourglass.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.ui.util.toTimerColour
import com.hourglass.viewmodel.TimerCard

/** A full-weight sand timer: the things the user planned their day around. */
@Composable
fun SandTimerCard(
    card: TimerCard,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val accent = card.colourHex.toTimerColour(colors.accent)
    val active = !card.isIdle
    val timeColour = if (card.isOvertime) colors.overtime else colors.textPrimary

    TimerCardShell(
        accent = accent,
        active = active,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
        contentPadding = Spacing.lg
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HourglassGlass(
                progress = card.progress,
                sandColor = accent,
                running = card.isRunning,
                overtime = card.isOvertime,
                modifier = Modifier.size(width = 66.dp, height = 88.dp)
            )

            Spacer(Modifier.width(Spacing.lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = TimeFormat.signedClock(card.remainingMillis),
                    style = MaterialTheme.typography.displaySmall,
                    color = timeColour,
                    modifier = Modifier.semantics {
                        contentDescription = card.name + " " +
                            TimeFormat.compact(card.remainingMillis)
                    }
                )
                Spacer(Modifier.height(Spacing.sm))
                StatusLine(card = card, accent = accent)
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.remove_timer, card.name),
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        SandTrack(
            progress = card.progress,
            accent = accent,
            overtime = card.isOvertime
        )

        Spacer(Modifier.height(Spacing.lg))

        TimerControls(
            card = card,
            accent = accent,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop
        )
    }
}

/** Allocation, session count, and the live state badge. */
@Composable
private fun StatusLine(card: TimerCard, accent: Color) {
    val colors = HourglassTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = card.isOvertime || card.isPaused,
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
        ) {
            Row {
                StateBadge(
                    label = stringResource(
                        if (card.isOvertime) R.string.overtime else R.string.paused
                    ),
                    tint = if (card.isOvertime) colors.overtime else colors.textSecondary
                )
                Spacer(Modifier.width(Spacing.sm))
            }
        }
        Text(
            text = stringResource(
                R.string.allocation_and_sessions,
                TimeFormat.compact(card.durationMillis),
                card.sessionsCompleted
            ),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (card.isRunning) {
            Spacer(Modifier.width(Spacing.sm))
            RunningDot(accent)
        }
    }
}

@Composable
private fun StateBadge(label: String, tint: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    )
}

@Composable
private fun RunningDot(accent: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(accent)
    )
}

/** A thin bed of sand that fills as the allocation is consumed. */
@Composable
fun SandTrack(
    progress: Float,
    accent: Color,
    overtime: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val fill = if (overtime) colors.overtime else accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(colors.outline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(listOf(fill.copy(alpha = 0.55f), fill))
                )
        )
    }
}

@Composable
private fun TimerControls(
    card: TimerCard,
    accent: Color,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        when {
            card.isIdle -> PrimaryAction(
                label = stringResource(R.string.start),
                icon = Icons.Rounded.PlayArrow,
                accent = accent,
                onClick = onStart,
                modifier = Modifier.weight(1f)
            )
            card.isRunning -> {
                PrimaryAction(
                    label = stringResource(R.string.pause),
                    icon = Icons.Rounded.Pause,
                    accent = accent,
                    onClick = onPause,
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(
                    label = stringResource(R.string.stop),
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                )
            }
            else -> {
                PrimaryAction(
                    label = stringResource(R.string.resume),
                    icon = Icons.Rounded.PlayArrow,
                    accent = accent,
                    onClick = onResume,
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(
                    label = stringResource(R.string.stop),
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
