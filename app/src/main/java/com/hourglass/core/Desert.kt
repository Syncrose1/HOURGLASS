package com.hourglass.core

import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/** One band of sand in the dune: a single session, in the colour it was run in. */
data class Stratum(
    val sand: TimerSand,
    val millis: Long,
    /** Where this band sits in the dune, `0f` at the base and `1f` at the crest. */
    val start: Float,
    val end: Float
) {
    val thickness: Float get() = end - start
}

/**
 * The dune your banked time builds.
 *
 * Forest plants a tree per session; sand pours a layer. Each finished session becomes
 * a visible band in the colour of the timer that earned it, newest on top, so the pile
 * is a cross-section of how the time was actually spent rather than a score.
 *
 * Height is logarithmic: the first hour has to feel like real progress, and the
 * five-hundredth must still add something, which a linear scale cannot do at both ends.
 */
data class Desert(
    val totalMillis: Long,
    /** `0f..1f` of the frame the dune fills. */
    val height: Float,
    /** Bands from the base upward. Capped, with older sessions merged into the base. */
    val strata: List<Stratum>,
    val sessionCount: Int,
    /** Milestones passed, largest first. */
    val reached: List<Milestone>
) {
    val isEmpty: Boolean get() = sessionCount == 0

    val nextMilestone: Milestone?
        get() = Milestone.entries.firstOrNull { it.millis > totalMillis }

    /** Progress toward [nextMilestone], `0f..1f`. */
    val milestoneProgress: Float
        get() {
            val next = nextMilestone ?: return 1f
            val previous = reached.firstOrNull()?.millis ?: 0L
            val span = (next.millis - previous).toFloat()
            if (span <= 0f) return 1f
            return ((totalMillis - previous) / span).coerceIn(0f, 1f)
        }

    companion object {
        /** Bands the dune can show before the oldest start merging into its base. */
        const val MAX_STRATA = 40

        /** Banked time at which the dune reaches the top of the frame. */
        private const val FULL_HEIGHT_HOURS = 500.0

        fun from(sessions: List<SessionSummary>): Desert {
            val banked = sessions.filter { it.elapsedMillis > 0 }
            val total = banked.sumOf { it.elapsedMillis }

            return Desert(
                totalMillis = total,
                height = heightFor(total),
                strata = strataFor(banked, total),
                sessionCount = banked.size,
                reached = Milestone.entries.filter { it.millis <= total }.reversed()
            )
        }

        /**
         * `0f..1f`, log-scaled. A first session lands visibly off the floor, and the
         * curve still has somewhere to go after hundreds of hours.
         */
        fun heightFor(totalMillis: Long): Float {
            if (totalMillis <= 0L) return 0f
            val hours = totalMillis / 3_600_000.0
            val scaled = ln(1.0 + hours) / ln(1.0 + FULL_HEIGHT_HOURS)
            return min(1.0, scaled).toFloat().coerceAtLeast(MIN_VISIBLE_HEIGHT)
        }

        private const val MIN_VISIBLE_HEIGHT = 0.06f

        /**
         * Bands, base-first, sized by each session's share of the total. Once there are
         * more sessions than bands, the oldest are merged into a single foundation
         * layer rather than being dropped.
         */
        private fun strataFor(sessions: List<SessionSummary>, total: Long): List<Stratum> {
            if (sessions.isEmpty() || total <= 0L) return emptyList()

            // Oldest first, so the newest session ends up at the crest.
            val ordered = sessions.sortedBy { it.epochDay }
            val bands = if (ordered.size <= MAX_STRATA) {
                ordered.map { it.sand to it.elapsedMillis }
            } else {
                val foundationCount = ordered.size - (MAX_STRATA - 1)
                val foundation = ordered.take(foundationCount)
                val rest = ordered.drop(foundationCount)
                listOf(
                    // The base keeps the colour of the oldest session it contains.
                    foundation.first().sand to foundation.sumOf { it.elapsedMillis }
                ) + rest.map { it.sand to it.elapsedMillis }
            }

            var cursor = 0f
            return bands.map { (sand, millis) ->
                val thickness = millis.toFloat() / total
                val stratum = Stratum(sand, millis, cursor, cursor + thickness)
                cursor += thickness
                stratum
            }
        }
    }
}

/** Landmarks the desert gains as time accumulates. */
enum class Milestone(val hours: Int) {
    FIRST_DUNE(1),
    GRASS(5),
    SHRUB(15),
    CACTUS(40),
    OASIS(100),
    PYRAMID(250);

    val millis: Long get() = hours * 3_600_000L

    companion object {
        /** Fraction of the dune's width a landmark sits at, spread so they do not stack. */
        fun offsetFor(milestone: Milestone): Float =
            (0.18f + 0.14f * milestone.ordinal).coerceAtMost(0.88f)
    }
}

/** Kept out of [Desert] so the curve can be exercised on its own. */
internal fun duneCurve(x: Float, height: Float, skew: Float): Float {
    // A windward slope that rises gently and a steeper lee side, which is what makes a
    // shape read as a dune rather than a hill.
    val t = x.coerceIn(0f, 1f)
    val peak = skew.coerceIn(0.2f, 0.8f)
    return if (t <= peak) {
        height * (t / peak).pow(1.6f)
    } else {
        height * (1f - ((t - peak) / (1f - peak)).pow(0.75f))
    }
}
