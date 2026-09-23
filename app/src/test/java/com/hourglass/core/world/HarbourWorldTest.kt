package com.hourglass.core.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class HarbourWorldTest {

    private fun world(seed: Long, minutes: Float) =
        HarbourWorld(72, 96, seed, HarbourWorld.quotaFor(minutes))

    private fun ticks(minutes: Float) = (minutes * 60 * 30).toInt()

    private fun runTimer(world: HarbourWorld, ticks: Int, check: (HarbourWorld) -> Unit = {}): Float {
        val random = Random(37)
        val pacer = world.newPacer()
        repeat(ticks) { index ->
            world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
            check(world)
        }
        return world.objectiveProgress
    }

    @Test
    fun `ships come and go and the warehouse fills`() {
        val world = world(3L, 15f)
        var previous = 0f
        var backwards = false
        runTimer(world, ticks(15f)) {
            if (it.objectiveProgress < previous) backwards = true
            previous = it.objectiveProgress
        }
        assertFalse("progress went backwards", backwards)
        assertTrue("no ship was ever unloaded", world.shipsServed >= 1)
        assertTrue("nothing was stored", world.loadsStored > 0)
    }

    @Test
    fun `timers of different lengths all land near the target`() {
        val target = WorldPacer().completionTarget
        val failures = listOf(5f, 12f, 25f).flatMap { minutes ->
            (1L..3L).mapNotNull { seed ->
                val landed = runTimer(world(seed, minutes), ticks(minutes))
                if (abs(landed - target) > 0.15f) "${minutes}m seed $seed landed at $landed" else null
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
