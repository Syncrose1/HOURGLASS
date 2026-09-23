package com.hourglass.core.world

import kotlin.math.exp
import kotlin.random.Random

/** The kinds of world a timer can run. */
enum class WorldKind {
    /** A crew digs seams out of layered ground and hauls them to a cart. */
    MINE,

    /** Beavers dismantle a logjam and let a held-back lake flow down its valley. */
    RIVER,

    /** A colony forages a patch of ground from above, trails and all. */
    ANTS,

    /** An undersea volcano builds an island, flow by flow. */
    ISLAND,

    /** A lumber crew fells a stand of trees and stacks the timber. */
    FOREST,

    /** Two armies contest a valley, flag by flag. */
    BATTLE,

    /** Ships come and go at a quay; a crane and dockers fill the warehouse. */
    HARBOUR,

    /** Masons raise a castle while a trebuchet knocks bits off it. */
    SIEGE;

    /** The value persisted with a timer. */
    val token: String get() = name.lowercase()

    companion object {
        val DEFAULT = MINE

        fun parse(value: String?): WorldKind =
            entries.firstOrNull { it.token.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * A little world that runs for the length of a timer.
 *
 * The world is genuinely simulated: nothing about what happens in it is scripted, and
 * no two runs are the same. What is governed is only the *tempo* — see [WorldPacer].
 */
interface World {
    val width: Int
    val height: Int

    /** Palette indices, row-major, one per cell. Index 0 is empty. */
    val cells: IntArray

    /** How much of what this world is trying to do is done, `0f..1f`. */
    val objectiveProgress: Float

    /** A short statement of what the world is working toward, for the UI. */
    val objective: String

    /** Which kind of world this is; decides how the UI colours its cells. */
    val kind: WorldKind

    /**
     * Who did what, for the ten [Souls]. Null for a world with nobody in it — a
     * volcano builds its island on its own.
     */
    val deeds: Deeds? get() = null

    /** The souls on shift here, each once. */
    val shift: List<Int> get() = emptyList()

    /** Copies the world into [buffer] with its inhabitants drawn in. */
    fun renderInto(buffer: IntArray)

    /**
     * Where the story is, top to bottom, for a view that has to crop: 0 keeps the top
     * of the world in frame, 1 the bottom. A mine's headframe and stockpile are at
     * the surface; a river's lake, jam and valley sit in the middle under open sky.
     */
    val focusY: Float get() = 0.5f

    /**
     * A pacer tuned to how quickly this world answers to effort. Most answer within
     * seconds; a battle answers over minutes, and a pacer that pushes as if it were
     * digging only whipsaws the line.
     */
    fun newPacer(): WorldPacer = WorldPacer()

    /**
     * Advances one tick.
     *
     * [effort] scales how much the world's inhabitants get done — above 1 they work
     * harder, below 1 they dawdle. It never changes *what* they decide to do.
     */
    fun step(effort: Float, random: Random)
}

/**
 * Ties an emergent world to a clock without scripting it.
 *
 * This is the whole trick, and it is worth being precise about: the simulation is
 * never told what to do or when to finish. It is told how hard to work. Each tick the
 * pacer compares how far along the world's objective is against how far along the
 * timer is, and nudges the inhabitants' effort up or down to close the gap.
 *
 * So everything that makes a run interesting stays free — where the tunnels go, which
 * seams get found, which miner gets buried, whether the dam gives way at one end or
 * the middle. Only the rate is governed. A world that genuinely cannot finish simply
 * arrives at the end less complete, which is a truthful outcome rather than a broken
 * one.
 *
 * The target at the timer's end is deliberately short of complete, so there is always
 * something left for overtime to buy.
 */
class WorldPacer(
    /** Where the objective should be when the timer runs out. */
    val completionTarget: Float = DEFAULT_COMPLETION_TARGET,
    /** How hard the pacer corrects. Higher is twitchier. */
    private val gain: Float = 5f,
    private val minEffort: Float = 0.005f,
    private val maxEffort: Float = 8f,
    /** How quickly the baseline effort learns the pace this world actually needs. */
    private val learningRate: Float = 0.02f
) {

    /**
     * The effort that keeps this world on the clock, learned as it runs.
     *
     * A purely proportional pacer centred on an effort of 1 has a steady-state error:
     * for a long timer, "working normally" is far too fast, so the world settles
     * permanently ahead of the clock by exactly the margin it takes to slow it down —
     * and a two-hour mine finished at 100% instead of 85%. The baseline integrates the
     * error so that, once settled, the world tracks the clock with no offset at all.
     */
    private var baseline = 1f.coerceIn(minEffort, maxEffort)

    /**
     * Where the objective ought to be at [timerProgress], which runs past 1 into
     * overtime. Linear to [completionTarget] while the timer runs, then asymptotic
     * toward complete — overtime keeps paying, with diminishing returns.
     */
    fun desiredProgress(timerProgress: Float): Float {
        val t = timerProgress.coerceAtLeast(0f)
        if (t <= 1f) return t * completionTarget
        val overtime = t - 1f
        val remaining = 1f - completionTarget
        return completionTarget + remaining * (1f - Math.exp(-(overtime * OVERTIME_RATE).toDouble()).toFloat())
    }

    /**
     * How hard the world should work this tick. Call once per tick: the pacer learns
     * from each call.
     */
    fun effortFor(timerProgress: Float, objectiveProgress: Float): Float {
        val error = desiredProgress(timerProgress) - objectiveProgress
        // Integral in log space, so the baseline scales rather than shifts: halving an
        // effort of 0.02 and halving an effort of 4 are the same kind of correction.
        baseline = (baseline * exp(learningRate * error)).coerceIn(minEffort, maxEffort)
        return (baseline * exp(gain * error)).coerceIn(minEffort, maxEffort)
    }

    companion object {
        /**
         * Short of complete on purpose: arriving at exactly 100% as the clock runs out
         * would make the ending feel scripted, and leaves overtime nothing to offer.
         */
        const val DEFAULT_COMPLETION_TARGET = 0.85f

        /** How fast overtime closes the remaining gap. */
        private const val OVERTIME_RATE = 1.6f
    }
}
