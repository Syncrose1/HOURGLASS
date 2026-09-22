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
import com.hourglass.core.world.Mat
import com.hourglass.ui.components.lerp
import com.hourglass.ui.theme.HourglassTheme
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
    val colors = HourglassTheme.colors
    val world = session.world
    val progress by rememberUpdatedState(timerProgress)

    var frame by remember { mutableIntStateOf(0) }

    val bitmap = remember(world) {
        Bitmap.createBitmap(world.width, world.height, Bitmap.Config.ARGB_8888)
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val cells = remember(world) { IntArray(world.width * world.height) }
    val pixels = remember(world) { IntArray(world.width * world.height) }
    val palette = remember(mineral, colors.isDark) { minePalette(mineral) }

    val skyTop = lerp(colors.duskTop, colors.backdropTop, if (colors.isDark) 0.15f else 0.55f)
    val skyBottom = lerp(colors.accentSoft, colors.backdropTop, if (colors.isDark) 0.1f else 0.35f)

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
        for (index in cells.indices) pixels[index] = palette[cells[index]]
        bitmap.setPixels(pixels, 0, world.width, 0, 0, world.width, world.height)

        drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)))

        // Crop to fill, like a photograph: never stretch a cell out of square.
        val scale = max(size.width / world.width, size.height / world.height)
        val drawnWidth = (world.width * scale).roundToInt()
        val drawnHeight = (world.height * scale).roundToInt()
        drawImage(
            image = image,
            dstOffset = IntOffset(
                ((size.width - drawnWidth) / 2f).roundToInt(),
                ((size.height - drawnHeight) / 2f).roundToInt()
            ),
            dstSize = IntSize(drawnWidth, drawnHeight),
            filterQuality = FilterQuality.None
        )
    }
}

/**
 * The underground is dark in both themes — it is underground. The seams are the one
 * thing in the ground that carries the timer's own colour, so the haul is visibly
 * *this timer's* haul.
 */
private fun minePalette(mineral: Color): IntArray {
    val slots = IntArray(Mat.COUNT)
    fun set(slot: Int, colour: Color) {
        slots[slot] = colour.toArgb()
    }
    set(Mat.SKY, Color.Transparent)
    set(Mat.AIR, Color(0xFF1B130D))
    set(Mat.SAND, Color(0xFFD8B77C))
    set(Mat.SAND_DARK, Color(0xFFC7A366))
    set(Mat.SANDSTONE, Color(0xFFAE7849))
    set(Mat.SANDSTONE_DARK, Color(0xFF98683E))
    set(Mat.ROCK, Color(0xFF5C4F47))
    set(Mat.ROCK_DARK, Color(0xFF4B403A))
    set(Mat.MINERAL, mineral)
    set(Mat.MINERAL_BRIGHT, lerp(mineral, Color.White, 0.45f))
    set(Mat.CART, Color(0xFF6E4B2E))
    set(Mat.MINER, Color(0xFFF6EBD6))
    set(Mat.MINER_LOADED, lerp(mineral, Color.White, 0.25f))
    set(Mat.STOCK, lerp(mineral, Color.White, 0.15f))
    return slots
}

/** About thirty steps a second: brisk enough to watch, calm enough not to fizz. */
private const val STEP_INTERVAL_MILLIS = 33L
