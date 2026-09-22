package com.hourglass.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hourglass.core.Direction
import com.hourglass.core.HourglassSim
import com.hourglass.core.SandGrid
import com.hourglass.ui.theme.HourglassTheme
import kotlin.random.Random

/**
 * Grid sizes for a glass, coarse to fine.
 *
 * A glass only needs enough cells for a grain to read as a grain at the size it is
 * drawn; a wall tile does not need the resolution the focus view does, and paying for
 * it on every tile is how you lose the frame rate.
 */
enum class SandDetail(val gridWidth: Int, val gridHeight: Int, val neckHalfWidth: Int) {
    GLYPH(18, 32, 1),
    SMALL(26, 46, 1),
    MEDIUM(38, 66, 2),
    LARGE(62, 108, 2)
}

/**
 * A sandglass whose sand is simulated rather than drawn.
 *
 * This is the app's one hourglass. Everywhere a glass appears — a tile on the wall,
 * the preview on the new-timer form, the focus view — it is this, running the same
 * automaton at whatever resolution the space deserves. A drawn approximation of sand
 * would be a picture of the thing the app is about.
 *
 * A glass that is not running does no work at all: it is primed straight to its
 * position and then sits there, so a wall of idle timers costs nothing per frame.
 */
@Composable
fun SandGlass(
    progress: Float,
    sand: Color,
    running: Boolean,
    overtime: Boolean,
    modifier: Modifier = Modifier,
    detail: SandDetail = SandDetail.MEDIUM,
    interactive: Boolean = false,
    gravity: Direction = Direction.S,
    /** Identity of the timer being shown; a change rebuilds the glass from scratch. */
    key: Any? = null
) {
    val colors = HourglassTheme.colors
    val random = remember { Random(System.nanoTime()) }

    val sim = remember(key, detail) {
        HourglassSim(
            SandGrid(detail.gridWidth, detail.gridHeight),
            neckHalfWidth = detail.neckHalfWidth
        ).also { it.prime(progress, SAND_TINTS, random) }
    }

    val currentProgress by rememberUpdatedState(progress)

    // A timer that was stopped or restarted winds its glass back; anything else is
    // handled by the per-frame drain, which only ever moves sand forward.
    LaunchedEffect(sim, progress < sim.releasedCount.toFloat() / sim.capacity - REWIND) {
        val drained = if (sim.capacity == 0) 0f else sim.releasedCount.toFloat() / sim.capacity
        if (currentProgress < drained - REWIND) sim.prime(currentProgress, SAND_TINTS, random)
    }

    val tints = remember(sand, colors.overtime) {
        shadesOf(sand) + shadesOf(colors.overtime)
    }

    SandCanvas(
        grid = sim.grid,
        tints = tints,
        wallColor = colors.glass.copy(alpha = 0.55f),
        running = running,
        gravity = gravity,
        interactive = interactive,
        modifier = modifier,
        onBeforeStep = { grid ->
            if (overtime) {
                // The glass does not stop when the allocation does: overtime keeps
                // raining into the lower chamber, in its own colour.
                grid.pour(
                    x = grid.width / 2,
                    y = sim.neckRow + 1,
                    amount = 1,
                    tint = OVERTIME_TINTS.random(random),
                    spread = 2,
                    random = random
                )
            } else {
                sim.syncTo(currentProgress, random = random)
            }
        }
    )
}

/** Tint indices 1..3 are the sand's own shades, 4..6 the overtime colour's. */
private val SAND_TINTS = 1..3
private val OVERTIME_TINTS = 4..6

/** How far progress must fall behind the glass before it is rebuilt. */
private const val REWIND = 0.05f

/**
 * A lighter, a base and a darker step of one colour. A single flat tint renders as a
 * poured slab; a speckle reads as material.
 */
internal fun shadesOf(base: Color): List<Color> = listOf(
    lerp(base, Color.White, 0.20f),
    base,
    lerp(base, Color.Black, 0.18f)
)

/** Picks a grid size from the short edge of the space a glass has been given. */
fun sandDetailFor(shortEdgeDp: Float): SandDetail = when {
    shortEdgeDp >= 190f -> SandDetail.LARGE
    shortEdgeDp >= 110f -> SandDetail.MEDIUM
    shortEdgeDp >= 62f -> SandDetail.SMALL
    else -> SandDetail.GLYPH
}
