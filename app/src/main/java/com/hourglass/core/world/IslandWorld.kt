package com.hourglass.core.world

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Cell slots in an island world. */
object IslandMat {
    const val SKY = 1
    const val WATER = 2
    const val WATER_SURFACE = 3
    const val SEABED = 4
    const val SEABED_SAND = 5
    const val LAVA_HOT = 6
    const val LAVA = 7
    const val LAVA_COOL = 8
    const val BASALT = 9
    const val BASALT_DARK = 10
    const val ASH = 11
    const val STEAM = 12
    const val GRASS = 13
    const val SHRUB = 14
    const val COUNT = 15

    fun isLava(cell: Int) = cell == LAVA_HOT || cell == LAVA || cell == LAVA_COOL
    fun isOpen(cell: Int) = cell == SKY || cell == WATER || cell == WATER_SURFACE || cell == STEAM
    fun isRock(cell: Int) = cell == BASALT || cell == BASALT_DARK || cell == ASH ||
        cell == GRASS || cell == SHRUB
}

/**
 * A volcano building an island out of open sea, seen side-on.
 *
 * Lava wells up from a vent on the seabed. It runs downhill while it is hot, crusts
 * over as it cools, and quenches fast in the sea — in clouds of steam. While the vent
 * is shallow, seawater flashing to steam blasts it apart and it rains back down as ash,
 * which piles, slumps, and is worn away by the waves. Once the cone clears the surface,
 * the lava runs out over land, and grass and scrub take hold on rock that has cooled.
 *
 * Where the flows go, which side of the cone grows, whether a flow reaches the sea — all
 * of it comes from the rules. What is tied to the clock is only how hard the volcano
 * erupts ([WorldPacer]); the objective is rock laid down, so the island a timer earns is
 * however much the eruption managed to build.
 */
class IslandWorld(
    override val width: Int,
    override val height: Int,
    seed: Long,
    /** Cells of new rock the eruption is after. */
    private val quota: Int = 2400
) : World {

    override val cells = IntArray(width * height)
    private val heat = IntArray(width * height)

    private val generator = Random(seed)
    private val seaLevel = (height * SEA_LEVEL).toInt()

    private var ventX = width / 2 + generator.nextInt(-6, 7)
    private var ventY = 0
    private var eruptBank = 0f
    private var laid = 0
    private var tick = 0
    private var scanLeft = false

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val ash: Boolean)
    private val particles = ArrayList<Particle>()

    override val objectiveProgress: Float
        get() = (laid.toFloat() / quota).coerceAtMost(1f)

    override val objective: String get() = "${(objectiveProgress * 100).roundToInt()}% of the island built"

    override val kind: WorldKind get() = WorldKind.ISLAND

    override val focusY: Float get() = 0.45f

    val rockLaid: Int get() = laid

    /** Solid cells above the waterline: the island proper. */
    val landAboveSea: Int
        get() {
            var count = 0
            for (y in 0 until seaLevel) for (x in 0 until width) {
                if (IslandMat.isRock(cells[y * width + x])) count++
            }
            return count
        }

    init {
        buildSea()
    }

    private fun buildSea() {
        val phase = generator.nextFloat() * 6f
        val seabed = IntArray(width) { x ->
            val swell = sin(x * 0.11f + phase) * 2.5f + sin(x * 0.29f + phase * 2f) * 1.2f
            val edge = abs(x - width / 2f) / (width / 2f)
            (height * SEABED + swell + edge * 4f).roundToInt().coerceIn(seaLevel + 8, height - 3)
        }
        for (y in 0 until height) for (x in 0 until width) {
            cells[y * width + x] = when {
                y >= seabed[x] + 2 -> IslandMat.SEABED
                y >= seabed[x] -> if (generator.nextFloat() < 0.7f) IslandMat.SEABED_SAND else IslandMat.SEABED
                else -> openAt(y)
            }
        }
        ventY = seabed[ventX] - 1
    }

    private fun openAt(y: Int) = when {
        y < seaLevel -> IslandMat.SKY
        y == seaLevel -> IslandMat.WATER_SURFACE
        else -> IslandMat.WATER
    }

    private fun isWater(cell: Int) = cell == IslandMat.WATER || cell == IslandMat.WATER_SURFACE

    override fun step(effort: Float, random: Random) {
        tick++
        erupt(effort, random)
        flow(random)
        moveParticles(random)
        if (tick % ASH_EVERY == 0) settleRubble(random)
        if (tick % GROWTH_EVERY == 0) grow(random)
        if (tick % EROSION_EVERY == 0) erode(random)
    }

    /**
     * The vent pushes up through whatever is on top of it — its own lava, or rock that
     * has just crusted over it — so the cone climbs as it builds. Now and then the vent
     * shifts sideways, which is what gives a cone its lopsided flanks.
     */
    private fun erupt(effort: Float, random: Random) {
        eruptBank += effort * ERUPT_RATE
        if (random.nextFloat() < VENT_WANDER) {
            val nx = (ventX + if (random.nextBoolean()) 1 else -1).coerceIn(4, width - 5)
            ventX = nx
        }
        while (eruptBank >= 1f) {
            eruptBank -= 1f
            // Climb the conduit: the vent opens onto the first open cell above it.
            var y = ventY
            while (y > 0 && !IslandMat.isOpen(cells[y * width + ventX])) y--
            // Refind the base after a sideways shift or a collapse.
            ventY = y
            if (y <= 1) return
            val index = y * width + ventX
            val depthBelowSurface = y - seaLevel
            val cell = cells[index]
            if (depthBelowSurface in 0..SURTSEYAN_DEPTH && random.nextFloat() < SURTSEYAN_CHANCE) {
                // Seawater meets magma near the surface: a steam blast throws out ash.
                repeat(3) {
                    particles += Particle(
                        ventX + 0.5f, y.toFloat(),
                        (random.nextFloat() - 0.5f) * 1.4f,
                        -(0.8f + random.nextFloat() * 1.1f),
                        ash = true
                    )
                }
                puffSteam(ventX, y - 1, random)
                continue
            }
            if (depthBelowSurface < 0 && random.nextFloat() < FOUNTAIN_CHANCE) {
                // Above the sea, the odd fountain of spatter.
                particles += Particle(
                    ventX + 0.5f, y.toFloat(),
                    (random.nextFloat() - 0.5f) * 0.8f,
                    -(0.6f + random.nextFloat() * 0.7f),
                    ash = false
                )
                continue
            }
            if (IslandMat.isOpen(cell)) {
                cells[index] = IslandMat.LAVA_HOT
                heat[index] = MAX_HEAT
            }
        }
    }

    /**
     * Lava is viscous: it moves in fits, falls when it can, slides down a slope while it
     * is hot, and stiffens as it cools. It loses heat to air and far faster to water,
     * and crusts over into basalt.
     */
    private fun flow(random: Random) {
        scanLeft = !scanLeft
        for (y in height - 1 downTo 0) {
            for (i in 0 until width) {
                val x = if (scanLeft) i else width - 1 - i
                val index = y * width + x
                val cell = cells[index]
                when {
                    IslandMat.isLava(cell) -> lavaStep(x, y, index, random)
                    cell == IslandMat.STEAM -> steamStep(x, y, index, random)
                }
            }
        }
    }

    private fun lavaStep(x: Int, y: Int, index: Int, random: Random) {
        var h = heat[index]
        var water = 0
        var open = 0
        for (d in 0 until 4) {
            val nx = x + DX[d]
            val ny = y + DY[d]
            if (nx !in 0 until width || ny !in 0 until height) continue
            val c = cells[ny * width + nx]
            if (isWater(c)) water++ else if (IslandMat.isOpen(c)) open++
        }
        h -= 1 + water * WATER_QUENCH + open
        if (water > 0 && random.nextFloat() < STEAM_CHANCE) {
            val above = index - width
            if (y > 0 && IslandMat.isOpen(cells[above])) cells[above] = IslandMat.STEAM
        }
        if (h <= 0) {
            cells[index] = if (random.nextFloat() < 0.3f) IslandMat.BASALT_DARK else IslandMat.BASALT
            heat[index] = 0
            laid++
            return
        }
        heat[index] = h
        cells[index] = shadeFor(h)

        // Nothing molten hangs in the air: unsupported lava drops every tick.
        if (y + 1 < height && IslandMat.isOpen(cells[index + width])) {
            move(index, index + width, y, y + 1)
            return
        }
        if (random.nextFloat() > MOVE_CHANCE) return
        val fluidity = h.toFloat() / MAX_HEAT
        // Down a slope, then — only while hot — along the level.
        val first = if (random.nextBoolean()) 1 else -1
        for (side in intArrayOf(first, -first)) {
            val nx = x + side
            if (nx !in 0 until width) {
                // Over the edge of the frame and gone.
                if (fluidity > 0.3f && random.nextFloat() < 0.2f) {
                    cells[index] = openAt(y)
                    heat[index] = 0
                }
                return
            }
            if (y + 1 < height && IslandMat.isOpen(cells[(y + 1) * width + nx]) &&
                IslandMat.isOpen(cells[index + side])
            ) {
                move(index, (y + 1) * width + nx, y, y + 1)
                return
            }
        }
        if (random.nextFloat() < fluidity * SPREAD) {
            for (side in intArrayOf(first, -first)) {
                val nx = x + side
                if (nx in 0 until width && IslandMat.isOpen(cells[index + side])) {
                    move(index, index + side, y, y)
                    return
                }
            }
        }
    }

    private fun move(from: Int, to: Int, fromY: Int, @Suppress("UNUSED_PARAMETER") toY: Int) {
        cells[to] = cells[from]
        heat[to] = heat[from]
        cells[from] = openAt(fromY)
        heat[from] = 0
    }

    private fun shadeFor(h: Int) = when {
        h > MAX_HEAT * 2 / 3 -> IslandMat.LAVA_HOT
        h > MAX_HEAT / 3 -> IslandMat.LAVA
        else -> IslandMat.LAVA_COOL
    }

    private fun steamStep(x: Int, y: Int, index: Int, random: Random) {
        if (random.nextFloat() < STEAM_FADE || y == 0) {
            cells[index] = openAt(y)
            return
        }
        if (random.nextFloat() > 0.5f) return
        val nx = (x + random.nextInt(-1, 2)).coerceIn(0, width - 1)
        val up = (y - 1) * width + nx
        if (IslandMat.isOpen(cells[up]) && cells[up] != IslandMat.STEAM) {
            cells[up] = IslandMat.STEAM
            cells[index] = openAt(y)
        }
    }

    private fun puffSteam(x: Int, y: Int, random: Random) {
        repeat(4) {
            val nx = (x + random.nextInt(-2, 3)).coerceIn(0, width - 1)
            val ny = (y - random.nextInt(0, 3)).coerceIn(0, height - 1)
            val i = ny * width + nx
            if (IslandMat.isOpen(cells[i])) cells[i] = IslandMat.STEAM
        }
    }

    /** Ash and spatter in flight: thrown, dragged, and dropped where they land. */
    private fun moveParticles(random: Random) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val water = p.y >= seaLevel
            p.vy += if (water) 0.02f else GRAVITY
            val drag = if (water) 0.85f else 0.985f
            p.vx *= drag
            p.vy *= drag
            val nx = p.x + p.vx
            val ny = p.y + p.vy
            val cx = nx.toInt()
            val cy = ny.toInt()
            if (cx !in 0 until width || cy >= height) {
                iterator.remove()
                continue
            }
            if (cy < 0) {
                p.x = nx
                p.y = ny
                continue
            }
            if (!IslandMat.isOpen(cells[cy * width + cx])) {
                // Landed: settle into the last open cell on the way down.
                val lx = p.x.toInt().coerceIn(0, width - 1)
                val ly = p.y.toInt().coerceIn(0, height - 1)
                val li = ly * width + lx
                if (IslandMat.isOpen(cells[li])) {
                    if (p.ash) {
                        cells[li] = IslandMat.ASH
                        laid++
                    } else {
                        cells[li] = IslandMat.LAVA
                        heat[li] = MAX_HEAT / 2
                    }
                }
                iterator.remove()
                continue
            }
            p.x = nx
            p.y = ny
        }
    }

    /**
     * Loose ash slumps to its angle of rest and sinks through water. Fresh basalt does
     * too, more grudgingly: lava quenched in the sea shatters into rubble that tumbles
     * down the flanks, which is why a seamount is a cone and not a tower.
     */
    private fun settleRubble(random: Random) {
        for (y in height - 2 downTo 0) {
            for (x in 0 until width) {
                val index = y * width + x
                val cell = cells[index]
                val slide = when (cell) {
                    IslandMat.ASH -> 1f
                    IslandMat.BASALT, IslandMat.BASALT_DARK -> if (y >= seaLevel) RUBBLE_SLIDE else LAND_SLIDE
                    else -> continue
                }
                val below = index + width
                if (IslandMat.isOpen(cells[below])) {
                    cells[below] = cell
                    cells[index] = openAt(y)
                    continue
                }
                if (random.nextFloat() >= slide) continue
                val side = if (random.nextBoolean()) 1 else -1
                val nx = x + side
                // Basalt rubble is blocky and locks together: it only tumbles where the
                // flank drops away two cells for one across, so it stands steeper than ash.
                val steep = cell == IslandMat.ASH ||
                    (y + 2 < height && IslandMat.isOpen(cells[below + width + side]))
                if (nx in 0 until width && steep && IslandMat.isOpen(cells[below + side]) &&
                    IslandMat.isOpen(cells[index + side])
                ) {
                    cells[below + side] = cell
                    cells[index] = openAt(y)
                }
            }
        }
    }

    /**
     * Life arrives on cooled rock above the waterline: grass first, carried by wind and
     * birds, then scrub where grass has held.
     */
    private fun grow(random: Random) {
        repeat(GROWTH_SAMPLES) {
            val x = random.nextInt(width)
            val y = random.nextInt(1, seaLevel)
            val index = y * width + x
            val cell = cells[index]
            if (cells[index - width] != IslandMat.SKY) return@repeat
            if (cell == IslandMat.BASALT || cell == IslandMat.BASALT_DARK || cell == IslandMat.ASH) {
                if (nearHeat(x, y)) return@repeat
                val neighbourGrass = (x > 0 && cells[index - 1] == IslandMat.GRASS) ||
                    (x < width - 1 && cells[index + 1] == IslandMat.GRASS)
                if (random.nextFloat() < if (neighbourGrass) 0.5f else 0.12f) cells[index] = IslandMat.GRASS
            } else if (cell == IslandMat.GRASS && random.nextFloat() < 0.08f && y > 1) {
                cells[index - width] = IslandMat.SHRUB
            }
        }
    }

    private fun nearHeat(x: Int, y: Int): Boolean {
        for (dy in -2..2) for (dx in -2..2) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height &&
                IslandMat.isLava(cells[ny * width + nx])
            ) return true
        }
        return false
    }

    /** Waves wear loose ash off the shoreline; basalt stands. */
    private fun erode(random: Random) {
        for (y in seaLevel - 1..seaLevel + 2) {
            for (x in 0 until width) {
                val index = y * width + x
                if (cells[index] != IslandMat.ASH) continue
                val exposed = (x > 0 && isWater(cells[index - 1])) ||
                    (x < width - 1 && isWater(cells[index + 1]))
                if (exposed && random.nextFloat() < WAVE_EROSION) cells[index] = openAt(y)
            }
        }
    }

    override fun renderInto(buffer: IntArray) {
        cells.copyInto(buffer)
        particles.forEach { p ->
            val x = p.x.toInt()
            val y = p.y.toInt()
            if (x in 0 until width && y in 0 until height) {
                buffer[y * width + x] = if (p.ash) IslandMat.ASH else IslandMat.LAVA_HOT
            }
        }
    }

    companion object {
        /**
         * Rock for a timer this long. Even a short timer builds enough to break the
         * surface — an island that never shows is not much of a story — and longer
         * ones build a bigger island, more calmly.
         */
        fun quotaFor(durationMinutes: Float): Int =
            (durationMinutes * 200f).roundToInt().coerceIn(1800, 3400)

        private const val SEA_LEVEL = 0.5f
        private const val SEABED = 0.8f

        /** Lava cells per tick at effort 1. */
        private const val ERUPT_RATE = 0.14f
        private const val VENT_WANDER = 0.004f
        private const val SURTSEYAN_DEPTH = 5
        private const val SURTSEYAN_CHANCE = 0.25f
        private const val FOUNTAIN_CHANCE = 0.12f

        private const val MAX_HEAT = 240
        private const val WATER_QUENCH = 5
        private const val STEAM_CHANCE = 0.03f
        private const val STEAM_FADE = 0.03f
        private const val MOVE_CHANCE = 0.4f
        private const val SPREAD = 0.9f
        private const val GRAVITY = 0.06f

        private const val ASH_EVERY = 2
        private const val RUBBLE_SLIDE = 0.5f
        private const val LAND_SLIDE = 0.08f
        private const val GROWTH_EVERY = 15
        private const val GROWTH_SAMPLES = 24
        private const val EROSION_EVERY = 10
        private const val WAVE_EROSION = 0.02f

        private val DX = intArrayOf(1, -1, 0, 0)
        private val DY = intArrayOf(0, 0, 1, -1)
    }
}
