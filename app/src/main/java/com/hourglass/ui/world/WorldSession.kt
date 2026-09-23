package com.hourglass.ui.world

import com.hourglass.core.TimerRef
import com.hourglass.core.world.AntWorld
import com.hourglass.core.world.BattleWorld
import com.hourglass.core.world.ForestWorld
import com.hourglass.core.world.HarbourWorld
import com.hourglass.core.world.IslandWorld
import com.hourglass.core.world.MineWorld
import com.hourglass.core.world.RiverWorld
import com.hourglass.core.world.SiegeWorld
import com.hourglass.core.world.World
import com.hourglass.core.world.WorldKind
import com.hourglass.core.world.WorldPacer
import kotlin.random.Random

/**
 * One timer's world and the pacer that keeps it on the clock.
 *
 * Owned by [WorldRegistry] rather than by a composable, so the tile on the wall and
 * the focus view are two windows onto the same world instead of two worlds.
 */
class WorldSession(val world: World) {

    private val pacer = world.newPacer()

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

    fun obtain(
        ref: TimerRef,
        sessionStartedAt: Long?,
        durationMillis: Long,
        kind: WorldKind
    ): WorldSession {
        if (sessionStartedAt == null) {
            live.remove(ref)
            idle[ref]?.let { if (it.world.kind == kind) return it }
            return create(kind, System.nanoTime() xor ref.hashCode().toLong(), durationMillis)
                .also { idle[ref] = it }
        }

        live[ref]?.let { (startedAt, session) ->
            if (startedAt == sessionStartedAt && session.world.kind == kind) return session
        }

        // Adopt the preview the user was looking at, if it has not been touched.
        val session = idle.remove(ref)?.takeIf { !it.stepped && it.world.kind == kind }
            ?: create(kind, sessionStartedAt, durationMillis)
        live[ref] = sessionStartedAt to session
        return session
    }

    private fun create(kind: WorldKind, seed: Long, durationMillis: Long): WorldSession {
        val minutes = durationMillis / 60_000f
        val richness = MineWorld.richnessFor(minutes)
        val world: World = when (kind) {
            WorldKind.MINE -> MineWorld(WORLD_WIDTH, WORLD_HEIGHT, seed, richness)
            WorldKind.RIVER -> RiverWorld(WORLD_WIDTH, WORLD_HEIGHT, seed, richness * RIVER_SCALE)
            WorldKind.ANTS -> AntWorld(WORLD_WIDTH, WORLD_HEIGHT, seed, AntWorld.quotaFor(minutes))
            WorldKind.ISLAND -> IslandWorld(WORLD_WIDTH, WORLD_HEIGHT, seed, IslandWorld.quotaFor(minutes))
            WorldKind.FOREST -> ForestWorld(WORLD_WIDTH, WORLD_HEIGHT, seed, ForestWorld.quotaFor(minutes))
            WorldKind.BATTLE -> BattleWorld(WORLD_WIDTH, WORLD_HEIGHT, seed, BattleWorld.pointsFor(minutes))
            WorldKind.HARBOUR -> HarbourWorld(WORLD_WIDTH, WORLD_HEIGHT, seed, HarbourWorld.quotaFor(minutes))
            WorldKind.SIEGE -> SiegeWorld(WORLD_WIDTH, WORLD_HEIGHT, seed, SiegeWorld.quotaFor(minutes))
        }
        return WorldSession(world)
    }

    /** A fresh world for a preview, sized for a short demo timer and never registered. */
    fun sample(kind: WorldKind): WorldSession =
        create(kind, System.nanoTime(), durationMillis = SAMPLE_DURATION_MILLIS)

    private const val SAMPLE_DURATION_MILLIS = 10 * 60_000L

    /** Jam cells per mine load: a log is quicker work than a seam of ore. */
    private const val RIVER_SCALE = 5

    const val WORLD_WIDTH = 72
    const val WORLD_HEIGHT = 96
}
