package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HourglassSimTest {

    private fun seeded() = Random(99_887)

    private fun sim(width: Int = 21, height: Int = 33) =
        HourglassSim(SandGrid(width, height), neckHalfWidth = 1)

    @Test
    fun `the vessel has a waist and sealed ends`() {
        val sim = sim()
        val grid = sim.grid
        // The neck row is almost entirely wall.
        val openAtNeck = (0 until grid.width).count { grid.isEmpty(it, sim.neckRow) }
        assertTrue("neck should be narrow, was $openAtNeck", openAtNeck <= 3)
        // A row near the top is much wider.
        val openHigh = (0 until grid.width).count { grid.isEmpty(it, 2) }
        assertTrue("chamber should be wide, was $openHigh", openHigh > openAtNeck)
        // Sealed top and bottom.
        assertTrue((0 until grid.width).all { grid.isWall(it, 0) })
        assertTrue((0 until grid.width).all { grid.isWall(it, grid.height - 1) })
    }

    @Test
    fun `filling loads the upper chamber only`() {
        val sim = sim()
        sim.fill(1..1)
        assertEquals(sim.capacity, sim.grid.grainCount)
        assertEquals(0, sim.fallenCount())
    }

    @Test
    fun `progress drives how much has fallen`() {
        val sim = sim()
        val random = seeded()
        sim.fill(1..1)
        val total = sim.grid.grainCount

        // Run to the halfway mark, giving the automaton frames to settle between syncs.
        repeat(400) {
            sim.syncTo(0.5f, random = random)
            sim.step(random = random)
        }

        assertEquals("grains are conserved", total, sim.grid.grainCount)
        val fallen = sim.fallenCount()
        assertTrue("about half should have fallen, was $fallen of $total", fallen > total / 4)
        assertTrue("but not all of it", fallen < total)
    }

    @Test
    fun `a full run drains the chamber`() {
        val sim = sim()
        val random = seeded()
        sim.fill(1..1)
        val total = sim.grid.grainCount

        repeat(2000) {
            sim.syncTo(1f, random = random)
            sim.step(random = random)
        }

        assertEquals(total, sim.grid.grainCount)
        assertTrue("upper chamber should be empty", sim.fallenCount() > total * 0.9)
    }

    @Test
    fun `a single sync never dumps the whole chamber`() {
        val sim = sim()
        sim.fill(1..1)
        val released = sim.syncTo(1f, maxPerCall = 5, random = seeded())
        assertTrue("released $released", released <= 5)
    }

    @Test
    fun `progress running backwards releases nothing`() {
        val sim = sim()
        val random = seeded()
        sim.fill(1..1)
        repeat(200) {
            sim.syncTo(0.5f, random = random)
            sim.step(random = random)
        }
        val fallen = sim.fallenCount()
        assertEquals(0, sim.syncTo(0.1f, random = random))
        assertEquals(fallen, sim.fallenCount())
    }

    @Test
    fun `the surface sinks level instead of eroding from a corner`() {
        val sim = sim(width = 31, height = 45)
        val random = seeded()
        sim.fill(1..1)

        repeat(600) {
            sim.syncTo(0.35f, random = random)
            sim.step(random = random)
        }

        // Sample the surface height across the chamber. The middle should be the
        // lowest point — a cone above the neck — and the two edges should be within
        // a few cells of each other rather than one corner being gouged out.
        val centre = sim.grid.width / 2
        val left = sim.surfaceRowAt(centre - 8)
        val right = sim.surfaceRowAt(centre + 8)
        val middle = sim.surfaceRowAt(centre)

        assertNotNull("left edge should still hold sand", left)
        assertNotNull("right edge should still hold sand", right)
        assertNotNull("middle should still hold sand", middle)

        // y grows downward, so a lower surface is a larger row index.
        assertTrue(
            "middle ($middle) should sit at or below the edges ($left, $right)",
            middle!! >= left!! - 1 && middle >= right!! - 1
        )
        assertTrue(
            "edges should be level with each other, were $left and $right",
            kotlin.math.abs(left - right!!) <= 4
        )
    }

    @Test
    fun `fill speckles grains across the given tints`() {
        val sim = sim()
        sim.fill(1..3, random = seeded())
        val seen = mutableSetOf<Int>()
        for (y in 0 until sim.grid.height) {
            for (x in 0 until sim.grid.width) {
                if (sim.grid.isGrain(x, y)) seen += sim.grid[x, y]
            }
        }
        assertEquals(setOf(1, 2, 3), seen)
    }

    @Test
    fun `an empty glass has nothing to release`() {
        val sim = sim()
        assertEquals(0, sim.syncTo(1f, random = seeded()))
    }
}

class HourglassPrimeTest {

    private fun seeded() = Random(5_150)

    private fun sim(width: Int = 26, height: Int = 46) =
        HourglassSim(SandGrid(width, height), neckHalfWidth = 1)

    @Test
    fun `priming lands at the requested fill without animating there`() {
        val sim = sim()
        sim.prime(0.5f, 1..1, random = seeded())

        val total = sim.grid.grainCount
        val fallen = sim.fallenCount()
        assertTrue("half should have fallen, was $fallen of $total", fallen > total / 4)
        assertTrue("but not all of it, was $fallen of $total", fallen < total * 0.85)
        // Already at rest: a tile must not animate itself into position on appearing.
        assertEquals(0, sim.step(random = seeded()))
    }

    @Test
    fun `priming to zero leaves a full glass`() {
        val sim = sim()
        sim.prime(0f, 1..1, random = seeded())
        assertEquals(0, sim.fallenCount())
        assertEquals(sim.capacity, sim.grid.grainCount)
    }

    @Test
    fun `priming to one drains the upper chamber`() {
        val sim = sim()
        sim.prime(1f, 1..1, random = seeded())
        assertTrue(sim.fallenCount() > sim.grid.grainCount * 0.9)
    }

    @Test
    fun `priming is repeatable on the same glass`() {
        val sim = sim()
        sim.prime(0.8f, 1..1, random = seeded())
        val first = sim.fallenCount()
        sim.prime(0.2f, 1..1, random = seeded())
        assertTrue("re-priming should refill, was ${sim.fallenCount()} then $first",
            sim.fallenCount() < first)
        assertEquals(sim.capacity, sim.grid.grainCount)
    }

    @Test
    fun `the released counter tracks what went through the neck`() {
        val sim = sim()
        val random = seeded()
        sim.fill(1..1, random)
        assertEquals(0, sim.releasedCount)
        repeat(60) {
            sim.syncTo(1f, maxPerCall = 4, random = random)
            sim.step(random = random)
        }
        assertTrue(sim.releasedCount > 0)
        assertEquals(sim.releasedCount, sim.fallenCount())
    }
}
