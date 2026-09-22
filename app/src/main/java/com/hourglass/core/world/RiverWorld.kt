package com.hourglass.core.world

import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Palette slots for the river. */
object RiverMat {
    const val AIR = 0
    const val SKY = 1
    const val EARTH = 2
    const val EARTH_DARK = 3
    const val ROCK = 4
    const val WATER = 5
    const val LOG = 6
    const val LOG_DARK = 7
    const val STICK = 8
    const val MUD = 9
    const val GRASS_DRY = 10
    const val GRASS = 11
    const val WATER_SURFACE = 12
    const val BEAVER = 13
    const val BEAVER_LOADED = 14

    const val COUNT = 15

    fun isOpen(cell: Int): Boolean = cell == AIR || cell == SKY || cell == WATER

    /** Open to water specifically: water spreads into air and sky, not into water. */
    fun isDry(cell: Int): Boolean = cell == AIR || cell == SKY

    fun isWood(cell: Int): Boolean = cell == LOG || cell == LOG_DARK || cell == STICK

    fun isJam(cell: Int): Boolean = isWood(cell) || cell == MUD

    fun hardness(cell: Int): Float = when (cell) {
        MUD -> 1f
        STICK -> 1.5f
        LOG, LOG_DARK -> 5f
        else -> Float.MAX_VALUE
    }
}

/**
 * A valley, a logjam, and the beavers taking it apart.
 *
 * On the left a spring-fed lake stands behind a jam of logs, sticks and mud; on the
 * right the riverbed is dry and the grass is brown. Beavers climb the jam from the dry
 * side and pull it apart from the top — you cannot drag a log out from under a pile —
 * hauling each piece off into the woods beyond the frame. They swim.
 *
 * The water is its own cellular automaton and owes the beavers nothing. The spring
 * holds the lake just under the top of the jam, so as the jam comes down the lake
 * begins to overtop it: first a trickle over the lowest notch, then a fall, then a
 * river running down the valley and away off the edge of the frame. The flow grows
 * with the work rather than arriving all at once. Grass the water reaches turns green.
 *
 * An earlier version let the beavers take the cheapest wood, which was always the
 * bottom: the jam breached in the first minutes, a closed valley flooded flat, and
 * for the rest of the timer nothing moved but a remnant of jam hanging in the air.
 */
class RiverWorld(
    override val width: Int,
    override val height: Int,
    seed: Long,
    /** Roughly how many cells of wood are in the jam. */
    private val richness: Int = MineWorld.DEFAULT_RICHNESS * 3
) : World, Site {

    override val cells = IntArray(width * height)
    private val damage = FloatArray(width * height)
    private val stamp = IntArray(width * height)
    private var pass = 0

    private val generator = Random(seed)
    private lateinit var crew: Crew

    private var woodTotal = 0
    private var woodHauled = 0
    private var lakeLevel = 0
    private var greenCounter = 0
    private var damLeft = 0
    private var damRight = 0

    /** The highest row that still has jam in it. */
    private var crest = 0

    /** Water that has run off the edge of the frame: the river's total flow. */
    var outflow: Int = 0
        private set

    override val objectiveProgress: Float
        get() = if (woodTotal == 0) 1f else (woodHauled.toFloat() / woodTotal).coerceAtMost(1f)

    override val objective: String get() = "$woodHauled of $woodTotal pulled from the jam"

    override val kind: WorldKind get() = WorldKind.RIVER

    override val focusY: Float get() = 0.55f

    val waterCount: Int get() = cells.count { it == RiverMat.WATER }
    val beaverCount: Int get() = crew.workers.size
    val jamTotal: Int get() = woodTotal
    val jamHauled: Int get() = woodHauled

    init {
        generate()
    }

    // --- generation -------------------------------------------------------

    private fun generate() {
        val bed = (height * BED_FRACTION).roundToInt()
        val damTop = (height * DAM_TOP_FRACTION).roundToInt()
        val damHeight = bed - damTop
        val damWidth = (richness / damHeight.coerceAtLeast(1)).coerceIn(4, width / 5)
        damLeft = (width * DAM_LEFT_FRACTION).roundToInt()
        damRight = damLeft + damWidth
        val drop = height * DOWNSTREAM_DROP
        val phase = generator.nextFloat() * 6.28f

        // Ground: a flat lake bed on the left, falling away downstream on the right.
        val surface = IntArray(width) { x ->
            if (x < damRight) bed
            else {
                val t = (x - damRight).toFloat() / (width - damRight)
                (bed + t * drop + sin(t * 9f + phase) * height * 0.012f).roundToInt()
                    .coerceIn(bed, height - 4)
            }
        }

        for (x in 0 until width) {
            for (y in 0 until height) {
                val depth = y - surface[x]
                cells[y * width + x] = when {
                    depth < 0 -> RiverMat.SKY
                    depth == 0 && x >= damRight -> RiverMat.GRASS_DRY
                    depth < 5 -> if (generator.nextFloat() < 0.3f) RiverMat.EARTH_DARK else RiverMat.EARTH
                    depth < 14 -> RiverMat.EARTH_DARK
                    else -> RiverMat.ROCK
                }
            }
        }

        // The lake, filled to just under the top of the jam and held there by the spring.
        lakeLevel = damTop + LAKE_FREEBOARD
        for (x in 0 until damLeft) {
            for (y in lakeLevel until surface[x]) cells[y * width + x] = RiverMat.WATER
        }

        buildJam(damLeft, damRight, damTop, bed)

        // The crew comes out of the woods at the right-hand edge.
        crew = Crew(
            site = this,
            seed = generator.nextLong(),
            spawns = List((width / 20).coerceIn(2, 5)) { index ->
                val x = (width - 2 - index * 2).coerceIn(damRight + 1, width - 1)
                x to surface[x] - 1
            }
        )
    }

    /**
     * Logs laid across the channel in runs, packed with sticks and plastered with mud —
     * heavier timber low down, lighter debris towards the top, as a real jam settles.
     */
    private fun buildJam(left: Int, right: Int, top: Int, bed: Int) {
        for (y in top until bed) {
            val depth = (y - top).toFloat() / (bed - top)
            var x = left
            while (x < right) {
                val roll = generator.nextFloat()
                when {
                    roll < 0.35f + depth * 0.35f -> {
                        val length = generator.nextInt(2, 6)
                        val tint = if (generator.nextFloat() < 0.4f) RiverMat.LOG_DARK else RiverMat.LOG
                        repeat(length) {
                            if (x < right) {
                                cells[y * width + x] = tint
                                x++
                            }
                        }
                    }
                    roll < 0.8f -> {
                        cells[y * width + x] = RiverMat.STICK
                        x++
                    }
                    else -> {
                        cells[y * width + x] = RiverMat.MUD
                        x++
                    }
                }
            }
        }
        // Everything in the jam counts: the mud has to come off too.
        woodTotal = cells.count { RiverMat.isJam(it) }
        crest = top
    }

    // --- simulation -------------------------------------------------------

    override fun step(effort: Float, random: Random) {
        crew.step(effort, random)
        spring(random)
        repeat(WATER_PASSES) { flow(random) }
        drain()
        greenCounter++
        if (greenCounter >= GREEN_EVERY) {
            greenCounter = 0
            green()
        }
    }

    /**
     * Falling-sand water: drop, else slide diagonally, else spread along the level.
     * Mass is conserved — a cell of water only ever moves.
     */
    private fun flow(random: Random) {
        pass++
        for (y in height - 2 downTo 0) {
            val leftFirst = random.nextBoolean()
            for (step in 0 until width) {
                val x = if (leftFirst) step else width - 1 - step
                val here = y * width + x
                if (cells[here] != RiverMat.WATER || stamp[here] == pass) continue

                val below = here + width
                if (RiverMat.isDry(cells[below])) {
                    move(here, below)
                    continue
                }

                val side = if (random.nextBoolean()) 1 else -1
                if (slide(x, here, below, side) || slide(x, here, below, -side)) continue

                var target = spread(x, y, side)
                if (target < 0) target = spread(x, y, -side)
                if (target >= 0) move(here, target)
            }
        }
    }

    private fun slide(x: Int, here: Int, below: Int, side: Int): Boolean {
        val nx = x + side
        if (nx !in 0 until width) return false
        if (!RiverMat.isDry(cells[below + side]) || !RiverMat.isDry(cells[here + side])) return false
        move(here, below + side)
        return true
    }

    /** Furthest dry cell along the row within reach, or -1. */
    private fun spread(x: Int, y: Int, side: Int): Int {
        var target = -1
        for (k in 1..DISPERSION) {
            val nx = x + side * k
            if (nx !in 0 until width) break
            val cell = y * width + nx
            if (!RiverMat.isDry(cells[cell])) break
            target = cell
        }
        return target
    }

    private fun move(from: Int, to: Int) {
        cells[to] = RiverMat.WATER
        cells[from] = RiverMat.SKY
        stamp[to] = pass
    }

    /**
     * Tops the lake up, never above its level, so it holds steady behind an intact jam
     * and keeps feeding whatever the beavers open. Water is added on the surface at
     * random points across the lake: feeding a single edge stacked a pillar of water
     * there that the automaton could never quite level out.
     */
    private fun spring(random: Random) {
        repeat(SPRING_RATE) {
            val x = random.nextInt(0, damLeft.coerceAtLeast(1))
            var y = lakeLevel
            // Find the first dry cell above the water in this column, at or below level.
            while (y < height && !RiverMat.isDry(cells[y * width + x])) y++
            var top = y
            while (top + 1 < height && RiverMat.isDry(cells[(top + 1) * width + x])) top++
            if (top in lakeLevel until height && RiverMat.isDry(cells[top * width + x])) {
                cells[top * width + x] = RiverMat.WATER
            }
        }
    }

    /** Water reaching the right-hand edge runs away off the frame. */
    private fun drain() {
        for (y in 0 until height) {
            val here = y * width + width - 1
            if (cells[here] == RiverMat.WATER) {
                cells[here] = RiverMat.SKY
                outflow++
            }
        }
    }

    /** Grass the water has reached greens, and stays green. */
    private fun green() {
        for (y in 1 until height) {
            for (x in 0 until width) {
                val here = y * width + x
                if (cells[here] != RiverMat.GRASS_DRY) continue
                if (wetNear(x, y)) cells[here] = RiverMat.GRASS
            }
        }
    }

    private fun wetNear(x: Int, y: Int): Boolean {
        for (dy in -2..0) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height &&
                cells[ny * width + nx] == RiverMat.WATER
            ) return true
        }
        return false
    }

    // --- the crew's view of the ground ------------------------------------

    override fun isOpen(index: Int): Boolean = RiverMat.isOpen(cells[index])

    /** Beavers swim, so water holds them up; otherwise it is ground below or beside. */
    override fun supports(x: Int, y: Int): Boolean {
        if (y >= height - 1) return true
        if (cells[y * width + x] == RiverMat.WATER) return true
        return solid(x, y + 1) ||
            solid(x - 1, y) || solid(x + 1, y) ||
            solid(x - 1, y + 1) || solid(x + 1, y + 1)
    }

    private fun solid(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && !RiverMat.isOpen(cells[y * width + x])

    override fun breakCost(index: Int, carrying: Boolean): Int {
        if (carrying || !isQuarry(index)) return -1
        return 1 + (RiverMat.hardness(cells[index]) * GNAW_WEIGHT).toInt()
    }

    /**
     * Jam with nothing on top of it, near the crest.
     *
     * "Nothing on top" alone let the beavers dig straight down the nearest column: each
     * log taken exposed the one below, and the jam was eaten a column at a time from
     * the downstream face. The lake then held unchanged until the last column went and
     * flooded all at once. Working only near the crest lowers the whole jam together,
     * so the lake overtops it early and the flow grows as the crest comes down.
     */
    override fun isQuarry(index: Int): Boolean {
        if (!RiverMat.isJam(cells[index])) return false
        if (index / width > crest + CREST_LAYER) return false
        val above = index - width
        return above < 0 || RiverMat.isOpen(cells[above])
    }

    /** Off into the woods beyond the right-hand edge. */
    override fun isDropOff(x: Int, y: Int): Boolean = x >= width - 2

    /** A jam stands in the open: the beavers can see all of it. */
    override fun startsKnown(index: Int): Boolean = true

    override fun work(index: Int, random: Random): Boolean {
        val cell = cells[index]
        damage[index] += GNAW_POWER
        if (damage[index] < RiverMat.hardness(cell)) return false
        damage[index] = 0f
        cells[index] = RiverMat.SKY
        updateCrest()
        return true
    }

    private fun updateCrest() {
        for (y in crest until height) {
            for (x in damLeft until damRight) {
                if (RiverMat.isJam(cells[y * width + x])) {
                    crest = y
                    return
                }
            }
        }
        crest = height
    }

    override fun unload(random: Random) {
        woodHauled++
    }

    override fun renderInto(buffer: IntArray) {
        cells.copyInto(buffer)
        // The top of any body of water catches the light.
        for (index in width until buffer.size) {
            if (buffer[index] == RiverMat.WATER && RiverMat.isDry(cells[index - width])) {
                buffer[index] = RiverMat.WATER_SURFACE
            }
        }
        crew.workers.forEach { beaver ->
            if (beaver.x !in 0 until width || beaver.y !in 0 until height) return@forEach
            val slot = if (beaver.carrying) RiverMat.BEAVER_LOADED else RiverMat.BEAVER
            buffer[beaver.y * width + beaver.x] = slot
            // Beavers are long and low: a second pixel beside rather than above.
            val tail = beaver.x - 1
            if (tail >= 0 && RiverMat.isOpen(cells[beaver.y * width + tail])) {
                buffer[beaver.y * width + tail] = RiverMat.BEAVER
            }
        }
    }

    private companion object {
        const val BED_FRACTION = 0.64f
        const val DAM_TOP_FRACTION = 0.34f
        const val DAM_LEFT_FRACTION = 0.38f
        const val DOWNSTREAM_DROP = 0.16f
        /** Rows between the lake's surface and the top of the jam. */
        const val LAKE_FREEBOARD = 2

        /** Cells of water the spring adds per tick. */
        const val SPRING_RATE = 6

        /** Rows below the crest the beavers will work. */
        const val CREST_LAYER = 1

        /** Cells a drop of water can travel along the level in one pass. */
        const val DISPERSION = 3
        const val WATER_PASSES = 2
        const val GREEN_EVERY = 6

        const val GNAW_POWER = 1f
        const val GNAW_WEIGHT = 2f
    }
}
