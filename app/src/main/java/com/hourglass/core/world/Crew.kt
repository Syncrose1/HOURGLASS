package com.hourglass.core.world

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.random.Random

/**
 * The ground a [Crew] works: what it can walk through, what it can break, what it
 * is after and where it takes it.
 *
 * A world describes its terrain through this and gets a whole crew for free — routing,
 * digging, hauling, falling, climbing out of holes. Miners, beavers and anything else
 * that goes and fetches things are all the same machinery pointed at different ground.
 */
interface Site {
    val width: Int
    val height: Int

    /** A worker may occupy this cell: air, sky, water. */
    fun isOpen(index: Int): Boolean

    /** A worker standing (or swimming) here stays put rather than falling. */
    fun supports(x: Int, y: Int): Boolean

    /**
     * Planner cost of breaking through this cell, or -1 if it cannot be broken.
     * [carrying] lets a site forbid, say, digging through a seam with a load in hand.
     */
    fun breakCost(index: Int, carrying: Boolean): Int

    /** A cell a worker wants to break and take away. */
    fun isQuarry(index: Int): Boolean

    /** A loaded worker may unload here. */
    fun isDropOff(x: Int, y: Int): Boolean

    /** One unit of work on a cell. Returns true once it gives way. */
    fun work(index: Int, random: Random): Boolean

    /** A load has been delivered. */
    fun unload(random: Random)

    /**
     * Whether a worker can tell what this cell is without getting close. Open ground is
     * obvious; what is inside the rock is not, until someone brings a lamp near it.
     */
    fun startsKnown(index: Int): Boolean = isOpen(index)

    /** What a worker guesses it will cost to get through a cell it has not seen. */
    val assumedBreakCost: Int get() = 9

    /**
     * How promising an unexplored cell looks to a worker with nothing better to do.
     * Zero or less means not worth heading for.
     */
    fun prospect(x: Int, y: Int): Float = 1f
}

/** One member of a crew. */
class Worker(var x: Int, var y: Int, internal val pace: Float) {
    var carrying: Boolean = false

    /** Which of the ten [Souls] this is. */
    var soul: Int = -1
        internal set

    /** True while prospecting: heading for somewhere that might turn something up. */
    var exploring: Boolean = false
        internal set

    /** Ticks of rest left. A resting worker does nothing at all. */
    var resting: Int = 0
        internal set

    internal var banked: Float = 0f
    internal var path: IntArray? = null
    internal var pathPos: Int = 0
    internal var climbing: Boolean = false
    internal var cooldown: Int = 0
    internal var patience: Int = 0
}

/**
 * A crew of workers fetching things from a [Site].
 *
 * Workers route by effort — open ground is cheap, soft ground costs a few blows, hard
 * ground many — so they reuse each other's workings, share shafts, and only cut new
 * ground where they must. They fall when nothing holds them, climb along walls, and
 * when there is truly no honest route home they take a rope. [effort] from the
 * [WorldPacer] sets how hard they work; nothing else about them is governed.
 */
class Crew(
    private val site: Site,
    spawns: List<Pair<Int, Int>>,
    seed: Long = 0L,
    private val rate: Float = DEFAULT_RATE,
    /** Where the crew's work is credited, and what to call it; an empty name goes uncounted. */
    val deeds: Deeds? = null,
    private val deliveredDeed: String = "loads delivered",
    private val brokeDeed: String = "cells broken"
) {
    private val temperament = Random(seed)

    private val souls = Souls.cast(spawns.size, temperament)

    /** Each worker is one of the ten souls, and goes at that soul's pace. */
    val workers: List<Worker> = spawns.mapIndexed { i, (x, y) ->
        Worker(x, y, pace = Souls.pace(souls[i], temperament)).also { it.soul = souls[i] }
    }

    private val width = site.width
    private val height = site.height
    private val dist = IntArray(width * height)
    private val prev = IntArray(width * height)
    private val frontier = PriorityQueue<Long>()

    /**
     * What the crew has seen. Workers plan with this, not with the truth: a seam nobody
     * has shone a lamp on cannot be a destination, and a boulder nobody has hit is
     * assumed to be ordinary rock — until someone tunnels up to it.
     */
    private val known = BooleanArray(width * height) { site.startsKnown(it) }

    init {
        workers.forEach { reveal(it) }
    }

    fun isKnown(index: Int): Boolean = known[index]

    fun step(effort: Float, random: Random) {
        workers.forEach { worker ->
            if (worker.resting > 0) {
                worker.resting--
                return@forEach
            }
            // Below a working pace, effort is spent as breaks rather than slow motion:
            // a crew ahead of the clock sits down for a while, it does not mime digging
            // at a tenth of the speed. Each worker takes a break often enough that the
            // time spent working, times the working pace, comes back to the effort asked.
            if (effort < SLACK_PACE) {
                val breakChance = (SLACK_PACE / effort.coerceAtLeast(0.001f) - 1f) / MEAN_REST_TICKS
                if (random.nextFloat() < breakChance && site.supports(worker.x, worker.y)) {
                    rest(worker, random)
                    return@forEach
                }
            }
            worker.banked += effort.coerceAtLeast(SLACK_PACE) * rate * worker.pace
            var actions = 0
            while (worker.banked >= 1f && actions < MAX_ACTIONS_PER_TICK) {
                worker.banked -= 1f
                actions++
                act(worker, random)
            }
            // A crew that cannot spend what it earns should not hoard a burst for later.
            if (worker.banked > MAX_ACTIONS_PER_TICK) worker.banked = MAX_ACTIONS_PER_TICK.toFloat()
        }
    }

    private fun act(worker: Worker, random: Random) {
        val here = worker.y * width + worker.x
        if (!worker.climbing && site.isOpen(here) && !site.supports(worker.x, worker.y) &&
            worker.y + 1 < height && site.isOpen(here + width)
        ) {
            worker.y++
            endPath(worker)
            return
        }

        if (worker.carrying && site.isDropOff(worker.x, worker.y)) {
            worker.carrying = false
            endPath(worker)
            site.unload(random)
            if (deliveredDeed.isNotEmpty()) deeds?.credit(worker.soul, deliveredDeed)
            // A load home is a good moment to stop for a breather.
            if (random.nextFloat() < REST_AFTER_DELIVERY) rest(worker, random)
            return
        }

        if (!worker.carrying && random.nextFloat() < REST_CHANCE) {
            rest(worker, random)
            return
        }

        // Prospecting runs on patience. A tunnel that is not turning anything up gets
        // abandoned, and the worker turns back to try somewhere else.
        if (worker.exploring && worker.path != null) {
            worker.patience--
            if (worker.patience <= 0) endPath(worker)
        }

        // A worker with no route waits before searching again. A failed search walks
        // the whole grid twice, and a stranded worker retrying on every action was the
        // single most expensive thing in any world.
        if (worker.path == null && worker.cooldown > 0) {
            worker.cooldown--
            wander(worker, random)
            return
        }

        val path = worker.path ?: chooseRoute(worker, random)?.also {
            worker.path = it
            worker.pathPos = 0
        }
        if (path == null) {
            worker.cooldown = REPLAN_COOLDOWN
            wander(worker, random)
            return
        }

        val next = path[worker.pathPos]
        val nx = next % width
        val ny = next / width
        if (abs(nx - worker.x) + abs(ny - worker.y) != 1) {
            endPath(worker)
            return
        }

        when {
            site.isOpen(next) -> {
                if (worker.climbing || ny > worker.y || site.supports(nx, ny)) {
                    worker.x = nx
                    worker.y = ny
                    worker.pathPos++
                    if (reveal(worker) && worker.exploring) {
                        // Something worth having just came into the light.
                        endPath(worker)
                    } else if (worker.pathPos >= path.size) {
                        endPath(worker)
                    }
                } else {
                    endPath(worker)
                }
            }
            site.breakCost(next, worker.carrying) >= 0 -> {
                val quarry = site.isQuarry(next)
                if (site.work(next, random)) {
                    deeds?.credit(worker.soul, brokeDeed)
                    if (quarry && !worker.carrying) worker.carrying = true
                    endPath(worker)
                    reveal(worker)
                }
            }
            // Walked up to something that will not give — a boulder, say. It was
            // unknown when the route was planned; now it is known, and the worker has
            // to turn back and find another way.
            else -> endPath(worker)
        }
    }

    private fun rest(worker: Worker, random: Random) {
        worker.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
        endPath(worker)
    }

    /**
     * Marks everything within lamplight as seen. Returns true if that turned up a
     * quarry nobody knew about.
     */
    private fun reveal(worker: Worker): Boolean {
        var found = false
        for (dy in -SENSE_RADIUS..SENSE_RADIUS) {
            for (dx in -SENSE_RADIUS..SENSE_RADIUS) {
                if (dx * dx + dy * dy > SENSE_RADIUS * SENSE_RADIUS) continue
                val x = worker.x + dx
                val y = worker.y + dy
                if (x !in 0 until width || y !in 0 until height) continue
                val index = y * width + x
                if (known[index]) continue
                known[index] = true
                if (site.isQuarry(index)) found = true
            }
        }
        return found
    }

    /**
     * Where to go next. A loaded worker heads home. An empty one heads for the nearest
     * seam it knows of — and if it knows of none it can reach, it goes prospecting.
     */
    private fun chooseRoute(worker: Worker, random: Random): IntArray? {
        worker.exploring = false
        val direct = plan(worker, climbing = false, target = -1)
            ?: plan(worker, climbing = true, target = -1)?.also { worker.climbing = true }
        if (direct != null || worker.carrying) return direct
        return prospect(worker, random)
    }

    /**
     * Picks somewhere promising that nobody has seen, and tunnels for it. Most of these
     * trips find nothing — which is what prospecting is, and why a mine is a tangle of
     * side galleries and dead ends rather than a set of straight lines to the seams.
     */
    private fun prospect(worker: Worker, random: Random): IntArray? {
        var best = -1
        var bestScore = 0f
        repeat(PROSPECT_SAMPLES) {
            val x = worker.x + random.nextInt(-PROSPECT_RANGE, PROSPECT_RANGE + 1)
            val y = worker.y + random.nextInt(-PROSPECT_RANGE, PROSPECT_RANGE + 1)
            if (x !in 0 until width || y !in 0 until height) return@repeat
            val index = y * width + x
            if (known[index]) return@repeat
            val score = site.prospect(x, y) * (0.5f + random.nextFloat())
            if (score > bestScore) {
                bestScore = score
                best = index
            }
        }
        if (best < 0) return null
        val route = plan(worker, climbing = false, target = best) ?: return null
        worker.exploring = true
        worker.patience = route.size * PATIENCE_FACTOR + PATIENCE_SLACK
        return route
    }

    private fun endPath(worker: Worker) {
        worker.path = null
        worker.climbing = false
    }

    private fun plan(worker: Worker, climbing: Boolean, target: Int): IntArray? {
        val start = worker.y * width + worker.x
        dist.fill(Int.MAX_VALUE)
        frontier.clear()
        dist[start] = 0
        frontier.add(encode(0, start))

        var expansions = 0
        while (frontier.isNotEmpty() && expansions < MAX_EXPANSIONS) {
            val entry = frontier.poll() ?: break
            val cost = (entry ushr 32).toInt()
            val at = (entry and 0xffffffffL).toInt()
            if (cost > dist[at]) continue
            expansions++

            val x = at % width
            val y = at / width
            val reached = if (target >= 0) at == target else isGoal(worker, at, x, y)
            if (at != start && reached) return reconstruct(start, at)

            for (direction in 0 until 4) {
                val nx = x + DX[direction]
                val ny = y + DY[direction]
                if (nx !in 0 until width || ny !in 0 until height) continue
                val next = ny * width + nx
                val step = stepCost(worker, y, nx, ny, next, climbing)
                if (step < 0) continue
                val total = cost + step
                if (total < dist[next]) {
                    dist[next] = total
                    prev[next] = at
                    frontier.add(encode(total, next))
                }
            }
        }
        return null
    }

    private fun isGoal(worker: Worker, index: Int, x: Int, y: Int): Boolean =
        if (worker.carrying) known[index] && site.isOpen(index) && site.isDropOff(x, y)
        else known[index] && site.isQuarry(index)

    private fun stepCost(worker: Worker, y: Int, nx: Int, ny: Int, next: Int, climbing: Boolean): Int =
        when {
            // Planning is done on belief: unseen ground is priced at a guess.
            !known[next] -> site.assumedBreakCost
            site.isOpen(next) -> when {
                ny > y -> 1
                site.supports(nx, ny) -> 1
                climbing -> CLIMB_COST
                else -> -1
            }
            else -> site.breakCost(next, worker.carrying)
        }

    private fun reconstruct(start: Int, goal: Int): IntArray {
        val reversed = ArrayList<Int>()
        var at = goal
        while (at != start) {
            reversed.add(at)
            at = prev[at]
        }
        reversed.reverse()
        return reversed.toIntArray()
    }

    private fun wander(worker: Worker, random: Random) {
        val direction = random.nextInt(4)
        val nx = worker.x + DX[direction]
        val ny = worker.y + DY[direction]
        if (nx in 0 until width && ny in 0 until height &&
            site.isOpen(ny * width + nx) && site.supports(nx, ny)
        ) {
            worker.x = nx
            worker.y = ny
        }
    }

    private fun encode(cost: Int, index: Int): Long = (cost.toLong() shl 32) or index.toLong()

    companion object {
        /**
         * Actions per tick at effort 1: about six a second at thirty ticks a second.
         * Fast enough to follow, slow enough to watch.
         */
        const val DEFAULT_RATE = 0.2f

        /** How far a worker can see into unexplored ground. */
        private const val SENSE_RADIUS = 4

        /** Prospecting: candidate spots considered, and how far afield. */
        private const val PROSPECT_SAMPLES = 14
        private const val PROSPECT_RANGE = 14

        /** Actions a prospecting trip gets, per cell of planned route, before giving up. */
        private const val PATIENCE_FACTOR = 3
        private const val PATIENCE_SLACK = 12

        /** Chance per action of stopping for a rest, and per delivery. */
        private const val REST_CHANCE = 0.004f
        private const val REST_AFTER_DELIVERY = 0.25f
        private const val REST_MIN_TICKS = 45
        private const val REST_MAX_TICKS = 160
        private const val MEAN_REST_TICKS = (REST_MIN_TICKS + REST_MAX_TICKS) / 2f

        /** The slowest a worker ever visibly works; less effort than this is breaks. */
        private const val SLACK_PACE = 0.5f

        /** Stops a big effort correction from teleporting the crew across the map. */
        private const val MAX_ACTIONS_PER_TICK = 6

        /** Planner cost of a step through open air on a rope. */
        private const val CLIMB_COST = 6

        /** Actions a worker waits after finding no route before searching again. */
        private const val REPLAN_COOLDOWN = 12

        /** Bounds a single route search. */
        private const val MAX_EXPANSIONS = 20_000

        private val DX = intArrayOf(0, 1, 0, -1)
        private val DY = intArrayOf(-1, 0, 1, 0)
    }
}
