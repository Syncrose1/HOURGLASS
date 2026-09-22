package com.hourglass.core

import kotlin.random.Random

/**
 * The eight neighbours, clockwise from north.
 *
 * Ordered so that a direction's two diagonal fallbacks are simply its neighbours in
 * this list — which is what lets the sand fall in any direction, not just down.
 */
enum class Direction(val dx: Int, val dy: Int) {
    N(0, -1),
    NE(1, -1),
    E(1, 0),
    SE(1, 1),
    S(0, 1),
    SW(-1, 1),
    W(-1, 0),
    NW(-1, -1);

    /** The neighbour `steps` positions clockwise. */
    fun rotated(steps: Int): Direction {
        val size = entries.size
        return entries[((ordinal + steps) % size + size) % size]
    }

    companion object {
        /**
         * The compass point closest to a gravity vector, in screen coordinates
         * (y grows downward). Used to turn an accelerometer reading into a fall
         * direction.
         */
        fun nearest(gx: Float, gy: Float): Direction {
            if (gx == 0f && gy == 0f) return S
            var best = S
            var bestDot = Float.NEGATIVE_INFINITY
            val length = kotlin.math.sqrt(gx * gx + gy * gy)
            entries.forEach { direction ->
                val dLength = kotlin.math.sqrt(
                    (direction.dx * direction.dx + direction.dy * direction.dy).toFloat()
                )
                val dot = (gx * direction.dx + gy * direction.dy) / (length * dLength)
                if (dot > bestDot) {
                    bestDot = dot
                    best = direction
                }
            }
            return best
        }
    }
}

/**
 * A falling-sand automaton.
 *
 * Each cell is either empty or a grain carrying a tint. On every step a grain tries to
 * move along gravity; failing that, to one of the two diagonals either side of it,
 * picked at random so piles do not lean. That one rule is the whole simulation — the
 * angle of repose, the way a pile collapses when you poke it, and the way sand drains
 * through a neck all fall out of it rather than being animated.
 *
 * Grains are conserved: a step only ever moves them, and never off the grid.
 */
class SandGrid(
    val width: Int,
    val height: Int,
    private val cells: IntArray = IntArray(width * height)
) {
    init {
        require(width > 0 && height > 0) { "grid must have positive dimensions" }
        require(cells.size == width * height) { "cell buffer does not match dimensions" }
    }

    /** Number of grains currently on the grid. Walls are not grains. */
    var grainCount: Int = cells.count { it > EMPTY }
        private set

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    operator fun get(x: Int, y: Int): Int =
        if (inBounds(x, y)) cells[y * width + x] else OUT_OF_BOUNDS

    fun isEmpty(x: Int, y: Int): Boolean = get(x, y) == EMPTY

    fun isGrain(x: Int, y: Int): Boolean = get(x, y) > EMPTY

    fun isWall(x: Int, y: Int): Boolean = get(x, y) == WALL

    /**
     * Marks a cell as solid. Walls block grains and never move, which is what lets a
     * grid be shaped into an hourglass, a funnel or a dune floor.
     */
    fun wall(x: Int, y: Int) {
        if (!inBounds(x, y)) return
        val index = y * width + x
        if (cells[index] > EMPTY) grainCount--
        cells[index] = WALL
    }

    /** Direct read access for renderers; the array is the grid's own buffer. */
    fun buffer(): IntArray = cells

    fun clear() {
        cells.fill(EMPTY)
        grainCount = 0
    }

    /** Removes the grains but keeps the walls, so the vessel survives a reset. */
    fun clearGrains() {
        for (index in cells.indices) {
            if (cells[index] > EMPTY) cells[index] = EMPTY
        }
        grainCount = 0
    }

    /** Places a grain, replacing whatever was there. [tint] must be >= 1. */
    fun place(x: Int, y: Int, tint: Int) {
        if (!inBounds(x, y)) return
        require(tint >= 1) { "tint must be >= 1; 0 means empty" }
        val index = y * width + x
        if (cells[index] == EMPTY) grainCount++
        cells[index] = tint
    }

    fun remove(x: Int, y: Int) {
        if (!inBounds(x, y)) return
        val index = y * width + x
        if (cells[index] > EMPTY) grainCount--
        cells[index] = EMPTY
    }

    /**
     * Drops [amount] grains into a column, spread over [spread] cells either side.
     * Grains that land on an occupied cell are simply not added — the neck is full.
     * Returns how many were actually placed.
     */
    fun pour(
        x: Int,
        y: Int,
        amount: Int,
        tint: Int,
        spread: Int = 1,
        random: Random = Random
    ): Int {
        var placed = 0
        repeat(amount) {
            val offset = if (spread <= 0) 0 else random.nextInt(-spread, spread + 1)
            val column = x + offset
            if (inBounds(column, y) && isEmpty(column, y)) {
                place(column, y, tint)
                placed++
            }
        }
        return placed
    }

    /**
     * Advances the simulation one step. Returns the number of grains that moved, so a
     * renderer can stop drawing frames once the pile has settled.
     */
    fun step(gravity: Direction = Direction.S, random: Random = Random): Int {
        val slideFirst = gravity.rotated(1)
        val slideSecond = gravity.rotated(-1)

        // Cells are visited from the downhill end so a grain can only advance one cell
        // per step; visiting uphill-first would let a column fall in a single frame.
        val xs = if (gravity.dx > 0) (width - 1) downTo 0 else 0 until width
        val ys = if (gravity.dy > 0) (height - 1) downTo 0 else 0 until height

        var moved = 0
        for (y in ys) {
            for (x in xs) {
                val tint = cells[y * width + x]
                if (tint <= EMPTY) continue // empty, or a wall that never moves

                val first: Direction
                val second: Direction
                if (random.nextBoolean()) {
                    first = slideFirst
                    second = slideSecond
                } else {
                    first = slideSecond
                    second = slideFirst
                }

                val target = firstFreeOrNull(x, y, gravity, first, second) ?: continue
                cells[y * width + x] = EMPTY
                cells[target.second * width + target.first] = tint
                moved++
            }
        }
        return moved
    }

    private fun firstFreeOrNull(
        x: Int,
        y: Int,
        vararg candidates: Direction
    ): Pair<Int, Int>? {
        candidates.forEach { direction ->
            val nx = x + direction.dx
            val ny = y + direction.dy
            if (inBounds(nx, ny) && cells[ny * width + nx] == EMPTY) return nx to ny
        }
        return null
    }

    /**
     * Steps until nothing moves or [maxSteps] is reached, and reports how many steps
     * it took. Used to pre-settle a pile that should already look old.
     */
    fun settle(gravity: Direction = Direction.S, maxSteps: Int = 400, random: Random = Random): Int {
        repeat(maxSteps) { index ->
            if (step(gravity, random) == 0) return index + 1
        }
        return maxSteps
    }

    /**
     * Nudges grains near a point, as if the pile had been poked. Each affected grain
     * hops one cell sideways if there is room, which is enough to start a collapse.
     */
    fun disturb(x: Int, y: Int, radius: Int, random: Random = Random) {
        if (radius <= 0) return
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius) continue
                val px = x + dx
                val py = y + dy
                if (!isGrain(px, py)) continue
                val toward = if (random.nextBoolean()) 1 else -1
                val nx = px + toward
                if (inBounds(nx, py) && isEmpty(nx, py)) {
                    val tint = cells[py * width + px]
                    cells[py * width + px] = EMPTY
                    cells[py * width + nx] = tint
                }
            }
        }
    }

    /** Height of the tallest occupied column, in cells. */
    fun pileHeight(): Int {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (cells[y * width + x] > EMPTY) return height - y
            }
        }
        return 0
    }

    companion object {
        const val EMPTY = 0
        const val OUT_OF_BOUNDS = -1

        /** A solid cell: grains cannot enter it and it never moves. */
        const val WALL = -2
    }
}
