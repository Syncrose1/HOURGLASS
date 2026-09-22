package com.hourglass.core.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BattleWorldTest {

    private fun world(seed: Long, minutes: Float) =
        BattleWorld(72, 96, seed, BattleWorld.pointsFor(minutes))

    private fun ticks(minutes: Float) = (minutes * 60 * 30).toInt()

    private fun runTimer(world: BattleWorld, ticks: Int, check: (BattleWorld) -> Unit = {}): Float {
        val random = Random(31)
        val pacer = world.newPacer()
        repeat(ticks) { index ->
            world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
            check(world)
        }
        return world.objectiveProgress
    }

    @Test
    fun `the line moves both ways, and the campaign score only climbs`() {
        val world = world(3L, 15f)
        var previous = 0f
        var lowest = 1f
        var highest = 0f
        var backwards = false
        var tick = 0
        runTimer(world, ticks(15f)) {
            if (it.objectiveProgress < previous) backwards = true
            previous = it.objectiveProgress
            // After the opening rush, the front should surge and give way.
            if (++tick > ticks(3f)) {
                lowest = minOf(lowest, it.holding)
                highest = maxOf(highest, it.holding)
            }
        }
        assertFalse("score went backwards", backwards)
        assertTrue("the front never moved: $lowest..$highest", highest - lowest > 0.2f)
    }

    @Test
    fun `routed troops come back`() {
        val world = world(5L, 10f)
        var sawRouted = false
        runTimer(world, ticks(6f)) { if (it.routedCount > 0) sawRouted = true }
        assertTrue("nobody was ever routed", sawRouted)
        // Nobody is lost for good: every soldier is either in the field or regrouping.
        assertTrue(world.strength(0) + world.strength(1) + world.routedCount == 60)
    }

    @Test
    fun `campaigns of different lengths land near the target`() {
        val target = WorldPacer().completionTarget
        val failures = listOf(10f, 20f).flatMap { minutes ->
            (1L..3L).mapNotNull { seed ->
                val landed = runTimer(world(seed, minutes), ticks(minutes))
                if (abs(landed - target) > 0.15f) "${minutes}m seed $seed landed at $landed" else null
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
