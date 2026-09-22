package com.hourglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hourglass.core.Desert
import com.hourglass.core.Direction
import com.hourglass.core.SandGrid
import com.hourglass.core.TimerSand
import com.hourglass.ui.theme.HourglassTheme
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The dune your banked time has built — poured, not painted.
 *
 * Every session becomes a measured quantity of actual grains in the colour of the
 * timer that earned it, oldest poured first, and the pile that results is whatever
 * the automaton makes of them. The slope is the sand's own angle of repose rather
 * than a curve someone chose, and the bands are where each session's grains happened
 * to land. Drawing this would have been easier and would have meant nothing.
 *
 * The pour is progressive, so opening the screen shows the desert being laid down.
 */
@Composable
fun DuneCanvas(
    desert: Desert,
    nightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val random = remember { Random(desert.totalMillis) }

    val grid = remember(desert) { SandGrid(GRID_WIDTH, GRID_HEIGHT).also { floorIt(it) } }

    // One tint per sand, two shades each, so the strata have some grain to them.
    val tints = remember(colors) {
        TimerSand.entries.flatMap { sand ->
            val base = colors.sand(sand)
            listOf(lerp(base, Color.White, 0.14f), lerp(base, Color.Black, 0.12f))
        }
    }

    // The grains still to pour, oldest session first, as tint indices.
    val schedule = remember(desert) { pourSchedule(desert) }
    val poured = remember(desert) { intArrayOf(0) }

    val skyTop = if (nightMode) colors.duskTop else colors.backdropTop
    val skyBottom = if (nightMode) colors.duskBottom else colors.accentSoft

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(listOf(skyTop, skyBottom)), size = size)
            val bodyTint = if (nightMode) colors.glassHighlight else colors.accent
            val centre = Offset(size.width * 0.78f, size.height * 0.2f)
            val radius = size.minDimension * 0.06f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(bodyTint.copy(alpha = 0.3f), Color.Transparent),
                    center = centre,
                    radius = radius * 3.5f
                ),
                radius = radius * 3.5f,
                center = centre
            )
            drawCircle(bodyTint.copy(alpha = 0.9f), radius, centre)
        }

        SandCanvas(
            grid = grid,
            tints = tints,
            wallColor = Color.Transparent,
            running = true,
            gravity = Direction.S,
            interactive = true,
            modifier = Modifier.fillMaxSize(),
            onBeforeStep = { sand ->
                val index = poured[0]
                if (index < schedule.size) {
                    val batch = minOf(POUR_PER_FRAME, schedule.size - index)
                    repeat(batch) { offset ->
                        sand.pour(
                            x = POUR_COLUMN,
                            y = 1,
                            amount = 1,
                            tint = schedule[index + offset],
                            spread = POUR_SPREAD,
                            random = random
                        )
                    }
                    poured[0] = index + batch
                }
            }
        )
    }
}

/** Walls along the floor and both sides, so the pile has something to rest on. */
private fun floorIt(grid: SandGrid) {
    for (x in 0 until grid.width) grid.wall(x, grid.height - 1)
    for (y in 0 until grid.height) {
        grid.wall(0, y)
        grid.wall(grid.width - 1, y)
    }
}

/**
 * The grains to pour, oldest stratum first so the earliest sessions end up at the
 * bottom of the pile. Scaled to the dune's height so a long history fills the frame
 * without overflowing it.
 */
private fun pourSchedule(desert: Desert): IntArray {
    if (desert.isEmpty) return IntArray(0)

    val capacity = (GRID_WIDTH * GRID_HEIGHT * MAX_FILL).roundToInt()
    val total = (capacity * desert.height).roundToInt().coerceAtLeast(MIN_GRAINS)

    val grains = ArrayList<Int>(total)
    desert.strata.forEach { stratum ->
        val count = (total * stratum.thickness).roundToInt()
        // Two tints per sand, alternating, so a band is speckled rather than flat.
        val first = stratum.sand.ordinal * 2 + 1
        repeat(count) { index -> grains.add(if (index % 2 == 0) first else first + 1) }
    }
    return grains.toIntArray()
}

private const val GRID_WIDTH = 108
private const val GRID_HEIGHT = 72

/** Poured left of centre, so the dune's lee side faces the open half of the frame. */
private const val POUR_COLUMN = GRID_WIDTH * 2 / 5
private const val POUR_SPREAD = 3

/** Fraction of the grid a full desert occupies. */
private const val MAX_FILL = 0.55f

/** Even one short session should leave something visible on the floor. */
private const val MIN_GRAINS = 120

/** Grains per frame; the pour should read as a pour, not a paste. */
private const val POUR_PER_FRAME = 14
