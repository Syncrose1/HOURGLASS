package com.hourglass.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MineWorldTest {

    private fun world(seed: Long = 7L, w: Int = 48, h: Int = 64) = MineWorld(w, h, seed)

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
        val column = world.width / 2
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

        runTimer(world, ticks = 4000)
        assertTrue("nothing was hauled", world.hauledSeams > 0)
    }

    @Test
    fun `a short timer and a long timer both land near the target`() {
        val target = WorldPacer().completionTarget

        val quick = runTimer(world(seed = 11L), ticks = 2500)
        val slow = runTimer(world(seed = 11L), ticks = 30000)

        assertTrue("quick run landed at $quick", abs(quick - target) < 0.25f)
        assertTrue("slow run landed at $slow", abs(slow - target) < 0.25f)
        // The point of the pacer: wildly different tick budgets, same finish.
        assertTrue(
            "quick $quick and slow $slow should agree",
            abs(quick - slow) < 0.15f
        )
    }

    @Test
    fun `without pacing the finish depends entirely on how long you waited`() {
        // The control for the test above: at a fixed effort the world has no idea a
        // clock exists, and a short run and a long run end nowhere near each other.
        val flat = WorldPacer(gain = 0f, minEffort = 1f, maxEffort = 1f, learningRate = 0f)
        val quick = runTimer(world(seed = 11L), ticks = 2500, pacer = flat)
        val slow = runTimer(world(seed = 11L), ticks = 30000, pacer = flat)

        assertTrue(
            "unpaced runs should diverge, got $quick and $slow",
            abs(quick - slow) > 0.2f
        )
    }

    @Test
    fun `overtime buys the rest of the haul`() {
        val world = world(seed = 21L)
        val atEnd = runTimer(world, ticks = 4000)
        val afterOvertime = runTimer(world(seed = 21L), ticks = 4000, overtimeTicks = 4000)

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
        repeat(3000) { index ->
            world.step(WorldPacer().effortFor((index + 1) / 3000f, world.objectiveProgress), random)
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
        runTimer(world, ticks = 1500)
        val openAfter = world.cells.count { Mat.isOpen(it) }

        assertTrue("tunnels should have appeared", openAfter > openBefore)
        assertEquals("the crew should all still be here", world.minerCount, world.minerCount)
    }

    @Test
    fun `sand slumps into tunnels but rock holds`() {
        val world = world(seed = 13L)
        runTimer(world, ticks = 2000)

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
        (1L..24L).forEach { seed ->
            val world = MineWorld(48, 64, seed)
            val random = Random(seed * 31)
            val ticks = 5000
            val pacer = WorldPacer()
            repeat(ticks) { index ->
                world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
            }
            val landed = world.objectiveProgress
            if (abs(landed - target) > 0.2f) failures += "seed $seed landed at $landed"
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
