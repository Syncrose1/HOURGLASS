package com.hourglass.core.world

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The day as a strip of pixel sky: a sun sinking across it toward a line of dunes,
 * the light going gold, then dusk, then night — with stars — when the day is over.
 *
 * Drawn at the same chunky resolution as the worlds, so the bar over the wall is
 * the same kind of thing as the timers under it. Pure ARGB, no platform types.
 */
class DaySky(val width: Int, val height: Int, seed: Long = 7L) {

    private val horizon = height - DUNE_ROWS
    private val farDunes = IntArray(width)
    private val nearDunes = IntArray(width)
    private val stars: List<Triple<Int, Int, Int>>

    init {
        val random = Random(seed)
        val phaseA = random.nextFloat() * 6f
        val phaseB = random.nextFloat() * 6f
        for (x in 0 until width) {
            farDunes[x] = horizon - 1 + (sin(x * 0.09f + phaseA) * 1.3f + sin(x * 0.23f + phaseB) * 0.6f).toInt()
            nearDunes[x] = horizon + 1 + (sin(x * 0.05f + phaseB) * 1.6f).toInt()
        }
        stars = List(width / 5) {
            Triple(random.nextInt(width), random.nextInt(0, (horizon - 2).coerceAtLeast(1)), random.nextInt(8))
        }
    }

    /**
     * Paints the sky for a day [spent] fraction gone (0 is morning, 1 is bedtime) into
     * [buffer], ARGB, row-major. [tick] only makes the stars twinkle.
     */
    fun render(buffer: IntArray, spent: Float, tick: Int) {
        val t = spent.coerceIn(0f, 1f)
        val top = keyed(t, TOPS)
        val bottom = keyed(t, BOTTOMS)
        for (y in 0 until height) {
            val row = Palettes.mix(top, bottom, y.toFloat() / horizon)
            // Banded, not smooth: a few steps of colour, the way a pixel sky is painted.
            val band = Palettes.mix(top, bottom, (y * BANDS / horizon.coerceAtLeast(1)).toFloat() / BANDS)
            val colour = if (y < horizon) band else row
            for (x in 0 until width) buffer[y * width + x] = colour
        }

        // Stars once the light has gone.
        val starlight = ((t - 0.88f) / 0.12f).coerceIn(0f, 1f)
        if (starlight > 0f) {
            stars.forEach { (x, y, phase) ->
                val twinkle = ((tick / 6 + phase) % 8) != 0
                if (twinkle && (phase + 1) / 8f <= starlight + 0.2f) {
                    buffer[y * width + x] = Palettes.mix(buffer[y * width + x], STAR, starlight)
                }
            }
        }

        // The sun: an arc from high on the left down into the dunes on the right.
        val sunX = (width * (0.12f + 0.76f * t)).toInt()
        val altitude = cos(t * PI / 2).toFloat()
        val sunY = (horizon + 1 - altitude * (horizon - 2)).toInt()
        val sunColour = Palettes.mix(SUN_HIGH, SUN_LOW, t)
        for (dy in -SUN_RADIUS..SUN_RADIUS) for (dx in -SUN_RADIUS..SUN_RADIUS) {
            if (dx * dx + dy * dy > SUN_RADIUS * SUN_RADIUS + 1) continue
            val x = sunX + dx
            val y = sunY + dy
            if (x in 0 until width && y in 0 until horizon + 1) buffer[y * width + x] = sunColour
        }

        // Dunes last, so the sun sets behind them.
        val far = keyed(t, FAR_DUNES)
        val near = keyed(t, NEAR_DUNES)
        for (x in 0 until width) {
            for (y in farDunes[x].coerceAtLeast(0) until height) buffer[y * width + x] = far
            for (y in nearDunes[x].coerceAtLeast(0) until height) buffer[y * width + x] = near
        }
    }

    private fun keyed(t: Float, keys: IntArray): Int {
        val position = t * (keys.size - 1)
        val index = position.toInt().coerceAtMost(keys.size - 2)
        return Palettes.mix(keys[index], keys[index + 1], position - index)
    }

    companion object {
        private const val DUNE_ROWS = 4
        private const val BANDS = 5
        private const val SUN_RADIUS = 2

        // Morning, midday, golden hour, dusk, night.
        private val TOPS = intArrayOf(
            0xFF8DB3CF.toInt(), 0xFF7FA6C9.toInt(), 0xFF7C84B0.toInt(), 0xFF3B3E6E.toInt(), 0xFF111831.toInt()
        )
        private val BOTTOMS = intArrayOf(
            0xFFE6DCC0.toInt(), 0xFFE9D6AE.toInt(), 0xFFF0C07A.toInt(), 0xFFD9825A.toInt(), 0xFF2A2748.toInt()
        )
        private val FAR_DUNES = intArrayOf(
            0xFFD6B77F.toInt(), 0xFFD1AE72.toInt(), 0xFFC08F58.toInt(), 0xFF7D4F45.toInt(), 0xFF231F35.toInt()
        )
        private val NEAR_DUNES = intArrayOf(
            0xFFC7A366.toInt(), 0xFFC29B5C.toInt(), 0xFFA8764A.toInt(), 0xFF5B3A3A.toInt(), 0xFF18152A.toInt()
        )
        private const val SUN_HIGH = 0xFFFFF4D6.toInt()
        private const val SUN_LOW = 0xFFFF9A4D.toInt()
        private const val STAR = 0xFFF4EFD9.toInt()
    }
}
