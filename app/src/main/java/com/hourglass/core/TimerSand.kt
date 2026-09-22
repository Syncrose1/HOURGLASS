package com.hourglass.core

import java.util.Locale

/**
 * The eight sands a timer can be filled with.
 *
 * Stored as a token rather than a hex value. Each sand resolves to a different step
 * in light and dark — a step chosen for that surface, not flipped from the other one —
 * so a stored hex could only ever have been correct in one theme.
 *
 * The eight hues and their light/dark steps were picked by running the palette through
 * a colour-vision validator rather than by eye: adjacent swatches clear ΔE 15 for
 * normal vision and ΔE 15 under protanopia/deuteranopia/tritanopia. Identity is never
 * carried by colour alone regardless — every card and every chart row is labelled.
 */
enum class TimerSand {
    AMBER,
    TERRACOTTA,
    ROSE,
    PLUM,
    LAVENDER,
    INDIGO,
    AQUA,
    OLIVE;

    /** The value persisted in the `colour` column. */
    val token: String get() = name.lowercase(Locale.US)

    companion object {
        val DEFAULT = AMBER

        fun parse(value: String?): TimerSand {
            val raw = value?.trim() ?: return DEFAULT
            entries.firstOrNull { it.token.equals(raw, ignoreCase = true) }?.let { return it }
            // Rows written before the palette became theme-aware stored a hex.
            return LEGACY_HEX[raw.uppercase(Locale.US).removePrefix("#").takeLast(6)] ?: DEFAULT
        }

        /**
         * Swatches the app has previously written into the `colour` column, mapped to
         * the slot that replaced them. Anything unrecognised falls back to [DEFAULT].
         */
        private val LEGACY_HEX = mapOf(
            "E0A63C" to AMBER,
            "E8A838" to AMBER,
            "C4903A" to AMBER,
            "D4A437" to AMBER,
            "E08A4B" to TERRACOTTA,
            "E07A5F" to TERRACOTTA,
            "D4604A" to TERRACOTTA,
            "D4663D" to TERRACOTTA,
            "C2506E" to ROSE,
            "D4496F" to ROSE,
            "8A5E9E" to PLUM,
            "52689E" to INDIGO,
            "5E6AC8" to INDIGO,
            "2F8F86" to AQUA,
            "3DAA9E" to AQUA,
            "6F8F45" to OLIVE,
            "7A9E3F" to OLIVE,
            "A1887F" to TERRACOTTA
        )
    }
}
