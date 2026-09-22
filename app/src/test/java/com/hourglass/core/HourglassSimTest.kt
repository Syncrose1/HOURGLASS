package com.hourglass.core

import org.junit.Assert.assertEquals
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
        sim.fill(tint = 1)
        assertEquals(sim.capacity, sim.grid.grainCount)
        assertEquals(0, sim.fallenCount())
    }

    @Test
    fun `progress drives how much has fallen`() {
        val sim = sim()
        val random = seeded()
        sim.fill(tint = 1)
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
        sim.fill(tint = 1)
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
        sim.fill(tint = 1)
        val released = sim.syncTo(1f, maxPerCall = 5, random = seeded())
        assertTrue("released $released", released <= 5)
    }

    @Test
    fun `progress running backwards releases nothing`() {
        val sim = sim()
        val random = seeded()
        sim.fill(tint = 1)
        repeat(200) {
            sim.syncTo(0.5f, random = random)
            sim.step(random = random)
        }
        val fallen = sim.fallenCount()
        assertEquals(0, sim.syncTo(0.1f, random = random))
        assertEquals(fallen, sim.fallenCount())
    }

    @Test
    fun `an empty glass has nothing to release`() {
        val sim = sim()
        assertEquals(0, sim.syncTo(1f, random = seeded()))
    }
}
