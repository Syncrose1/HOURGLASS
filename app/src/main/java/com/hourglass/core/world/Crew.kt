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
}

/** One member of a crew. */
class Worker(var x: Int, var y: Int) {
    var carrying: Boolean = false
    internal var banked: Float = 0f
    internal var path: IntArray? = null
    internal var pathPos: Int = 0
    internal var climbing: Boolean = false
    internal var cooldown: Int = 0
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
    private val rate: Float = DEFAULT_RATE
) {
    val workers: List<Worker> = spawns.map { (x, y) -> Worker(x, y) }

    private val width = site.width
    private val height = site.height
    private val dist = IntArray(width * height)
    private val prev = IntArray(width * height)
    private val frontier = PriorityQueue<Long>()

    fun step(effort: Float, random: Random) {
        workers.forEach { worker ->
            worker.banked += effort * rate
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
            return
        }

        // A worker with no route waits before searching again. A failed search walks
        // the whole grid twice, and a stranded worker retrying on every action was the
        // single most expensive thing in any world.
        if (worker.path == null && worker.cooldown > 0) {
            worker.cooldown--
            wander(worker, random)
            return
        }

        val path = worker.path
            ?: (plan(worker, climbing = false) ?: plan(worker, climbing = true)?.also {
                worker.climbing = true
            })?.also {
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
                    if (worker.pathPos >= path.size) endPath(worker)
                } else {
                    endPath(worker)
                }
            }
            site.breakCost(next, worker.carrying) >= 0 -> {
                val quarry = site.isQuarry(next)
                if (site.work(next, random)) {
                    if (quarry && !worker.carrying) worker.carrying = true
                    endPath(worker)
                }
            }
            else -> endPath(worker)
        }
    }

    private fun endPath(worker: Worker) {
        worker.path = null
        worker.climbing = false
    }

    private fun plan(worker: Worker, climbing: Boolean): IntArray? {
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
            if (at != start && isGoal(worker, at, x, y)) return reconstruct(start, at)

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
        if (worker.carrying) site.isOpen(index) && site.isDropOff(x, y)
        else site.isQuarry(index)

    private fun stepCost(worker: Worker, y: Int, nx: Int, ny: Int, next: Int, climbing: Boolean): Int =
        when {
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
        /** Actions per tick at effort 1. */
        const val DEFAULT_RATE = 0.6f

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
