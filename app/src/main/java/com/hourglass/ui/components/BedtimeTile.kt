package com.hourglass.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.core.TileDetail
import com.hourglass.ui.theme.GoldSoft
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing

/**
 * The tile that frames the day.
 *
 * It is the only tile that is not sand-coloured — a strip of dusk on the wall, warming
 * as the evening closes. The figure is quarter-hour rounded like the notification, so
 * the tile and the shade both step rather than tick.
 */
@Composable
fun BedtimeTile(
    bedtime: TimeOfDay,
    minutesUntil: Int,
    detail: TileDetail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val windingDown = Bedtime.isWindingDown(minutesUntil)
    val warmth = Bedtime.windDownProgress(minutesUntil)
    val rounded = Bedtime.roundForDisplay(minutesUntil)

    TileShell(
        modifier = modifier,
        accent = GoldSoft,
        active = windingDown,
        onClick = onClick,
        onLongClick = null,
        description = Bedtime.describeDayRemaining(minutesUntil),
        background = Brush.linearGradient(
            listOf(
                colors.duskTop,
                lerp(colors.duskTop, colors.duskBottom, 0.35f + 0.5f * warmth)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HourglassMark(
                size = if (detail == TileDetail.FULL) Spacing.xl else Spacing.lg,
                tint = GoldSoft,
                animated = windingDown
            )

            if (detail.showsTime) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = if (rounded <= 0) "—" else Bedtime.describe(rounded),
                    style = if (detail == TileDetail.FULL) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (detail.showsName) {
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

internal fun lerp(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction
)
