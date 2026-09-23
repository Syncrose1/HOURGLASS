package com.hourglass.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.core.world.DaySky
import com.hourglass.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The day, as a strip of sky across the top of everything.
 *
 * It is drawn the way the worlds are — in chunky pixels — so it belongs with them: a
 * sun crosses it and sinks into the dunes as bedtime comes, the light goes gold and
 * then dusk, and once the day is over the stars are out. The figure is quarter-hour
 * floored, like the notification, so it steps rather than ticks.
 */
@Composable
fun BedtimeBar(
    bedtime: TimeOfDay,
    minutesUntil: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rounded = Bedtime.roundForDisplay(minutesUntil)
    val spent = DaySky.spentFor(minutesUntil)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics { contentDescription = Bedtime.describeDayRemaining(minutesUntil) }
    ) {
        val density = LocalDensity.current
        val cell = with(density) { CELL.toPx() }
        val columns = (constraints.maxWidth / cell).roundToInt().coerceAtLeast(8)
        val rows = (constraints.maxHeight / cell).roundToInt().coerceAtLeast(6)
        val sky = remember(columns, rows) { DaySky(columns, rows) }
        val bitmap = remember(columns, rows) { Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888) }
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        val pixels = remember(columns, rows) { IntArray(columns * rows) }

        // Only the stars move, and only at night: no need to animate a daytime sky.
        var tick by remember { mutableIntStateOf(0) }
        val night = spent > 0.88f
        LaunchedEffect(night) {
            while (night) {
                delay(TWINKLE_MILLIS)
                tick++
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            sky.render(pixels, spent, tick)
            bitmap.setPixels(pixels, 0, columns, 0, 0, columns, rows)
            drawImage(
                image = image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.None
            )
        }

        val dusk = spent > 0.6f
        val ink = if (dusk) Color.White else INK_DAY
        val shade = if (dusk) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.5f)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (rounded <= 0) Bedtime.DAY_OVER else "${Bedtime.describe(rounded)} left",
                style = MaterialTheme.typography.titleMedium.merge(
                    TextStyle(fontWeight = FontWeight.SemiBold, shadow = Shadow(shade, blurRadius = 6f))
                ),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                Text(
                    text = "bed ${bedtime.formatFriendly()}",
                    style = MaterialTheme.typography.labelMedium.merge(
                        TextStyle(shadow = Shadow(shade, blurRadius = 6f))
                    ),
                    color = ink.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

private val BAR_HEIGHT = 56.dp

/** One sky pixel, on screen: chunky enough to read as pixel art, like the worlds. */
private val CELL = 4.dp

private val INK_DAY = Color(0xFF2B2230)

private const val TWINKLE_MILLIS = 120L
