package com.hourglass.core.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ForestWorldTest {

    private fun world(seed: Long, minutes: Float) =
        ForestWorld(72, 96, seed, ForestWorld.quotaFor(minutes))

    private fun ticks(minutes: Float) = (minutes * 60 * 30).toInt()

    private fun runTimer(world: ForestWorld, ticks: Int, check: (ForestWorld) -> Unit = {}): Float {
        val random = Random(29)
        val pacer = WorldPacer()
        repeat(ticks) { index ->
            world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
            check(world)
        }
        return world.objectiveProgress
    }

    @Test
    fun `the crew fells trees and stacks the timber`() {
        val world = world(3L, 15f)
        runTimer(world, ticks(15f))
        assertTrue("no trees came down", world.treesDown > 0)
        assertTrue("nothing was stacked", world.lengthsStacked > 0)
    }

    @Test
    fun `nobody is standing where a tree comes down`() {
        val world = world(5L, 15f)
        var hit = false
        runTimer(world, ticks(15f)) { if (it.anyoneUnderFallingTree()) hit = true }
        assertFalse("someone was under a falling tree", hit)
    }

    @Test
    fun `timers of different lengths all land near the target`() {
        val target = WorldPacer().completionTarget
        val failures = listOf(5f, 12f, 25f).flatMap { minutes ->
            (1L..4L).mapNotNull { seed ->
                val landed = runTimer(world(seed, minutes), ticks(minutes))
                if (abs(landed - target) > 0.18f) "${minutes}m seed $seed landed at $landed" else null
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun `the same seed grows the same stand`() {
        assertTrue(world(42L, 10f).cells.contentEquals(world(42L, 10f).cells))
    }
}
