package com.hourglass.core

import kotlin.math.max
import kotlin.math.min

/** One laid-out cell: [item] occupying the rectangle at ([x], [y]) sized [width] × [height]. */
data class TreemapCell<T>(
    val item: T,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val area: Float get() = width * height
}

/**
 * Squarified treemap layout — the WinDirStat carve-up.
 *
 * Timers divide the space they are given in proportion to the time allocated to them,
 * so an afternoon's work is visibly a bigger piece of the day than a ten-minute
 * errand, and adding a timer redivides the wall rather than extending it downward.
 * Nothing ever scrolls.
 *
 * "Squarified" is the part that matters for a wall you have to read at a glance: the
 * naive strip layout produces long slivers, and this trades exact ordering for cells
 * that stay close to square. The algorithm is Bruls, Huizing and van Wijk (2000).
 */
object Treemap {

    /**
     * Lays [items] into a [width] × [height] rectangle.
     *
     * [minWeight] floors each weight so a zero-length item still gets a cell it can
     * be tapped on, rather than collapsing to nothing.
     */
    fun <T> squarify(
        items: List<T>,
        width: Float,
        height: Float,
        minWeight: Float = 1f,
        weight: (T) -> Float
    ): List<TreemapCell<T>> {
        if (items.isEmpty() || width <= 0f || height <= 0f) return emptyList()

        val weighted = items
            .map { it to max(weight(it), minWeight).toDouble() }
            .sortedByDescending { it.second }

        val totalWeight = weighted.sumOf { it.second }
        if (totalWeight <= 0.0) return emptyList()

        // Work in areas from here on, so a row's thickness falls out of its sum.
        val totalArea = width.toDouble() * height.toDouble()
        val scaled = weighted.map { (item, w) -> item to w / totalWeight * totalArea }

        val cells = mutableListOf<TreemapCell<T>>()
        var originX = 0.0
        var originY = 0.0
        var freeWidth = width.toDouble()
        var freeHeight = height.toDouble()

        var index = 0
        while (index < scaled.size) {
            val shorterSide = min(freeWidth, freeHeight)

            // Grow the row while doing so improves its worst aspect ratio.
            val row = mutableListOf(scaled[index])
            var next = index + 1
            while (next < scaled.size) {
                val areas = row.map { it.second }
                val withNext = areas + scaled[next].second
                if (worstAspect(withNext, shorterSide) > worstAspect(areas, shorterSide)) break
                row.add(scaled[next])
                next++
            }

            val rowArea = row.sumOf { it.second }
            val isLast = next >= scaled.size

            if (freeWidth >= freeHeight) {
                // Lay the row down the left edge as a vertical strip.
                val stripWidth = if (isLast) freeWidth else rowArea / freeHeight
                var cursorY = originY
                row.forEachIndexed { position, (item, area) ->
                    val cellHeight =
                        if (position == row.lastIndex) originY + freeHeight - cursorY
                        else area / stripWidth
                    cells += TreemapCell(
                        item,
                        originX.toFloat(),
                        cursorY.toFloat(),
                        stripWidth.toFloat(),
                        cellHeight.toFloat()
                    )
                    cursorY += cellHeight
                }
                originX += stripWidth
                freeWidth -= stripWidth
            } else {
                // Lay it across the top edge as a horizontal strip.
                val stripHeight = if (isLast) freeHeight else rowArea / freeWidth
                var cursorX = originX
                row.forEachIndexed { position, (item, area) ->
                    val cellWidth =
                        if (position == row.lastIndex) originX + freeWidth - cursorX
                        else area / stripHeight
                    cells += TreemapCell(
                        item,
                        cursorX.toFloat(),
                        originY.toFloat(),
                        cellWidth.toFloat(),
                        stripHeight.toFloat()
                    )
                    cursorX += cellWidth
                }
                originY += stripHeight
                freeHeight -= stripHeight
            }

            index = next
        }

        return cells
    }

    /**
     * The worst aspect ratio in a row laid along a side of length [side], as defined
     * by the paper: closer to 1 is squarer, so a smaller value is better.
     */
    private fun worstAspect(areas: List<Double>, side: Double): Double {
        if (areas.isEmpty() || side <= 0.0) return Double.MAX_VALUE
        val sum = areas.sum()
        if (sum <= 0.0) return Double.MAX_VALUE
        val largest = areas.max()
        val smallest = areas.min()
        if (smallest <= 0.0) return Double.MAX_VALUE
        val sideSquared = side * side
        val sumSquared = sum * sum
        return max(sideSquared * largest / sumSquared, sumSquared / (sideSquared * smallest))
    }
}
