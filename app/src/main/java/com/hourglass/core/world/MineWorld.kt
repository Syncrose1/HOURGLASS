package com.hourglass.core.world

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Palette slots. The UI supplies the colours; the world only names them. */
object Mat {
    const val AIR = 0
    const val SKY = 1
    const val SAND = 2
    const val SAND_DARK = 3
    const val SANDSTONE = 4
    const val SANDSTONE_DARK = 5
    const val ROCK = 6
    const val ROCK_DARK = 7
    const val MINERAL = 8
    const val MINERAL_BRIGHT = 9
    const val CART = 10
    const val MINER = 11
    const val MINER_LOADED = 12
    const val STOCK = 13
    const val SAND_PACKED = 14
    const val PLATFORM = 15

    const val COUNT = 16

    /** How much digging a cell takes. Rock is a commitment. */
    fun hardness(cell: Int): Float = when (cell) {
        SAND, SAND_DARK -> 1.2f
        SAND_PACKED -> 1.6f
        MINERAL, MINERAL_BRIGHT -> 2.0f
        STOCK -> 2.0f
        SANDSTONE, SANDSTONE_DARK -> 4.0f
        ROCK, ROCK_DARK -> 9.0f
        else -> Float.MAX_VALUE
    }

    fun isOpen(cell: Int): Boolean = cell == AIR || cell == SKY

    fun isMineral(cell: Int): Boolean = cell == MINERAL || cell == MINERAL_BRIGHT

    fun isDiggable(cell: Int): Boolean = !isOpen(cell) && hardness(cell) < Float.MAX_VALUE

    /**
     * Loose material slumps into open space; the harder strata hold their shape, and
     * so does sand a miner has packed into a tunnel lining.
     */
    fun isLoose(cell: Int): Boolean = cell == SAND || cell == SAND_DARK
}

/**
 * A cross-section of ground, and the little crew digging it out.
 *
 * Strata run sand over sandstone over rock, with seams scattered through and getting
 * richer the deeper you go — so the easy minerals go first and the last of them cost
 * real digging. Miners plan routes the way a digger would: through open workings where
 * they can, through soft ground where they must, and around rock when there is a way.
 * They haul each find back to the cart, where the stockpile grows. Sand slumps into
 * the tunnels behind them; rock does not.
 *
 * Nothing here is staged. Where the shafts go, which seams get reached first, whether
 * a crater opens up around a shaft sunk through the sand — all of it falls out of the
 * rules. The only thing tied to the clock is how hard the crew works ([WorldPacer]).
 */
class MineWorld(
    override val width: Int,
    override val height: Int,
    seed: Long,
    /** Roughly how many loads of mineral to bury. Longer timers get richer ground. */
    private val richness: Int = DEFAULT_RICHNESS
) : World, Site {

    override val cells = IntArray(width * height)

    /** Accumulated pick damage per cell; a cell breaks when it passes its hardness. */
    private val damage = FloatArray(width * height)

    private val generator = Random(seed)
    private lateinit var crew: Crew

    private var mineralsTotal = 0
    private var mineralsDelivered = 0

    private var cartX = width / 2
    private var cartY = 0

    private var settleCounter = 0

    override val objectiveProgress: Float
        get() = if (mineralsTotal == 0) 1f
        else (mineralsDelivered.toFloat() / mineralsTotal).coerceAtMost(1f)

    override val objective: String get() = "$mineralsDelivered of $mineralsTotal hauled"

    override val kind: WorldKind get() = WorldKind.MINE

    override val focusY: Float get() = 0.08f

    val minerCount: Int get() = crew.workers.size
    val totalSeams: Int get() = mineralsTotal
    val hauledSeams: Int get() = mineralsDelivered


    init {
        generate()
    }

    // --- generation -------------------------------------------------------

    private fun generate() {
        val surface = surfaceProfile()

        for (x in 0 until width) {
            for (y in 0 until height) {
                cells[y * width + x] = if (y < surface[x]) Mat.SKY else strataAt(x, y, surface[x])
            }
        }

        // Off centre, so the first shaft is not dead in the middle of the frame.
        cartX = (width * (0.3f + generator.nextFloat() * 0.4f)).roundToInt().coerceIn(2, width - 4)
        cartY = surface[cartX] - 1
        // A headframe platform for the cart and the stockpile.
        //
        // Two failures shaped this. A cart standing on sand was left floating when the
        // sand under it slumped away, stranding every loaded miner. And loads tipped
        // beside the cart poured straight down the first shaft the crew sank there. The
        // platform cannot be dug, so it stays put and nothing drains through it.
        val ground = surface[cartX]
        for (x in cartX - 1..cartX + PLATFORM_WIDTH) {
            if (x !in 0 until width) continue
            for (y in 0 until height) {
                val index = y * width + x
                cells[index] = when {
                    y < ground -> Mat.SKY
                    y == ground -> Mat.PLATFORM
                    y < ground + PAD_DEPTH -> Mat.SANDSTONE
                    else -> cells[index]
                }
            }
            surface[x] = ground
        }
        cells[cartY * width + cartX] = Mat.CART
        cells[cartY * width + cartX + 1] = Mat.CART

        scatterMinerals(surface)
        spawnMiners(surface)
    }

    /** A gently rolling surface, high enough to leave sky but low enough to dig deep. */
    private fun surfaceProfile(): IntArray {
        val base = height * SURFACE_FRACTION
        val amplitude = height * 0.04f
        val phase = generator.nextFloat() * 6.28f
        val phase2 = generator.nextFloat() * 6.28f
        return IntArray(width) { x ->
            val t = x.toFloat() / width
            val wave = sin(t * 5.1f + phase) * amplitude + sin(t * 11.3f + phase2) * amplitude * 0.4f
            (base + wave).roundToInt().coerceIn(3, height - 12)
        }
    }

    /** Sand near the top, then sandstone, then rock, with ragged boundaries. */
    private fun strataAt(x: Int, y: Int, top: Int): Int {
        val depth = y - top
        val below = height - top
        val jitter = (sin(x * 0.7f + y * 0.13f) + sin(x * 0.21f)) * below * 0.02f
        val sandFloor = below * 0.2f + jitter
        val stoneFloor = below * 0.52f + jitter
        val dark = generator.nextFloat() < 0.35f
        return when {
            depth < sandFloor -> if (dark) Mat.SAND_DARK else Mat.SAND
            depth < stoneFloor -> if (dark) Mat.SANDSTONE_DARK else Mat.SANDSTONE
            else -> if (dark) Mat.ROCK_DARK else Mat.ROCK
        }
    }

    /**
     * Seams get commoner and bigger with depth, so the crew works downward and the
     * last of the haul is genuinely expensive.
     */
    private fun scatterMinerals(surface: IntArray) {
        var placed = 0
        var attempts = 0
        while (placed < richness && attempts < richness * 60) {
            attempts++
            val x = generator.nextInt(2, width - 2)
            val top = surface[x]
            val diggable = height - top
            if (diggable < 10) continue

            val depth = generator.nextInt(3, diggable - 1)
            val richnessAtDepth = depth.toFloat() / diggable
            if (generator.nextFloat() > richnessAtDepth * richnessAtDepth + 0.05f) continue

            val size = 1 + generator.nextInt(if (richnessAtDepth > 0.6f) 2 else 1) + 0
            for (dy in 0 until size) {
                for (dx in 0 until size) {
                    if (placed >= richness) break
                    val px = x + dx
                    val py = top + depth + dy
                    if (px !in 1 until width - 1 || py !in 0 until height - 1) continue
                    val index = py * width + px
                    if (Mat.isOpen(cells[index]) || Mat.isMineral(cells[index])) continue
                    if (cells[index] == Mat.CART) continue
                    cells[index] = if (generator.nextFloat() < 0.4f) Mat.MINERAL_BRIGHT else Mat.MINERAL
                    placed++
                }
            }
        }
        mineralsTotal = placed
    }

    private fun spawnMiners(surface: IntArray) {
        val count = (width / 16).coerceIn(2, 6)
        crew = Crew(
            site = this,
            spawns = List(count) { index ->
                val x = (cartX - 2 + index * 2 - count / 2).coerceIn(1, width - 2)
                x to (surface[x] - 1).coerceAtLeast(0)
            }
        )
    }

    // --- simulation -------------------------------------------------------

    override fun step(effort: Float, random: Random) {
        crew.step(effort, random)
        settleCounter++
        if (settleCounter >= SETTLE_EVERY) {
            settleCounter = 0
            slump(random)
        }
    }

    // --- the crew's view of the ground ------------------------------------

    override fun isOpen(index: Int): Boolean = Mat.isOpen(cells[index])

    /**
     * An open cell is standable if there is ground beneath it or real ground beside it
     * to cling to. The edge of the frame does not count: treating it as a wall left
     * miners hanging off the side of the picture in mid-air.
     */
    override fun supports(x: Int, y: Int): Boolean {
        if (y >= height - 1) return true
        return ground(x, y + 1) ||
            ground(x - 1, y) || ground(x + 1, y) ||
            ground(x - 1, y + 1) || ground(x + 1, y + 1)
    }

    private fun ground(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && !Mat.isOpen(cells[y * width + x])

    override fun breakCost(index: Int, carrying: Boolean): Int {
        val cell = cells[index]
        if (Mat.isMineral(cell) && carrying) return -1
        if (!Mat.isDiggable(cell)) return -1
        return 1 + (Mat.hardness(cell) * DIG_WEIGHT).toInt()
    }

    override fun isQuarry(index: Int): Boolean = Mat.isMineral(cells[index])

    override fun isDropOff(x: Int, y: Int): Boolean =
        x in (cartX - 2)..(cartX + 3) && abs(y - cartY) <= 1

    /** One swing of the pick. The cell gives way once the damage passes its hardness. */
    override fun work(index: Int, random: Random): Boolean {
        val cell = cells[index]
        damage[index] += PICK_POWER
        if (damage[index] < Mat.hardness(cell)) return false
        damage[index] = 0f
        cells[index] = Mat.AIR
        shore(index, random)
        return true
    }

    override fun unload(random: Random) {
        mineralsDelivered++
        stockpile(random)
    }

    /**
     * Packs the loose ground around a fresh cut, the way a real crew shores a tunnel.
     *
     * Without it every shaft through the sand became a drain: sand slumped in, the crew
     * dug it back out — destroying it — and more poured in behind, until the entire
     * sand layer had gone down the hole. Shoring is deliberately incomplete, so cave-ins
     * still happen; they are just local, and different every run.
     */
    private fun shore(index: Int, random: Random) {
        val x = index % width
        val y = index / width
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until width || ny !in 0 until height) continue
            val neighbour = ny * width + nx
            val cell = cells[neighbour]
            if ((cell == Mat.SAND || cell == Mat.SAND_DARK) && random.nextFloat() < SHORE_CHANCE) {
                cells[neighbour] = Mat.SAND_PACKED
            }
        }
    }

    /**
     * Each load is tipped onto the heap on the platform.
     *
     * Stacking every load on one column built a pole; letting the heap slump like sand
     * spilled it off the platform's edge and down the nearest shaft. So the heap is
     * built as a mound directly: each load goes on whichever column is lowest,
     * favouring the middle, which keeps the sides at a steady slope and the whole
     * thing on the boards.
     */
    private fun stockpile(random: Random) {
        val centre = cartX + STOCK_OFFSET
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (column in centre - STOCK_HALF_WIDTH..centre + STOCK_HALF_WIDTH) {
            if (column !in 0 until width) continue
            val top = heapTop(column) ?: continue
            val score = -top + abs(column - centre) * STOCK_SLOPE + random.nextFloat() * 0.3f
            if (score < bestScore) {
                bestScore = score
                best = column
            }
        }
        if (best < 0) return
        heapTop(best)?.let { y -> cells[index(best, y)] = Mat.STOCK }
    }

    /** The open cell on top of the heap in [column], or null if the column is full. */
    private fun heapTop(column: Int): Int? {
        var y = 0
        while (y < height - 1 && Mat.isOpen(cells[index(column, y + 1)])) y++
        return if (y >= 1 && Mat.isOpen(cells[index(column, y)])) y else null
    }

    /**
     * Loose material falls into whatever has been dug out beneath it, so shafts in
     * sand close up behind the crew and shafts in rock stay open.
     */
    private fun slump(random: Random) {
        for (y in height - 2 downTo 0) {
            for (x in 0 until width) {
                val here = y * width + x
                if (!Mat.isLoose(cells[here])) continue

                val below = here + width
                if (Mat.isOpen(cells[below])) {
                    cells[below] = cells[here]
                    vacate(here)
                    continue
                }
                val side = if (random.nextBoolean()) 1 else -1
                for (direction in intArrayOf(side, -side)) {
                    val nx = x + direction
                    if (nx !in 0 until width) continue
                    val diagonal = below + direction
                    if (Mat.isOpen(cells[diagonal]) && Mat.isOpen(cells[here + direction])) {
                        cells[diagonal] = cells[here]
                        vacate(here)
                        break
                    }
                }
            }
        }
        openToSky()
    }

    /** A cell something just left: sky if it is under open sky, a void otherwise. */
    private fun vacate(index: Int) {
        val above = index - width
        cells[index] = if (above < 0 || cells[above] == Mat.SKY) Mat.SKY else Mat.AIR
    }

    /**
     * A pit wider than a shaft is open to the sky and should look like it. A one-wide
     * shaft stays dark — you are looking down a hole — but a crater under open sky
     * rendered as a black cave with the sky for a ceiling until this pass.
     */
    private fun openToSky() {
        for (y in 1 until height) {
            for (x in 0 until width) {
                val here = y * width + x
                if (cells[here] != Mat.AIR || cells[here - width] != Mat.SKY) continue
                val leftSky = x > 0 && cells[here - 1] == Mat.SKY
                val rightSky = x < width - 1 && cells[here + 1] == Mat.SKY
                val leftOpen = x > 0 && Mat.isOpen(cells[here - 1])
                val rightOpen = x < width - 1 && Mat.isOpen(cells[here + 1])
                if (leftSky || rightSky || (leftOpen && rightOpen)) cells[here] = Mat.SKY
            }
        }
    }

    // --- rendering --------------------------------------------------------

    /** Copies the ground into [buffer] and draws the crew over it. */
    override fun renderInto(buffer: IntArray) {
        cells.copyInto(buffer)
        crew.workers.forEach { miner ->
            if (miner.x !in 0 until width || miner.y !in 0 until height) return@forEach
            val slot = if (miner.carrying) Mat.MINER_LOADED else Mat.MINER
            buffer[index(miner.x, miner.y)] = slot
            // A second pixel so a miner reads as a figure rather than a speck.
            if (miner.y > 0) buffer[index(miner.x, miner.y - 1)] = Mat.MINER
        }
    }

    private fun index(x: Int, y: Int) = y * width + x


    companion object {
        /** Loads of mineral in a world of default size. */
        const val DEFAULT_RICHNESS = 36

        /** Where the ground starts, as a fraction of the frame. */
        private const val SURFACE_FRACTION = 0.2f

        /** Damage per swing. */
        private const val PICK_POWER = 1f

        /** How much a planner dislikes digging relative to walking. */
        private const val DIG_WEIGHT = 2f

        /** Chance each loose neighbour of a fresh cut gets packed. */
        private const val SHORE_CHANCE = 0.75f

        /** Columns right of the cart where loads are tipped; on the platform. */
        private const val STOCK_OFFSET = 5

        /** Half-width of the stockpile, and how steep its flanks are. */
        private const val STOCK_HALF_WIDTH = 3
        private const val STOCK_SLOPE = 1.4f

        /** Platform width to the right of the cart. */
        private const val PLATFORM_WIDTH = 8

        /** Rows of sandstone under the cart. */
        private const val PAD_DEPTH = 3

        /** Terrain settles less often than the crew acts; it is the expensive pass. */
        private const val SETTLE_EVERY = 3


        /**
         * Loads to bury for a timer of this length.
         *
         * Sized so a crew working at an ordinary pace is roughly on the clock. On the
         * phone, a 20-minute mine with 18 loads was three loads in after half a minute
         * — far ahead — so the pacer had to throttle the crew almost to a standstill
         * for minutes while the clock caught up. A pacer can keep any world on time;
         * it is the size of the job that decides whether the world looks alive.
         * Long timers still hit the ceiling and run calmer: there is only so much
         * ground in the frame.
         */
        fun richnessFor(durationMinutes: Float): Int =
            (durationMinutes * LOADS_PER_MINUTE).roundToInt().coerceIn(24, 240)

        private const val LOADS_PER_MINUTE = 6f
    }
}
