package com.hourglass.core.world

/**
 * How a world is coloured: one ARGB colour per cell slot, which slots stay bright
 * rather than darkening with depth, and which slot is sky (drawn transparent so a
 * gradient can show through).
 *
 * Pure data in the core, so the app, the snapshot tool, and any other renderer that
 * ever hosts these worlds all draw them the same way.
 */
class WorldPalette(
    val colours: IntArray,
    val bright: Set<Int>,
    val sky: Int,
    /** How much the bottom of the world darkens; none for a world seen from above. */
    val depthShading: Float = Palettes.DEPTH_DARKENING
)

object Palettes {

    /** [accent] is the timer's own colour, ARGB; each world puts it somewhere that matters. */
    fun forKind(kind: WorldKind, accent: Int): WorldPalette = when (kind) {
        WorldKind.MINE -> mine(accent)
        WorldKind.RIVER -> river(accent)
        WorldKind.ANTS -> ants(accent)
    }

    /**
     * Dry ground at midday, seen from above. The food is the timer's colour, and so is
     * what the ants carry home. Trails are ground worn and darkened by traffic.
     */
    private fun ants(accent: Int) = WorldPalette(
        colours = IntArray(AntMat.COUNT).also {
            it[AntMat.GROUND] = 0xFFC9A571.toInt()
            it[AntMat.GROUND_DARK] = 0xFFBE9964.toInt()
            it[AntMat.PEBBLE] = 0xFF8C8177.toInt()
            it[AntMat.PEBBLE_LIGHT] = 0xFFA99E92.toInt()
            it[AntMat.GRASS] = 0xFF7D8C45.toInt()
            it[AntMat.FOOD] = accent
            it[AntMat.FOOD_BRIGHT] = mix(accent, WHITE, 0.35f)
            it[AntMat.NEST] = 0xFF2A1D14.toInt()
            it[AntMat.MOUND] = 0xFFA87E4C.toInt()
            it[AntMat.TRAIL] = 0xFFA68258.toInt()
            it[AntMat.TRAIL_FAINT] = 0xFFB69160.toInt()
            it[AntMat.ANT] = 0xFF2B1A12.toInt()
            it[AntMat.ANT_CARRYING] = mix(accent, BLACK, 0.2f)
        },
        bright = setOf(AntMat.ANT, AntMat.ANT_CARRYING, AntMat.FOOD, AntMat.FOOD_BRIGHT),
        sky = -1,
        depthShading = 0f
    )

    /**
     * The underground is dark in both themes — it is underground. The seams are the one
     * thing in the ground that carries the timer's colour, so the haul is visibly this
     * timer's haul.
     */
    private fun mine(accent: Int) = WorldPalette(
        colours = IntArray(Mat.COUNT).also {
            it[Mat.SKY] = TRANSPARENT
            it[Mat.AIR] = 0xFF1B130D.toInt()
            it[Mat.SAND] = 0xFFD8B77C.toInt()
            it[Mat.SAND_DARK] = 0xFFC7A366.toInt()
            it[Mat.SANDSTONE] = 0xFFAE7849.toInt()
            it[Mat.SANDSTONE_DARK] = 0xFF98683E.toInt()
            it[Mat.ROCK] = 0xFF5C4F47.toInt()
            it[Mat.ROCK_DARK] = 0xFF4B403A.toInt()
            it[Mat.MINERAL] = accent
            it[Mat.MINERAL_BRIGHT] = mix(accent, WHITE, 0.45f)
            it[Mat.CART] = 0xFF6E4B2E.toInt()
            it[Mat.MINER] = 0xFFF6EBD6.toInt()
            it[Mat.MINER_LOADED] = mix(accent, WHITE, 0.25f)
            it[Mat.STOCK] = mix(accent, WHITE, 0.15f)
            it[Mat.SAND_PACKED] = 0xFFB89A68.toInt()
            it[Mat.PLATFORM] = 0xFF7A5634.toInt()
            it[Mat.BOULDER] = 0xFF2E2A2C.toInt()
            it[Mat.BOULDER_DARK] = 0xFF242124.toInt()
        },
        bright = setOf(Mat.SKY, Mat.MINER, Mat.MINER_LOADED, Mat.MINERAL, Mat.MINERAL_BRIGHT),
        sky = Mat.SKY
    )

    /**
     * A valley in daylight. The timer's colour rides on the beavers' loads, so the wood
     * leaving the jam is visibly this timer's work. Beavers are warm and bright on
     * purpose: dark-brown ones vanished against the logs and against a night sky.
     */
    private fun river(accent: Int) = WorldPalette(
        colours = IntArray(RiverMat.COUNT).also {
            it[RiverMat.SKY] = TRANSPARENT
            it[RiverMat.AIR] = 0xFF1B130D.toInt()
            it[RiverMat.EARTH] = 0xFF8A6A48.toInt()
            it[RiverMat.EARTH_DARK] = 0xFF735638.toInt()
            it[RiverMat.ROCK] = 0xFF5A4E46.toInt()
            it[RiverMat.WATER] = 0xFF3F86B8.toInt()
            it[RiverMat.WATER_SURFACE] = 0xFF7FB6DA.toInt()
            it[RiverMat.LOG] = 0xFF7B5433.toInt()
            it[RiverMat.LOG_DARK] = 0xFF5E3F25.toInt()
            it[RiverMat.STICK] = 0xFFA07C52.toInt()
            it[RiverMat.MUD] = 0xFF4E3B2A.toInt()
            it[RiverMat.GRASS_DRY] = 0xFFB59A5E.toInt()
            it[RiverMat.GRASS] = 0xFF5E9A3E.toInt()
            it[RiverMat.BEAVER] = 0xFFD08A4E.toInt()
            it[RiverMat.BEAVER_LOADED] = mix(accent, WHITE, 0.2f)
        },
        bright = setOf(RiverMat.SKY, RiverMat.BEAVER, RiverMat.BEAVER_LOADED, RiverMat.WATER_SURFACE),
        sky = RiverMat.SKY
    )

    /** Linear mix of two ARGB colours. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val from = (a ushr shift) and 0xFF
            val to = (b ushr shift) and 0xFF
            return (from + (to - from) * f).toInt() and 0xFF
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * One palette per row, darkening toward the bottom so the ground reads as depth.
     * Bright slots are exempt: they are what you look for.
     */
    fun shadedByDepth(palette: WorldPalette, height: Int, darkening: Float = palette.depthShading): IntArray {
        val slots = palette.colours.size
        val table = IntArray(height * slots)
        for (y in 0 until height) {
            val t = darkening * y / height
            for (slot in 0 until slots) {
                val base = palette.colours[slot]
                table[y * slots + slot] = if (slot in palette.bright) base else mix(base, BLACK, t)
            }
        }
        return table
    }

    const val DEPTH_DARKENING = 0.4f
    private const val TRANSPARENT = 0x00000000
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
}
