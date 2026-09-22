package com.hourglass.core

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
        val widest = (grid.width / 2f) - 1f
        val t = (distance / reach).coerceIn(0f, 1f)
        // Eased so the wall bows outward from the waist rather than running straight.
        val eased = t.let { it * it * (3f - 2f * it) }
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

    /** Fills the upper chamber. */
    fun fill(tint: Int) {
        grid.clearGrains()
        for (y in 1 until neckRow) {
            for (x in 0 until grid.width) {
                if (grid.isEmpty(x, y)) grid.place(x, y, tint)
            }
        }
    }

    /** Grains that have made it past the waist. */
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
        var released = 0
        while (fallenCount() + released < target && released < maxPerCall) {
            if (!releaseOne(random)) break
            released++
        }
        return released
    }

    /**
     * Moves the lowest grain in the upper chamber to just below the waist.
     * Returns false when there is nothing to release or nowhere to put it.
     */
    private fun releaseOne(random: Random): Boolean {
        val landing = neckRow + 1
        if (landing >= grid.height) return false

        val landingX = (centreColumn - neckHalfWidth..centreColumn + neckHalfWidth)
            .filter { grid.isEmpty(it, landing) }
            .randomOrNull(random) ?: return false

        for (y in neckRow - 1 downTo 1) {
            val candidates = (0 until grid.width).filter { grid.isGrain(it, y) }
            if (candidates.isEmpty()) continue
            // Prefer a grain near the middle: that is the one over the hole.
            val x = candidates.minByOrNull { kotlin.math.abs(it - centreColumn) } ?: continue
            val tint = grid[x, y]
            grid.remove(x, y)
            grid.place(landingX, landing, tint)
            return true
        }
        return false
    }

    fun step(gravity: Direction = Direction.S, random: Random = Random): Int =
        grid.step(gravity, random)

    /** True once everything has drained through. */
    fun isDrained(): Boolean = fallenCount() >= grid.grainCount
}
