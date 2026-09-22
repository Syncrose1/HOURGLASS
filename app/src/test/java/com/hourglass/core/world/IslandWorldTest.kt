package com.hourglass.core.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class IslandWorldTest {

    private fun world(seed: Long, minutes: Float) =
        IslandWorld(72, 96, seed, IslandWorld.quotaFor(minutes))

    private fun ticks(minutes: Float) = (minutes * 60 * 30).toInt()

    private fun runTimer(world: IslandWorld, ticks: Int, check: (IslandWorld) -> Unit = {}): Float {
        val random = Random(23)
        val pacer = WorldPacer()
        repeat(ticks) { index ->
            world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
            check(world)
        }
        return world.objectiveProgress
    }

    @Test
    fun `the island breaks the surface by the end of the timer`() {
        val world = world(3L, 5f)
        assertTrue("no land at the start", world.landAboveSea == 0)
        runTimer(world, ticks(5f))
        assertTrue("island should show above the sea, had ${world.landAboveSea}", world.landAboveSea > 40)
    }

    @Test
    fun `a short timer and a long timer both land near the target`() {
        val target = WorldPacer().completionTarget
        val quick = runTimer(world(11L, 5f), ticks(5f))
        val slow = runTimer(world(11L, 20f), ticks(20f))
        assertTrue("quick run landed at $quick", abs(quick - target) < 0.1f)
        assertTrue("slow run landed at $slow", abs(slow - target) < 0.1f)
    }

    @Test
    fun `every seed lands near the target, and progress never runs backwards`() {
        val target = WorldPacer().completionTarget
        val failures = (1L..6L).mapNotNull { seed ->
            var previous = 0f
            var backwards = false
            val landed = runTimer(world(seed, 10f), ticks(10f)) {
                if (it.objectiveProgress < previous) backwards = true
                previous = it.objectiveProgress
            }
            when {
                backwards -> "seed $seed went backwards"
                abs(landed - target) > 0.1f -> "seed $seed landed at $landed"
                else -> null
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun `the same seed builds the same sea`() {
        assertTrue(world(42L, 10f).cells.contentEquals(world(42L, 10f).cells))
        assertFalse(world(42L, 10f).cells.contentEquals(world(43L, 10f).cells))
    }
}
