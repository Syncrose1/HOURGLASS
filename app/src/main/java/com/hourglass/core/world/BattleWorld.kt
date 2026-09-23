package com.hourglass.core.world

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Cell slots in a battle world. */
object BattleMat {
    const val GRASS = 1
    const val GRASS_DARK = 2
    const val GRASS_LIGHT = 3
    const val FOREST = 4
    const val FOREST_DARK = 5
    const val ROCK = 6
    const val WATER = 7
    const val WATER_LIGHT = 8
    const val FORD = 9
    const val BRIDGE = 10
    const val HILL = 11
    const val HILL_DARK = 12
    const val CRATER = 13
    const val SCORCH = 14
    const val TENT_OURS = 15
    const val TENT_THEIRS = 16
    const val POLE = 17
    const val FLAG_OURS = 18
    const val FLAG_THEIRS = 19
    const val FLAG_NEUTRAL = 20
    const val OURS = 21
    const val THEIRS = 22
    const val OURS_ROUTED = 23
    const val THEIRS_ROUTED = 24
    const val TRACER = 25
    const val SHELL = 26
    const val BLAST = 27
    const val SHADOW = 28
    const val COUNT = 29

    fun isForest(cell: Int) = cell == FOREST || cell == FOREST_DARK
    fun isHill(cell: Int) = cell == HILL || cell == HILL_DARK
    fun blocksSight(cell: Int) = cell == ROCK
}

/**
 * Two armies contesting a valley, seen from above.
 *
 * Each side's commander looks the field over every few seconds and gives its squads
 * orders: assault a flag, work round its flank, or hold one that is under threat. A
 * flanking squad picks its route to keep out of the enemy's fire — through the woods,
 * across the far ford — so pincers happen on their own. Squads that find themselves
 * badly outnumbered fall back to ground they hold, and a line that was advancing gives
 * way. Troops who are hit are routed, not killed: they stream back to camp, regroup, and
 * come out again as reinforcements. Each camp has a gun that shells the thickest knot of
 * the enemy, and leaves the ground scarred.
 *
 * What is tied to the clock is only your side's vigour — pace, rate of fire, how fast
 * the routed regroup ([WorldPacer]). The enemy fights at a steady pace. The objective is
 * the share of the field's flags you hold, so ground can be lost as well as won.
 */
class BattleWorld(
    override val width: Int,
    override val height: Int,
    seed: Long,
    /** Victory points the campaign calls for; see [pointsFor]. */
    private val quota: Float = pointsFor(10f)
) : World {

    override val cells = IntArray(width * height)

    private val generator = Random(seed)
    private val cost = FloatArray(width * height)

    private val ours = 0
    private val theirs = 1

    private class Point(val x: Int, val y: Int, val home: Int, val worth: Float) {
        /** -1 theirs, +1 ours. */
        var capture = 0f
    }

    private class Soldier(var x: Float, var y: Float, val side: Int, var squad: Int) {
        var routed = false
        var cooldown = 0
        var regroup = 0
    }

    private enum class Order { ASSAULT, FLANK, HOLD }

    private inner class Squad(val side: Int) {
        var target = 0
        var order = Order.ASSAULT
        var fallback = 0
        val field = FloatArray(width * height)
        var nextPlan = 0
    }

    private class Tracer(val x0: Int, val y0: Int, val x1: Int, val y1: Int, var ttl: Int)
    private class Shell(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val side: Int) {
        var t = 0f
    }
    private class Blast(val x: Int, val y: Int, var ttl: Int)

    private val points = ArrayList<Point>()
    private val units = ArrayList<Soldier>()
    private val squads = ArrayList<Squad>()
    private val tracers = ArrayList<Tracer>()
    private val shells = ArrayList<Shell>()
    private val blasts = ArrayList<Blast>()
    private val danger = Array(2) { FloatArray(width * height) }
    private val homeField = Array(2) { FloatArray(width * height) }
    private val occupied = IntArray(width * height)
    private val queue = PriorityQueue<Long>()

    private val baseY = intArrayOf(height - 5, 4)
    private val gunCharge = FloatArray(2)
    private var tick = 0

    /** Victory points so far: every tick, a point for holding the whole field. */
    private var score = 0f

    override val objectiveProgress: Float
        get() = (score / quota).coerceAtMost(1f)

    /**
     * How much of the field we hold right now, weighted: the line can surge and give
     * way, and this goes up and down with it, but the campaign is scored on how long
     * ground was held — so progress only climbs, faster while we are winning.
     */
    val holding: Float
        get() {
            // Flags deep in their ground are worth more than the ones on our doorstep:
            // the near pair are nearly free, their camp is the real prize.
            var held = 0f
            var total = 0f
            points.forEach {
                if (it.home == ours) return@forEach
                held += max(0f, it.capture) * it.worth
                total += it.worth
            }
            return held / total
        }

    private val contested: Int get() = points.count { it.home != ours }

    override val objective: String
        get() = "${points.count { it.home != ours && it.capture > 0.5f }} of $contested flags held · ${(objectiveProgress * 100).roundToInt()}% of the campaign"

    override val kind: WorldKind get() = WorldKind.BATTLE

    override val focusY: Float get() = 0.5f

    override fun newPacer(): WorldPacer = WorldPacer(gain = PACER_GAIN, learningRate = PACER_LEARNING)

    /** Units currently in the field, not routed, per side. */
    fun strength(side: Int): Int = units.count { it.side == side && !it.routed }

    val routedCount: Int get() = units.count { it.routed }

    init {
        paintTerrain()
        placePoints()
        computeHomeFields()
        raiseArmies()
    }

    // --- Terrain ----------------------------------------------------------------------

    private fun paintTerrain() {
        for (i in cells.indices) {
            val r = generator.nextFloat()
            cells[i] = when {
                r < 0.18f -> BattleMat.GRASS_DARK
                r < 0.28f -> BattleMat.GRASS_LIGHT
                else -> BattleMat.GRASS
            }
        }
        // A river across the middle of the valley, with fords and a bridge.
        val riverY = height / 2 + generator.nextInt(-4, 5)
        val phase = generator.nextFloat() * 6f
        val crossings = mutableListOf(generator.nextInt(6, width / 3), generator.nextInt(width * 2 / 3, width - 6))
        val bridge = generator.nextInt(width / 3, width * 2 / 3)
        for (x in 0 until width) {
            val centre = riverY + (sin(x * 0.12f + phase) * 3f).roundToInt()
            for (dy in -1..1) {
                val y = centre + dy
                if (y !in 0 until height) continue
                val index = y * width + x
                cells[index] = when {
                    abs(x - bridge) <= 1 -> BattleMat.BRIDGE
                    crossings.any { abs(x - it) <= 2 } -> BattleMat.FORD
                    dy == 0 && generator.nextFloat() < 0.3f -> BattleMat.WATER_LIGHT
                    else -> BattleMat.WATER
                }
            }
        }
        // Woods: cover, and slow going.
        repeat(7) {
            val cx = generator.nextInt(width)
            val cy = generator.nextInt(10, height - 10)
            val rx = generator.nextFloat() * 6f + 3f
            val ry = generator.nextFloat() * 5f + 3f
            blob(cx, cy, rx, ry) { index ->
                if (isOpenGround(cells[index])) {
                    cells[index] = if (generator.nextFloat() < 0.35f) BattleMat.FOREST_DARK else BattleMat.FOREST
                }
            }
        }
        // A hill: ground that sees further.
        val hx = width / 2 + generator.nextInt(-10, 11)
        val hy = height / 2 + generator.nextInt(-20, 20).let { if (abs(it) < 8) it + 14 else it }
        blob(hx, hy, 7f, 6f) { index ->
            if (isOpenGround(cells[index])) {
                cells[index] = if (generator.nextFloat() < 0.3f) BattleMat.HILL_DARK else BattleMat.HILL
            }
        }
        // Boulders.
        repeat(14) {
            val cx = generator.nextInt(width)
            val cy = generator.nextInt(8, height - 8)
            blob(cx, cy, generator.nextFloat() * 1.5f + 0.8f, generator.nextFloat() * 1.5f + 0.8f) { index ->
                if (isOpenGround(cells[index]) || BattleMat.isForest(cells[index])) cells[index] = BattleMat.ROCK
            }
        }
        // Camps.
        for (side in 0..1) {
            val y = baseY[side]
            for (tx in intArrayOf(width / 2 - 8, width / 2 - 3, width / 2 + 3, width / 2 + 8)) {
                for (dy in 0..1) for (dx in 0..1) {
                    cells[(y + dy - 1) * width + tx + dx] = if (side == ours) BattleMat.TENT_OURS else BattleMat.TENT_THEIRS
                }
            }
        }
        for (i in cells.indices) cost[i] = costOf(cells[i])
    }

    private fun isOpenGround(cell: Int) =
        cell == BattleMat.GRASS || cell == BattleMat.GRASS_DARK || cell == BattleMat.GRASS_LIGHT

    private fun costOf(cell: Int): Float = when (cell) {
        BattleMat.WATER, BattleMat.WATER_LIGHT, BattleMat.ROCK -> -1f
        BattleMat.FOREST, BattleMat.FOREST_DARK -> 2.2f
        BattleMat.FORD -> 3f
        BattleMat.HILL, BattleMat.HILL_DARK -> 1.3f
        BattleMat.CRATER -> 1.6f
        else -> 1f
    }

    private inline fun blob(cx: Int, cy: Int, rx: Float, ry: Float, paint: (Int) -> Unit) {
        for (y in (cy - ry - 1).toInt()..(cy + ry + 1).toInt()) for (x in (cx - rx - 1).toInt()..(cx + rx + 1).toInt()) {
            if (x !in 0 until width || y !in 0 until height) continue
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            if (dx * dx + dy * dy <= 1f + generator.nextFloat() * 0.3f) paint(y * width + x)
        }
    }

    /** Flags up the valley: a pair on our side, one on the river, a pair beyond, their camp. */
    private fun placePoints() {
        val layout = listOf(
            Triple(0.25f, 0.74f, 1f), Triple(0.75f, 0.74f, 1f),
            Triple(0.5f, 0.52f, 2f),
            Triple(0.22f, 0.3f, 3f), Triple(0.78f, 0.3f, 3f),
            Triple(0.5f, 0.12f, 4f)
        )
        layout.forEach { (fx, fy, worth) ->
            var x = (width * fx).toInt() + generator.nextInt(-3, 4)
            var y = (height * fy).toInt() + generator.nextInt(-2, 3)
            // Flags stand on walkable ground.
            var tries = 0
            while (cost[y * width + x] < 0f && tries < 40) {
                x = (x + generator.nextInt(-2, 3)).coerceIn(2, width - 3)
                y = (y + generator.nextInt(-2, 3)).coerceIn(2, height - 3)
                tries++
            }
            points += Point(x, y, home = -1, worth = worth)
        }
        // Their camp's flag starts theirs; everything else is up for grabs.
        points.last().capture = -1f
    }

    private fun computeHomeFields() {
        for (side in 0..1) {
            dijkstra(homeField[side], listOf(baseY[side] * width + width / 2), null, 0f)
        }
    }

    private fun raiseArmies() {
        for (side in 0..1) {
            repeat(SQUADS) { s ->
                val squad = Squad(side)
                squad.nextPlan = s * 17
                squads += squad
                repeat(SQUAD_SIZE) {
                    val x = width / 2f + generator.nextFloat() * 20f - 10f
                    val y = baseY[side] + (if (side == ours) -2f else 2f) + generator.nextFloat() * 2f
                    units += Soldier(x, y, side, squads.size - 1)
                }
            }
        }
    }

    // --- Pathing ----------------------------------------------------------------------

    /** Distance to [goals] over the terrain, with [threat] (if any) priced in. */
    private fun dijkstra(field: FloatArray, goals: List<Int>, threat: FloatArray?, threatWeight: Float) {
        field.fill(Float.MAX_VALUE)
        queue.clear()
        goals.forEach {
            field[it] = 0f
            queue.add(it.toLong())
        }
        while (true) {
            val entry = queue.poll() ?: break
            val index = (entry and 0xFFFFFFFFL).toInt()
            val at = field[index]
            if ((entry ushr 32).toInt() > (at * 64).toInt() + 1) continue
            val x = index % width
            val y = index / width
            for (d in 0 until 8) {
                val nx = x + DX[d]
                val ny = y + DY[d]
                if (nx !in 0 until width || ny !in 0 until height) continue
                val next = ny * width + nx
                val c = cost[next]
                if (c < 0f) continue
                var step = c * STEP[d]
                if (threat != null) step += threat[next] * threatWeight
                val total = at + step
                if (total < field[next]) {
                    field[next] = total
                    queue.add(((total * 64).toLong() shl 32) or next.toLong())
                }
            }
        }
    }

    // --- Simulation -------------------------------------------------------------------

    override fun step(effort: Float, random: Random) {
        tick++
        // Effort reaches the troops through a soft curve: a slack side still holds its
        // ground, and a driven one does not simply steamroll. A battle has momentum; a
        // hard lever on it only swings the line back and forth.
        val vigour = arrayOf(effort.coerceAtLeast(0.01f).pow(VIGOUR_CURVE).coerceIn(VIGOUR_MIN, VIGOUR_MAX), 1f)
        if (tick % DANGER_EVERY == 1) computeDanger()
        if (tick % COMMAND_EVERY == 1) command(random)
        replanSquads()
        occupied.fill(0)
        units.forEach { occupied[cellOf(it)]++ }
        units.forEach { unit -> actUnit(unit, vigour[unit.side], random) }
        capturePoints(vigour)
        score += holding
        fireGuns(vigour, random)
        advanceEffects(random)
    }

    private fun cellOf(unit: Soldier): Int =
        unit.y.toInt().coerceIn(0, height - 1) * width + unit.x.toInt().coerceIn(0, width - 1)

    private fun computeDanger() {
        for (side in 0..1) {
            val map = danger[side]
            map.fill(0f)
            units.forEach { enemy ->
                if (enemy.side == side || enemy.routed) return@forEach
                val ex = enemy.x.toInt()
                val ey = enemy.y.toInt()
                for (dy in -RANGE..RANGE) for (dx in -RANGE..RANGE) {
                    val x = ex + dx
                    val y = ey + dy
                    if (x !in 0 until width || y !in 0 until height) continue
                    val d2 = dx * dx + dy * dy
                    if (d2 > RANGE * RANGE) continue
                    map[y * width + x] += 1f - d2.toFloat() / (RANGE * RANGE)
                }
            }
        }
    }

    /**
     * The commander's round: hold what is threatened, then pick the softest flag not
     * yet held and send two squads — one straight at it, one round the side.
     */
    private fun command(random: Random) {
        for (side in 0..1) {
            val sign = if (side == ours) 1f else -1f
            val mine = squads.filter { it.side == side && it.fallback <= 0 }
            if (mine.isEmpty()) continue
            val presence = points.map { p -> nearby(p.x, p.y, 1 - side, CAPTURE_RADIUS + 4) }

            val threatened = points.indices.filter { i ->
                points[i].capture * sign > 0.3f && presence[i] > 0
            }
            val targets = points.indices.filter { points[it].capture * sign < 0.95f }
                .sortedBy { i ->
                    val p = points[i]
                    val distance = abs(p.y - baseY[side]).toFloat()
                    distance + presence[i] * 6f + random.nextFloat() * 14f
                }

            var cursor = 0
            val shuffled = mine.shuffled(random)
            shuffled.forEachIndexed { n, squad ->
                val previous = squad.target to squad.order
                when {
                    n < threatened.size -> {
                        squad.target = threatened[n]
                        squad.order = Order.HOLD
                    }
                    targets.isNotEmpty() -> {
                        val pick = targets[(cursor / 2).coerceAtMost(targets.size - 1)]
                        squad.target = pick
                        squad.order = if (cursor % 2 == 0) Order.ASSAULT else Order.FLANK
                        cursor++
                    }
                    else -> {
                        // Everything is ours: sit on the flags.
                        squad.target = n % points.size
                        squad.order = Order.HOLD
                    }
                }
                if (previous != squad.target to squad.order) squad.nextPlan = tick
            }
        }
    }

    private fun nearby(x: Int, y: Int, side: Int, radius: Int): Int =
        units.count { it.side == side && !it.routed && abs(it.x - x) <= radius && abs(it.y - y) <= radius }

    private fun replanSquads() {
        squads.forEach { squad ->
            if (squad.fallback > 0) squad.fallback--
            if (tick < squad.nextPlan) return@forEach
            squad.nextPlan = tick + REPLAN_EVERY
            val point = points[squad.target]
            val weight = when (squad.order) {
                Order.FLANK -> FLANK_CAUTION
                Order.ASSAULT -> ASSAULT_CAUTION
                Order.HOLD -> 0f
            }
            dijkstra(squad.field, listOf(point.y * width + point.x), danger[squad.side], weight)
        }
    }

    private fun actUnit(unit: Soldier, vigour: Float, random: Random) {
        val speed = BASE_SPEED * vigour
        if (unit.routed) {
            // Back to camp, then regroup.
            if (abs(unit.y - baseY[unit.side]) < 2.5f && abs(unit.x - width / 2f) < 12f) {
                unit.regroup -= max(1, (vigour * 2f).toInt())
                if (unit.regroup <= 0) {
                    unit.routed = false
                    unit.squad = weakestSquad(unit.side)
                }
                return
            }
            follow(unit, homeField[unit.side], speed * 1.1f, random)
            return
        }

        val squad = squads[unit.squad]
        if (unit.cooldown > 0) unit.cooldown--

        // Take a shot if there is one.
        var engaged = false
        if (unit.cooldown <= 0) {
            val target = nearestEnemy(unit, rangeFor(unit))
            if (target != null) {
                engaged = true
                shoot(unit, target, vigour, random)
            }
        } else if (nearestEnemy(unit, rangeFor(unit)) != null) {
            engaged = true
        }

        // Morale: badly outnumbered close by, the squad falls back to ground it holds.
        if (engaged && squad.fallback <= 0 && random.nextFloat() < 0.02f) {
            val friends = nearbyF(unit.x, unit.y, unit.side, 9f)
            val foes = nearbyF(unit.x, unit.y, 1 - unit.side, 9f)
            if (foes > friends * 1.6f + 1) {
                val sign = if (unit.side == ours) 1f else -1f
                val refuge = points.indices.filter { points[it].capture * sign > 0.2f }
                    .minByOrNull { abs(points[it].y - unit.y) + abs(points[it].x - unit.x) }
                squad.fallback = FALLBACK_TICKS
                squad.order = Order.HOLD
                squad.target = refuge ?: squad.target
                squad.nextPlan = tick
                if (refuge == null) {
                    // Nothing held to fall back on: back toward camp.
                    squad.target = points.indices.minBy { abs(points[it].y - baseY[unit.side]) }
                }
            }
        }

        // Hold at range when engaged, unless assaulting; always close in on the flag.
        val point = points[squad.target]
        val atFlag = abs(unit.x - point.x) < CAPTURE_RADIUS && abs(unit.y - point.y) < CAPTURE_RADIUS
        val hold = engaged && (squad.order == Order.HOLD || (squad.order == Order.FLANK && random.nextFloat() < 0.5f))
        when {
            atFlag -> mill(unit, point, speed * 0.4f, random)
            hold -> Unit
            else -> follow(unit, squad.field, speed * if (engaged) 0.6f else 1f, random)
        }
    }

    private fun rangeFor(unit: Soldier): Int =
        if (BattleMat.isHill(cells[cellOf(unit)])) RANGE + 3 else RANGE

    private fun weakestSquad(side: Int): Int =
        squads.indices.filter { squads[it].side == side }
            .minBy { s -> units.count { it.squad == s && !it.routed } }

    private fun nearbyF(x: Float, y: Float, side: Int, radius: Float): Int =
        units.count { it.side == side && !it.routed && abs(it.x - x) <= radius && abs(it.y - y) <= radius }

    private fun nearestEnemy(unit: Soldier, range: Int): Soldier? {
        var best: Soldier? = null
        var bestDistance = (range * range).toFloat()
        units.forEach { other ->
            if (other.side == unit.side || other.routed) return@forEach
            val dx = other.x - unit.x
            val dy = other.y - unit.y
            val d2 = dx * dx + dy * dy
            if (d2 <= bestDistance && lineOfSight(unit, other)) {
                bestDistance = d2
                best = other
            }
        }
        return best
    }

    private fun lineOfSight(a: Soldier, b: Soldier): Boolean {
        var x = a.x.toInt()
        var y = a.y.toInt()
        val x1 = b.x.toInt()
        val y1 = b.y.toInt()
        val dx = abs(x1 - x)
        val dy = -abs(y1 - y)
        val sx = if (x < x1) 1 else -1
        val sy = if (y < y1) 1 else -1
        var err = dx + dy
        var woods = 0
        while (!(x == x1 && y == y1)) {
            val cell = cells[y * width + x]
            if (BattleMat.blocksSight(cell)) return false
            if (BattleMat.isForest(cell)) woods++
            if (woods > 3) return false
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
        return true
    }

    private fun shoot(unit: Soldier, target: Soldier, vigour: Float, random: Random) {
        unit.cooldown = (random.nextInt(COOLDOWN_MIN, COOLDOWN_MAX) / vigour).toInt()
        tracers += Tracer(unit.x.toInt(), unit.y.toInt(), target.x.toInt(), target.y.toInt(), TRACER_TICKS)
        var chance = HIT_CHANCE
        // Overreach: troops deep in enemy ground are exposed, defenders near their own
        // camp are dug in. It is what makes a front settle somewhere instead of one
        // side simply rolling the other up.
        val depth = abs(target.y - baseY[target.side]) / abs(baseY[theirs] - baseY[ours]).toFloat()
        chance *= OVERREACH_MIN + (OVERREACH_MAX - OVERREACH_MIN) * depth
        if (BattleMat.isForest(cells[cellOf(target)])) chance *= 0.45f
        if (BattleMat.isHill(cells[cellOf(unit)])) chance *= 1.3f
        if (random.nextFloat() < chance) rout(target, random)
    }

    private fun rout(unit: Soldier, random: Random) {
        unit.routed = true
        unit.regroup = random.nextInt(REGROUP_MIN, REGROUP_MAX)
    }

    /** One step down the field, sidestepping anyone already in the cell ahead. */
    private fun follow(unit: Soldier, field: FloatArray, speed: Float, random: Random) {
        val cx = unit.x.toInt().coerceIn(0, width - 1)
        val cy = unit.y.toInt().coerceIn(0, height - 1)
        var best = field[cy * width + cx]
        var bx = cx
        var by = cy
        val start = random.nextInt(8)
        for (k in 0 until 8) {
            val d = (start + k) % 8
            val nx = cx + DX[d]
            val ny = cy + DY[d]
            if (nx !in 0 until width || ny !in 0 until height) continue
            val index = ny * width + nx
            val value = field[index] + occupied[index] * CROWDING
            if (value < best) {
                best = value
                bx = nx
                by = ny
            }
        }
        moveToward(unit, bx + 0.5f, by + 0.5f, speed * terrainPace(cy * width + cx), random)
    }

    private fun mill(unit: Soldier, point: Point, speed: Float, random: Random) {
        val tx = point.x + 0.5f + random.nextFloat() * 5f - 2.5f
        val ty = point.y + 0.5f + random.nextFloat() * 5f - 2.5f
        val index = ty.toInt().coerceIn(0, height - 1) * width + tx.toInt().coerceIn(0, width - 1)
        if (cost[index] > 0f) moveToward(unit, tx, ty, speed, random)
    }

    private fun terrainPace(index: Int): Float = 1f / cost[index].coerceAtLeast(1f)

    private fun moveToward(unit: Soldier, tx: Float, ty: Float, speed: Float, random: Random) {
        val dx = tx - unit.x + (random.nextFloat() - 0.5f) * 0.3f
        val dy = ty - unit.y + (random.nextFloat() - 0.5f) * 0.3f
        val length = hypot(dx, dy)
        if (length < 0.001f) return
        val step = min(speed, length)
        val nx = unit.x + dx / length * step
        val ny = unit.y + dy / length * step
        val index = ny.toInt().coerceIn(0, height - 1) * width + nx.toInt().coerceIn(0, width - 1)
        if (cost[index] < 0f) return
        unit.x = nx.coerceIn(0f, width - 0.01f)
        unit.y = ny.coerceIn(0f, height - 0.01f)
    }

    private fun capturePoints(vigour: Array<Float>) {
        points.forEach { point ->
            val mine = nearby(point.x, point.y, ours, CAPTURE_RADIUS)
            val foes = nearby(point.x, point.y, theirs, CAPTURE_RADIUS)
            if (mine > 0 && foes == 0) {
                point.capture = (point.capture + CAPTURE_RATE * min(mine, 4) * vigour[ours]).coerceAtMost(1f)
            } else if (foes > 0 && mine == 0) {
                point.capture = (point.capture - CAPTURE_RATE * min(foes, 4)).coerceAtLeast(-1f)
            }
        }
    }

    /** Each camp's gun lobs a shell at the thickest knot of the enemy it can find. */
    private fun fireGuns(vigour: Array<Float>, random: Random) {
        for (side in 0..1) {
            gunCharge[side] += vigour[side]
            if (gunCharge[side] < GUN_RELOAD) continue
            val targets = units.filter { it.side != side && !it.routed }
            if (targets.isEmpty()) continue
            val aim = targets.maxBy { t -> targets.count { abs(it.x - t.x) < 4 && abs(it.y - t.y) < 4 } }
            gunCharge[side] = 0f
            val scatter = 3f
            shells += Shell(
                width / 2f, baseY[side].toFloat(),
                (aim.x + (random.nextFloat() - 0.5f) * scatter * 2).coerceIn(0f, width - 1f),
                (aim.y + (random.nextFloat() - 0.5f) * scatter * 2).coerceIn(0f, height - 1f),
                side
            )
        }
    }

    private fun advanceEffects(random: Random) {
        tracers.removeAll { --it.ttl <= 0 }
        blasts.removeAll { --it.ttl <= 0 }
        val landed = shells.filter { it.t >= 1f }
        shells.removeAll(landed)
        shells.forEach { it.t += SHELL_SPEED }
        landed.forEach { shell ->
            val bx = shell.x1.toInt()
            val by = shell.y1.toInt()
            blasts += Blast(bx, by, BLAST_TICKS)
            for (dy in -2..2) for (dx in -2..2) {
                val x = bx + dx
                val y = by + dy
                if (x !in 0 until width || y !in 0 until height || dx * dx + dy * dy > 5) continue
                val index = y * width + x
                val cell = cells[index]
                if (cost[index] < 0f || cell == BattleMat.BRIDGE || cell == BattleMat.FORD) continue
                val d2 = dx * dx + dy * dy
                // A small hole, and a ragged scorch round it that the eye barely notices.
                cells[index] = when {
                    d2 == 0 -> BattleMat.CRATER
                    random.nextFloat() < 0.45f -> BattleMat.SCORCH
                    else -> continue
                }
                cost[index] = costOf(cells[index])
            }
            units.forEach { unit ->
                if (unit.side != shell.side && !unit.routed &&
                    abs(unit.x - bx) <= BLAST_RADIUS && abs(unit.y - by) <= BLAST_RADIUS &&
                    random.nextFloat() < 0.6f
                ) rout(unit, random)
            }
        }
    }

    // --- Rendering ------------------------------------------------------------------

    override fun renderInto(buffer: IntArray) {
        cells.copyInto(buffer)
        fun plot(x: Int, y: Int, mat: Int) {
            if (x in 0 until width && y in 0 until height) buffer[y * width + x] = mat
        }
        points.forEach { point ->
            val flag = when {
                point.capture > 0.5f -> BattleMat.FLAG_OURS
                point.capture < -0.5f -> BattleMat.FLAG_THEIRS
                else -> BattleMat.FLAG_NEUTRAL
            }
            plot(point.x, point.y, BattleMat.POLE)
            plot(point.x, point.y - 1, BattleMat.POLE)
            plot(point.x, point.y - 2, BattleMat.POLE)
            // The flag's length shows how firmly it is held.
            val length = 1 + (abs(point.capture) * 2).roundToInt()
            for (i in 1..length) plot(point.x + i, point.y - 2, flag)
            if (length > 1) plot(point.x + 1, point.y - 1, flag)
        }
        tracers.forEach { tracer ->
            // Only a short streak near the target: a line the whole way reads as a laser.
            val steps = max(abs(tracer.x1 - tracer.x0), abs(tracer.y1 - tracer.y0)).coerceAtLeast(1)
            val from = (steps * (0.4f + 0.3f * (TRACER_TICKS - tracer.ttl))).toInt().coerceAtMost(steps)
            for (s in from..min(steps, from + 2)) {
                val x = tracer.x0 + (tracer.x1 - tracer.x0) * s / steps
                val y = tracer.y0 + (tracer.y1 - tracer.y0) * s / steps
                plot(x, y, BattleMat.TRACER)
            }
        }
        units.forEach { unit ->
            val mat = when {
                unit.side == ours && unit.routed -> BattleMat.OURS_ROUTED
                unit.side == ours -> BattleMat.OURS
                unit.routed -> BattleMat.THEIRS_ROUTED
                else -> BattleMat.THEIRS
            }
            // A shadow under each soldier: a lone pixel vanishes on a phone, a figure
            // with a foot on the ground reads.
            val x = unit.x.toInt()
            val y = unit.y.toInt()
            if (y + 1 < height && buffer[(y + 1) * width + x] < BattleMat.OURS) plot(x, y + 1, BattleMat.SHADOW)
            plot(x, y, mat)
        }
        shells.forEach { shell ->
            val x = shell.x0 + (shell.x1 - shell.x0) * shell.t
            // Lobbed: a little lift in the middle of the flight.
            val y = shell.y0 + (shell.y1 - shell.y0) * shell.t - sin(shell.t * Math.PI).toFloat() * 6f
            plot(x.toInt(), y.toInt(), BattleMat.SHELL)
        }
        blasts.forEach { blast ->
            val r = if (blast.ttl > BLAST_TICKS / 2) 1 else 2
            for (dy in -r..r) for (dx in -r..r) {
                if (dx * dx + dy * dy <= r * r + 1 && (blast.ttl + dx + dy) % 2 == 0) plot(blast.x + dx, blast.y + dy, BattleMat.BLAST)
            }
        }
    }

    companion object {
        /**
         * Victory points for a timer this long: what a side holding its usual share of
         * the field earns over the timer, so an ordinary fight lands on the target.
         */
        fun pointsFor(durationMinutes: Float): Float =
            durationMinutes * 60f * 30f * TYPICAL_HOLDING / WorldPacer.DEFAULT_COMPLETION_TARGET

        private const val TYPICAL_HOLDING = 0.28f
        private const val PACER_GAIN = 2f
        private const val PACER_LEARNING = 0.004f

        private const val SQUADS = 5
        private const val SQUAD_SIZE = 6

        private const val RANGE = 7
        private const val CAPTURE_RADIUS = 3
        private const val CAPTURE_RATE = 0.0003f

        private const val VIGOUR_CURVE = 0.4f
        private const val VIGOUR_MIN = 0.65f
        private const val VIGOUR_MAX = 2.2f
        private const val BASE_SPEED = 0.05f
        private const val CROWDING = 0.8f
        private const val HIT_CHANCE = 0.16f
        private const val OVERREACH_MIN = 0.3f
        private const val OVERREACH_MAX = 1.9f
        private const val COOLDOWN_MIN = 45
        private const val COOLDOWN_MAX = 110
        private const val REGROUP_MIN = 240
        private const val REGROUP_MAX = 480
        private const val FALLBACK_TICKS = 360

        private const val FLANK_CAUTION = 6f
        private const val ASSAULT_CAUTION = 0.5f

        private const val DANGER_EVERY = 45
        private const val COMMAND_EVERY = 240
        private const val REPLAN_EVERY = 90

        private const val GUN_RELOAD = 1100f
        private const val SHELL_SPEED = 0.02f
        private const val BLAST_TICKS = 10
        private const val BLAST_RADIUS = 2

        private const val TRACER_TICKS = 3

        private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        private val STEP = floatArrayOf(1f, 1.414f, 1f, 1.414f, 1f, 1.414f, 1f, 1.414f)
    }
}
