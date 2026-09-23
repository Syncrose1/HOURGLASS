package com.hourglass.core.world

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Cell slots in a harbour world. */
object HarbourMat {
    const val SKY = 1
    const val WATER = 2
    const val WATER_DEEP = 3
    const val FOAM = 4
    const val SEABED = 5
    const val STONE = 6
    const val STONE_DARK = 7
    const val BOLLARD = 8
    const val HULL = 9
    const val HULL_DARK = 10
    const val DECK = 11
    const val MAST = 12
    const val SAIL = 13
    const val ROPE = 14
    const val CRANE = 15
    const val CRANE_DARK = 16
    const val CRATE = 17
    const val CRATE_DARK = 18
    const val BARREL = 19
    const val SACK = 20
    const val WALL = 21
    const val WALL_DARK = 22
    const val ROOF = 23
    const val INTERIOR = 24
    const val SKIN = 25
    const val SHIRT = 26
    const val TROUSERS = 27
    const val GULL = 28
    const val RAIN = 29
    const val LAMP = 30
    const val COUNT = 31
}

/**
 * A working quay, side-on.
 *
 * Ships sail in under canvas, take in their sails and tie up. The crane swings its hook
 * over the deck, hoists a load, runs it in over the quay and sets it down; dockers carry
 * the loads into the warehouse, whose shelves you can watch fill through its open end.
 * A ship rides higher as she is emptied, then casts off and makes way for the next.
 * The tide comes and goes, the water chops, gulls wheel and perch on the masts, and
 * now and then a shower blows through. And now and then a load slips the hook and goes
 * into the harbour, to lie on the bottom with the others.
 *
 * What is tied to the clock is only how hard the harbour works ([WorldPacer]).
 */
class HarbourWorld(
    override val width: Int,
    override val height: Int,
    seed: Long,
    /** Loads the warehouse is waiting for. */
    private val quota: Int = 30
) : World {

    override val cells = IntArray(width * height)

    private val generator = Random(seed)

    private val quayTop = (height * QUAY).toInt()
    private val meanSea = quayTop + 3
    private val seabed = height - 5
    private val quayEdge = (width * 0.47f).toInt()
    private val warehouseLeft = width - WAREHOUSE_WIDTH - 1
    private val warehouseTop = quayTop - WAREHOUSE_HEIGHT
    private val craneX = quayEdge + 2
    private val craneTop = quayTop - CRANE_HEIGHT
    private val pileLeft = craneX + 3
    private val mooredX = quayEdge - 1

    // --- State ------------------------------------------------------------------------

    private var tick = 0
    private var stored = 0
    private var lost = 0

    /** Surface height offsets per column: a little wave field. */
    private val wave = FloatArray(width)
    private val waveSpeed = FloatArray(width)

    private enum class ShipState { ARRIVING, MOORED, DEPARTING, AWAY }

    private inner class Ship(val length: Int, val masts: Int, cargoCount: Int) {
        var x = -length.toFloat() - 2f
        var state = ShipState.ARRIVING
        var speed = 0.25f
        val cargo = ArrayList<Int>().apply { repeat(cargoCount) { add(randomCargo()) } }
        val loaded = cargoCount
        /** How deep she sits: laden ships sit low. */
        val draft: Int get() = 1 + (cargo.size * 3 + loaded - 1) / loaded.coerceAtLeast(1)
        val left: Int get() = x.roundToInt()
        val right: Int get() = left + length - 1
    }

    private var ship: Ship? = null
    private var awayTicks = 60f

    private enum class HookState { IDLE, TO_PICK, LOWER_PICK, HOIST, TO_DROP, LOWER_DROP, RAISE }

    private var trolleyX = craneX - 4f
    private var hookY = craneTop + 3f
    private var hookState = HookState.IDLE
    private var hookLoad = -1
    private var targetX = 0
    private var targetY = 0
    private var craneBank = 0f

    /** Loads set down on the quay, waiting for dockers; index = slot. */
    private val pile = ArrayList<Int>()

    private inner class Docker(var x: Int, val pace: Float) {
        var banked = 0f
        var load = -1
        var slot = -1
        var resting = 0
        var facing = 1
    }

    private val dockers = ArrayList<Docker>()

    /** Loads on the shelves, in slot order. */
    private val shelves = ArrayList<Int>()

    /** What went into the water and where it came to rest. */
    private class Sinking(val x: Int, var y: Float, val kind: Int)
    private val sinking = ArrayList<Sinking>()
    private val wrecks = ArrayList<Pair<Int, Int>>()
    private val wreckKinds = ArrayList<Int>()

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, val mat: Int)
    private val particles = ArrayList<Particle>()

    private class Gull(var x: Float, var y: Float, var vx: Float, var perch: Int)
    private val gulls = ArrayList<Gull>()

    private var raining = 0
    private var dryTicks = 0

    // --- Objective --------------------------------------------------------------------

    override val objectiveProgress: Float
        get() = if (quota == 0) 1f else (stored.toFloat() / quota).coerceAtMost(1f)

    override val objective: String get() = "$stored of $quota loads in the warehouse"

    override val kind: WorldKind get() = WorldKind.HARBOUR

    override val focusY: Float get() = 0.8f

    val loadsStored: Int get() = stored
    val loadsLost: Int get() = lost
    var shipsServed: Int = 0
        private set

    init {
        paintStatic()
        repeat(DOCKERS) { dockers += Docker(pileLeft + 4 + it * 2, 0.75f + generator.nextFloat() * 0.45f) }
        repeat(3) {
            gulls += Gull(generator.nextFloat() * width, generator.nextFloat() * (quayTop - 30) + 6, 0.15f, -1)
        }
        dryTicks = generator.nextInt(1800, 5400)
        ship = newShip()
    }

    private fun randomCargo(): Int = when (generator.nextInt(10)) {
        in 0..5 -> HarbourMat.CRATE
        in 6..7 -> HarbourMat.BARREL
        else -> HarbourMat.SACK
    }

    private fun newShip(): Ship {
        val big = generator.nextFloat() < 0.4f
        val length = if (big) generator.nextInt(26, 32) else generator.nextInt(18, 24)
        return Ship(length, if (big) 2 else 1, if (big) generator.nextInt(12, 18) else generator.nextInt(6, 11))
    }

    /** The parts of the scene that do not move: quay, seabed, warehouse. */
    private fun paintStatic() {
        for (y in 0 until height) for (x in 0 until width) {
            cells[y * width + x] = when {
                y >= seabed -> HarbourMat.SEABED
                x >= quayEdge && y >= quayTop -> if ((x + y * 3) % 7 == 0 || (y - quayTop) % 3 == 0) HarbourMat.STONE_DARK else HarbourMat.STONE
                else -> HarbourMat.SKY
            }
        }
        // Bollards along the edge.
        for (x in intArrayOf(quayEdge + 1, quayEdge + 12)) cells[(quayTop - 1) * width + x] = HarbourMat.BOLLARD
        // The warehouse: back wall inside, a roof, the near end left open to see in.
        for (y in warehouseTop until quayTop) for (x in warehouseLeft until width) {
            val edge = x == warehouseLeft || x == width - 1
            cells[y * width + x] = when {
                edge -> HarbourMat.WALL_DARK
                y == warehouseTop -> HarbourMat.WALL
                else -> HarbourMat.INTERIOR
            }
        }
        // Door in the near wall.
        for (y in quayTop - 5 until quayTop) cells[y * width + warehouseLeft] = HarbourMat.INTERIOR
        cells[(quayTop - 7) * width + warehouseLeft - 1] = HarbourMat.LAMP
        for (row in 0..4) {
            val y = warehouseTop - 1 - row
            for (x in warehouseLeft - 1 + row..width - 1 - row) {
                if (y >= 0) cells[y * width + x] = HarbourMat.ROOF
            }
        }
        // The crane: tower on the quay, jib out over the water.
        for (y in craneTop until quayTop) {
            cells[y * width + craneX] = if (y % 3 == 0) HarbourMat.CRANE_DARK else HarbourMat.CRANE
            cells[y * width + craneX + 1] = if (y % 3 == 1) HarbourMat.CRANE_DARK else HarbourMat.CRANE
        }
        for (x in JIB_REACH_OUT..craneX + JIB_BACK) {
            val jibX = craneX - (craneX - x)
            if (jibX in 0 until width) cells[craneTop * width + jibX] = HarbourMat.CRANE
            if (jibX in 0 until width && (jibX % 3 == 0)) cells[(craneTop + 1) * width + jibX] = HarbourMat.CRANE_DARK
        }
    }

    // --- Simulation -------------------------------------------------------------------

    private val seaLevel: Int
        get() = meanSea + (sin(tick * TIDE_RATE) * TIDE).roundToInt()

    override fun step(effort: Float, random: Random) {
        tick++
        stepShip(effort, random)
        stepCrane(effort, random)
        stepDockers(effort, random)
        stepWater(random)
        stepSinking(random)
        stepGulls(random)
        stepWeather(random)
        particles.removeAll { p ->
            p.life--
            p.vy += 0.03f
            p.x += p.vx
            p.y += p.vy
            p.life <= 0 || (p.mat != HarbourMat.RAIN && p.y > seaLevel + 1) || p.y >= height
        }
    }

    private fun stepShip(effort: Float, random: Random) {
        val current = ship
        if (current == null) {
            // The next ship is on her way; a busy harbour master brings her in sooner.
            awayTicks -= effort.coerceIn(0.4f, 3f)
            if (awayTicks <= 0f) ship = newShip()
            return
        }
        when (current.state) {
            ShipState.ARRIVING -> {
                val remaining = mooredX - current.right
                // Coming in: sails set far out, then drifting the last stretch.
                current.speed = (remaining * 0.012f).coerceIn(0.03f, 0.3f)
                current.x += current.speed
                pushWave(current.right, 0.12f)
                if (remaining <= 0) {
                    current.x = (mooredX - current.length + 1).toFloat()
                    current.state = ShipState.MOORED
                }
            }
            ShipState.MOORED -> {
                if (current.cargo.isEmpty() && hookState == HookState.IDLE && hookLoad < 0) {
                    current.state = ShipState.DEPARTING
                    current.speed = 0.02f
                    shipsServed++
                }
            }
            ShipState.DEPARTING -> {
                current.speed = (current.speed + 0.002f).coerceAtMost(0.3f)
                current.x -= current.speed
                pushWave(current.left, 0.1f)
                if (current.right < -2) {
                    ship = null
                    awayTicks = random.nextInt(AWAY_MIN, AWAY_MAX).toFloat()
                }
            }
            ShipState.AWAY -> Unit
        }
    }

    private fun deckY(ship: Ship): Int = seaLevel + ship.draft - HULL_DEPTH

    /** Deck slots for cargo, left to right in pairs of columns; loads stack two high. */
    private fun cargoPosition(ship: Ship, index: Int): Pair<Int, Int> {
        val columns = ((ship.length - 8) / 2).coerceAtLeast(1)
        val column = index % columns
        val tier = index / columns
        return (ship.left + 4 + column * 2) to (deckY(ship) - 2 - tier * 2)
    }

    private fun pileSlotPosition(slot: Int): Pair<Int, Int> {
        val column = slot % PILE_COLUMNS
        val tier = slot / PILE_COLUMNS
        return (pileLeft + column * 2) to (quayTop - 2 - tier * 2)
    }

    private fun shelfPosition(slot: Int): Pair<Int, Int> {
        val columns = (WAREHOUSE_WIDTH - 3) / 2
        val column = slot % columns
        val tier = slot / columns
        // Filled from the back wall forward, bottom row first.
        return (width - 4 - column * 2) to (quayTop - 2 - tier * 2)
    }

    /**
     * The crane runs a cycle: over the deck, down, hoist, in over the quay, down, let go,
     * up. It will not bring a load in if there is nowhere on the quay to set it.
     */
    private fun stepCrane(effort: Float, random: Random) {
        craneBank += effort.coerceAtLeast(SLACK_PACE) * CRANE_RATE
        // Below a working pace, the crane stands idle between lifts rather than crawling.
        if (effort < SLACK_PACE && hookState == HookState.IDLE && random.nextFloat() < (SLACK_PACE / effort.coerceAtLeast(0.01f) - 1f) / 60f) {
            craneBank = 0f
            return
        }
        var actions = 0
        while (craneBank >= 1f && actions < 4) {
            craneBank -= 1f
            actions++
            craneAct(random)
        }
    }

    private fun craneAct(random: Random) {
        val current = ship
        when (hookState) {
            HookState.IDLE -> {
                if (current?.state == ShipState.MOORED && current.cargo.isNotEmpty() && pile.size < PILE_CAPACITY) {
                    val (x, y) = cargoPosition(current, current.cargo.size - 1)
                    targetX = x
                    targetY = y
                    hookState = HookState.TO_PICK
                } else {
                    moveHook((craneX - 4).toFloat(), craneTop + 3f)
                }
            }
            HookState.TO_PICK -> if (moveHook(targetX.toFloat(), craneTop + 3f)) hookState = HookState.LOWER_PICK
            HookState.LOWER_PICK -> {
                val ship = current ?: return run { hookState = HookState.RAISE }
                val (x, y) = cargoPosition(ship, ship.cargo.size - 1)
                targetX = x
                if (moveHook(x.toFloat(), (y - 1).toFloat())) {
                    hookLoad = ship.cargo.removeAt(ship.cargo.size - 1)
                    hookState = HookState.HOIST
                }
            }
            HookState.HOIST -> if (moveHook(hookX(), craneTop + 3f)) {
                val (x, y) = pileSlotPosition(pile.size)
                targetX = x
                targetY = y
                hookState = HookState.TO_DROP
            }
            HookState.TO_DROP -> {
                // Over open water, a load can slip the hook.
                if (hookX() < quayEdge && random.nextFloat() < SLIP_CHANCE) {
                    sinking += Sinking(hookX().toInt(), hookY + 1f, hookLoad)
                    hookLoad = -1
                    lost++
                    hookState = HookState.RAISE
                    return
                }
                if (moveHook(targetX.toFloat(), craneTop + 3f)) hookState = HookState.LOWER_DROP
            }
            HookState.LOWER_DROP -> {
                val (x, y) = pileSlotPosition(pile.size)
                if (moveHook(x.toFloat(), (y - 1).toFloat())) {
                    pile += hookLoad
                    hookLoad = -1
                    hookState = HookState.RAISE
                }
            }
            HookState.RAISE -> if (moveHook(hookX(), craneTop + 3f)) hookState = HookState.IDLE
        }
    }

    private fun hookX() = trolleyX

    /** One cell's movement of trolley or hook toward the target; true when there. */
    private fun moveHook(tx: Float, ty: Float): Boolean {
        // Raise before travelling, lower after: loads do not sweep across the deck.
        if (abs(trolleyX - tx) > 0.5f && hookY > craneTop + 3.5f) {
            hookY -= 1f
            return false
        }
        if (abs(trolleyX - tx) > 0.5f) {
            trolleyX += if (tx > trolleyX) 1f else -1f
            return false
        }
        trolleyX = tx
        if (abs(hookY - ty) > 0.5f) {
            hookY += if (ty > hookY) 1f else -1f
            return false
        }
        hookY = ty
        return true
    }

    private fun stepDockers(effort: Float, random: Random) {
        dockers.forEach { docker ->
            if (docker.resting > 0) {
                docker.resting--
                return@forEach
            }
            if (effort < SLACK_PACE && docker.load < 0) {
                val breakChance = (SLACK_PACE / effort.coerceAtLeast(0.001f) - 1f) / MEAN_REST_TICKS
                if (random.nextFloat() < breakChance) {
                    docker.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
                    return@forEach
                }
            }
            docker.banked += effort.coerceAtLeast(SLACK_PACE) * DOCKER_RATE * docker.pace
            var actions = 0
            while (docker.banked >= 1f && actions < 4) {
                docker.banked -= 1f
                actions++
                dockerAct(docker, random)
            }
        }
    }

    private fun dockerAct(docker: Docker, random: Random) {
        if (docker.load < 0) {
            // Fetch the top load off the pile, if nobody else is on it.
            if (pile.isEmpty()) {
                val home = pileLeft + PILE_COLUMNS * 2 + 1 + dockers.indexOf(docker) * 2
                if (docker.x != home) walk(docker, home) else if (random.nextFloat() < 0.01f) {
                    docker.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
                }
                return
            }
            val (px, _) = pileSlotPosition(pile.size - 1)
            if (abs(docker.x - px) > 1) {
                walk(docker, px + 2)
                return
            }
            docker.load = pile.removeAt(pile.size - 1)
            docker.slot = stored + dockers.count { it.load >= 0 && it !== docker }
            return
        }
        val (sx, _) = shelfPosition(docker.slot.coerceAtMost(SHELF_CAPACITY - 1))
        if (docker.x != sx - 1) {
            walk(docker, sx - 1)
            return
        }
        shelves += docker.load
        stored++
        docker.load = -1
        if (random.nextFloat() < REST_AFTER_LOAD) docker.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
    }

    private fun walk(docker: Docker, x: Int) {
        val target = x.coerceIn(quayEdge + 1, width - 2)
        if (docker.x < target) {
            docker.x++
            docker.facing = 1
        } else if (docker.x > target) {
            docker.x--
            docker.facing = -1
        }
    }

    /** A simple wave field on the surface: chop from the wind, wakes from ships. */
    private fun stepWater(random: Random) {
        if (random.nextFloat() < WIND_CHANCE * (if (raining > 0) 3f else 1f)) {
            pushWave(random.nextInt(quayEdge), (random.nextFloat() - 0.5f) * 0.6f)
        }
        for (x in 0 until quayEdge) {
            val left = wave[(x - 1).coerceAtLeast(0)]
            val right = wave[(x + 1).coerceAtMost(quayEdge - 1)]
            waveSpeed[x] += (left + right - 2 * wave[x]) * 0.18f - wave[x] * 0.01f
            waveSpeed[x] *= 0.985f
        }
        for (x in 0 until quayEdge) wave[x] += waveSpeed[x]
    }

    private fun pushWave(x: Int, amount: Float) {
        if (x in 0 until quayEdge) waveSpeed[x] += amount
    }

    private fun surfaceAt(x: Int): Int = seaLevel + wave[x.coerceIn(0, width - 1)].roundToInt().coerceIn(-1, 1)

    private fun stepSinking(random: Random) {
        val settled = ArrayList<Sinking>()
        sinking.forEach { load ->
            val before = load.y
            load.y += if (load.y < seaLevel) 0.35f else 0.08f
            if (before < seaLevel && load.y >= seaLevel) {
                pushWave(load.x, 0.8f)
                repeat(6) {
                    particles += Particle(
                        load.x.toFloat(), seaLevel - 1f, (random.nextFloat() - 0.5f) * 0.6f,
                        -0.4f - random.nextFloat() * 0.4f, 25, HarbourMat.FOAM
                    )
                }
            }
            val floor = seabed - 2 - wrecks.count { abs(it.first - load.x) < 2 } * 2
            if (load.y >= floor) settled += load
        }
        settled.forEach { load ->
            val floor = seabed - 2 - wrecks.count { abs(it.first - load.x) < 2 } * 2
            wrecks += load.x to floor
            wreckKinds += load.kind
        }
        sinking.removeAll(settled)
    }

    private fun stepGulls(random: Random) {
        gulls.forEach { gull ->
            if (gull.perch >= 0) {
                if (random.nextFloat() < 0.004f || ship == null || ship?.state != ShipState.MOORED) gull.perch = -1
                return@forEach
            }
            gull.x += gull.vx
            gull.y += (random.nextFloat() - 0.5f) * 0.3f + sin((tick + gull.x) * 0.05f) * 0.05f
            gull.y = gull.y.coerceIn(3f, quayTop - 12f)
            if (gull.x < -2 || gull.x > width + 2) gull.vx = -gull.vx
            if (random.nextFloat() < 0.003f) gull.vx = -gull.vx
            val moored = ship
            if (moored?.state == ShipState.MOORED && random.nextFloat() < 0.002f) gull.perch = 0
        }
    }

    private fun stepWeather(random: Random) {
        if (raining > 0) {
            raining--
            repeat(2) {
                particles += Particle(random.nextFloat() * (width + 10) - 10, 0f, 0.35f, 1.2f, 80, HarbourMat.RAIN)
            }
            if (raining == 0) dryTicks = random.nextInt(3600, 9000)
        } else {
            dryTicks--
            if (dryTicks <= 0) raining = random.nextInt(600, 1500)
        }
    }

    // --- Rendering ------------------------------------------------------------------

    override fun renderInto(buffer: IntArray) {
        cells.copyInto(buffer)
        fun plot(x: Int, y: Int, mat: Int) {
            if (x in 0 until width && y in 0 until height) buffer[y * width + x] = mat
        }
        fun load(x: Int, y: Int, kind: Int) {
            when (kind) {
                HarbourMat.CRATE -> {
                    plot(x, y, HarbourMat.CRATE); plot(x + 1, y, HarbourMat.CRATE_DARK)
                    plot(x, y - 1, HarbourMat.CRATE_DARK); plot(x + 1, y - 1, HarbourMat.CRATE)
                }
                HarbourMat.BARREL -> {
                    plot(x, y, HarbourMat.BARREL); plot(x + 1, y, HarbourMat.BARREL)
                    plot(x, y - 1, HarbourMat.HULL_DARK); plot(x + 1, y - 1, HarbourMat.BARREL)
                }
                else -> {
                    plot(x, y, HarbourMat.SACK); plot(x + 1, y, HarbourMat.SACK)
                    plot(x, y - 1, HarbourMat.SACK)
                }
            }
        }

        // Water, with its chop and the tide.
        for (x in 0 until quayEdge) {
            val surface = surfaceAt(x)
            for (y in surface until seabed) {
                plot(x, y, if (y == surface) HarbourMat.FOAM.takeIf { wave[x] > 0.6f } ?: HarbourMat.WATER
                else if (y > surface + 6) HarbourMat.WATER_DEEP else HarbourMat.WATER)
            }
        }
        // The quay wall runs down into the water.
        for (y in quayTop until seabed) plot(quayEdge, y, HarbourMat.STONE_DARK)

        wrecks.forEachIndexed { i, (x, y) -> load(x, y, wreckKinds[i]) }
        sinking.forEach { load(it.x, it.y.toInt(), it.kind) }

        ship?.let { drawShip(it, ::plot, ::load) }

        // Shelves, pile, crane rope and hook.
        shelves.forEachIndexed { slot, kind ->
            if (slot < SHELF_CAPACITY) {
                val (x, y) = shelfPosition(slot)
                load(x, y, kind)
            }
        }
        pile.forEachIndexed { slot, kind ->
            val (x, y) = pileSlotPosition(slot)
            load(x, y, kind)
        }
        val hx = trolleyX.roundToInt()
        plot(hx, craneTop + 1, HarbourMat.CRANE_DARK)
        for (y in craneTop + 2..hookY.toInt()) plot(hx, y, HarbourMat.ROPE)
        if (hookLoad >= 0) load(hx, hookY.toInt() + 2, hookLoad)

        dockers.forEach { docker ->
            val sitting = docker.resting > 0 && docker.load < 0
            val top = if (sitting) quayTop - 2 else quayTop - 3
            plot(docker.x, top, HarbourMat.SKIN)
            plot(docker.x, top + 1, HarbourMat.SHIRT)
            if (!sitting) plot(docker.x, top + 2, HarbourMat.TROUSERS)
            if (docker.load >= 0) load(docker.x, top - 1, docker.load)
        }

        gulls.forEach { gull ->
            val flap = ((tick / 8) + gull.x.toInt()) % 2 == 0
            val gx = gull.x.toInt()
            val gy = gull.y.toInt()
            val moored = ship
            if (gull.perch >= 0 && moored != null) {
                val mastX = moored.left + moored.length / 2
                plot(mastX, deckY(moored) - MAST_HEIGHT - 1, HarbourMat.GULL)
            } else {
                plot(gx, gy, HarbourMat.GULL)
                plot(gx - 1, if (flap) gy - 1 else gy, HarbourMat.GULL)
                plot(gx + 1, if (flap) gy - 1 else gy, HarbourMat.GULL)
            }
        }

        particles.forEach { plot(it.x.toInt(), it.y.toInt(), it.mat) }
    }

    private inline fun drawShip(ship: Ship, plot: (Int, Int, Int) -> Unit, load: (Int, Int, Int) -> Unit) {
        val deck = deckY(ship)
        // Hull: rounded at both ends, darker below the waterline.
        for (row in 0 until HULL_DEPTH) {
            val inset = when (row) {
                HULL_DEPTH - 1 -> 3
                HULL_DEPTH - 2 -> 1
                else -> 0
            }
            for (x in ship.left + inset..ship.right - inset) {
                val y = deck + row
                val mat = when {
                    row == 0 -> HarbourMat.DECK
                    y >= seaLevel -> HarbourMat.HULL_DARK
                    else -> HarbourMat.HULL
                }
                plot(x, y, mat)
            }
        }
        // Raised stern and bow rails.
        plot(ship.left, deck - 1, HarbourMat.HULL)
        plot(ship.right, deck - 1, HarbourMat.HULL)
        plot(ship.right - 1, deck - 1, HarbourMat.HULL)

        val sailing = ship.state != ShipState.MOORED
        val mastXs = if (ship.masts == 1) listOf(ship.left + ship.length / 2)
        else listOf(ship.left + ship.length / 3, ship.left + ship.length * 2 / 3)
        mastXs.forEach { mx ->
            val height = if (mx == mastXs.first()) MAST_HEIGHT else MAST_HEIGHT - 3
            for (y in deck - height until deck) plot(mx, y, HarbourMat.MAST)
            if (sailing) {
                // Sails set: a full square sail on each mast.
                val half = (ship.length / (ship.masts * 3)).coerceAtLeast(2)
                for (y in deck - height + 1..deck - 4) for (x in mx - half..mx + half) {
                    if (x != mx || y % 3 == 0) plot(x, y, HarbourMat.SAIL)
                }
            } else {
                // Sails furled along the yard.
                val half = (ship.length / (ship.masts * 3)).coerceAtLeast(2)
                for (x in mx - half..mx + half) plot(x, deck - height + 1, HarbourMat.SAIL)
            }
        }
        ship.cargo.forEachIndexed { index, kind ->
            val (x, y) = cargoPosition(ship, index)
            load(x, y, kind)
        }
        // Mooring lines to the bollard while tied up.
        if (ship.state == ShipState.MOORED) {
            plot(ship.right + 1, deck - 1, HarbourMat.ROPE)
        }
    }

    companion object {
        /**
         * Loads to store for a timer this long. Measured: the harbour moves about two
         * loads a minute at an ordinary pace, ships' comings and goings included.
         */
        fun quotaFor(durationMinutes: Float): Int =
            (durationMinutes * LOADS_PER_MINUTE).roundToInt().coerceIn(6, SHELF_CAPACITY)

        private const val LOADS_PER_MINUTE = 2f

        private const val QUAY = 0.74f
        private const val WAREHOUSE_WIDTH = 18
        private const val WAREHOUSE_HEIGHT = 22
        private const val CRANE_HEIGHT = 26
        private const val JIB_BACK = 6
        private const val JIB_REACH_OUT = 6
        private const val HULL_DEPTH = 6
        private const val MAST_HEIGHT = 22
        private const val PILE_COLUMNS = 4
        private const val PILE_CAPACITY = 12
        private const val SHELF_CAPACITY = 7 * 10
        private const val DOCKERS = 3

        private const val TIDE = 1.4f
        private const val TIDE_RATE = 0.0009f
        private const val WIND_CHANCE = 0.08f

        private const val CRANE_RATE = 0.16f
        private const val DOCKER_RATE = 0.12f
        private const val SLACK_PACE = 0.5f
        private const val SLIP_CHANCE = 0.004f
        private const val REST_MIN_TICKS = 45
        private const val REST_MAX_TICKS = 150
        private const val MEAN_REST_TICKS = (REST_MIN_TICKS + REST_MAX_TICKS) / 2f
        private const val REST_AFTER_LOAD = 0.2f
        private const val AWAY_MIN = 400
        private const val AWAY_MAX = 1100
    }
}
