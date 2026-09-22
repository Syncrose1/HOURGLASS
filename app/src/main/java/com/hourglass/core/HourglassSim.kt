package com.hourglass.core

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A sandglass built out of [SandGrid].
 *
 * The heaps, the slumping and the cone under the neck are not animated — they are what
 * the automaton does on its own. What this class adds is the one thing physics must not
 * decide: *when* a grain falls. The drain is slaved to the timer's progress, so the
 * glass is a readout and not just an ornament that happens to look busy.
 */
class HourglassSim(
    val grid: SandGrid,
    /** Half-width of the opening at the waist, in cells. */
    private val neckHalfWidth: Int = 1
) {
    val neckRow: Int = grid.height / 2
    private val centreColumn: Int = grid.width / 2

    /** Interior half-width of the chamber at row [y], in cells. */
    private fun halfWidthAt(y: Int): Int {
        val distance = kotlin.math.abs(y - neckRow).toFloat()
        val reach = neckRow.toFloat().coerceAtLeast(1f)
        val widest = (grid.width / 2f) - WALL_MARGIN
        val t = (distance / reach).coerceIn(0f, 1f)
        // Flares hard off the waist and then stands almost vertical, which is the
        // profile of a blown-glass bulb. A smoothstep here eases at both ends and
        // gives two straight cones instead — the sim stopped looking like the
        // sandglass the rest of the app draws.
        val eased = 1f - (1f - t).pow(BULB_FLARE)
        return (neckHalfWidth + eased * (widest - neckHalfWidth)).roundToInt()
    }

    /**
     * Grains the upper chamber holds when full.
     *
     * Measured once, while the chamber is still empty. It cannot be lazy: the first
     * read would land after [fill] and count zero free cells, which would peg the
     * drain target at nothing and freeze the glass.
     */
    val capacity: Int

    init {
        buildVessel()
        capacity = countEmptyAbove()
    }

    private fun countEmptyAbove(): Int {
        var count = 0
        for (y in 1 until neckRow) {
            for (x in 0 until grid.width) if (grid.isEmpty(x, y)) count++
        }
        return count
    }

    private fun buildVessel() {
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                if (kotlin.math.abs(x - centreColumn) > halfWidthAt(y)) grid.wall(x, y)
            }
        }
        // The waist is closed, not merely narrow.
        //
        // An open neck would let gravity carry grains through on its own, and the
        // drain would then be set by the physics rather than by the clock — a glass
        // that empties at its own pace instead of the timer's. Everything crosses the
        // waist through releaseOne, which the timer meters.
        for (x in 0 until grid.width) grid.wall(x, neckRow)
        // Caps, so nothing escapes at the ends.
        for (x in 0 until grid.width) {
            grid.wall(x, 0)
            grid.wall(x, grid.height - 1)
        }
    }

    /**
     * Fills the upper chamber, speckling the grains across [tints].
     *
     * A single flat tint renders as a poured slab of colour; two or three shades of
     * the same sand give the mass some grain and make the heaps read as material.
     */
    fun fill(tints: IntRange, random: Random = Random) {
        grid.clearGrains()
        releasedCount = 0
        for (y in 1 until neckRow) {
            for (x in 0 until grid.width) {
                if (grid.isEmpty(x, y)) {
                    grid.place(x, y, random.nextInt(tints.first, tints.last + 1))
                }
            }
        }
    }

    /**
     * Grains let through so far.
     *
     * Kept as a counter rather than recounted from the grid: [fallenCount] walks the
     * whole lower chamber, which is affordable once but not once per released grain
     * per frame, per glass, on a wall of them.
     */
    var releasedCount: Int = 0
        private set

    /** Grains that have made it past the waist. Walks the grid; use sparingly. */
    fun fallenCount(): Int {
        var count = 0
        for (y in neckRow until grid.height) {
            for (x in 0 until grid.width) if (grid.isGrain(x, y)) count++
        }
        return count
    }

    /**
     * Lets through however many grains the timer says should have fallen by now,
     * up to [maxPerCall] so a long pause does not dump the whole chamber in one frame.
     *
     * Returns the number released.
     */
    fun syncTo(progress: Float, maxPerCall: Int = 12, random: Random = Random): Int {
        val target = (capacity * progress.coerceIn(0f, 1f)).roundToInt()
        var moved = 0
        while (releasedCount < target && moved < maxPerCall) {
            if (!releaseOne(random)) break
            moved++
        }
        return moved
    }

    /**
     * Brings the glass straight to [progress] without animating the journey — what a
     * tile needs when it first appears showing a timer that is already part-run.
     * Bounded, so a glass that cannot drain any further gives up rather than spinning.
     */
    fun prime(
        progress: Float,
        tints: IntRange,
        random: Random = Random,
        maxIterations: Int = 400
    ) {
        fill(tints, random)
        var iterations = 0
        while (iterations < maxIterations) {
            if (syncTo(progress, maxPerCall = PRIME_BATCH, random = random) == 0) break
            step(random = random)
            iterations++
        }
        grid.settle(maxSteps = SETTLE_STEPS, random = random)
    }

    /**
     * Takes a grain off the top of the pile and drops it below the waist.
     *
     * Deliberately the surface, not the base. Draining from the bottom of a packed
     * chamber sends the void travelling up through the mass, and it breaks out at
     * whichever corner happens to settle last — the sand appears to erode from one
     * edge, which is nothing like an hourglass. Real sand keeps a level surface that
     * sinks, with a cone opening above the neck. Removing the highest grain, biased
     * toward the middle, produces that directly, and the automaton's diagonal rule
     * smooths the surface between removals.
     *
     * Returns false when there is nothing to release or nowhere to put it.
     */
    private fun releaseOne(random: Random): Boolean {
        val landing = neckRow + 1
        if (landing >= grid.height) return false

        val landingX = (centreColumn - neckHalfWidth..centreColumn + neckHalfWidth)
            .filter { grid.isEmpty(it, landing) }
            .randomOrNull(random) ?: return false

        val surfaceY = (1 until neckRow).firstOrNull { y ->
            (0 until grid.width).any { grid.isGrain(it, y) }
        } ?: return false

        val candidates = (0 until grid.width).filter { grid.isGrain(it, surfaceY) }
        if (candidates.isEmpty()) return false

        // Jitter keeps the centre bias from drilling a one-cell shaft: the dip opens
        // out into a cone instead.
        val x = candidates.minByOrNull {
            kotlin.math.abs(it - centreColumn) + random.nextInt(CENTRE_JITTER)
        } ?: return false

        val tint = grid[x, surfaceY]
        grid.remove(x, surfaceY)
        grid.place(landingX, landing, tint)
        releasedCount++
        return true
    }

    fun step(gravity: Direction = Direction.S, random: Random = Random): Int =
        grid.step(gravity, random)

    /** Topmost occupied row in the upper chamber, or null when it is empty. */
    fun surfaceRow(): Int? = (1 until neckRow).firstOrNull { y ->
        (0 until grid.width).any { grid.isGrain(it, y) }
    }

    /** Topmost occupied row in a single column of the upper chamber. */
    fun surfaceRowAt(x: Int): Int? = (1 until neckRow).firstOrNull { grid.isGrain(x, it) }

    private companion object {
        /** Cells of slack on the centre bias, in each direction. */
        const val CENTRE_JITTER = 5

        /** Higher flares the bulb out from the waist faster. */
        const val BULB_FLARE = 2.2f

        /** Cells of clearance between the widest point and the grid edge. */
        const val WALL_MARGIN = 2f

        /** Grains released per step while priming. */
        const val PRIME_BATCH = 8

        /** Steps allowed for the pile to come to rest after priming. */
        const val SETTLE_STEPS = 160
    }

    /** True once everything has drained through. */
    fun isDrained(): Boolean = fallenCount() >= grid.grainCount
}
