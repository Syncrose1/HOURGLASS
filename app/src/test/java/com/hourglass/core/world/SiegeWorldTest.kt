package com.hourglass.core.world

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SiegeWorldTest {

    private fun world(seed: Long, minutes: Float) =
        SiegeWorld(72, 96, seed, SiegeWorld.quotaFor(minutes))

    private fun ticks(minutes: Float) = (minutes * 60 * 30).toInt()

    private fun runTimer(world: SiegeWorld, ticks: Int, check: (SiegeWorld) -> Unit = {}): Float {
        val random = Random(41)
        val pacer = world.newPacer()
        repeat(ticks) { index ->
            world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
            check(world)
        }
        return world.objectiveProgress
    }

    @Test
    fun `the walls go up, and the trebuchet knocks some of them down`() {
        val world = world(3L, 15f)
        var peak = 0
        var setback = false
        runTimer(world, ticks(15f)) {
            if (it.blocksStanding < peak) setback = true
            peak = maxOf(peak, it.blocksStanding)
        }
        assertTrue("nothing was built", world.blocksStanding > 50)
        assertTrue("the trebuchet never hit anything", world.hits > 0)
        assertTrue("the wall never lost a block", setback)
    }

    @Test
    fun `timers of different lengths all land near the target`() {
        val target = WorldPacer().completionTarget
        val failures = listOf(5f, 12f, 25f).flatMap { minutes ->
            (1L..3L).mapNotNull { seed ->
                val landed = runTimer(world(seed, minutes), ticks(minutes))
                if (abs(landed - target) > 0.12f) "${minutes}m seed $seed landed at $landed" else null
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
