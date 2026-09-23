package com.hourglass.ui.world

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.hourglass.core.world.DaySky
import com.hourglass.core.world.Palettes
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A window onto a timer's world.
 *
 * The world is painted into a bitmap the size of its own grid and scaled up with
 * filtering off, so every cell is a crisp square: a miner is two pixels, a seam is a
 * glint, and nothing is smoothed into mush. It fills its box the way a photograph
 * would — cropped, never stretched — so a wide tile and a tall one both show real
 * ground at the right proportions.
 */
@Composable
fun WorldView(
    session: WorldSession,
    mineral: Color,
    running: Boolean,
    timerProgress: Float,
    modifier: Modifier = Modifier
) {
    val world = session.world
    val progress by rememberUpdatedState(timerProgress)

    var frame by remember { mutableIntStateOf(0) }

    val bitmap = remember(world) {
        Bitmap.createBitmap(world.width, world.height, Bitmap.Config.ARGB_8888)
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val cells = remember(world) { IntArray(world.width * world.height) }
    val pixels = remember(world) { IntArray(world.width * world.height) }
    val palette = remember(mineral, world.kind) { Palettes.forKind(world.kind, mineral.toArgb()) }
    val shaded = remember(palette, world) { Palettes.shadedByDepth(palette, world.height) }

    // The same sky as the bar at the top of the wall, at the same hour.
    val spent = LocalDaySpent.current
    val skyTop = Color(DaySky.topAt(spent))
    val skyBottom = Color(DaySky.bottomAt(spent))

    LaunchedEffect(session, running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (now - last >= STEP_INTERVAL_MILLIS) {
                    last = now
                    session.advance(progress)
                    frame++
                }
            }
        }
    }

    Canvas(modifier = modifier.clipToBounds()) {
        @Suppress("UNUSED_EXPRESSION")
        frame

        world.renderInto(cells)
        val slots = palette.colours.size
        for (index in cells.indices) {
            pixels[index] = shaded[(index / world.width) * slots + cells[index]]
        }
        bitmap.setPixels(pixels, 0, world.width, 0, 0, world.width, world.height)

        drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)))

        // Crop to fill, like a photograph: never stretch a cell out of square. Where
        // the crop falls is the world's call — centring it showed a mine's middle
        // strata and cut off the headframe, which is where the story is.
        val scale = max(size.width / world.width, size.height / world.height)
        val drawnWidth = (world.width * scale).roundToInt()
        val drawnHeight = (world.height * scale).roundToInt()
        drawImage(
            image = image,
            dstOffset = IntOffset(
                ((size.width - drawnWidth) / 2f).roundToInt(),
                ((size.height - drawnHeight) * world.focusY).roundToInt()
            ),
            dstSize = IntSize(drawnWidth, drawnHeight),
            filterQuality = FilterQuality.None
        )
    }
}

/** About thirty steps a second: brisk enough to watch, calm enough not to fizz. */
private const val STEP_INTERVAL_MILLIS = 33L
