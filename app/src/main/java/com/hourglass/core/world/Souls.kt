package com.hourglass.core.world

import kotlin.random.Random

/**
 * The same ten people, in every world.
 *
 * A mine's crew, a river's beavers, a castle's masons: whoever is on shift is cast
 * from this one small company, so the miner digging today is the beaver who broke
 * the jam last week. What each of them gets done is credited to their name under the
 * role they played, and it adds up across every timer, for good.
 */
object Souls {

    val NAMES = listOf("Jeb", "Mira", "Otto", "Wren", "Bram", "Tilly", "Ezra", "Nell", "Iggy", "Pip")

    /**
     * Who they are, the same in every world. Pace is how quickly they get through
     * work; care is how precisely — a careful crane driver drops fewer loads, a careful
     * shot hits more often; luck is for whatever the dice decide. The crew averages out
     * near an ordinary pace, so the worlds' timing holds whoever is on shift.
     */
    private val TEMPERAMENTS = listOf(
        Temperament(pace = 1.0f, care = 0.5f, luck = 0.5f, nature = "steady"),
        Temperament(pace = 0.8f, care = 0.25f, luck = 0.9f, nature = "slow, slapdash, lucky"),
        Temperament(pace = 0.8f, care = 0.9f, luck = 0.5f, nature = "slow and precise"),
        Temperament(pace = 1.25f, care = 0.3f, luck = 0.5f, nature = "quick and careless"),
        Temperament(pace = 0.95f, care = 0.7f, luck = 0.4f, nature = "solid"),
        Temperament(pace = 1.15f, care = 0.75f, luck = 0.5f, nature = "quick and careful"),
        Temperament(pace = 0.85f, care = 0.5f, luck = 0.6f, nature = "a dreamer"),
        Temperament(pace = 1.1f, care = 0.55f, luck = 0.45f, nature = "brisk"),
        Temperament(pace = 1.2f, care = 0.35f, luck = 0.2f, nature = "in a hurry, unlucky"),
        Temperament(pace = 1.05f, care = 0.5f, luck = 0.7f, nature = "eager")
    )

    val count: Int get() = NAMES.size

    fun name(soul: Int): String = NAMES[soul.mod(NAMES.size)]

    fun temperament(soul: Int): Temperament = TEMPERAMENTS[soul.mod(TEMPERAMENTS.size)]

    /** A soul's pace with a little day-to-day variation, so no two shifts are identical. */
    fun pace(soul: Int, random: Random): Float = temperament(soul).pace * (0.92f + random.nextFloat() * 0.16f)

    /**
     * Souls for [places] jobs. Up to ten, everyone is different, picked at random; a
     * colony or an army needs more hands than there are souls, so past ten the names
     * come round again, at random.
     */
    fun cast(places: Int, random: Random): IntArray {
        val order = NAMES.indices.shuffled(random)
        return IntArray(places) { i -> if (i < order.size) order[i] else random.nextInt(NAMES.size) }
    }
}

/** How a soul goes about things. Each trait runs 0..1 except pace, a multiplier around 1. */
data class Temperament(val pace: Float, val care: Float, val luck: Float, val nature: String = "")

/** One soul's tally for one kind of deed in one role. */
data class Deed(val soul: Int, val role: String, val deed: String, val count: Int)

/**
 * A world's book of deeds: who did what, since it was last collected. The world
 * credits; the app collects and keeps the running totals.
 */
class Deeds(
    /** What the souls are in this world: "Miner", "Beaver". */
    val role: String
) {
    private val counts = LinkedHashMap<Triple<Int, String, String>, Int>()

    /** Credits [soul] with [amount] of [deed], working as [trade] — a world can have more than one. */
    fun credit(soul: Int, deed: String, amount: Int = 1, trade: String = role) {
        if (soul < 0 || amount <= 0) return
        val key = Triple(soul, trade, deed)
        counts[key] = (counts[key] ?: 0) + amount
    }

    /** Everything credited since the last call, and a clean page. */
    fun collect(): List<Deed> {
        if (counts.isEmpty()) return emptyList()
        return counts.map { (key, n) -> Deed(key.first, key.second, key.third, n) }.also { counts.clear() }
    }

    /** Uncollected total for [soul] and [deed]; for tests and for a live view. */
    fun pending(soul: Int, deed: String, trade: String = role): Int = counts[Triple(soul, trade, deed)] ?: 0
}
