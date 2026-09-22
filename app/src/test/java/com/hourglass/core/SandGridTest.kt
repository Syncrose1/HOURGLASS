package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SandGridTest {

    private fun seeded() = Random(20_260_922)

    @Test
    fun `a lone grain falls one cell per step`() {
        val grid = SandGrid(3, 5)
        grid.place(1, 0, 1)

        grid.step(random = seeded())
        assertTrue(grid.isEmpty(1, 0))
        assertEquals(1, grid[1, 1])

        grid.step(random = seeded())
        assertEquals(1, grid[1, 2])
    }

    @Test
    fun `a grain on the floor stays put`() {
        val grid = SandGrid(3, 3)
        grid.place(1, 2, 1)
        assertEquals(0, grid.step(random = seeded()))
        assertEquals(1, grid[1, 2])
    }

    @Test
    fun `a grain landing on another slides off the side`() {
        val grid = SandGrid(3, 3)
        grid.place(1, 2, 1) // floor
        grid.place(1, 1, 2) // directly on top

        grid.step(random = seeded())

        assertEquals(1, grid[1, 2])
        // The upper grain cannot go straight down, so it takes a diagonal.
        assertTrue(grid[0, 2] == 2 || grid[2, 2] == 2)
        assertTrue(grid.isEmpty(1, 1))
    }

    @Test
    fun `a grain wedged in a pit does not move`() {
        val grid = SandGrid(3, 3)
        // Fill the bottom row, then sit a grain in the middle of the row above.
        (0..2).forEach { grid.place(it, 2, 1) }
        grid.place(1, 1, 2)

        assertEquals(0, grid.step(random = seeded()))
        assertEquals(2, grid[1, 1])
    }

    @Test
    fun `grains are conserved and stay on the grid`() {
        val grid = SandGrid(16, 16)
        val random = seeded()
        repeat(120) { grid.pour(8, 0, 1, tint = 1, spread = 2, random = random) }
        val before = grid.grainCount

        repeat(200) { grid.step(random = random) }

        assertEquals(before, grid.grainCount)
        var counted = 0
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                if (!grid.isEmpty(x, y)) counted++
            }
        }
        assertEquals(before, counted)
    }

    @Test
    fun `a poured stream settles into a pile that stops moving`() {
        val grid = SandGrid(21, 21)
        val random = seeded()
        repeat(200) {
            grid.pour(10, 0, 1, tint = 1, spread = 0, random = random)
            grid.step(random = random)
        }
        val steps = grid.settle(random = random)

        assertTrue("pile should settle", steps < 400)
        assertEquals(0, grid.step(random = random))
        assertTrue("pile should have height", grid.pileHeight() > 1)
    }

    @Test
    fun `a settled pile slopes rather than stacking into a tower`() {
        val grid = SandGrid(31, 31)
        val random = seeded()
        repeat(400) {
            grid.pour(15, 0, 1, tint = 1, spread = 0, random = random)
            grid.step(random = random)
        }
        grid.settle(random = random)

        // Sand poured down one column must spread sideways; a tower would mean the
        // diagonal rule never fired.
        var occupiedColumns = 0
        for (x in 0 until grid.width) {
            for (y in 0 until grid.height) {
                if (!grid.isEmpty(x, y)) {
                    occupiedColumns++
                    break
                }
            }
        }
        assertTrue("pile spread over $occupiedColumns columns", occupiedColumns > 5)
    }

    @Test
    fun `gravity can point in any direction`() {
        val grid = SandGrid(5, 5)
        grid.place(2, 2, 1)

        grid.step(Direction.W, random = seeded())
        assertEquals(1, grid[1, 2])

        grid.step(Direction.N, random = seeded())
        assertEquals(1, grid[1, 1])

        grid.step(Direction.E, random = seeded())
        assertEquals(1, grid[2, 1])
    }

    @Test
    fun `pour reports what it could not fit`() {
        val grid = SandGrid(3, 3)
        grid.place(1, 0, 1)
        // One cell wide, already occupied.
        assertEquals(0, grid.pour(1, 0, amount = 5, tint = 1, spread = 0, random = seeded()))
        assertEquals(1, grid.grainCount)
    }

    @Test
    fun `removing and placing keep the count honest`() {
        val grid = SandGrid(4, 4)
        grid.place(1, 1, 1)
        grid.place(1, 1, 2) // replace, not add
        assertEquals(1, grid.grainCount)
        grid.remove(1, 1)
        grid.remove(1, 1) // second removal is a no-op
        assertEquals(0, grid.grainCount)
    }

    @Test
    fun `out of bounds reads are flagged rather than crashing`() {
        val grid = SandGrid(2, 2)
        assertEquals(SandGrid.OUT_OF_BOUNDS, grid[-1, 0])
        assertEquals(SandGrid.OUT_OF_BOUNDS, grid[0, 9])
        grid.place(-5, -5, 1)
        assertEquals(0, grid.grainCount)
    }

    @Test
    fun `clear empties the grid`() {
        val grid = SandGrid(4, 4)
        repeat(8) { grid.place(it % 4, it / 4, 1) }
        grid.clear()
        assertEquals(0, grid.grainCount)
        assertEquals(0, grid.pileHeight())
    }

    @Test
    fun `direction rotation wraps both ways`() {
        assertEquals(Direction.SW, Direction.S.rotated(1))
        assertEquals(Direction.SE, Direction.S.rotated(-1))
        assertEquals(Direction.N, Direction.S.rotated(4))
        assertEquals(Direction.S, Direction.S.rotated(8))
    }

    @Test
    fun `gravity vectors map to the nearest compass point`() {
        assertEquals(Direction.S, Direction.nearest(0f, 1f))
        assertEquals(Direction.N, Direction.nearest(0f, -1f))
        assertEquals(Direction.E, Direction.nearest(1f, 0f))
        assertEquals(Direction.SE, Direction.nearest(1f, 1f))
        assertEquals(Direction.S, Direction.nearest(0f, 0f))
    }
}

class SandGridWallTest {

    private fun seeded() = Random(4_242)

    @Test
    fun `walls block grains and never move`() {
        val grid = SandGrid(3, 3)
        grid.wall(1, 2)
        grid.place(1, 0, 1)

        repeat(4) { grid.step(random = seeded()) }

        assertTrue(grid.isWall(1, 2))
        // The grain came to rest on the wall, or slid off it, but never through.
        assertTrue(grid.isGrain(1, 1) || grid.isGrain(0, 2) || grid.isGrain(2, 2))
        assertEquals(1, grid.grainCount)
    }

    @Test
    fun `walls are not counted as grains`() {
        val grid = SandGrid(4, 4)
        grid.wall(0, 0)
        grid.wall(1, 0)
        assertEquals(0, grid.grainCount)

        grid.place(2, 0, 1)
        assertEquals(1, grid.grainCount)

        // Walling an occupied cell replaces the grain.
        grid.wall(2, 0)
        assertEquals(0, grid.grainCount)
    }

    @Test
    fun `an hourglass drains through its neck`() {
        val grid = SandGrid(9, 13)
        // Walls down both sides, with a one-cell neck in the middle row.
        for (y in 0 until grid.height) {
            grid.wall(0, y)
            grid.wall(8, y)
        }
        for (x in 1..7) {
            if (x != 4) grid.wall(x, 6)
        }
        for (x in 1..7) grid.wall(x, 12)
        // Fill the upper chamber.
        for (y in 1..5) for (x in 1..7) grid.place(x, y, 1)
        val total = grid.grainCount

        repeat(600) { grid.step(random = seeded()) }

        assertEquals("grains are conserved through the neck", total, grid.grainCount)
        var below = 0
        for (y in 7..11) for (x in 1..7) if (grid.isGrain(x, y)) below++
        assertTrue("sand should have fallen through, found $below", below > 0)
    }

    @Test
    fun `clearGrains keeps the vessel`() {
        val grid = SandGrid(4, 4)
        grid.wall(0, 3)
        grid.place(1, 0, 1)
        grid.clearGrains()
        assertEquals(0, grid.grainCount)
        assertTrue(grid.isWall(0, 3))
    }

    @Test
    fun `disturb leaves walls alone`() {
        val grid = SandGrid(5, 5)
        grid.wall(2, 2)
        grid.disturb(2, 2, radius = 2, random = seeded())
        assertTrue(grid.isWall(2, 2))
    }
}
