package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TreemapTest {

    private val width = 400f
    private val height = 700f

    private fun layout(weights: List<Float>, w: Float = width, h: Float = height) =
        Treemap.squarify(weights.indices.toList(), w, h) { weights[it] }

    @Test
    fun `nothing to lay out yields nothing`() {
        assertTrue(Treemap.squarify(emptyList<Int>(), width, height) { 1f }.isEmpty())
        assertTrue(Treemap.squarify(listOf(1), 0f, height) { 1f }.isEmpty())
        assertTrue(Treemap.squarify(listOf(1), width, -5f) { 1f }.isEmpty())
    }

    @Test
    fun `a single item fills the whole rectangle`() {
        val cells = layout(listOf(3f))
        assertEquals(1, cells.size)
        val cell = cells.single()
        assertEquals(0f, cell.x, 0.01f)
        assertEquals(0f, cell.y, 0.01f)
        assertEquals(width, cell.width, 0.01f)
        assertEquals(height, cell.height, 0.01f)
    }

    @Test
    fun `every item gets a cell`() {
        (1..24).forEach { count ->
            val cells = layout(List(count) { (it + 1).toFloat() })
            assertEquals("count $count", count, cells.size)
            assertEquals(count, cells.map { it.item }.toSet().size)
        }
    }

    @Test
    fun `cells stay inside the rectangle`() {
        val cells = layout(List(13) { (it % 5 + 1).toFloat() })
        cells.forEach { cell ->
            assertTrue("$cell starts left of origin", cell.x >= -0.01f)
            assertTrue("$cell starts above origin", cell.y >= -0.01f)
            assertTrue("$cell overflows width", cell.x + cell.width <= width + 0.01f)
            assertTrue("$cell overflows height", cell.y + cell.height <= height + 0.01f)
            assertTrue("$cell has no area", cell.width > 0f && cell.height > 0f)
        }
    }

    @Test
    fun `cells never overlap`() {
        val cells = layout(List(17) { (it % 7 + 1).toFloat() })
        for (a in cells.indices) {
            for (b in a + 1 until cells.size) {
                val first = cells[a]
                val second = cells[b]
                val overlapX = min(first.x + first.width, second.x + second.width) -
                    max(first.x, second.x)
                val overlapY = min(first.y + first.height, second.y + second.height) -
                    max(first.y, second.y)
                assertTrue(
                    "$first overlaps $second",
                    overlapX <= 0.01f || overlapY <= 0.01f
                )
            }
        }
    }

    @Test
    fun `the cells tile the whole rectangle`() {
        listOf(1, 2, 3, 5, 8, 13, 21).forEach { count ->
            val cells = layout(List(count) { (it % 4 + 1).toFloat() })
            val covered = cells.sumOf { it.area.toDouble() }
            assertEquals("count $count", (width * height).toDouble(), covered, 1.0)
        }
    }

    @Test
    fun `area follows weight`() {
        val weights = listOf(1f, 2f, 4f, 8f)
        val cells = layout(weights)
        val total = weights.sum()
        cells.forEach { cell ->
            val expected = weights[cell.item] / total * width * height
            assertEquals("item ${cell.item}", expected, cell.area, expected * 0.02f)
        }
    }

    @Test
    fun `equal weights give equal areas`() {
        val cells = layout(List(6) { 1f })
        val expected = width * height / 6f
        cells.forEach { assertEquals(expected, it.area, expected * 0.02f) }
    }

    @Test
    fun `a zero weight still gets something tappable`() {
        val cells = Treemap.squarify(listOf("big", "empty"), width, height, minWeight = 1f) {
            if (it == "big") 500f else 0f
        }
        val empty = cells.single { it.item == "empty" }
        assertTrue("zero-weight cell collapsed: $empty", empty.area > 0f)
    }

    @Test
    fun `squarifying keeps cells closer to square than a plain strip layout`() {
        val cells = layout(List(9) { 1f }, w = 400f, h = 400f)
        val worst = cells.maxOf { max(it.width / it.height, it.height / it.width) }
        // Nine equal strips across 400x400 would each be 400x44, an aspect of 9.
        assertTrue("worst aspect was $worst", worst < 2.5f)
    }

    @Test
    fun `a wide rectangle is handled as well as a tall one`() {
        listOf(400f to 700f, 700f to 400f, 500f to 500f).forEach { (w, h) ->
            val cells = layout(List(7) { (it + 1).toFloat() }, w, h)
            assertEquals(7, cells.size)
            assertEquals((w * h).toDouble(), cells.sumOf { it.area.toDouble() }, 1.0)
            cells.forEach {
                assertTrue("$it out of bounds in ${w}x$h", it.x + it.width <= w + 0.01f)
                assertTrue("$it out of bounds in ${w}x$h", it.y + it.height <= h + 0.01f)
            }
        }
    }

    @Test
    fun `heavier items come first`() {
        val cells = layout(listOf(1f, 9f, 3f))
        assertEquals(1, cells.first().item)
        assertTrue(cells.first().area > cells.last().area)
    }

    @Test
    fun `a lopsided split does not produce degenerate slivers`() {
        val cells = layout(listOf(100f, 1f, 1f, 1f))
        cells.forEach {
            val aspect = max(it.width / it.height, it.height / it.width)
            assertTrue("$it is a sliver (aspect $aspect)", aspect < 40f)
            assertTrue("$it has no width", it.width > 1f)
        }
        assertTrue(abs(cells.sumOf { it.area.toDouble() } - width * height) < 1.0)
    }
}
