package com.hourglass.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hourglass.core.DayTotal
import com.hourglass.core.TaskTotal
import com.hourglass.core.TimeFormat
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing

/*
 * Chart marks.
 *
 * One measure, one hue: focus time is a magnitude, not an identity, so the week reads
 * as a single sequential series with today picked out — emphasis rather than a
 * categorical palette that would imply the days mean different things.
 *
 * Specs held constant across both charts: marks capped at 24dp so a band always keeps
 * some air, a 4dp radius on the data end and square at the baseline, hairline
 * recessive gridlines, and values labelled selectively rather than on every mark.
 */

private val MARK_THICKNESS = 20.dp
private val MARK_RADIUS = 4.dp
private val COLUMN_PLOT_HEIGHT = 128.dp

/** Focus per day across the window. Today carries the accent; the rest recede. */
@Composable
fun FocusColumns(
    days: List<DayTotal>,
    todayEpochDay: Long,
    dayLabel: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val peak = days.maxOfOrNull { it.millis } ?: 0L
    // The single labelled mark: the best day, or today when nothing stands out.
    val labelledDay = days.maxByOrNull { it.millis }?.takeIf { it.millis > 0 }?.epochDay

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(COLUMN_PLOT_HEIGHT),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { day ->
                val isToday = day.epochDay == todayEpochDay
                val fraction = if (peak <= 0L) 0f else day.millis.toFloat() / peak
                val height by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                    label = "column_height"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clearAndSetSemantics {
                            contentDescription =
                                "${dayLabel(day.epochDay)}: ${TimeFormat.compact(day.millis)}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (day.epochDay == labelledDay) {
                        Text(
                            text = TimeFormat.compact(day.millis),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(max = MARK_THICKNESS)
                            .fillMaxWidth()
                            // A day with nothing on it still shows a seed of a mark, so
                            // the row reads as seven days rather than four.
                            .fillMaxHeight(height.coerceAtLeast(if (day.millis > 0) 0.04f else 0.012f))
                            .clip(
                                RoundedCornerShape(
                                    topStart = MARK_RADIUS,
                                    topEnd = MARK_RADIUS
                                )
                            )
                            .background(if (isToday) colors.accent else colors.chartMuted)
                    )
                }
            }
        }

        // Hairline baseline: recessive, solid, one step off the surface.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.chartGrid)
        )

        Spacer(Modifier.height(Spacing.sm))

        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val isToday = day.epochDay == todayEpochDay
                Text(
                    text = dayLabel(day.epochDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) colors.textPrimary else colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Time banked per task. Horizontal because task names are long, and each row is
 * labelled with its name — identity never rests on the sand colour alone.
 */
@Composable
fun TaskBars(
    tasks: List<TaskTotal>,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val peak = tasks.maxOfOrNull { it.millis } ?: 0L

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        tasks.forEach { task ->
            val fraction = if (peak <= 0L) 0f else task.millis.toFloat() / peak
            val width by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                label = "bar_width"
            )
            val fill = if (task.isOther) colors.chartMuted else colors.sand(task.sand)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription =
                            "${task.name}: ${TimeFormat.compact(task.millis)}, " +
                                "${task.sessions} sessions"
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = TimeFormat.compact(task.millis),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(topEnd = MARK_RADIUS, bottomEnd = MARK_RADIUS))
                        .background(colors.chartGrid)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(width.coerceAtLeast(0.02f))
                            .height(8.dp)
                            .clip(
                                RoundedCornerShape(
                                    topEnd = MARK_RADIUS,
                                    bottomEnd = MARK_RADIUS
                                )
                            )
                            .background(fill)
                    )
                }
            }
        }
    }
}

/**
 * The one number the screen leads with. Proportional figures, not tabular: at this
 * size tabular digits look loose.
 */
@Composable
fun HeroFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.displayLarge,
            color = colors.textPrimary,
            maxLines = 1
        )
    }
}

/** `label · value`, with an optional supporting line. No plot, so no hover layer. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    support: String? = null
) {
    val colors = HourglassTheme.colors
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .padding(vertical = Spacing.md, horizontal = Spacing.md)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
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
