package com.hourglass.core.world

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Cell slots in a siege world. */
object SiegeMat {
    const val SKY = 1
    const val GRASS = 2
    const val DIRT = 3
    const val DIRT_DARK = 4
    const val STONE = 5
    const val STONE_DARK = 6
    const val BLOCK = 7
    const val SCAFFOLD = 8
    const val ROCK = 9
    const val ROCK_DARK = 10
    const val RUBBLE = 11
    const val TIMBER = 12
    const val TIMBER_DARK = 13
    const val WEIGHT = 14
    const val ROPE = 15
    const val SHOT = 16
    const val SKIN = 17
    const val OURS = 18
    const val THEIRS = 19
    const val TROUSERS = 20
    const val BANNER = 21
    const val ARROW = 22
    const val DUST = 23
    const val COUNT = 24
}

/**
 * A castle going up under fire, side-on.
 *
 * Quarrymen cut blocks from the rock face at one end — the face recedes as they work —
 * and masons carry them to the walls and set them course by course, always on
 * something solid, scaffolding marking where the plan still runs. Across the field
 * an enemy crew winch down their trebuchet, load it, and let it go: the arm swings
 * over, the stone flies on a real arc, and where it lands blocks are knocked out of
 * the wall to tumble down as rubble. The masons use the rubble first, and patch the
 * holes before they build higher. Once a tower stands, archers on it shoot back at
 * the trebuchet crew and make them duck; a finished tower flies your banner.
 *
 * What is tied to the clock is only how hard your people work ([WorldPacer]); the
 * trebuchet keeps its own time.
 */
class SiegeWorld(
    override val width: Int,
    override val height: Int,
    seed: Long,
    /** Blocks of castle the timer calls for. */
    private val quota: Int = 300
) : World {

    override val cells = IntArray(width * height)

    private val generator = Random(seed)
    private val groundY = (height * GROUND).toInt()
    private val footY = groundY - 1

    /** Which cells the plan calls for, and which are built. */
    private val plan = BooleanArray(width * height)
    private val built = BooleanArray(width * height)
    private val claimed = BooleanArray(width * height)
    private var planned = 0
    private var standing = 0

    private val rock = BooleanArray(width * height)
    private var quarryWork = 0f
    private var quarriedFromCell = 0
    private var stockpile = 0
    private val rubble = ArrayList<Int>() // x positions of rubble lying at the foot of the walls

    private data class Tower(val left: Int, val right: Int, val top: Int)
    private val towers = ArrayList<Tower>()

    // --- People -----------------------------------------------------------------------

    private enum class Job { NONE, FETCH, CARRY, CLIMB, DESCEND, QUARRY }

    private inner class Worker(var x: Int, val pace: Float, val quarryman: Boolean) {
        var y = footY
        var banked = 0f
        var job = Job.NONE
        var targetX = 0
        var targetY = 0
        var carrying = false
        var resting = 0
        var swing = false
    }

    private val workers = ArrayList<Worker>()

    // --- Trebuchet --------------------------------------------------------------------

    private val pivotX = TREBUCHET_X
    private val pivotY = groundY - PIVOT_HEIGHT
    private enum class Throw { WINDING, LOADED, SWINGING }
    private var throwState = Throw.WINDING
    /** Arm angle, radians: 0 points right, PI/2 up. */
    private var arm = ARM_FIRED
    private var spin = 0f
    private var waitTicks = 0
    private var ducking = 0

    private class Shot(var x: Float, var y: Float, var vx: Float, var vy: Float, val ours: Boolean)
    private val shots = ArrayList<Shot>()

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, val mat: Int)
    private val particles = ArrayList<Particle>()

    private var tick = 0
    var hits: Int = 0
        private set

    // --- Objective --------------------------------------------------------------------

    override val objectiveProgress: Float
        get() = if (order == 0) 1f else (standing.toFloat() / order).coerceAtMost(1f)

    private val order: Int get() = minOf(quota, planned)

    override val objective: String get() = "$standing of $order blocks standing"

    override val kind: WorldKind get() = WorldKind.SIEGE

    override val focusY: Float get() = 0.8f

    val blocksStanding: Int get() = standing

    init {
        paintGround()
        drawPlan()
        cutQuarry()
        repeat(MASONS) { workers += Worker(QUARRY_LEFT - 3 - it, 0.75f + generator.nextFloat() * 0.45f, quarryman = false) }
        repeat(QUARRYMEN) { workers += Worker(QUARRY_LEFT - 1, 0.8f + generator.nextFloat() * 0.4f, quarryman = true) }
        stockpile = 6
        waitTicks = generator.nextInt(200, 600)
    }

    private fun paintGround() {
        for (y in 0 until height) for (x in 0 until width) {
            cells[y * width + x] = when {
                y < groundY -> SiegeMat.SKY
                y == groundY -> SiegeMat.GRASS
                generator.nextFloat() < 0.25f -> SiegeMat.DIRT_DARK
                else -> SiegeMat.DIRT
            }
        }
    }

    /** Two towers, a curtain wall with a gate between them, all with battlements. */
    private fun drawPlan() {
        val left = CASTLE_LEFT + generator.nextInt(0, 3)
        val towerWidth = 7
        val wallHeight = 20 + generator.nextInt(0, 4)
        val leftTowerHeight = 32 + generator.nextInt(0, 5)
        val rightTowerHeight = 34 + generator.nextInt(0, 5)
        val right = QUARRY_LEFT - 5
        fun block(x0: Int, x1: Int, top: Int) {
            for (x in x0..x1) for (y in (groundY - top) until groundY) mark(x, y)
            // Battlements: merlons every other cell along the top.
            for (x in x0..x1) if ((x - x0) % 2 == 0) mark(x, groundY - top - 1)
        }
        block(left, left + towerWidth - 1, leftTowerHeight)
        block(right - towerWidth + 1, right, rightTowerHeight)
        block(left + towerWidth, right - towerWidth, wallHeight)
        towers += Tower(left, left + towerWidth - 1, groundY - leftTowerHeight - 1)
        towers += Tower(right - towerWidth + 1, right, groundY - rightTowerHeight - 1)
        // The gate: an arch left open through the curtain wall.
        val gateCentre = (left + right) / 2
        for (x in gateCentre - 2..gateCentre + 2) for (y in groundY - 8 until groundY) unmark(x, y)
        for (x in gateCentre - 1..gateCentre + 1) unmark(x, groundY - 9)
        // Arrow slits in the towers.
        towers.forEach { tower ->
            val mx = (tower.left + tower.right) / 2
            for (y in tower.top + 6 until groundY - 10 step 7) {
                unmark(mx, y)
                unmark(mx, y + 1)
            }
        }
        planned = plan.count { it }
    }

    private fun mark(x: Int, y: Int) {
        if (x in 0 until width && y in 0 until height) plan[y * width + x] = true
    }

    private fun unmark(x: Int, y: Int) {
        if (x in 0 until width && y in 0 until height) plan[y * width + x] = false
    }

    private fun cutQuarry() {
        for (x in QUARRY_LEFT until width) {
            val top = groundY - QUARRY_HEIGHT + (x - QUARRY_LEFT) / 2
            for (y in top.coerceAtLeast(0) until groundY) rock[y * width + x] = true
        }
    }

    // --- Simulation -------------------------------------------------------------------

    override fun step(effort: Float, random: Random) {
        tick++
        workers.forEach { worker ->
            if (worker.resting > 0) {
                worker.resting--
                return@forEach
            }
            if (effort < SLACK_PACE && !worker.carrying && worker.y == footY) {
                val breakChance = (SLACK_PACE / effort.coerceAtLeast(0.001f) - 1f) / MEAN_REST_TICKS
                if (random.nextFloat() < breakChance) {
                    worker.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
                    return@forEach
                }
            }
            worker.banked += effort.coerceAtLeast(SLACK_PACE) * RATE * worker.pace
            var actions = 0
            while (worker.banked >= 1f && actions < 4) {
                worker.banked -= 1f
                actions++
                if (worker.quarryman) quarry(worker, random) else mason(worker, random)
            }
        }
        trebuchet(random)
        archers(random)
        moveShots(random)
        particles.removeAll { p ->
            p.life--
            p.vy += 0.04f
            p.x += p.vx
            p.y += p.vy
            p.life <= 0 || p.y >= groundY
        }
    }

    private fun quarry(worker: Worker, random: Random) {
        // Work the nearest face of the rock.
        var face = -1
        for (x in QUARRY_LEFT until width) {
            if ((0 until groundY).any { rock[it * width + x] }) {
                face = x
                break
            }
        }
        if (face < 0) {
            // Worked out: the quarrymen dig on at ground level, out of the frame.
            face = width - 1
        }
        val stand = face - 1
        if (worker.x != stand) {
            worker.x += if (worker.x < stand) 1 else -1
            return
        }
        if (stockpile >= STOCKPILE_CAPACITY) {
            if (random.nextFloat() < 0.02f) worker.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
            return
        }
        worker.swing = !worker.swing
        quarryWork += 1f
        if (random.nextFloat() < 0.4f) {
            particles += Particle(face.toFloat(), footY - 2f, -0.3f - random.nextFloat() * 0.3f, -0.3f, 20, SiegeMat.DUST)
        }
        if (quarryWork >= QUARRY_WORK) {
            quarryWork = 0f
            stockpile++
            quarriedFromCell++
            if (quarriedFromCell >= BLOCKS_PER_ROCK) {
                quarriedFromCell = 0
                // The face recedes: take the topmost rock of the nearest column.
                for (y in 0 until groundY) {
                    val index = y * width + face
                    if (rock[index]) {
                        rock[index] = false
                        break
                    }
                }
            }
        }
    }

    /**
     * Fetch a block — rubble at the wall foot first, else from the stockpile by the
     * quarry — carry it to the lowest place the wall can take one, climb, set it, and
     * come back down.
     */
    private fun mason(worker: Worker, random: Random) {
        when (worker.job) {
            Job.NONE -> {
                val near = rubble.minByOrNull { abs(it - worker.x) }
                if (near != null) {
                    worker.targetX = near
                    worker.job = Job.FETCH
                } else if (stockpile > 0) {
                    worker.targetX = STOCKPILE_X
                    worker.job = Job.FETCH
                } else if (worker.x != STOCKPILE_X - 2) {
                    worker.x += if (worker.x < STOCKPILE_X - 2) 1 else -1
                }
            }
            Job.FETCH -> {
                if (worker.x != worker.targetX) {
                    worker.x += if (worker.x < worker.targetX) 1 else -1
                    return
                }
                val piece = rubble.indexOfFirst { it == worker.x }
                when {
                    piece >= 0 -> rubble.removeAt(piece)
                    worker.x == STOCKPILE_X && stockpile > 0 -> stockpile--
                    else -> {
                        worker.job = Job.NONE
                        return
                    }
                }
                worker.carrying = true
                val spot = chooseSpot(worker, random)
                if (spot < 0) {
                    // Nowhere to put it yet: set it back down.
                    stockpile++
                    worker.carrying = false
                    worker.job = Job.NONE
                    worker.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
                    return
                }
                claimed[spot] = true
                worker.targetX = spot % width
                worker.targetY = spot / width
                worker.job = Job.CARRY
            }
            Job.CARRY -> {
                if (worker.x != worker.targetX) {
                    worker.x += if (worker.x < worker.targetX) 1 else -1
                    return
                }
                worker.job = Job.CLIMB
            }
            Job.CLIMB -> {
                if (worker.y > worker.targetY) {
                    worker.y--
                    return
                }
                val index = worker.targetY * width + worker.targetX
                claimed[index] = false
                if (plan[index] && !built[index] && supported(worker.targetX, worker.targetY)) {
                    built[index] = true
                    standing++
                    worker.swing = !worker.swing
                } else {
                    // Someone beat us to it, or the course below was shot away: take
                    // the block back down.
                    stockpile++
                }
                worker.carrying = false
                worker.job = Job.DESCEND
            }
            Job.DESCEND -> {
                if (worker.y < footY) {
                    worker.y++
                    return
                }
                worker.job = Job.NONE
                if (random.nextFloat() < REST_AFTER_BLOCK) worker.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
            }
            Job.QUARRY -> worker.job = Job.NONE
        }
    }

    private fun supported(x: Int, y: Int): Boolean =
        y + 1 >= groundY || built[(y + 1) * width + x]

    /** The lowest open place in the plan with something solid under it, nearest first. */
    private fun chooseSpot(worker: Worker, random: Random): Int {
        var best = -1
        var bestScore = Int.MAX_VALUE
        for (y in groundY - 1 downTo 0) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!plan[index] || built[index] || claimed[index] || !supported(x, y)) continue
                val score = (groundY - y) * 40 + abs(x - worker.x) + random.nextInt(0, 6)
                if (score < bestScore) {
                    bestScore = score
                    best = index
                }
            }
            // Courses go up in order: once this row has a candidate, go no higher.
            if (best >= 0 && bestScore < (groundY - y + 1) * 40) break
        }
        return best
    }

    /**
     * The trebuchet: winch the arm down, load, let the counterweight drop, and release
     * the stone near the top of the swing. Its crew keep their own time.
     */
    private fun trebuchet(random: Random) {
        if (ducking > 0) {
            ducking--
            return
        }
        when (throwState) {
            Throw.WINDING -> {
                if (waitTicks > 0) {
                    waitTicks--
                    return
                }
                arm += WIND_RATE
                if (arm >= ARM_COCKED) {
                    arm = ARM_COCKED
                    throwState = Throw.LOADED
                    waitTicks = random.nextInt(60, 180)
                }
            }
            Throw.LOADED -> {
                if (waitTicks > 0) {
                    waitTicks--
                    return
                }
                throwState = Throw.SWINGING
                spin = 0f
            }
            Throw.SWINGING -> {
                spin += SWING_ACCEL
                val before = arm
                arm -= spin
                if (before > RELEASE_ANGLE && arm <= RELEASE_ANGLE) release(random)
                if (arm <= ARM_FIRED) {
                    arm = ARM_FIRED
                    throwState = Throw.WINDING
                    waitTicks = random.nextInt(RELOAD_MIN, RELOAD_MAX)
                }
            }
        }
    }

    private fun armTip(): Pair<Float, Float> =
        (pivotX + cos(arm) * ARM_LONG) to (pivotY - sin(arm) * ARM_LONG)

    private fun release(random: Random) {
        val (x, y) = armTip()
        // Aimed at the castle, never quite true: some fall short, some go long.
        val targetX = (towers.first().left + random.nextInt(0, QUARRY_LEFT - towers.first().left)).toFloat()
        val flight = FLIGHT_TICKS + random.nextInt(-12, 12)
        val vx = (targetX - x) / flight
        val targetY = groundY - random.nextInt(4, 24)
        val vy = (targetY - y - 0.5f * GRAVITY * flight * flight) / flight
        shots += Shot(x, y, vx * (0.9f + random.nextFloat() * 0.2f), vy, ours = false)
    }

    private fun archers(random: Random) {
        // A finished tower top is a firing platform.
        towers.forEach { tower ->
            val top = (tower.left..tower.right).count { built[(tower.top + 1) * width + it] }
            if (top < tower.right - tower.left) return@forEach
            if (random.nextFloat() < ARCHER_CHANCE) {
                val x = tower.left.toFloat()
                val y = tower.top.toFloat()
                val flight = 45f + random.nextInt(0, 20)
                val tx = pivotX + random.nextInt(-6, 8)
                val vx = (tx - x) / flight
                val vy = (footY - y - 0.5f * GRAVITY * flight * flight) / flight
                shots += Shot(x, y, vx, vy, ours = true)
            }
        }
    }

    private fun moveShots(random: Random) {
        val iterator = shots.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            shot.vy += GRAVITY
            shot.x += shot.vx
            shot.y += shot.vy
            val cx = shot.x.toInt()
            val cy = shot.y.toInt()
            if (cx !in 0 until width || cy >= height) {
                iterator.remove()
                continue
            }
            if (cy < 0) continue
            if (shot.ours) {
                if (cy >= footY) {
                    // Close enough to the crew and they take cover for a while.
                    if (abs(cx - pivotX) < 6) ducking = random.nextInt(60, 160)
                    iterator.remove()
                }
                continue
            }
            val index = cy * width + cx
            if (built[index] || cy >= groundY) {
                impact(cx, cy, random)
                iterator.remove()
            }
        }
    }

    /** A stone strikes: blocks around it are knocked loose and fall as rubble. */
    private fun impact(x: Int, y: Int, random: Random) {
        hits++
        repeat(8) {
            particles += Particle(
                x.toFloat(), y.toFloat(), (random.nextFloat() - 0.5f) * 0.8f,
                -0.2f - random.nextFloat() * 0.5f, 30, SiegeMat.DUST
            )
        }
        if (y >= groundY) return
        var knocked = 0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until width || ny !in 0 until groundY) continue
            val index = ny * width + nx
            if (!built[index] || random.nextFloat() > KNOCK_CHANCE || knocked >= MAX_KNOCKED) continue
            // Only blocks with nothing resting on them come away cleanly.
            if (ny > 0 && built[(ny - 1) * width + nx] && random.nextFloat() < 0.6f) continue
            built[index] = false
            standing--
            knocked++
            particles += Particle(nx.toFloat(), ny.toFloat(), (random.nextFloat() - 0.8f) * 0.5f, -0.2f, 120, SiegeMat.RUBBLE)
            rubble += (nx - random.nextInt(1, 4)).coerceIn(CASTLE_LEFT - 6, QUARRY_LEFT - 2)
        }
    }

    // --- Rendering ------------------------------------------------------------------

    override fun renderInto(buffer: IntArray) {
        cells.copyInto(buffer)
        fun plot(x: Int, y: Int, mat: Int) {
            if (x in 0 until width && y in 0 until height) buffer[y * width + x] = mat
        }

        // Quarry face.
        for (i in rock.indices) if (rock[i]) {
            buffer[i] = if ((i % width + i / width) % 5 == 0) SiegeMat.ROCK_DARK else SiegeMat.ROCK
        }

        // The castle, and scaffolding where the plan runs a little above the work.
        val course = (groundY - 1 downTo 0).firstOrNull { y ->
            (0 until width).any { x -> plan[y * width + x] && !built[y * width + x] }
        } ?: 0
        for (y in 0 until groundY) for (x in 0 until width) {
            val index = y * width + x
            if (built[index]) {
                val brick = (x + (y % 2) * 2) % 4 == 0
                buffer[index] = if (brick) SiegeMat.STONE_DARK else SiegeMat.STONE
            } else if (plan[index] && y >= course - SCAFFOLD_AHEAD && (x % 5 == 0 || y % 4 == 0)) {
                buffer[index] = SiegeMat.SCAFFOLD
            }
        }

        // Banners on finished towers.
        towers.forEach { tower ->
            val done = (tower.left..tower.right).all { x ->
                (tower.top until groundY).all { y -> !plan[y * width + x] || built[y * width + x] }
            }
            if (done) {
                val mx = (tower.left + tower.right) / 2
                for (y in tower.top - 5 until tower.top) plot(mx, y, SiegeMat.TIMBER_DARK)
                val wave = (tick / 10) % 2
                for (dx in 1..3) plot(mx + dx, tower.top - 5 + if (dx == 3) wave else 0, SiegeMat.BANNER)
                plot(mx + 1, tower.top - 4, SiegeMat.BANNER)
                plot(mx + 2, tower.top - 4, SiegeMat.BANNER)
            }
        }

        // Stockpile and rubble.
        for (i in 0 until stockpile.coerceAtMost(STOCKPILE_CAPACITY)) {
            plot(STOCKPILE_X - (i % 2), footY - i / 2, SiegeMat.BLOCK)
        }
        rubble.forEach { plot(it, footY, SiegeMat.RUBBLE) }

        drawTrebuchet(::plot)

        workers.forEach { worker ->
            val sitting = worker.resting > 0 && worker.y == footY
            val top = if (sitting) worker.y - 1 else worker.y - 2
            plot(worker.x, top, SiegeMat.SKIN)
            plot(worker.x, top + 1, SiegeMat.OURS)
            if (!sitting) plot(worker.x, worker.y, SiegeMat.TROUSERS)
            if (worker.carrying) plot(worker.x, top - 1, SiegeMat.BLOCK)
            if (worker.quarryman && worker.swing) plot(worker.x + 1, top, SiegeMat.TIMBER_DARK)
        }

        shots.forEach { shot ->
            plot(shot.x.toInt(), shot.y.toInt(), if (shot.ours) SiegeMat.ARROW else SiegeMat.SHOT)
        }
        particles.forEach { plot(it.x.toInt(), it.y.toInt(), it.mat) }
    }

    private inline fun drawTrebuchet(plot: (Int, Int, Int) -> Unit) {
        // A-frame.
        for (i in 0..PIVOT_HEIGHT) {
            plot(pivotX - 3 + i * 3 / PIVOT_HEIGHT, groundY - i, SiegeMat.TIMBER_DARK)
            plot(pivotX + 3 - i * 3 / PIVOT_HEIGHT, groundY - i, SiegeMat.TIMBER_DARK)
        }
        for (x in pivotX - 4..pivotX + 4) plot(x, footY, SiegeMat.TIMBER)
        // Arm, long end and counterweight end.
        val c = cos(arm)
        val s = sin(arm)
        for (i in -ARM_SHORT..ARM_LONG) {
            plot((pivotX + c * i).roundToInt(), (pivotY - s * i).roundToInt(), SiegeMat.TIMBER)
        }
        val wx = (pivotX - c * ARM_SHORT).roundToInt()
        val wy = (pivotY + s * ARM_SHORT).roundToInt()
        for (dy in 0..2) for (dx in -1..1) plot(wx + dx, wy + dy, SiegeMat.WEIGHT)
        // The stone in the sling, while loaded.
        if (throwState == Throw.LOADED || (throwState == Throw.SWINGING && arm > RELEASE_ANGLE)) {
            val (tx, ty) = armTip()
            plot(tx.roundToInt(), ty.roundToInt() + 1, SiegeMat.SHOT)
        }
        // Crew: two figures at the winch, crouched while arrows fall.
        for (k in 0..1) {
            val x = pivotX + 4 + k * 2
            val crouch = if (ducking > 0) 1 else 0
            plot(x, footY - 2 + crouch, SiegeMat.SKIN)
            plot(x, footY - 1 + crouch, SiegeMat.THEIRS)
            if (crouch == 0) plot(x, footY, SiegeMat.TROUSERS)
        }
    }

    companion object {
        /**
         * Blocks for a timer this long. Measured: the masons set about twenty-five
         * blocks a minute at an ordinary pace, less what the trebuchet knocks down.
         */
        fun quotaFor(durationMinutes: Float): Int =
            (durationMinutes * BLOCKS_PER_MINUTE).roundToInt().coerceIn(60, 900)

        private const val BLOCKS_PER_MINUTE = 22f

        private const val GROUND = 0.84f
        private const val CASTLE_LEFT = 24
        private const val QUARRY_LEFT = 64
        private const val QUARRY_HEIGHT = 22
        private const val STOCKPILE_X = 61
        private const val STOCKPILE_CAPACITY = 20
        private const val SCAFFOLD_AHEAD = 4
        private const val MASONS = 5
        private const val QUARRYMEN = 2

        private const val RATE = 0.2f
        private const val SLACK_PACE = 0.5f
        private const val REST_MIN_TICKS = 45
        private const val REST_MAX_TICKS = 150
        private const val MEAN_REST_TICKS = (REST_MIN_TICKS + REST_MAX_TICKS) / 2f
        private const val REST_AFTER_BLOCK = 0.12f
        private const val QUARRY_WORK = 5f
        private const val BLOCKS_PER_ROCK = 4

        private const val TREBUCHET_X = 7
        private const val PIVOT_HEIGHT = 9
        private const val ARM_LONG = 11
        private const val ARM_SHORT = 3
        private val ARM_COCKED = (PI * 1.15).toFloat()   // long end down behind
        private val ARM_FIRED = (PI * 0.3).toFloat()     // swung over, forward
        private val RELEASE_ANGLE = (PI * 0.62).toFloat()
        private const val WIND_RATE = 0.006f
        private const val SWING_ACCEL = 0.006f
        private const val RELOAD_MIN = 300
        private const val RELOAD_MAX = 700
        private const val FLIGHT_TICKS = 70
        private const val GRAVITY = 0.05f
        private const val KNOCK_CHANCE = 0.7f
        private const val MAX_KNOCKED = 3

        private const val ARCHER_CHANCE = 0.006f
    }
}
