package com.hourglass.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.ui.theme.GoldSoft
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing

/**
 * The day, as a bar across the top of everything.
 *
 * It frames the wall rather than competing with it: the timers below divide up the
 * time inside this. The strip of dusk fills left to right as the evening runs down,
 * so the bar is also the day's own progress meter — and the figure is quarter-hour
 * floored, like the notification, so it steps rather than ticks.
 */
@Composable
fun BedtimeBar(
    bedtime: TimeOfDay,
    minutesUntil: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val windingDown = Bedtime.isWindingDown(minutesUntil)
    val rounded = Bedtime.roundForDisplay(minutesUntil)

    // How much of the waking day is gone, so the fill grows through the day.
    val spent by animateFloatAsState(
        targetValue = (1f - minutesUntil / WAKING_MINUTES).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "day_spent"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .background(lerp(colors.surfaceMuted, colors.duskTop, 0.45f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = Bedtime.describeDayRemaining(minutesUntil) }
    ) {
        // The dusk creeps across as the day goes.
        Box(
            modifier = Modifier
                .fillMaxWidth(spent)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colors.duskTop,
                            if (windingDown) colors.duskBottom else colors.duskTop
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HourglassMark(
                size = 26.dp,
                tint = if (windingDown) GoldSoft else Color.White.copy(alpha = 0.8f),
                animated = windingDown
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (rounded <= 0) Bedtime.DAY_OVER
                    else "Your day ends in ${Bedtime.describe(rounded)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bedtime.formatFriendly(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

private val BAR_HEIGHT = 72.dp

/** A nominal waking day, used only to scale the bar's fill. */
private const val WAKING_MINUTES = 16f * 60f
