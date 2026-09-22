package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLayoutTest {

    /** A typical phone content area: taller than it is wide. */
    private val phone = 0.62f

    @Test
    fun `a single tile fills the space`() {
        assertEquals(GridShape(1, 1), TileLayout.shapeFor(1, phone))
    }

    @Test
    fun `nothing to show still yields a valid shape`() {
        assertEquals(GridShape(1, 1), TileLayout.shapeFor(0, phone))
    }

    @Test
    fun `every shape has room for everything it was asked to hold`() {
        (1..60).forEach { count ->
            val shape = TileLayout.shapeFor(count, phone)
            assertTrue(
                "shape $shape cannot hold $count",
                shape.capacity >= count
            )
        }
    }

    @Test
    fun `a shape is never wastefully large`() {
        // Dropping a whole row must leave the grid too small — no empty trailing rows.
        (1..60).forEach { count ->
            val shape = TileLayout.shapeFor(count, phone)
            if (shape.rows > 1) {
                assertTrue(
                    "shape $shape has a spare row for $count",
                    shape.columns * (shape.rows - 1) < count
                )
            }
        }
    }

    @Test
    fun `columns grow with the count`() {
        val few = TileLayout.shapeFor(2, phone)
        val many = TileLayout.shapeFor(24, phone)
        assertTrue("$few then $many", many.columns > few.columns)
    }

    @Test
    fun `a tall container prefers fewer columns than a wide one`() {
        val tall = TileLayout.shapeFor(12, containerAspect = 0.5f)
        val wide = TileLayout.shapeFor(12, containerAspect = 2.0f)
        assertTrue("tall $tall wide $wide", wide.columns >= tall.columns)
    }

    @Test
    fun `detail thins out as tiles shrink`() {
        assertEquals(TileDetail.FULL, TileLayout.detailFor(180f))
        assertEquals(TileDetail.COMPACT, TileLayout.detailFor(120f))
        assertEquals(TileDetail.MINIMAL, TileLayout.detailFor(80f))
        assertEquals(TileDetail.GLYPH, TileLayout.detailFor(40f))
    }

    @Test
    fun `detail levels agree with what they claim to show`() {
        assertTrue(TileDetail.FULL.showsName && TileDetail.FULL.showsAllocation)
        assertTrue(TileDetail.COMPACT.showsName && !TileDetail.COMPACT.showsAllocation)
        assertTrue(!TileDetail.MINIMAL.showsName && TileDetail.MINIMAL.showsTime)
        assertTrue(!TileDetail.GLYPH.showsTime)
    }
}
