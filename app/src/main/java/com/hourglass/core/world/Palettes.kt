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
        WorldKind.ISLAND -> island(accent)
        WorldKind.FOREST -> forest(accent)
        WorldKind.BATTLE -> battle(accent)
        WorldKind.HARBOUR -> harbour(accent)
        WorldKind.SIEGE -> siege(accent)
    }

    /** Pale limestone under open sky. Your people and your banner wear the timer's colour. */
    private fun siege(accent: Int) = WorldPalette(
        colours = IntArray(SiegeMat.COUNT).also {
            it[SiegeMat.SKY] = TRANSPARENT
            it[SiegeMat.GRASS] = 0xFF6E8F3E.toInt()
            it[SiegeMat.DIRT] = 0xFF6B4E33.toInt()
            it[SiegeMat.DIRT_DARK] = 0xFF5B412A.toInt()
            it[SiegeMat.STONE] = 0xFFCBBFA6.toInt()
            it[SiegeMat.STONE_DARK] = 0xFFAA9D84.toInt()
            it[SiegeMat.BLOCK] = 0xFFDCD2BC.toInt()
            it[SiegeMat.SCAFFOLD] = 0xFF8A6A48.toInt()
            it[SiegeMat.ROCK] = 0xFF9B9282.toInt()
            it[SiegeMat.ROCK_DARK] = 0xFF7F776A.toInt()
            it[SiegeMat.RUBBLE] = 0xFFB3A78F.toInt()
            it[SiegeMat.TIMBER] = 0xFF7A5436.toInt()
            it[SiegeMat.TIMBER_DARK] = 0xFF5C3E27.toInt()
            it[SiegeMat.WEIGHT] = 0xFF3F3A36.toInt()
            it[SiegeMat.ROPE] = 0xFF3A322B.toInt()
            it[SiegeMat.SHOT] = 0xFF4A4540.toInt()
            it[SiegeMat.SKIN] = 0xFFF1C9A0.toInt()
            it[SiegeMat.OURS] = accent
            it[SiegeMat.THEIRS] = 0xFF3D4A66.toInt()
            it[SiegeMat.TROUSERS] = 0xFF2F2A26.toInt()
            it[SiegeMat.BANNER] = accent
            it[SiegeMat.ARROW] = 0xFF2B2320.toInt()
            it[SiegeMat.DUST] = 0xFFD9CDB4.toInt()
        },
        bright = setOf(SiegeMat.SKY, SiegeMat.OURS, SiegeMat.BANNER, SiegeMat.SKIN, SiegeMat.DUST),
        sky = SiegeMat.SKY,
        depthShading = 0.15f
    )

    /** A stone quay on grey-green water. The crates are the timer's colour. */
    private fun harbour(accent: Int) = WorldPalette(
        colours = IntArray(HarbourMat.COUNT).also {
            it[HarbourMat.SKY] = TRANSPARENT
            it[HarbourMat.WATER] = 0xFF3F7288.toInt()
            it[HarbourMat.WATER_DEEP] = 0xFF2D5568.toInt()
            it[HarbourMat.FOAM] = 0xFFD7E6EA.toInt()
            it[HarbourMat.SEABED] = 0xFF6B5E47.toInt()
            it[HarbourMat.STONE] = 0xFF9C958A.toInt()
            it[HarbourMat.STONE_DARK] = 0xFF7E776D.toInt()
            it[HarbourMat.BOLLARD] = 0xFF2E2A28.toInt()
            it[HarbourMat.HULL] = 0xFF6E4A2F.toInt()
            it[HarbourMat.HULL_DARK] = 0xFF4E3321.toInt()
            it[HarbourMat.DECK] = 0xFFB08A5C.toInt()
            it[HarbourMat.MAST] = 0xFF5A3E28.toInt()
            it[HarbourMat.SAIL] = 0xFFEDE4CF.toInt()
            it[HarbourMat.ROPE] = 0xFF3A322B.toInt()
            it[HarbourMat.CRANE] = 0xFF5B4632.toInt()
            it[HarbourMat.CRANE_DARK] = 0xFF3F3023.toInt()
            it[HarbourMat.CRATE] = accent
            it[HarbourMat.CRATE_DARK] = mix(accent, BLACK, 0.3f)
            it[HarbourMat.BARREL] = 0xFF8A5A33.toInt()
            it[HarbourMat.SACK] = 0xFFD8C79E.toInt()
            it[HarbourMat.WALL] = 0xFFA2573E.toInt()
            it[HarbourMat.WALL_DARK] = 0xFF7F4130.toInt()
            it[HarbourMat.ROOF] = 0xFF4D3B35.toInt()
            it[HarbourMat.INTERIOR] = 0xFF2B211C.toInt()
            it[HarbourMat.SKIN] = 0xFFF1C9A0.toInt()
            it[HarbourMat.SHIRT] = 0xFF3F5E86.toInt()
            it[HarbourMat.TROUSERS] = 0xFF2F2A26.toInt()
            it[HarbourMat.GULL] = 0xFFF4F4F0.toInt()
            it[HarbourMat.RAIN] = 0xFFAFC4CF.toInt()
            it[HarbourMat.LAMP] = 0xFFFFD27A.toInt()
        },
        bright = setOf(
            HarbourMat.SKY, HarbourMat.CRATE, HarbourMat.SAIL, HarbourMat.GULL,
            HarbourMat.FOAM, HarbourMat.LAMP, HarbourMat.SKIN, HarbourMat.RAIN
        ),
        sky = HarbourMat.SKY,
        depthShading = 0.25f
    )

    /**
     * A valley in summer, from above. Your side wears the timer's colour; theirs is a
     * cold grey-blue, so the two read apart at a glance on any accent.
     */
    private fun battle(accent: Int) = WorldPalette(
        colours = IntArray(BattleMat.COUNT).also {
            it[BattleMat.GRASS] = 0xFF7E9A4E.toInt()
            it[BattleMat.GRASS_DARK] = 0xFF728E45.toInt()
            it[BattleMat.GRASS_LIGHT] = 0xFF8AA657.toInt()
            it[BattleMat.FOREST] = 0xFF3F6B35.toInt()
            it[BattleMat.FOREST_DARK] = 0xFF32592C.toInt()
            it[BattleMat.ROCK] = 0xFF8F8A80.toInt()
            it[BattleMat.WATER] = 0xFF3C74A0.toInt()
            it[BattleMat.WATER_LIGHT] = 0xFF5B90B8.toInt()
            it[BattleMat.FORD] = 0xFF78A3B8.toInt()
            it[BattleMat.BRIDGE] = 0xFF8A6A48.toInt()
            it[BattleMat.HILL] = 0xFFA5AE62.toInt()
            it[BattleMat.HILL_DARK] = 0xFF979F58.toInt()
            it[BattleMat.CRATER] = 0xFF4A4132.toInt()
            it[BattleMat.SCORCH] = 0xFF6E7F45.toInt()
            it[BattleMat.TENT_OURS] = mix(accent, WHITE, 0.55f)
            it[BattleMat.TENT_THEIRS] = 0xFFC5CCD6.toInt()
            it[BattleMat.POLE] = 0xFF3A2A1E.toInt()
            it[BattleMat.FLAG_OURS] = accent
            it[BattleMat.FLAG_THEIRS] = THEIRS
            it[BattleMat.FLAG_NEUTRAL] = 0xFFF2EEE4.toInt()
            it[BattleMat.OURS] = mix(accent, WHITE, 0.1f)
            it[BattleMat.THEIRS] = THEIRS
            it[BattleMat.OURS_ROUTED] = mix(accent, 0xFF7E9A4E.toInt(), 0.6f)
            it[BattleMat.THEIRS_ROUTED] = mix(THEIRS, 0xFF7E9A4E.toInt(), 0.6f)
            it[BattleMat.TRACER] = 0xFFFFF1B8.toInt()
            it[BattleMat.SHELL] = 0xFF1E1B1A.toInt()
            it[BattleMat.BLAST] = 0xFFFFB347.toInt()
            it[BattleMat.SHADOW] = 0xFF3E4A2A.toInt()
        },
        bright = setOf(BattleMat.TRACER, BattleMat.BLAST, BattleMat.OURS, BattleMat.THEIRS),
        sky = -1,
        depthShading = 0f
    )

    private const val THEIRS = 0xFF3D4A66.toInt()

    /**
     * A clearing in late light. The crew wear the timer's colour, so the people doing
     * the work are visibly this timer's crew.
     */
    private fun forest(accent: Int) = WorldPalette(
        colours = IntArray(ForestMat.COUNT).also {
            it[ForestMat.SKY] = TRANSPARENT
            it[ForestMat.GRASS] = 0xFF6E8F3E.toInt()
            it[ForestMat.DIRT] = 0xFF6B4E33.toInt()
            it[ForestMat.DIRT_DARK] = 0xFF5B412A.toInt()
            it[ForestMat.TRUNK] = 0xFF7A5436.toInt()
            it[ForestMat.TRUNK_DARK] = 0xFF5C3E27.toInt()
            it[ForestMat.LEAF] = 0xFF4F8A3C.toInt()
            it[ForestMat.LEAF_DARK] = 0xFF3B6D30.toInt()
            it[ForestMat.LEAF_LIGHT] = 0xFF78A94C.toInt()
            it[ForestMat.PINE] = 0xFF2F5E3B.toInt()
            it[ForestMat.PINE_DARK] = 0xFF224A2F.toInt()
            it[ForestMat.STUMP] = 0xFFD2B07A.toInt()
            it[ForestMat.LOG_END] = 0xFFD9B98A.toInt()
            it[ForestMat.LOG_END_DARK] = 0xFFB18E5E.toInt()
            it[ForestMat.BRUSH] = 0xFF6B6A34.toInt()
            it[ForestMat.FIRE] = 0xFFE8702A.toInt()
            it[ForestMat.FIRE_HOT] = 0xFFFFD166.toInt()
            it[ForestMat.SMOKE] = 0xFFB9B4AE.toInt()
            it[ForestMat.CHIP] = 0xFFE3C99A.toInt()
            it[ForestMat.SKIN] = 0xFFF1C9A0.toInt()
            it[ForestMat.SHIRT] = accent
            it[ForestMat.TROUSERS] = 0xFF2F3A4F.toInt()
            it[ForestMat.TOOL] = 0xFFD9DDE2.toInt()
        },
        bright = setOf(
            ForestMat.SKY, ForestMat.FIRE, ForestMat.FIRE_HOT, ForestMat.SKIN,
            ForestMat.SHIRT, ForestMat.TOOL, ForestMat.SMOKE
        ),
        sky = ForestMat.SKY,
        depthShading = 0.15f
    )

    /**
     * Open sea under open sky. The timer's colour runs through the lava's glow, so the
     * eruption building the island is visibly this timer's.
     */
    private fun island(accent: Int) = WorldPalette(
        colours = IntArray(IslandMat.COUNT).also {
            it[IslandMat.SKY] = TRANSPARENT
            it[IslandMat.WATER] = 0xFF2F6F99.toInt()
            it[IslandMat.WATER_SURFACE] = 0xFF7DB8D8.toInt()
            it[IslandMat.SEABED] = 0xFF4A4038.toInt()
            it[IslandMat.SEABED_SAND] = 0xFFB59C6E.toInt()
            it[IslandMat.LAVA_HOT] = mix(0xFFFFD66B.toInt(), accent, 0.3f)
            it[IslandMat.LAVA] = mix(0xFFF07A2A.toInt(), accent, 0.2f)
            it[IslandMat.LAVA_COOL] = 0xFFA2361E.toInt()
            it[IslandMat.BASALT] = 0xFF3A3533.toInt()
            it[IslandMat.BASALT_DARK] = 0xFF2C2827.toInt()
            it[IslandMat.ASH] = 0xFF7B7068.toInt()
            it[IslandMat.STEAM] = 0xFFE8ECEF.toInt()
            it[IslandMat.GRASS] = 0xFF6FA045.toInt()
            it[IslandMat.SHRUB] = 0xFF3F7A3A.toInt()
        },
        bright = setOf(
            IslandMat.SKY, IslandMat.LAVA_HOT, IslandMat.LAVA, IslandMat.STEAM,
            IslandMat.WATER_SURFACE, IslandMat.GRASS, IslandMat.SHRUB
        ),
        sky = IslandMat.SKY
    )

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
