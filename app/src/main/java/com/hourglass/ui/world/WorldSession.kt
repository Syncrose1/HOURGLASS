package com.hourglass.ui.world

import com.hourglass.core.TimerRef
import com.hourglass.core.world.MineWorld
import com.hourglass.core.world.WorldPacer
import kotlin.random.Random

/**
 * One timer's world and the pacer that keeps it on the clock.
 *
 * Owned by [WorldRegistry] rather than by a composable, so the tile on the wall and
 * the focus view are two windows onto the same world instead of two worlds.
 */
class WorldSession(val world: MineWorld) {

    private val pacer = WorldPacer()

    /** Runtime randomness; the ground itself came from the seed. */
    private val random = Random(System.nanoTime())

    /** False until the first step: an untouched preview can be adopted by a session. */
    var stepped: Boolean = false
        private set

    /** Bumped on every step so the view knows to repaint. */
    var version: Int = 0
        private set

    /**
     * Moves the world one frame along. [timerProgress] runs past 1 in overtime.
     *
     * A world that has fallen well behind its clock — the app was in the background,
     * or the process was restarted — takes several steps a frame until it has caught
     * up, so returning to a timer shows the world where it ought to be rather than
     * where it was left.
     */
    fun advance(timerProgress: Float) {
        val behind = pacer.desiredProgress(timerProgress) - world.objectiveProgress
        val steps = if (behind > CATCH_UP_GAP) CATCH_UP_STEPS else 1
        repeat(steps) {
            world.step(pacer.effortFor(timerProgress, world.objectiveProgress), random)
        }
        stepped = true
        version++
    }

    private companion object {
        const val CATCH_UP_GAP = 0.04f
        const val CATCH_UP_STEPS = 24
    }
}

/**
 * Every timer's world, for as long as the process lives.
 *
 * An idle timer shows a freshly generated world — the ground it will dig. Starting the
 * timer adopts that exact world rather than rolling a new one, so the preview is a
 * promise. Each new session gets new ground; worlds are never reused.
 */
object WorldRegistry {

    private val idle = HashMap<TimerRef, WorldSession>()
    private val live = HashMap<TimerRef, Pair<Long, WorldSession>>()

    fun obtain(ref: TimerRef, sessionStartedAt: Long?, durationMillis: Long): WorldSession {
        if (sessionStartedAt == null) {
            live.remove(ref)
            return idle.getOrPut(ref) { create(System.nanoTime() xor ref.hashCode().toLong(), durationMillis) }
        }

        live[ref]?.let { (startedAt, session) -> if (startedAt == sessionStartedAt) return session }

        // Adopt the preview the user was looking at, if it has not been touched.
        val session = idle.remove(ref)?.takeIf { !it.stepped }
            ?: create(sessionStartedAt, durationMillis)
        live[ref] = sessionStartedAt to session
        return session
    }

    private fun create(seed: Long, durationMillis: Long) = WorldSession(
        MineWorld(
            width = WORLD_WIDTH,
            height = WORLD_HEIGHT,
            seed = seed,
            richness = MineWorld.richnessFor(durationMillis / 60_000f)
        )
    )

    const val WORLD_WIDTH = 72
    const val WORLD_HEIGHT = 96
}
