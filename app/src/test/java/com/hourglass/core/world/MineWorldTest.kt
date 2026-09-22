package com.hourglass.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MineWorldTest {

    /** The world the app builds for a timer of this many minutes. */
    private fun world(seed: Long = 7L, minutes: Float = 10f) =
        MineWorld(72, 96, seed, MineWorld.richnessFor(minutes))

    private fun ticks(minutes: Float) = (minutes * 60 * 30).toInt()

    /** Runs a whole timer's worth of ticks, pacing the world as the app would. */
    private fun runTimer(
        world: MineWorld,
        ticks: Int,
        pacer: WorldPacer = WorldPacer(),
        overtimeTicks: Int = 0
    ): Float {
        val random = Random(99)
        repeat(ticks) { index ->
            val progress = (index + 1).toFloat() / ticks
            world.step(pacer.effortFor(progress, world.objectiveProgress), random)
        }
        repeat(overtimeTicks) { index ->
            val progress = 1f + (index + 1).toFloat() / ticks
            world.step(pacer.effortFor(progress, world.objectiveProgress), random)
        }
        return world.objectiveProgress
    }

    @Test
    fun `the ground is layered, with sky above it`() {
        val world = world()
        // Away from the headframe, which sits on its own sandstone pad.
        val column = 2
        val stack = (0 until world.height).map { world.cells[it * world.width + column] }

        assertTrue("should start in sky", stack.first() == Mat.SKY)
        assertTrue("should end in ground", !Mat.isOpen(stack.last()))

        val firstSand = stack.indexOfFirst { it == Mat.SAND || it == Mat.SAND_DARK }
        val firstStone = stack.indexOfFirst { it == Mat.SANDSTONE || it == Mat.SANDSTONE_DARK }
        val firstRock = stack.indexOfFirst { it == Mat.ROCK || it == Mat.ROCK_DARK }

        assertTrue("sand above sandstone", firstSand in 0 until firstStone)
        assertTrue("sandstone above rock", firstStone in 0 until firstRock)
    }

    @Test
    fun `seams get richer with depth`() {
        val world = world(seed = 3L)
        val surface = (world.height * 0.18f).toInt()
        val floor = world.height

        var shallow = 0
        var deep = 0
        val midpoint = (surface + floor) / 2
        for (y in surface until floor) {
            for (x in 0 until world.width) {
                if (!Mat.isMineral(world.cells[y * world.width + x])) continue
                if (y < midpoint) shallow++ else deep++
            }
        }
        assertTrue("found no seams at all", shallow + deep > 0)
        assertTrue("deep ($deep) should beat shallow ($shallow)", deep > shallow)
    }

    @Test
    fun `the crew actually hauls seams back`() {
        val world = world()
        assertEquals(0, world.hauledSeams)
        assertTrue("world should have seams", world.totalSeams > 0)
        assertTrue("world should have miners", world.minerCount > 0)

        runTimer(world, ticks = ticks(5f))
        assertTrue("nothing was hauled", world.hauledSeams > 0)
    }

    @Test
    fun `a short timer and a long timer both land near the target`() {
        val target = WorldPacer().completionTarget

        // A five-minute and a twenty-minute timer, each with the world the app would
        // build for it.
        val quick = runTimer(world(seed = 11L, minutes = 5f), ticks = ticks(5f))
        val slow = runTimer(world(seed = 11L, minutes = 20f), ticks = ticks(20f))

        assertTrue("quick run landed at $quick", abs(quick - target) < 0.2f)
        assertTrue("slow run landed at $slow", abs(slow - target) < 0.2f)
        assertTrue("quick $quick and slow $slow should agree", abs(quick - slow) < 0.2f)
    }

    @Test
    fun `without pacing the finish depends entirely on how long you waited`() {
        // The control for the test above: at a fixed effort the world has no idea a
        // clock exists, and a short run and a long run end nowhere near each other.
        val flat = WorldPacer(gain = 0f, minEffort = 1f, maxEffort = 1f, learningRate = 0f)
        val quick = runTimer(world(seed = 11L, minutes = 20f), ticks = ticks(5f), pacer = flat)
        val slow = runTimer(world(seed = 11L, minutes = 20f), ticks = ticks(20f), pacer = flat)

        assertTrue(
            "unpaced runs should diverge, got $quick and $slow",
            abs(quick - slow) > 0.2f
        )
    }

    @Test
    fun `overtime buys the rest of the haul`() {
        val world = world(seed = 21L)
        val atEnd = runTimer(world, ticks = ticks(8f))
        val afterOvertime = runTimer(world(seed = 21L), ticks = ticks(8f), overtimeTicks = ticks(6f))

        assertTrue(
            "overtime should add to $atEnd, ended at $afterOvertime",
            afterOvertime > atEnd
        )
        assertTrue("and never exceed the seam count", afterOvertime <= 1f)
    }

    @Test
    fun `progress never runs backwards`() {
        val world = world(seed = 5L)
        val random = Random(1)
        var previous = 0f
        val pacer = WorldPacer()
        val total = ticks(4f)
        repeat(total) { index ->
            world.step(pacer.effortFor((index + 1f) / total, world.objectiveProgress), random)
            assertTrue("went backwards at $index", world.objectiveProgress >= previous)
            previous = world.objectiveProgress
        }
    }

    @Test
    fun `the same seed builds the same world and a different one does not`() {
        val a = world(seed = 42L)
        val b = world(seed = 42L)
        val c = world(seed = 43L)

        assertTrue("same seed should match", a.cells.contentEquals(b.cells))
        assertNotEquals(
            "different seeds should differ",
            a.cells.toList(),
            c.cells.toList()
        )
    }

    @Test
    fun `digging opens the ground up rather than destroying miners`() {
        val world = world(seed = 8L)
        val openBefore = world.cells.count { Mat.isOpen(it) }
        runTimer(world, ticks = ticks(4f))
        val openAfter = world.cells.count { Mat.isOpen(it) }

        assertTrue("tunnels should have appeared", openAfter > openBefore)
        assertEquals("the crew should all still be here", world.minerCount, world.minerCount)
    }

    @Test
    fun `sand slumps into tunnels but rock holds`() {
        val world = world(seed = 13L)
        runTimer(world, ticks = ticks(4f))

        // No loose grain should be left hanging over open space once things settle.
        var floating = 0
        for (y in 0 until world.height - 1) {
            for (x in 0 until world.width) {
                val here = world.cells[y * world.width + x]
                val below = world.cells[(y + 1) * world.width + x]
                if (Mat.isLoose(here) && below == Mat.AIR) floating++
            }
        }
        assertTrue("loose sand left hanging in $floating places", floating < 12)
    }
}

class MineWorldSweepTest {

    /**
     * World-specific failures only show up across seeds: a cart left floating, a crew
     * stranded on a ledge. Every seed has to deliver, not just the ones a test happened
     * to pick.
     */
    @Test
    fun `every seed hauls and lands near the target`() {
        val target = WorldPacer().completionTarget
        val failures = mutableListOf<String>()
        (1L..10L).forEach { seed ->
            val world = MineWorld(72, 96, seed, MineWorld.richnessFor(10f))
            val random = Random(seed * 31)
            val ticks = 10 * 60 * 30
            val pacer = WorldPacer()
            repeat(ticks) { index ->
                world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
            }
            val landed = world.objectiveProgress
            if (abs(landed - target) > 0.15f) failures += "seed $seed landed at $landed"
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}

class MineImperfectionTest {

    @Test
    fun `bedrock is never dug`() {
        val world = MineWorld(72, 96, 3L, MineWorld.richnessFor(10f))
        val before = world.cells.count { Mat.isBoulder(it) }
        assertTrue("world should have boulders", before > 0)
        val pacer = WorldPacer()
        val random = Random(3)
        val ticks = 10 * 60 * 30
        repeat(ticks) { world.step(pacer.effortFor((it + 1f) / ticks, world.objectiveProgress), random) }
        assertEquals(before, world.cells.count { Mat.isBoulder(it) })
    }

    @Test
    fun `the crew does not know where deep seams are until it goes looking`() {
        val world = MineWorld(72, 96, 5L, MineWorld.richnessFor(10f))
        val deepSeam = (world.cells.indices).last { Mat.isMineral(world.cells[it]) }
        assertTrue("a deep seam should start unseen", !world.crewKnows(deepSeam))
    }

    @Test
    fun `prospecting leaves workings that lead nowhere`() {
        // A crew with perfect knowledge digs only what it needs. One that prospects
        // opens far more ground than the seams alone account for.
        val world = MineWorld(72, 96, 9L, MineWorld.richnessFor(10f))
        val openBefore = world.cells.count { it == Mat.AIR }
        val pacer = WorldPacer()
        val random = Random(9)
        val ticks = 10 * 60 * 30
        repeat(ticks) { world.step(pacer.effortFor((it + 1f) / ticks, world.objectiveProgress), random) }
        val dug = world.cells.count { it == Mat.AIR } - openBefore
        assertTrue("dug $dug cells for ${world.hauledSeams} loads", dug > world.hauledSeams * 4)
    }
}
