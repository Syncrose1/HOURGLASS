package com.hourglass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.ui.components.DuneCanvas
import com.hourglass.ui.components.FocusColumns
import com.hourglass.ui.components.HourglassTopBar
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.DesertViewModel
import java.text.DateFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the time adds up to.
 *
 * Forest grows a wood; this pours a dune. Every session you finish becomes a visible
 * band in the colour of the timer that earned it, so the pile is not a score but a
 * cross-section of how the time was actually spent — and the only way to add to it is
 * to run a timer through to the end.
 *
 * One screen, no scrolling, like everywhere else.
 */
@Composable
fun DesertScreen(
    onBack: () -> Unit,
    viewModel: DesertViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = HourglassTheme.colors
    val dayLabels = remember { weekdayInitials() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        HourglassTopBar(
            title = stringResource(R.string.desert),
            onBack = onBack
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Spacing.lg)
                .clip(MaterialTheme.shapes.large)
        ) {
            DuneCanvas(
                desert = state.desert,
                nightMode = state.nightMode,
                modifier = Modifier.fillMaxSize()
            )

            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = TimeFormat.compact(state.desert.totalMillis),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.total_banked),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            if (state.desert.isEmpty) {
                Text(
                    text = stringResource(R.string.desert_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Spacing.xxl)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        MilestoneLine(
            label = state.milestoneLabel(),
            progress = state.desert.milestoneProgress,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )

        Spacer(Modifier.height(Spacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Stat(
                label = stringResource(R.string.sessions),
                value = state.insights.sessionCount.toString(),
                support = stringResource(R.string.overran_count, state.insights.overrunCount)
            )
            Stat(
                label = stringResource(R.string.on_time),
                value = stringResource(
                    R.string.percent,
                    (state.insights.completionRate * 100).roundToInt()
                )
            )
            Stat(
                label = stringResource(R.string.streak),
                value = stringResource(R.string.streak_days, state.insights.streakDays)
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        if (state.topTasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                state.topTasks.forEach { task ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.sand(task.sand))
                        )
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Column {
                            Text(
                                text = task.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = TimeFormat.compact(task.millis),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }

        FocusColumns(
            days = state.insights.days,
            todayEpochDay = state.insights.days.lastOrNull()?.epochDay ?: 0L,
            dayLabel = { epochDay -> dayLabels[weekdayIndex(epochDay)] },
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )

        Spacer(Modifier.height(Spacing.xl))
    }
}

/** `label` above `value`, with an optional supporting line. No plot, so no chrome. */
@Composable
private fun Stat(label: String, value: String, support: String? = null) {
    val colors = HourglassTheme.colors
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1
        )
        if (support != null) {
            Text(
                text = support,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Progress toward the next landmark, as a single hairline track. */
@Composable
private fun MilestoneLine(label: String, progress: Float, modifier: Modifier = Modifier) {
    val colors = HourglassTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.chartGrid)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
            )
        }
    }
}

/** Weekday initials in the device's locale, indexed Sunday-first. */
private fun weekdayInitials(): List<String> {
    val short = DateFormatSymbols.getInstance().shortWeekdays // index 1..7; 0 is unused
    return (1..7).map { index ->
        short.getOrNull(index)?.take(1)?.uppercase(Locale.getDefault()).orEmpty()
    }
}

/** Sunday = 0. 1970-01-01 was a Thursday, which is index 4. */
private fun weekdayIndex(epochDay: Long): Int = (((epochDay + 4L) % 7 + 7) % 7).toInt()
