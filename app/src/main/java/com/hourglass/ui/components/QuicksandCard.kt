package com.hourglass.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.TimerCard

/**
 * Quicksand: the same machinery, deliberately smaller and quieter. It sits on the
 * muted surface with a compact glass and tighter type so it never competes with the
 * sand timers above it.
 */
@Composable
fun QuicksandCard(
    card: TimerCard,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val accent = colors.sand(card.sand)
    val active = !card.isIdle

    TimerCardShell(
        accent = accent,
        active = active,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        surface = colors.surfaceMuted,
        contentPadding = Spacing.md
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HourglassGlass(
                progress = card.progress,
                sandColor = accent,
                running = card.isRunning,
                overtime = card.isOvertime,
                showFrame = false,
                modifier = Modifier.size(width = 34.dp, height = 46.dp)
            )

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = TimeFormat.signedClock(card.remainingMillis),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (card.isOvertime) colors.overtime else colors.textPrimary
                )
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit_timer_named, card.name),
                        tint = colors.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.remove_timer, card.name),
                        tint = colors.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when {
                card.isIdle -> PrimaryAction(
                    label = stringResource(R.string.start),
                    icon = Icons.Rounded.PlayArrow,
                    accent = accent,
                    onClick = onStart,
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
                card.isRunning -> {
                    PrimaryAction(
                        label = stringResource(R.string.pause),
                        icon = Icons.Rounded.Pause,
                        accent = accent,
                        onClick = onPause,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryAction(
                        label = stringResource(R.string.stop),
                        onClick = onStop,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    PrimaryAction(
                        label = stringResource(R.string.resume),
                        icon = Icons.Rounded.PlayArrow,
                        accent = accent,
                        onClick = onResume,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryAction(
                        label = stringResource(R.string.stop),
                        onClick = onStop,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
