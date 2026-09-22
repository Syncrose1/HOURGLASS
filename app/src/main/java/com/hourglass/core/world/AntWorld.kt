package com.hourglass.core.world

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/** Cell slots in an ant world. */
object AntMat {
    const val GROUND = 1
    const val GROUND_DARK = 2
    const val PEBBLE = 3
    const val PEBBLE_LIGHT = 4
    const val GRASS = 5
    const val FOOD = 6
    const val FOOD_BRIGHT = 7
    const val NEST = 8
    const val MOUND = 9
    const val TRAIL = 10
    const val ANT = 11
    const val ANT_CARRYING = 12
    const val TRAIL_FAINT = 13
    const val COUNT = 14

    fun blocks(cell: Int) = cell == PEBBLE || cell == PEBBLE_LIGHT || cell == GRASS
}

/**
 * A colony foraging a patch of dry ground, seen from above.
 *
 * Nothing tells an ant where the food is. Foragers wander out from the nest; one that
 * stumbles on a crumb carries it home, laying scent as it goes, and others pick up the
 * scent and follow it back out. Trails strengthen while a source pays and fade once it
 * is stripped. Homing is by dead reckoning, which is good but not perfect: laden ants
 * drift, bump into pebbles and grass, and feel their way round. Every so many loads a
 * new worker hatches, and the spoil mound round the entrance grows with the colony.
 *
 * The only thing tied to the clock is how hard the colony works ([WorldPacer]).
 */
class AntWorld(
    override val width: Int,
    override val height: Int,
    seed: Long,
    /** Loads of food the colony is after. */
    private val quota: Int = 60
) : World {

    private val ground = IntArray(width * height)
    private val food = IntArray(width * height)

    /** Scent laid by ants carrying food: followed outward, toward food. */
    private val toFood = FloatArray(width * height)

    override val cells = IntArray(width * height)

    private val generator = Random(seed)
    private val nestX = width / 2
    private val nestY = (height * 0.58f).toInt()

    private val ants = ArrayList<Ant>()
    private var foodTotal = 0
    private var delivered = 0
    private var moundRadius = -1
    private var evaporateCounter = 0
    private var pendingHatches = 0

    private class Ant(var x: Int, var y: Int, var dir: Int, val pace: Float) {
        var carrying = false
        var banked = 0f
        /** Steps since leaving a source or the nest: scent laid weakens with distance. */
        var travelled = 0
        /** Steps of searching left before this forager gives up and heads home. */
        var patience = 0
        var homing = false
        var resting = 0
    }

    override val objectiveProgress: Float
        get() = if (order == 0) 1f else (delivered.toFloat() / order).coerceAtMost(1f)

    private val order: Int get() = minOf(quota, foodTotal)

    override val objective: String get() = "$delivered of $order crumbs stored"

    override val kind: WorldKind get() = WorldKind.ANTS

    override val focusY: Float get() = nestY.toFloat() / height

    val antCount: Int get() = ants.size

    /** Whether any ant is standing somewhere it could not have walked. */
    fun anyAntInsideObstacle(): Boolean = ants.any { AntMat.blocks(ground[it.y * width + it.x]) }
    val storedLoads: Int get() = delivered

    init {
        paintGround()
        placeObstacles()
        placeFood()
        repeat(STARTING_ANTS) { hatch() }
        rebuildCells()
    }

    private fun paintGround() {
        for (i in ground.indices) {
            ground[i] = if (generator.nextFloat() < 0.22f) AntMat.GROUND_DARK else AntMat.GROUND
        }
    }

    /** Pebbles and grass tufts: things a forager has to go round, never through. */
    private fun placeObstacles() {
        val pebbles = width * height / 300
        repeat(pebbles) {
            val cx = generator.nextInt(width)
            val cy = generator.nextInt(height)
            if (hypot((cx - nestX).toFloat(), (cy - nestY).toFloat()) < NEST_CLEARING) return@repeat
            val rx = generator.nextFloat() * 3f + 1.2f
            val ry = generator.nextFloat() * 2.5f + 1f
            blob(cx, cy, rx, ry) { x, y ->
                val light = (x - cx) + (y - cy) < -1
                ground[y * width + x] = if (light) AntMat.PEBBLE_LIGHT else AntMat.PEBBLE
            }
        }
        // Grass grows in rows of tufts, which makes for walls with gaps.
        repeat(width * height / 900) {
            var x = generator.nextInt(width)
            var y = generator.nextInt(height)
            repeat(generator.nextInt(6, 16)) {
                if (x in 0 until width && y in 0 until height &&
                    hypot((x - nestX).toFloat(), (y - nestY).toFloat()) >= NEST_CLEARING
                ) {
                    ground[y * width + x] = AntMat.GRASS
                }
                x += generator.nextInt(-1, 2)
                y += if (generator.nextFloat() < 0.7f) 1 else 0
                if (generator.nextFloat() < 0.15f) y++ // a gap
            }
        }
    }

    /**
     * Food comes in drops — a fallen fruit, a spill of seed — scattered well away from
     * the nest, so the colony has to find each one and each one runs out.
     */
    private fun placeFood() {
        val target = maxOf(quota * 3 / 2, BASE_FOOD)
        var attempts = 0
        while (foodTotal < target && attempts < 400) {
            attempts++
            val cx = generator.nextInt(3, width - 3)
            val cy = generator.nextInt(3, height - 3)
            val distance = hypot((cx - nestX).toFloat(), (cy - nestY).toFloat())
            if (distance < FOOD_MIN_DISTANCE) continue
            val size = generator.nextFloat() * 1.6f + 0.9f
            blob(cx, cy, size, size * (0.7f + generator.nextFloat() * 0.6f)) { x, y ->
                val index = y * width + x
                if (!AntMat.blocks(ground[index]) && foodTotal < target) {
                    val amount = generator.nextInt(1, 6)
                    food[index] += amount
                    foodTotal += amount
                }
            }
        }
    }

    private inline fun blob(cx: Int, cy: Int, rx: Float, ry: Float, paint: (Int, Int) -> Unit) {
        val x0 = (cx - rx - 1).toInt().coerceAtLeast(0)
        val x1 = (cx + rx + 1).toInt().coerceAtMost(width - 1)
        val y0 = (cy - ry - 1).toInt().coerceAtLeast(0)
        val y1 = (cy + ry + 1).toInt().coerceAtMost(height - 1)
        for (y in y0..y1) for (x in x0..x1) {
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            if (dx * dx + dy * dy <= 1f + generator.nextFloat() * 0.35f) paint(x, y)
        }
    }

    private fun hatch() {
        val ant = Ant(nestX, nestY, generator.nextInt(8), 0.7f + generator.nextFloat() * 0.55f)
        ant.patience = searchPatience()
        ants += ant
    }

    private fun searchPatience() = generator.nextInt(PATIENCE_MIN, PATIENCE_MAX)

    override fun step(effort: Float, random: Random) {
        ants.forEach { ant ->
            if (ant.resting > 0) {
                ant.resting--
                return@forEach
            }
            // Like the mine crews: little effort is spent as ants stopping to groom or
            // idle, not as the whole colony crawling in slow motion.
            if (effort < SLACK_PACE) {
                val breakChance = (SLACK_PACE / effort.coerceAtLeast(0.001f) - 1f) / MEAN_REST_TICKS
                if (random.nextFloat() < breakChance) {
                    ant.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
                    return@forEach
                }
            }
            ant.banked += effort.coerceAtLeast(SLACK_PACE) * RATE * ant.pace
            var actions = 0
            while (ant.banked >= 1f && actions < MAX_ACTIONS_PER_TICK) {
                ant.banked -= 1f
                actions++
                act(ant, random)
            }
            if (ant.banked > MAX_ACTIONS_PER_TICK) ant.banked = MAX_ACTIONS_PER_TICK.toFloat()
        }

        while (pendingHatches > 0) {
            pendingHatches--
            if (ants.size < MAX_ANTS) hatch()
        }

        // Scent fades at the pace the colony works, so a slow colony's trails last as
        // long in steps walked as a quick one's.
        evaporateCounter++
        if (evaporateCounter >= EVAPORATE_EVERY) {
            evaporateCounter = 0
            val factor = exp(-EVAPORATION * effort.coerceIn(SLACK_PACE, 8f) * EVAPORATE_EVERY)
            for (i in toFood.indices) {
                val v = toFood[i]
                if (v > 0f) toFood[i] = if (v < 0.01f) 0f else v * factor
            }
            rebuildCells()
        }
    }

    private fun Ant.atNest() = abs(x - nestX) <= 1 && abs(y - nestY) <= 1

    private fun act(ant: Ant, random: Random) {
        ant.travelled++

        if (ant.carrying || ant.homing) {
            if (ant.atNest()) {
                if (ant.carrying) deliver(random)
                ant.carrying = false
                ant.homing = false
                ant.travelled = 0
                ant.patience = searchPatience()
                ant.dir = random.nextInt(8)
                // A forager home from a long walk sometimes stops a while.
                if (random.nextFloat() < REST_AT_NEST) {
                    ant.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
                }
                return
            }
            if (ant.carrying) {
                val index = ant.y * width + ant.x
                val scent = (1f - ant.travelled / SCENT_REACH).coerceAtLeast(0f)
                if (scent > toFood[index]) toFood[index] = scent
            }
            move(ant, random, homeward = true)
            return
        }

        // Searching. Food next to the ant gets picked up.
        for (d in 0 until 8) {
            val nx = ant.x + DX[d]
            val ny = ant.y + DY[d]
            if (nx !in 0 until width || ny !in 0 until height) continue
            val index = ny * width + nx
            if (food[index] > 0) {
                food[index]--
                ant.carrying = true
                ant.travelled = 0
                ant.dir = (d + 4) % 8
                return
            }
        }

        ant.patience--
        if (ant.patience <= 0) {
            ant.homing = true
            return
        }
        move(ant, random, homeward = false)
    }

    private fun deliver(random: Random) {
        delivered++
        if (delivered % HATCH_EVERY == 0) pendingHatches++
    }

    /**
     * One step. The ant weighs the three cells ahead of it (and, rarely, a sharper turn):
     * searchers by the scent toward food, laden ants by their sense of where home is.
     */
    private fun move(ant: Ant, random: Random, homeward: Boolean) {
        val homeAngleX: Float
        val homeAngleY: Float
        if (homeward) {
            val dx = (nestX - ant.x).toFloat()
            val dy = (nestY - ant.y).toFloat()
            val length = hypot(dx, dy).coerceAtLeast(0.001f)
            homeAngleX = dx / length
            homeAngleY = dy / length
        } else {
            homeAngleX = 0f
            homeAngleY = 0f
        }

        var total = 0f
        for (turn in TURNS.indices) {
            val d = (ant.dir + TURNS[turn] + 8) % 8
            val nx = ant.x + DX[d]
            val ny = ant.y + DY[d]
            weights[turn] = 0f
            if (nx !in 0 until width || ny !in 0 until height) continue
            val index = ny * width + nx
            if (AntMat.blocks(ground[index])) continue
            var w = TURN_WEIGHT[turn]
            if (homeward) {
                val alignment = (DX[d] * homeAngleX + DY[d] * homeAngleY) / DIAGONAL[d]
                w *= exp(HOMING_PULL * alignment)
            } else {
                // Follow scent outward: stronger scent means nearer the food.
                val here = toFood[ant.y * width + ant.x]
                val there = toFood[index]
                w *= 1f + SCENT_PULL * there + if (there > here) SCENT_GRADIENT else 0f
            }
            weights[turn] = w
            total += w
        }

        if (total <= 0f) {
            // Boxed in ahead: turn and feel for a way round.
            ant.dir = (ant.dir + if (random.nextBoolean()) 2 else 6) % 8
            return
        }
        var pick = random.nextFloat() * total
        var chosen = 0
        for (turn in TURNS.indices) {
            pick -= weights[turn]
            if (pick <= 0f) {
                chosen = turn
                break
            }
            chosen = turn
        }
        ant.dir = (ant.dir + TURNS[chosen] + 8) % 8
        ant.x += DX[ant.dir]
        ant.y += DY[ant.dir]
    }

    private val weights = FloatArray(TURNS.size)

    private fun rebuildCells() {
        val targetRadius = (MOUND_MIN + objectiveProgress * MOUND_GROWTH).roundToInt()
        if (targetRadius != moundRadius) moundRadius = targetRadius
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            val base = ground[index]
            val distance = hypot((x - nestX).toFloat(), (y - nestY).toFloat())
            cells[index] = when {
                distance < 1.6f -> AntMat.NEST
                distance <= moundRadius && !AntMat.blocks(base) -> AntMat.MOUND
                food[index] > 0 -> if (food[index] >= 4) AntMat.FOOD_BRIGHT else AntMat.FOOD
                AntMat.blocks(base) -> base
                toFood[index] > TRAIL_STRONG -> AntMat.TRAIL
                toFood[index] > TRAIL_FAINT -> AntMat.TRAIL_FAINT
                else -> base
            }
        }
    }

    override fun renderInto(buffer: IntArray) {
        cells.copyInto(buffer)
        ants.forEach { ant ->
            if (ant.resting > 0 && ant.atNest()) return@forEach // underground
            val index = ant.y * width + ant.x
            buffer[index] = if (ant.carrying) AntMat.ANT_CARRYING else AntMat.ANT
        }
    }

    companion object {
        /**
         * Crumbs to store for a timer this long. Measured: a colony starts near ten a
         * minute and, as it grows and its trails settle, tops out near thirty.
         */
        fun quotaFor(durationMinutes: Float): Int =
            (durationMinutes * LOADS_PER_MINUTE).roundToInt().coerceIn(12, 500)

        private const val LOADS_PER_MINUTE = 15f

        private const val RATE = 0.15f
        private const val MAX_ACTIONS_PER_TICK = 6
        private const val SLACK_PACE = 0.5f
        private const val REST_MIN_TICKS = 40
        private const val REST_MAX_TICKS = 140
        private const val MEAN_REST_TICKS = (REST_MIN_TICKS + REST_MAX_TICKS) / 2f
        private const val REST_AT_NEST = 0.2f

        private const val STARTING_ANTS = 8
        private const val MAX_ANTS = 24
        private const val HATCH_EVERY = 12

        private const val BASE_FOOD = 180
        private const val FOOD_MIN_DISTANCE = 16f
        private const val NEST_CLEARING = 6f

        private const val PATIENCE_MIN = 180
        private const val PATIENCE_MAX = 420

        private const val SCENT_REACH = 160f
        private const val SCENT_PULL = 6f
        private const val SCENT_GRADIENT = 1.5f
        private const val HOMING_PULL = 2.2f
        private const val EVAPORATION = 0.0009f
        private const val EVAPORATE_EVERY = 6
        private const val TRAIL_STRONG = 0.45f
        private const val TRAIL_FAINT = 0.12f

        private const val MOUND_MIN = 2f
        private const val MOUND_GROWTH = 4f

        private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        private val DIAGONAL = floatArrayOf(1f, 1.414f, 1f, 1.414f, 1f, 1.414f, 1f, 1.414f)
        private val TURNS = intArrayOf(0, -1, 1, -2, 2)
        private val TURN_WEIGHT = floatArrayOf(1f, 0.55f, 0.55f, 0.08f, 0.08f)
    }
}
