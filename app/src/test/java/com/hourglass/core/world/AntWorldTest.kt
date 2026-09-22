package com.hourglass.core.world

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class AntWorldTest {

    private fun world(seed: Long, minutes: Float) =
        AntWorld(72, 96, seed, AntWorld.quotaFor(minutes))

    private fun ticks(minutes: Float) = (minutes * 60 * 30).toInt()

    private fun runTimer(world: AntWorld, ticks: Int, check: (AntWorld) -> Unit = {}): Float {
        val random = Random(17)
        val pacer = WorldPacer()
        repeat(ticks) { index ->
            world.step(pacer.effortFor((index + 1f) / ticks, world.objectiveProgress), random)
            check(world)
        }
        return world.objectiveProgress
    }

    @Test
    fun `the colony brings food home and grows`() {
        val world = world(3L, 10f)
        val before = world.antCount
        runTimer(world, ticks(10f))
        assertTrue("nothing stored", world.storedLoads > 0)
        assertTrue("colony should grow from $before, is ${world.antCount}", world.antCount > before)
    }

    @Test
    fun `a short timer and a long timer both land near the target`() {
        val target = WorldPacer().completionTarget
        val quick = runTimer(world(11L, 5f), ticks(5f))
        val slow = runTimer(world(11L, 20f), ticks(20f))
        assertTrue("quick run landed at $quick", abs(quick - target) < 0.12f)
        assertTrue("slow run landed at $slow", abs(slow - target) < 0.12f)
    }

    @Test
    fun `every seed lands near the target`() {
        val target = WorldPacer().completionTarget
        val failures = (1L..8L).mapNotNull { seed ->
            val landed = runTimer(world(seed, 10f), ticks(10f))
            if (abs(landed - target) > 0.12f) "seed $seed landed at $landed" else null
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun `ants go round pebbles and grass, never through`() {
        val world = world(5L, 5f)
        var trespass = false
        var previous = 0f
        runTimer(world, ticks(5f)) {
            if (it.anyAntInsideObstacle()) trespass = true
            assertTrue("progress went backwards", it.objectiveProgress >= previous)
            previous = it.objectiveProgress
        }
        assertFalse("an ant walked through an obstacle", trespass)
    }

    @Test
    fun `the same seed builds the same world`() {
        assertTrue(world(42L, 10f).cells.contentEquals(world(42L, 10f).cells))
        assertFalse(world(42L, 10f).cells.contentEquals(world(43L, 10f).cells))
    }
}
