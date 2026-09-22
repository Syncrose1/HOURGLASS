package com.hourglass.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln

/** A grid arrangement: [columns] across, [rows] down. */
data class GridShape(val columns: Int, val rows: Int) {
    val capacity: Int get() = columns * rows
}

/**
 * Works out how to fit everything on one screen.
 *
 * The app has no scrolling anywhere in its main view: however many timers exist, they
 * tile into the space available. That is a deliberate constraint rather than a layout
 * convenience — a list you can scroll is a list you can keep adding to, and a wall of
 * tiles that visibly gets denser is its own argument for having fewer timers.
 */
object TileLayout {

    /** Tiles look best a little taller than they are wide, like the glass they hold. */
    const val TARGET_ASPECT = 0.82f

    /**
     * Picks the column count whose resulting tiles come closest to [TARGET_ASPECT].
     *
     * [containerAspect] is width / height of the space being filled.
     */
    fun shapeFor(count: Int, containerAspect: Float): GridShape {
        if (count <= 0) return GridShape(1, 1)

        var best = GridShape(1, count)
        var bestScore = Float.MAX_VALUE
        for (columns in 1..count) {
            val rows = ceil(count.toFloat() / columns).toInt()
            val tileAspect = (containerAspect / columns) * rows
            // Compared in log space so "twice too wide" and "twice too tall" cost the same.
            val score = abs(ln(tileAspect / TARGET_ASPECT))
            if (score < bestScore) {
                bestScore = score
                best = GridShape(columns, rows)
            }
        }
        return best
    }

    /**
     * How much detail a tile of a given edge length can carry. Tiles shrink as the
     * grid fills, so the content has to thin out rather than clip.
     */
    fun detailFor(tileWidthDp: Float): TileDetail = when {
        tileWidthDp >= 150f -> TileDetail.FULL
        tileWidthDp >= 104f -> TileDetail.COMPACT
        tileWidthDp >= 68f -> TileDetail.MINIMAL
        else -> TileDetail.GLYPH
    }
}

/** What a tile has room to show. */
enum class TileDetail {
    /** Glass, name, remaining time and allocation. */
    FULL,

    /** Glass, name and remaining time. */
    COMPACT,

    /** Glass and remaining time. */
    MINIMAL,

    /** Glass only — the name lives in the content description. */
    GLYPH;

    val showsName: Boolean get() = this == FULL || this == COMPACT
    val showsTime: Boolean get() = this != GLYPH
    val showsAllocation: Boolean get() = this == FULL
}
