package com.hourglass.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldPacerTest {

    private val pacer = WorldPacer()

    @Test
    fun `the objective is meant to land short of complete when the timer ends`() {
        assertEquals(0f, pacer.desiredProgress(0f), 0.0001f)
        assertEquals(pacer.completionTarget, pacer.desiredProgress(1f), 0.0001f)
        assertEquals(pacer.completionTarget / 2f, pacer.desiredProgress(0.5f), 0.0001f)
    }

    @Test
    fun `overtime keeps paying, with diminishing returns`() {
        val atEnd = pacer.desiredProgress(1f)
        val shortOvertime = pacer.desiredProgress(1.25f)
        val longOvertime = pacer.desiredProgress(2.5f)

        assertTrue("overtime should add progress", shortOvertime > atEnd)
        assertTrue(longOvertime > shortOvertime)
        assertTrue("never past complete", longOvertime < 1f)
        // The first stretch of overtime buys more than the next equal stretch.
        assertTrue(
            (shortOvertime - atEnd) > (pacer.desiredProgress(1.5f) - shortOvertime)
        )
    }

    @Test
    fun `desired progress only ever goes forward`() {
        var previous = -1f
        var t = 0f
        while (t <= 4f) {
            val desired = pacer.desiredProgress(t)
            assertTrue("went backwards at $t", desired >= previous)
            previous = desired
            t += 0.01f
        }
    }

    @Test
    fun `falling behind makes the world work harder`() {
        val behind = pacer.effortFor(timerProgress = 0.5f, objectiveProgress = 0.1f)
        val onPace = pacer.effortFor(timerProgress = 0.5f, objectiveProgress = 0.425f)
        val ahead = pacer.effortFor(timerProgress = 0.5f, objectiveProgress = 0.8f)

        assertTrue("behind should push: $behind", behind > 1f)
        assertEquals("on pace should idle at one", 1f, onPace, 0.01f)
        assertTrue("ahead should ease off: $ahead", ahead < 1f)
    }

    @Test
    fun `effort stays within bounds however wrong things get`() {
        listOf(
            0f to 1f,
            1f to 0f,
            5f to 0f,
            0f to 0f,
            -1f to 2f
        ).forEach { (timer, objective) ->
            val effort = pacer.effortFor(timer, objective)
            assertTrue("effort $effort out of range for $timer/$objective", effort in 0.005f..8f)
        }
    }

    @Test
    fun `a world that keeps running ahead learns to slow down`() {
        // Proportional-only pacing settled ahead of the clock forever. The baseline
        // has to keep falling while the world stays ahead.
        val pacer = WorldPacer()
        val first = pacer.effortFor(timerProgress = 0.3f, objectiveProgress = 0.4f)
        repeat(500) { pacer.effortFor(timerProgress = 0.3f, objectiveProgress = 0.4f) }
        val later = pacer.effortFor(timerProgress = 0.3f, objectiveProgress = 0.4f)
        assertTrue("effort should keep falling: $first then $later", later < first * 0.5f)
    }

    @Test
    fun `a world that keeps falling behind learns to push`() {
        val pacer = WorldPacer()
        val first = pacer.effortFor(timerProgress = 0.4f, objectiveProgress = 0.2f)
        repeat(500) { pacer.effortFor(timerProgress = 0.4f, objectiveProgress = 0.2f) }
        val later = pacer.effortFor(timerProgress = 0.4f, objectiveProgress = 0.2f)
        assertTrue("effort should keep rising: $first then $later", later > first * 1.5f)
    }

    @Test
    fun `a world that cannot keep up is pushed but never given the answer`() {
        // The pacer's only lever is effort; it can never award progress directly.
        val stuck = pacer.effortFor(timerProgress = 0.9f, objectiveProgress = 0f)
        assertTrue("should be pushing hard", stuck > 3f)
        assertTrue("but still bounded", stuck <= 8f)
    }
}
