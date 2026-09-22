package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesertTest {

    private fun session(day: Long, minutes: Long, sand: TimerSand = TimerSand.AMBER) =
        SessionSummary(
            epochDay = day,
            taskName = "Task",
            sand = sand,
            elapsedMillis = minutes * 60_000L,
            overtimeMillis = 0L,
            completed = true
        )

    @Test
    fun `an empty desert has no dune`() {
        val desert = Desert.from(emptyList())
        assertTrue(desert.isEmpty)
        assertEquals(0f, desert.height, 0.0001f)
        assertTrue(desert.strata.isEmpty())
        assertTrue(desert.reached.isEmpty())
    }

    @Test
    fun `the first session is visible above the floor`() {
        val desert = Desert.from(listOf(session(1, 25)))
        assertTrue("height ${desert.height}", desert.height > 0.05f)
        assertEquals(1, desert.sessionCount)
    }

    @Test
    fun `height grows with time but never leaves the frame`() {
        val small = Desert.heightFor(3_600_000L)
        val large = Desert.heightFor(200 * 3_600_000L)
        val huge = Desert.heightFor(100_000 * 3_600_000L)
        assertTrue(small < large)
        assertTrue(large <= 1f)
        assertEquals(1f, huge, 0.0001f)
    }

    @Test
    fun `strata run base first and tile the whole dune`() {
        val desert = Desert.from(
            listOf(
                session(1, 30, TimerSand.AMBER),
                session(2, 60, TimerSand.AQUA),
                session(3, 30, TimerSand.ROSE)
            )
        )
        assertEquals(3, desert.strata.size)
        assertEquals(TimerSand.AMBER, desert.strata.first().sand)
        assertEquals(TimerSand.ROSE, desert.strata.last().sand)
        assertEquals(0f, desert.strata.first().start, 0.0001f)
        assertEquals(1f, desert.strata.last().end, 0.0001f)
        // The middle session is twice as long, so its band is twice as thick.
        assertEquals(
            desert.strata[0].thickness * 2f,
            desert.strata[1].thickness,
            0.0001f
        )
    }

    @Test
    fun `bands never overlap or leave gaps`() {
        val desert = Desert.from((1..12).map { session(it.toLong(), it * 7L) })
        desert.strata.zipWithNext().forEach { (lower, upper) ->
            assertEquals(lower.end, upper.start, 0.0001f)
        }
    }

    @Test
    fun `older sessions merge into a foundation rather than disappearing`() {
        val sessions = (1..(Desert.MAX_STRATA + 25)).map { session(it.toLong(), 10) }
        val desert = Desert.from(sessions)

        assertEquals(Desert.MAX_STRATA, desert.strata.size)
        assertEquals(sessions.size, desert.sessionCount)
        // The merged base carries the time of everything folded into it.
        assertTrue(desert.strata.first().thickness > desert.strata.last().thickness)
        assertEquals(1f, desert.strata.last().end, 0.0001f)
        assertEquals(
            sessions.sumOf { it.elapsedMillis },
            desert.strata.sumOf { it.millis }
        )
    }

    @Test
    fun `zero-length sessions are ignored`() {
        val desert = Desert.from(listOf(session(1, 0), session(2, 30)))
        assertEquals(1, desert.sessionCount)
        assertEquals(1, desert.strata.size)
    }

    @Test
    fun `milestones unlock in order`() {
        val oneHour = Desert.from(listOf(session(1, 60)))
        assertEquals(listOf(Milestone.FIRST_DUNE), oneHour.reached)
        assertEquals(Milestone.GRASS, oneHour.nextMilestone)

        val twentyHours = Desert.from(listOf(session(1, 20 * 60)))
        assertEquals(
            listOf(Milestone.SHRUB, Milestone.GRASS, Milestone.FIRST_DUNE),
            twentyHours.reached
        )
    }

    @Test
    fun `the final milestone has nothing after it`() {
        val desert = Desert.from(listOf(session(1, 400L * 60)))
        assertNull(desert.nextMilestone)
        assertEquals(1f, desert.milestoneProgress, 0.0001f)
    }

    @Test
    fun `milestone progress runs from the previous landmark to the next`() {
        // Three hours: past the 1h mark, two of the four hours to the 5h mark.
        val desert = Desert.from(listOf(session(1, 3 * 60)))
        assertEquals(Milestone.GRASS, desert.nextMilestone)
        assertEquals(0.5f, desert.milestoneProgress, 0.0001f)
    }

    @Test
    fun `the dune curve rises to its peak and falls away`() {
        val height = 1f
        val peak = duneCurve(0.4f, height, skew = 0.4f)
        assertEquals(height, peak, 0.0001f)
        assertTrue(duneCurve(0f, height, 0.4f) < peak)
        assertTrue(duneCurve(1f, height, 0.4f) < peak)
        // Windward side rises more gently than the lee side falls.
        assertTrue(duneCurve(0.2f, height, 0.4f) < duneCurve(0.6f, height, 0.4f))
    }
}
