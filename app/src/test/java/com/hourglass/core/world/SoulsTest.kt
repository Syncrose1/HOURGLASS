package com.hourglass.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SoulsTest {

    @Test
    fun `up to ten places everyone is different, past ten the names come round again`() {
        val few = Souls.cast(6, Random(1))
        assertEquals(6, few.distinct().size)
        val many = Souls.cast(30, Random(2))
        assertEquals(10, many.take(10).distinct().size)
        assertTrue(many.all { it in 0 until Souls.count })
    }

    @Test
    fun `every world with people in it credits the souls on shift`() {
        val worlds: List<World> = listOf(
            MineWorld(72, 96, 3L, MineWorld.richnessFor(10f)),
            RiverWorld(72, 96, 3L, 150),
            AntWorld(72, 96, 3L, AntWorld.quotaFor(10f)),
            ForestWorld(72, 96, 3L, ForestWorld.quotaFor(10f)),
            BattleWorld(72, 96, 3L, BattleWorld.pointsFor(10f)),
            HarbourWorld(72, 96, 3L, HarbourWorld.quotaFor(10f)),
            SiegeWorld(72, 96, 3L, SiegeWorld.quotaFor(10f))
        )
        worlds.forEach { world ->
            val pacer = world.newPacer()
            val random = Random(5)
            val ticks = 10 * 60 * 30
            repeat(ticks) { world.step(pacer.effortFor((it + 1f) / ticks, world.objectiveProgress), random) }
            val deeds = world.deeds!!.collect()
            assertTrue("${world.kind} credited nobody", deeds.isNotEmpty())
            assertTrue("${world.kind} credited someone not on shift", deeds.all { it.soul in world.shift })
            println("${world.kind}: " + deeds.groupBy { it.soul }.entries.take(3).joinToString(" | ") { (soul, list) ->
                Souls.name(soul) + " the " + list.first().role + ": " + list.joinToString { "${it.count} ${it.deed}" }
            })
            assertTrue("collecting should clear the page", world.deeds!!.collect().isEmpty())
        }
        assertEquals(null, IslandWorld(72, 96, 3L).deeds)
    }
}
