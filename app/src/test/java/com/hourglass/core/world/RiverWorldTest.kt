package com.hourglass.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class RiverWorldTest {

    private fun world(seed: Long = 7L) = RiverWorld(48, 64, seed, richness = 90)

    private fun run(world: RiverWorld, ticks: Int, overtime: Int = 0, pacer: WorldPacer = WorldPacer()): Float {
        val random = Random(world.hashCode().toLong())
        repeat(ticks + overtime) { index ->
            world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
        }
        return world.objectiveProgress
    }

    @Test
    fun `there is a lake, a jam and a crew`() {
        val world = world()
        assertTrue("no water", world.waterCount > 50)
        assertTrue("no jam", world.jamTotal > 20)
        assertTrue("no beavers", world.beaverCount >= 2)
    }

    @Test
    fun `an intact jam holds the lake back`() {
        // With no beavers working (effort pinned to nothing), the spring keeps the lake
        // level and not a drop gets past.
        val world = world()
        val idle = WorldPacer(gain = 0f, minEffort = 0.005f, maxEffort = 0.005f, learningRate = 0f)
        val random = Random(1)
        repeat(1500) { world.step(idle.effortFor(0f, 0f), random) }
        assertEquals("water escaped past an intact jam", 0, world.outflow)
    }

    @Test
    fun `the jam comes down from the top`() {
        val world = world(seed = 5L)
        val topBefore = jamTop(world)
        val bottomRow = jamBottom(world)
        val bottomBefore = jamCellsInRow(world, bottomRow)

        // A third of the way through a timer: well under way, far from finished.
        val pacer = WorldPacer()
        val random = Random(5)
        val ticks = 6000
        repeat(ticks / 3) { index ->
            world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
        }

        assertTrue("the top should have come down", jamTop(world) > topBefore)
        assertEquals(
            "the bottom row should be untouched while the top is being worked",
            bottomBefore,
            jamCellsInRow(world, bottomRow)
        )
    }

    @Test
    fun `the river keeps flowing once the jam is open`() {
        val world = world(seed = 4L)
        run(world, ticks = 6000)
        assertTrue("nothing ran off downstream", world.outflow > 0)
    }

    private fun jamTop(world: RiverWorld): Int {
        for (y in 0 until world.height) for (x in 0 until world.width) {
            if (RiverMat.isJam(world.cells[y * world.width + x])) return y
        }
        return world.height
    }

    private fun jamBottom(world: RiverWorld): Int {
        for (y in world.height - 1 downTo 0) for (x in 0 until world.width) {
            if (RiverMat.isJam(world.cells[y * world.width + x])) return y
        }
        return 0
    }

    private fun jamCellsInRow(world: RiverWorld, y: Int): Int =
        (0 until world.width).count { RiverMat.isJam(world.cells[y * world.width + it]) }

    @Test
    fun `beavers haul wood from the jam`() {
        val world = world()
        run(world, ticks = 3000)
        assertTrue("nothing hauled", world.jamHauled > 0)
    }

    @Test
    fun `once the jam is open the water reaches the dry side`() {
        val world = world(seed = 4L)
        run(world, ticks = 6000)
        // Water right of the jam means it found a way through.
        val right = (world.width * 0.6f).toInt()
        var downstream = 0
        for (y in 0 until world.height) {
            for (x in right until world.width) {
                if (world.cells[y * world.width + x] == RiverMat.WATER) downstream++
            }
        }
        assertTrue("no water got through", downstream > 0)
    }

    @Test
    fun `grass the water reaches turns green`() {
        val world = world(seed = 4L)
        val greenBefore = world.cells.count { it == RiverMat.GRASS }
        run(world, ticks = 6000)
        val greenAfter = world.cells.count { it == RiverMat.GRASS }
        assertEquals(0, greenBefore)
        assertTrue("nothing greened", greenAfter > 0)
    }

    @Test
    fun `every seed lands near the target`() {
        val target = WorldPacer().completionTarget
        val failures = mutableListOf<String>()
        (1L..16L).forEach { seed ->
            val landed = run(world(seed), ticks = 6000)
            if (abs(landed - target) > 0.2f) failures += "seed $seed landed at $landed"
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun `a short and a long timer agree`() {
        val quick = run(world(seed = 9L), ticks = 4000)
        val slow = run(world(seed = 9L), ticks = 24000)
        assertTrue("quick $quick slow $slow", abs(quick - slow) < 0.15f)
    }

    @Test
    fun `overtime buys more of the jam`() {
        val atEnd = run(world(seed = 12L), ticks = 5000)
        val after = run(world(seed = 12L), ticks = 5000, overtime = 5000)
        assertTrue("overtime added nothing: $atEnd then $after", after > atEnd)
    }
}
