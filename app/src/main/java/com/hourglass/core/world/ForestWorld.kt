package com.hourglass.core.world

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Cell slots in a forest world. */
object ForestMat {
    const val SKY = 1
    const val GRASS = 2
    const val DIRT = 3
    const val DIRT_DARK = 4
    const val TRUNK = 5
    const val TRUNK_DARK = 6
    const val LEAF = 7
    const val LEAF_DARK = 8
    const val PINE = 9
    const val PINE_DARK = 10
    const val STUMP = 11
    const val LOG_END = 12
    const val LOG_END_DARK = 13
    const val BRUSH = 14
    const val FIRE = 15
    const val FIRE_HOT = 16
    const val SMOKE = 17
    const val CHIP = 18
    const val SKIN = 19
    const val SHIRT = 20
    const val TROUSERS = 21
    const val TOOL = 22
    const val LEAF_LIGHT = 23
    const val COUNT = 24

    fun isTrunk(cell: Int) = cell == TRUNK || cell == TRUNK_DARK
}

/**
 * A lumber crew clearing a stand of trees, side-on.
 *
 * It is a big job done in stages, and the crew splits itself across them. Two fellers
 * chop at the foot of a tree, chips flying, until it goes; they call it, everyone clears
 * the drop zone, and the tree swings down under gravity. Then the others move in:
 * limbers strip the crown into a brush pile, a bucker saws the trunk into lengths, and
 * pairs carry each length — together, at the slower one's pace — to the pile by the
 * camp. The brush gets burned. Meanwhile the fellers have moved on to the next tree,
 * as long as its drop zone is clear of work in progress.
 *
 * Every tree grows differently, falls whichever way there is room, and gets worked
 * however the crew happens to divide up. What is tied to the clock is only how hard the
 * crew works ([WorldPacer]).
 */
class ForestWorld(
    override val width: Int,
    override val height: Int,
    seed: Long,
    /** Lengths of timber the job calls for. */
    private val quota: Int = 20
) : World {

    override val cells = IntArray(width * height)

    private val generator = Random(seed)
    private val groundY = (height * GROUND).toInt()
    /** The row feet stand on and logs lie in. */
    private val footY = groundY - 1

    /** Timber lying on the ground: what it is, and which tree it came from. */
    private val felled = IntArray(width * height)
    private val felledOwner = IntArray(width * height) { -1 }

    private val trees = ArrayList<Tree>()
    private val jacks = ArrayList<Jack>()
    private val carries = ArrayList<Carry>()
    private val particles = ArrayList<Particle>()
    private val pile = ArrayList<Int>()  // log-end slots filled, as indices into pileSlots
    private val pileSlots = ArrayList<Pair<Int, Int>>()

    private var stacked = 0
    private var sectionsTotal = 0
    private var tick = 0

    // --- Entities -------------------------------------------------------------------

    private enum class Stage { STANDING, CHOPPING, WARNING, FALLING, DOWN, DONE }

    private inner class Tree(val baseX: Int, val height: Int, val pine: Boolean) {
        val radius = if (pine) (height * 0.24f).toInt().coerceAtLeast(4) else (height * 0.3f).toInt().coerceAtLeast(5)
        /** Sprite: rows sy = -height..0 (0 is the ground row), columns sx = -radius..radius. */
        val sprite = IntArray((height + 1) * (2 * radius + 1))
        var stage = Stage.STANDING
        var dir = 0
        var chop = 0f
        var fellers = 0
        var limbers = 0
        var warnTicks = 0
        var angle = 0f
        var spin = 0f
        var sections = 0
        lateinit var cutDone: BooleanArray
        lateinit var cutClaimed: BooleanArray
        lateinit var sectionState: IntArray  // 0 lying, 1 claimed, 2 carried away
        var limbed = false
        var brush = 0
        var brushLit = false
        var brushClaimed = false
        var fire = 0f
        var lighting = 0

        fun spriteAt(sx: Int, sy: Int): Int {
            if (sx < -radius || sx > radius || sy < -height || sy > 0) return 0
            return sprite[(sy + height) * (2 * radius + 1) + sx + radius]
        }

        fun paint(sx: Int, sy: Int, mat: Int) {
            if (sx < -radius || sx > radius || sy < -height || sy > 0) return
            sprite[(sy + height) * (2 * radius + 1) + sx + radius] = mat
        }

        /** Distance along the fallen trunk to where section [i] starts. */
        fun sectionStart(i: Int) = LOG_START + i * SECTION
        fun xAt(distance: Int) = baseX + dir * distance
        val brushX: Int get() = xAt(LOG_START + sections * SECTION + 2).coerceIn(1, width - 2)

        /** The ground this tree will cover once down: nobody stands here when it goes. */
        fun zone(direction: Int = dir): IntRange {
            val near = baseX + direction
            val far = baseX + direction * (height + 1)
            return minOf(near, far)..maxOf(near, far)
        }

        fun usableFor(direction: Int): Int {
            val room = if (direction > 0) width - 1 - baseX else baseX - CAMP_EDGE
            return ((minOf(room, (height * 0.8f).toInt()) - LOG_START) / SECTION).coerceAtLeast(0)
        }

        val busy: Boolean get() = stage == Stage.WARNING || stage == Stage.FALLING
    }

    private enum class Task { NONE, FELL, LIMB, BUCK, HAUL, BURN }

    private inner class Jack(var x: Int, val pace: Float, val soul: Int) {
        var banked = 0f
        var resting = 0
        var task = Task.NONE
        var tree: Tree? = null
        var slot = 0
        var carry: Carry? = null
        var swing = false
    }

    /** A length of timber on two pairs of shoulders. */
    private inner class Carry(val tree: Tree, val section: Int, val length: Int) {
        val bearers = ArrayList<Jack>(2)
        var lifted = false
        var centre = 0
        val ready = BooleanArray(2)
        val nearX: Int get() = tree.xAt(tree.sectionStart(section) + if (tree.dir > 0) 0 else SECTION - 1)
        val farX: Int get() = tree.xAt(tree.sectionStart(section) + if (tree.dir > 0) SECTION - 1 else 0)
    }

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, val mat: Int, val rises: Boolean)

    // --- Objective --------------------------------------------------------------------

    override val objectiveProgress: Float
        get() = if (order == 0) 1f else (stacked.toFloat() / order).coerceAtMost(1f)

    private val order: Int get() = minOf(quota, sectionsTotal)

    override val objective: String get() = "$stacked of $order lengths stacked"

    override val kind: WorldKind get() = WorldKind.FOREST

    override val focusY: Float get() = 0.85f

    override val deeds: Deeds = Deeds("Lumberjack")

    override val shift: List<Int> get() = jacks.map { it.soul }.distinct()

    val treesDown: Int get() = trees.count { it.stage == Stage.DOWN || it.stage == Stage.DONE }
    val lengthsStacked: Int get() = stacked
    val crewSize: Int get() = jacks.size

    /** Whether anyone is standing where a tree is coming down. */
    fun anyoneUnderFallingTree(): Boolean = trees.any { tree ->
        tree.stage == Stage.FALLING && tree.angle > 0.6f &&
            jacks.any { it.x in tree.zone() }
    }

    // --- Generation -------------------------------------------------------------------

    init {
        paintGround()
        plantTrees()
        layOutPile()
        Souls.cast(CREW, generator).forEach { soul ->
            jacks += Jack(generator.nextInt(1, CAMP_EDGE), Souls.pace(soul, generator), soul)
        }
    }

    private fun paintGround() {
        for (y in 0 until height) for (x in 0 until width) {
            cells[y * width + x] = when {
                y < groundY -> ForestMat.SKY
                y == groundY -> ForestMat.GRASS
                generator.nextFloat() < 0.25f -> ForestMat.DIRT_DARK
                else -> ForestMat.DIRT
            }
        }
    }

    private fun plantTrees() {
        var x = CAMP_EDGE + 3 + generator.nextInt(0, 4)
        while (x < width - 3) {
            val room = maxOf(width - 1 - x, x - CAMP_EDGE)
            val tallest = ((room + 1) / 0.8f).toInt()
            val h = generator.nextInt(MIN_TREE, MAX_TREE + 1).coerceAtMost(tallest)
            if (h >= MIN_TREE) {
                val tree = Tree(x, h, pine = generator.nextFloat() < 0.5f)
                grow(tree)
                // Count only what the shorter fall would give: which way it goes is
                // decided on the day.
                val sections = listOf(tree.usableFor(1), tree.usableFor(-1)).filter { it > 0 }.minOrNull() ?: 0
                if (sections > 0) {
                    trees += tree
                    sectionsTotal += sections
                }
            }
            x += generator.nextInt(6, 11)
        }
    }

    private fun grow(tree: Tree) {
        val h = tree.height
        val r = tree.radius
        // Trunk, two wide, running most of the way up.
        val trunkTop = if (tree.pine) h - 1 else (h * 0.78f).toInt()
        for (d in 0..trunkTop) {
            tree.paint(0, -d, if (generator.nextFloat() < 0.3f) ForestMat.TRUNK_DARK else ForestMat.TRUNK)
            tree.paint(-1, -d, if (d > trunkTop - 3 && tree.pine) 0 else ForestMat.TRUNK_DARK)
        }
        if (tree.pine) {
            // Tiers of boughs, widest at the bottom.
            val crownBase = (h * 0.28f).toInt()
            for (d in crownBase..h) {
                val t = (d - crownBase).toFloat() / (h - crownBase)
                val tier = (d - crownBase) % 5
                val half = ((1f - t) * r * (0.75f + 0.25f * (4 - tier) / 4f)).roundToInt()
                for (sx in -half..half) {
                    if (sx == 0 || sx == -1) continue
                    if (abs(sx) == half && generator.nextFloat() < 0.4f) continue
                    tree.paint(sx, -d, if (generator.nextFloat() < 0.35f) ForestMat.PINE_DARK else ForestMat.PINE)
                }
                if (d > crownBase && tier == 0) {
                    tree.paint(0, -d, ForestMat.PINE_DARK)
                    tree.paint(-1, -d, ForestMat.PINE_DARK)
                }
            }
        } else {
            // A broad crown in a few overlapping lobes, with branches running into it.
            val cy = (h * 0.72f)
            repeat(4) { lobe ->
                val lx = generator.nextFloat() * r - r / 2f
                val ly = cy + generator.nextFloat() * h * 0.16f - h * 0.08f
                val lr = r * (0.55f + generator.nextFloat() * 0.3f)
                for (d in (ly - lr).toInt()..(ly + lr).toInt()) for (sx in -r..r) {
                    val dx = (sx - lx) / lr
                    val dy = (d - ly) / (lr * 0.85f)
                    if (dx * dx + dy * dy > 1f || d > h) continue
                    val shade = when {
                        dy > 0.35f && dx < 0f -> ForestMat.LEAF_LIGHT
                        dy < -0.3f -> ForestMat.LEAF_DARK
                        generator.nextFloat() < 0.25f -> ForestMat.LEAF_DARK
                        else -> ForestMat.LEAF
                    }
                    if (lobe == 0 || tree.spriteAt(sx, -d) == 0) tree.paint(sx, -d, shade)
                }
            }
            repeat(3) {
                val start = (h * (0.45f + generator.nextFloat() * 0.2f)).toInt()
                val side = if (generator.nextBoolean()) 1 else -1
                var sx = if (side > 0) 1 else -2
                var d = start
                repeat(generator.nextInt(2, r)) {
                    tree.paint(sx, -d, ForestMat.TRUNK_DARK)
                    sx += side
                    if (generator.nextBoolean()) d++
                }
            }
        }
    }

    /** Log ends stacked in a pyramid at the camp, a second pyramid behind if needed. */
    private fun layOutPile() {
        val base = 6
        for (stackIndex in 0 until 2) {
            // The second pyramid sits behind the first, a cell up and along.
            val originX = 1 + stackIndex
            val originY = footY - stackIndex
            for (row in 0 until base) {
                for (i in 0 until base - row) {
                    pileSlots += (originX + row + i * 2) to (originY - row * 2)
                }
            }
        }
    }

    // --- Simulation -------------------------------------------------------------------

    override fun step(effort: Float, random: Random) {
        tick++
        jacks.forEach { jack ->
            if (jack.resting > 0) {
                jack.resting--
                return@forEach
            }
            // Little effort is spent as breaks, not slow motion — but never mid-carry,
            // and never in the way of a tree that has been called.
            if (effort < SLACK_PACE && jack.carry?.lifted != true && !inDanger(jack)) {
                val breakChance = (SLACK_PACE / effort.coerceAtLeast(0.001f) - 1f) / MEAN_REST_TICKS
                if (random.nextFloat() < breakChance) {
                    jack.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
                    return@forEach
                }
            }
            jack.banked += effort.coerceAtLeast(SLACK_PACE) * RATE * jack.pace
            var actions = 0
            while (jack.banked >= 1f && actions < MAX_ACTIONS_PER_TICK) {
                jack.banked -= 1f
                actions++
                act(jack, random)
            }
            if (jack.banked > MAX_ACTIONS_PER_TICK) jack.banked = MAX_ACTIONS_PER_TICK.toFloat()
        }
        trees.forEach { advanceTree(it, random) }
        moveParticles()
    }

    private fun advanceTree(tree: Tree, random: Random) {
        when (tree.stage) {
            Stage.WARNING -> {
                tree.warnTicks++
                // The call wakes anyone dozing in the drop zone, and nothing goes down
                // until the zone is empty.
                val inZone = jacks.filter { it.x in tree.zone() }
                inZone.forEach { it.resting = 0 }
                if (inZone.isEmpty() && tree.warnTicks > WARN_MIN) {
                    tree.stage = Stage.FALLING
                }
            }
            Stage.FALLING -> {
                // A toppling pole: slow to start, fast at the end.
                tree.spin += FALL_KICK + FALL_GRAVITY * sin(tree.angle)
                tree.angle += tree.spin
                if (tree.angle >= HALF_PI) land(tree, random)
            }
            Stage.DOWN -> {
                if (tree.brushLit) {
                    if (random.nextFloat() < 0.35f) {
                        particles += Particle(
                            tree.brushX + random.nextFloat() * 3f - 1.5f, footY - 2f - brushHeight(tree),
                            (random.nextFloat() - 0.3f) * 0.08f, -0.12f - random.nextFloat() * 0.08f,
                            random.nextInt(60, 140), ForestMat.SMOKE, rises = true
                        )
                    }
                    tree.fire += 1f
                    if (tree.fire > BURN_TICKS_PER_BRUSH) {
                        tree.fire = 0f
                        tree.brush = (tree.brush - 1).coerceAtLeast(0)
                    }
                }
                val hauled = tree.sectionState.all { it == 2 }
                if (hauled && tree.limbed && tree.brush == 0) tree.stage = Stage.DONE
            }
            else -> Unit
        }
    }

    /** Bakes the fallen tree into the timber layer exactly as it lies, with a puff of dust. */
    private fun land(tree: Tree, random: Random) {
        tree.angle = HALF_PI
        forEachRotated(tree, HALF_PI) { x, y, mat ->
            val index = y * width + x
            // Trunk wins over crown where they overlap; the crown on the ground side
            // is crushed.
            if (felled[index] == 0 || ForestMat.isTrunk(mat)) {
                felled[index] = mat
                felledOwner[index] = trees.indexOf(tree)
            }
        }
        tree.stage = Stage.DOWN
        tree.sections = tree.usableFor(tree.dir)
        tree.cutDone = BooleanArray(tree.sections)
        tree.cutClaimed = BooleanArray(tree.sections)
        tree.sectionState = IntArray(tree.sections)
        repeat(12) {
            val x = tree.xAt(random.nextInt(2, tree.height))
            particles += Particle(
                x.toFloat(), footY.toFloat(), (random.nextFloat() - 0.5f) * 0.5f,
                -0.2f - random.nextFloat() * 0.3f, random.nextInt(15, 40), ForestMat.DIRT, rises = false
            )
        }
    }

    private inline fun forEachRotated(tree: Tree, angle: Float, plot: (Int, Int, Int) -> Unit) {
        val c = cos(angle)
        val s = sin(angle)
        val reach = tree.height + tree.radius + 1
        for (py in (footY - reach).coerceAtLeast(0)..footY) {
            for (px in (tree.baseX - reach).coerceAtLeast(0)..(tree.baseX + reach).coerceAtMost(width - 1)) {
                val dx = (px - tree.baseX).toFloat()
                val dy = (py - footY).toFloat()
                // Inverse rotation: where in the upright tree does this cell come from?
                val sxEff = (dx * c + dy * tree.dir * s).roundToInt()
                val sy = (dy * c - dx * tree.dir * s).roundToInt()
                // Stumps stay put: only what stood above the cut moves.
                if (sy > -LOG_START) continue
                val mat = tree.spriteAt(sxEff * tree.dir, sy)
                if (mat != 0) plot(px, py, mat)
            }
        }
    }

    private fun dangerFor(jack: Jack) = trees.firstOrNull { it.busy && jack.x in it.zone() }
    private fun inDanger(jack: Jack) = dangerFor(jack) != null

    private fun act(jack: Jack, random: Random) {
        // Anyone in the path of a tree about to go gets out of it first.
        val danger = dangerFor(jack)
        // A pair carrying is moved as a pair, in haul(), not scattered one by one.
        if (danger != null && jack.carry?.lifted != true) {
            val zone = danger.zone()
            val out = if (danger.dir > 0) zone.first - 2 else zone.last + 2
            stepToward(jack, out.coerceIn(0, width - 1))
            return
        }

        if (jack.task == Task.NONE) choose(jack, random)
        when (jack.task) {
            Task.FELL -> fell(jack, random)
            Task.LIMB -> limb(jack, random)
            Task.BUCK -> buck(jack, random)
            Task.HAUL -> haul(jack, random)
            Task.BURN -> burn(jack)
            Task.NONE -> {
                // Nothing to do: drift back toward camp and wait.
                if (jack.x > CAMP_EDGE + 2) stepToward(jack, CAMP_EDGE) else if (random.nextFloat() < 0.02f) {
                    jack.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
                }
            }
        }
    }

    /**
     * The crew divides itself: join a carry that is short a hand, then keep the lengths
     * moving, then clear crowns, then burn brush, then — if nothing is on the ground to
     * work — fell. Two trees can be down at once, if their drop zones do not overlap.
     */
    private fun choose(jack: Jack, random: Random) {
        carries.firstOrNull { it.bearers.size == 1 }?.let { carry ->
            carry.bearers += jack
            jack.carry = carry
            jack.task = Task.HAUL
            return
        }
        val down = trees.filter { it.stage == Stage.DOWN }
        // Buck a cut on a limbed log.
        for (tree in down) {
            if (!tree.limbed) continue
            val cut = (1 until tree.sections).firstOrNull { !tree.cutDone[it] && !tree.cutClaimed[it] }
            if (cut != null) {
                tree.cutClaimed[cut] = true
                jack.tree = tree
                jack.slot = cut
                jack.task = Task.BUCK
                return
            }
        }
        // Start a carry on a length that is free at both ends.
        for (tree in down) {
            if (!tree.limbed) continue
            for (i in 0 until tree.sections) {
                if (tree.sectionState[i] != 0) continue
                val startFree = i == 0 || tree.cutDone[i]
                val endFree = i == tree.sections - 1 || tree.cutDone[i + 1]
                if (startFree && endFree) {
                    tree.sectionState[i] = 1
                    val carry = Carry(tree, i, SECTION - if (i == 0) 0 else 1)
                    carry.bearers += jack
                    carries += carry
                    jack.carry = carry
                    jack.task = Task.HAUL
                    return
                }
            }
        }
        for (tree in down) {
            if (!tree.limbed && tree.limbers < MAX_LIMBERS) {
                tree.limbers++
                jack.tree = tree
                jack.task = Task.LIMB
                return
            }
        }
        for (tree in down) {
            if (tree.limbed && tree.brush > 0 && !tree.brushLit && !tree.brushClaimed) {
                tree.brushClaimed = true
                jack.tree = tree
                jack.task = Task.BURN
                return
            }
        }
        // Help with a tree already being cut.
        trees.firstOrNull { it.stage == Stage.CHOPPING && it.fellers < MAX_FELLERS }?.let { tree ->
            tree.fellers++
            jack.tree = tree
            jack.slot = tree.fellers
            jack.task = Task.FELL
            return
        }
        if (trees.any { it.stage == Stage.CHOPPING || it.busy }) return
        if (down.size >= MAX_DOWN) return
        // Pick the next tree: nearest the camp that can go down somewhere clear, and
        // fell it whichever way yields the most timber.
        val claimed = trees.filter { it.stage == Stage.DOWN }.map { it.zone() }
        val next = trees.filter { it.stage == Stage.STANDING }
            .sortedBy { it.baseX + random.nextInt(0, 12) }
            .firstNotNullOfOrNull { tree ->
                listOf(-1, 1).filter { d ->
                    tree.usableFor(d) > 0 && claimed.none { zone -> overlaps(zone, tree.zone(d)) }
                }.maxByOrNull { tree.usableFor(it) }?.let { tree to it }
            } ?: return
        val (tree, direction) = next
        tree.dir = direction
        tree.stage = Stage.CHOPPING
        tree.fellers = 1
        jack.tree = tree
        jack.slot = 1
        jack.task = Task.FELL
    }

    private fun overlaps(a: IntRange, b: IntRange) = a.first <= b.last && b.first <= a.last

    private fun fell(jack: Jack, random: Random) {
        val tree = jack.tree!!
        if (tree.stage != Stage.CHOPPING) {
            done(jack)
            return
        }
        // Fellers work from the back, out of the way of the fall.
        val spot = tree.baseX - tree.dir * (1 + jack.slot)
        if (jack.x != spot) {
            stepToward(jack, spot.coerceIn(0, width - 1))
            return
        }
        jack.swing = !jack.swing
        tree.chop += 1f
        if (random.nextFloat() < 0.6f) {
            particles += Particle(
                tree.baseX.toFloat(), footY - 2f, (random.nextFloat() - 0.5f) * 0.6f,
                -0.3f - random.nextFloat() * 0.3f, 30, ForestMat.CHIP, rises = false
            )
        }
        if (tree.chop >= FELL_WORK) {
            tree.stage = Stage.WARNING
            tree.warnTicks = 0
            jacks.filter { it.tree === tree && it.task == Task.FELL }.forEach {
                deeds.credit(it.soul, "trees felled")
                done(it)
            }
        }
    }

    private fun limb(jack: Jack, random: Random) {
        val tree = jack.tree!!
        val owner = trees.indexOf(tree)
        // The nearest bit of crown still on this log.
        var target = -1
        var best = Int.MAX_VALUE
        for (y in 0..footY) for (x in 0 until width) {
            val index = y * width + x
            if (felledOwner[index] != owner || isTimber(tree, x, y)) continue
            val distance = abs(x - jack.x) * 4 + (footY - y)
            if (distance < best) {
                best = distance
                target = index
            }
        }
        if (target < 0) {
            tree.limbed = true
            tree.limbers--
            done(jack)
            return
        }
        val tx = target % width
        if (abs(tx - jack.x) > 1) {
            stepToward(jack, tx)
            return
        }
        jack.swing = !jack.swing
        // Each stroke clears a few cells of crown within reach, top first.
        var cleared = 0
        loop@ for (y in 0..footY) for (x in (jack.x - 1)..(jack.x + 1)) {
            if (x !in 0 until width) continue
            val index = y * width + x
            if (felledOwner[index] != owner || isTimber(tree, x, y)) continue
            felled[index] = 0
            felledOwner[index] = -1
            cleared++
            if (cleared >= LIMB_PER_STROKE) break@loop
        }
        tree.brush += cleared
        deeds.credit(jack.soul, "branches cleared", cleared)
        if (random.nextFloat() < 0.3f) {
            particles += Particle(
                jack.x.toFloat(), footY - 3f, (random.nextFloat() - 0.5f) * 0.4f, -0.25f, 25,
                if (tree.pine) ForestMat.PINE else ForestMat.LEAF, rises = false
            )
        }
    }

    /** Trunk cells that will become lengths: the limbers leave these alone. */
    private fun isTimber(tree: Tree, x: Int, y: Int): Boolean {
        if (y != footY && y != footY - 1) return false
        val distance = (x - tree.baseX) * tree.dir
        return distance in LOG_START until LOG_START + tree.sections * SECTION &&
            ForestMat.isTrunk(felled[y * width + x])
    }

    private fun buck(jack: Jack, random: Random) {
        val tree = jack.tree!!
        val cut = jack.slot
        val cx = tree.xAt(tree.sectionStart(cut))
        val stand = cx - tree.dir // beside the cut, on the stump side
        if (jack.x != stand) {
            stepToward(jack, stand.coerceIn(0, width - 1))
            return
        }
        jack.swing = !jack.swing
        if (random.nextFloat() < 0.5f) {
            particles += Particle(
                cx.toFloat(), footY.toFloat(), (random.nextFloat() - 0.5f) * 0.3f, -0.1f, 20,
                ForestMat.CHIP, rises = false
            )
        }
        if (random.nextFloat() < 1f / BUCK_WORK) {
            for (y in footY - 1..footY) {
                val index = y * width + cx
                felled[index] = 0
                felledOwner[index] = -1
            }
            tree.cutDone[cut] = true
            deeds.credit(jack.soul, "cuts sawn")
            done(jack)
        }
    }

    private fun haul(jack: Jack, random: Random) {
        val carry = jack.carry!!
        val me = carry.bearers.indexOf(jack)
        if (!carry.lifted) {
            if (carry.bearers.size < 2) {
                // Waiting for a second pair of hands, at the near end.
                if (jack.x != carry.nearX) stepToward(jack, carry.nearX) else jack.swing = false
                return
            }
            val spot = if (me == 0) carry.nearX else carry.farX
            if (jack.x != spot) {
                stepToward(jack, spot)
                return
            }
            carry.ready[me] = true
            if (carry.ready.all { it }) lift(carry)
            return
        }
        // Carrying: the pair moves one step when both have taken it.
        carry.ready[me] = true
        if (!carry.ready.all { it }) return
        carry.ready.fill(false)
        val half = carry.length / 2
        val span = (carry.centre - half - 1)..(carry.centre + half + 1)
        val zone = trees.firstOrNull { it.busy && overlaps(it.zone(), span) }?.zone()
        if (zone != null) {
            // Caught in a drop zone: get out by the nearer side.
            val left = span.last - zone.first + 1
            val right = zone.last - span.first + 1
            carry.centre += if (left <= right) -1 else 1
            placeBearers(carry)
            return
        }
        if (carry.centre > PILE_DROP_X) {
            // Never carry into ground a tree has been called on.
            val ahead = (span.first - 1)..(span.first - 1)
            if (trees.any { it.busy && overlaps(it.zone(), ahead) }) return
            carry.centre--
            placeBearers(carry)
            return
        }
        // At the pile: roll it on.
        if (stacked < pileSlots.size) pile += stacked
        stacked++
        carry.tree.sectionState[carry.section] = 2
        carries.remove(carry)
        carry.bearers.forEach {
            deeds.credit(it.soul, "lengths carried")
            done(it)
            if (random.nextFloat() < REST_AFTER_CARRY) it.resting = random.nextInt(REST_MIN_TICKS, REST_MAX_TICKS)
        }
    }

    private fun placeBearers(carry: Carry) {
        carry.bearers[0].x = (carry.centre - carry.length / 2).coerceIn(0, width - 1)
        carry.bearers[1].x = (carry.centre + carry.length / 2).coerceIn(0, width - 1)
    }

    private fun lift(carry: Carry) {
        val tree = carry.tree
        val owner = trees.indexOf(tree)
        val start = tree.sectionStart(carry.section)
        for (d in start until start + SECTION) {
            val x = tree.xAt(d)
            if (x !in 0 until width) continue
            for (y in footY - 1..footY) {
                val index = y * width + x
                if (felledOwner[index] == owner && ForestMat.isTrunk(felled[index])) {
                    felled[index] = 0
                    felledOwner[index] = -1
                }
            }
        }
        carry.lifted = true
        carry.ready.fill(false)
        carry.centre = (carry.nearX + carry.farX) / 2
        // Whoever is nearer the pile takes the front.
        carry.bearers.sortBy { it.x }
    }

    private fun burn(jack: Jack) {
        val tree = jack.tree!!
        if (jack.x != tree.brushX - tree.dir * 2) {
            stepToward(jack, (tree.brushX - tree.dir * 2).coerceIn(0, width - 1))
            return
        }
        jack.swing = !jack.swing
        tree.lighting++
        if (tree.lighting >= LIGHT_WORK) {
            tree.brushLit = true
            deeds.credit(jack.soul, "brush fires lit")
            done(jack)
        }
    }

    private fun done(jack: Jack) {
        jack.task = Task.NONE
        jack.tree = null
        jack.carry = null
        jack.swing = false
    }

    private fun stepToward(jack: Jack, x: Int) {
        val next = if (jack.x < x) jack.x + 1 else if (jack.x > x) jack.x - 1 else return
        // Nobody walks into ground a tree has been called on; they wait at the edge.
        val entering = trees.any { it.busy && next in it.zone() && jack.x !in it.zone() }
        if (!entering) jack.x = next
    }

    private fun moveParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life--
            if (!p.rises) p.vy += 0.04f else p.vx += 0.002f
            p.x += p.vx
            p.y += p.vy
            if (p.life <= 0 || p.y < 0 || p.x < 0 || p.x >= width || (!p.rises && p.y > footY)) iterator.remove()
        }
    }

    private fun brushHeight(tree: Tree) = sqrt(tree.brush.toFloat() / 3f).toInt().coerceAtMost(6)

    // --- Rendering ------------------------------------------------------------------

    override fun renderInto(buffer: IntArray) {
        cells.copyInto(buffer)
        fun plot(x: Int, y: Int, mat: Int) {
            if (x in 0 until width && y in 0 until height && mat != 0) buffer[y * width + x] = mat
        }

        trees.forEach { tree ->
            // Stump.
            plot(tree.baseX, footY, ForestMat.TRUNK_DARK)
            plot(tree.baseX - 1, footY, ForestMat.TRUNK_DARK)
            when (tree.stage) {
                Stage.STANDING, Stage.CHOPPING, Stage.WARNING -> {
                    for (sy in -tree.height..0) for (sx in -tree.radius..tree.radius) {
                        plot(tree.baseX + sx, footY + sy, tree.spriteAt(sx, sy))
                    }
                    // The notch opens on the side it will fall.
                    if (tree.stage != Stage.STANDING && tree.chop > FELL_WORK * 0.3f) {
                        plot(if (tree.dir > 0) tree.baseX else tree.baseX - 1, footY - 1, ForestMat.SKY)
                    }
                }
                Stage.FALLING -> {
                    plot(tree.baseX, footY - 1, ForestMat.STUMP)
                    plot(tree.baseX - 1, footY - 1, ForestMat.STUMP)
                    forEachRotated(tree, tree.angle) { x, y, mat -> plot(x, y, mat) }
                }
                Stage.DOWN, Stage.DONE -> {
                    plot(tree.baseX, footY - 1, ForestMat.STUMP)
                    plot(tree.baseX - 1, footY - 1, ForestMat.STUMP)
                }
            }
        }

        for (i in felled.indices) if (felled[i] != 0) buffer[i] = felled[i]

        // Brush piles, and fire on the ones that are lit.
        trees.forEach { tree ->
            if (tree.stage != Stage.DOWN || tree.brush == 0 || !tree.limbed && tree.brush < 3) return@forEach
            val h = brushHeight(tree)
            for (layer in 0..h) {
                val half = h - layer + 1
                for (dx in -half..half) {
                    val x = tree.brushX + dx
                    val y = footY - layer
                    val flame = tree.brushLit && layer >= h - 1 && ((x * 7 + tick / 4) % 3 != 0)
                    plot(x, y, if (flame) (if ((x + tick / 3) % 2 == 0) ForestMat.FIRE_HOT else ForestMat.FIRE) else ForestMat.BRUSH)
                }
            }
            if (tree.brushLit) plot(tree.brushX + ((tick / 5) % 3) - 1, footY - h - 1, ForestMat.FIRE)
        }

        pile.forEach { slot ->
            val (x, y) = pileSlots[slot]
            plot(x, y, ForestMat.LOG_END)
            plot(x + 1, y, ForestMat.LOG_END_DARK)
            plot(x, y - 1, ForestMat.LOG_END_DARK)
            plot(x + 1, y - 1, ForestMat.LOG_END)
        }

        particles.forEach { plot(it.x.toInt(), it.y.toInt(), it.mat) }

        carries.filter { it.lifted }.forEach { carry ->
            val left = carry.centre - carry.length / 2
            for (x in left until left + carry.length) plot(x, footY - 3, ForestMat.TRUNK)
        }

        jacks.forEach { jack ->
            val sitting = jack.resting > 0
            val top = if (sitting) footY - 1 else footY - 2
            plot(jack.x, top - 1, ForestMat.SKIN)
            plot(jack.x, top, ForestMat.SHIRT)
            if (!sitting) plot(jack.x, footY, ForestMat.TROUSERS)
            if (jack.task in WORKING && jack.swing) {
                val side = jack.tree?.let { if (it.baseX >= jack.x) 1 else -1 } ?: 1
                plot(jack.x + side, top - 1, ForestMat.TOOL)
            } else if (jack.task in WORKING) {
                val side = jack.tree?.let { if (it.baseX >= jack.x) 1 else -1 } ?: 1
                plot(jack.x + side, top, ForestMat.TOOL)
            }
        }
    }

    companion object {
        /**
         * Lengths to stack for a timer this long. A tree is a slow job — the crew brings
         * down and clears one in several minutes — so short timers ask for a tree's worth
         * and long ones for most of the stand.
         */
        fun quotaFor(durationMinutes: Float): Int =
            (durationMinutes * LENGTHS_PER_MINUTE).roundToInt().coerceIn(4, 40)

        private const val LENGTHS_PER_MINUTE = 0.8f

        private const val GROUND = 0.86f
        private const val CAMP_EDGE = 13
        private const val PILE_DROP_X = 16
        private const val MIN_TREE = 22
        private const val MAX_TREE = 44
        private const val CREW = 5

        private const val LOG_START = 2
        private const val SECTION = 7

        private const val RATE = 0.08f
        private const val MAX_ACTIONS_PER_TICK = 4
        private const val SLACK_PACE = 0.5f
        private const val REST_MIN_TICKS = 45
        private const val REST_MAX_TICKS = 160
        private const val MEAN_REST_TICKS = (REST_MIN_TICKS + REST_MAX_TICKS) / 2f
        private const val REST_AFTER_CARRY = 0.25f

        private const val FELL_WORK = 70f
        private const val MAX_FELLERS = 2
        private const val MAX_LIMBERS = 2
        private const val MAX_DOWN = 2
        private const val LIMB_PER_STROKE = 3
        private const val BUCK_WORK = 22
        private const val LIGHT_WORK = 6
        private const val BURN_TICKS_PER_BRUSH = 3f

        private const val WARN_MIN = 30
        private const val FALL_KICK = 0.0004f
        private const val FALL_GRAVITY = 0.0035f
        private const val HALF_PI = (PI / 2).toFloat()

        private val WORKING = setOf(Task.FELL, Task.LIMB, Task.BUCK, Task.BURN)
    }
}
