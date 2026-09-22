package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {

    private val today = 20_000L

    private fun session(
        day: Long,
        name: String = "Flashcards",
        minutes: Long = 30,
        overtime: Long = 0,
        completed: Boolean = true,
        sand: TimerSand = TimerSand.AMBER
    ) = SessionSummary(
        epochDay = day,
        taskName = name,
        sand = sand,
        elapsedMillis = minutes * 60_000L,
        overtimeMillis = overtime,
        completed = completed
    )

    @Test
    fun `empty log produces a zero-filled window`() {
        val insights = Insights.from(emptyList(), today)
        assertTrue(insights.isEmpty)
        assertEquals(Insights.WINDOW_DAYS, insights.days.size)
        assertTrue(insights.days.all { it.millis == 0L })
        assertEquals(0L, insights.bestDayMillis)
        assertEquals(0, insights.streakDays)
        assertEquals(0f, insights.completionRate, 0.0001f)
    }

    @Test
    fun `window is zero-filled and ordered oldest first`() {
        val insights = Insights.from(listOf(session(today), session(today - 3)), today)
        assertEquals(today - 6, insights.days.first().epochDay)
        assertEquals(today, insights.days.last().epochDay)
        assertEquals(listOf(0L, 0L, 0L, 30L, 0L, 0L, 30L), insights.days.map { it.millis / 60_000 })
    }

    @Test
    fun `sessions outside the window are excluded`() {
        val insights = Insights.from(
            listOf(session(today), session(today - 7), session(today + 1)),
            today
        )
        assertEquals(1, insights.sessionCount)
        assertEquals(30 * 60_000L, insights.windowMillis)
    }

    @Test
    fun `today and best day are picked out of the window`() {
        val insights = Insights.from(
            listOf(session(today, minutes = 20), session(today - 2, minutes = 95)),
            today
        )
        assertEquals(20 * 60_000L, insights.todayMillis)
        assertEquals(95 * 60_000L, insights.bestDayMillis)
        assertEquals(115 * 60_000L, insights.windowMillis)
    }

    @Test
    fun `tasks are ranked by time banked`() {
        val insights = Insights.from(
            listOf(
                session(today, name = "Essay", minutes = 15),
                session(today, name = "Flashcards", minutes = 40),
                session(today - 1, name = "Flashcards", minutes = 10)
            ),
            today
        )
        assertEquals(listOf("Flashcards", "Essay"), insights.tasks.map { it.name })
        assertEquals(50 * 60_000L, insights.tasks.first().millis)
        assertEquals(2, insights.tasks.first().sessions)
    }

    @Test
    fun `the task tail folds into a single Other row`() {
        val sessions = (1..10).map { session(today, name = "Task $it", minutes = it.toLong()) }
        val insights = Insights.from(sessions, today)

        assertEquals(Insights.MAX_TASK_ROWS, insights.tasks.size)
        val other = insights.tasks.last()
        assertTrue(other.isOther)
        assertEquals(Insights.OTHER_ROW, other.name)
        // Top five are 10..6 minutes; the remaining five are 5+4+3+2+1 = 15.
        assertEquals(15 * 60_000L, other.millis)
        assertEquals(5, other.sessions)
        assertTrue(insights.tasks.dropLast(1).none { it.isOther })
    }

    @Test
    fun `a task at exactly the row cap is not folded`() {
        val sessions = (1..Insights.MAX_TASK_ROWS).map { session(today, name = "Task $it") }
        val insights = Insights.from(sessions, today)
        assertEquals(Insights.MAX_TASK_ROWS, insights.tasks.size)
        assertTrue(insights.tasks.none { it.isOther })
    }

    @Test
    fun `completion and overrun counts come from the window`() {
        val insights = Insights.from(
            listOf(
                session(today, completed = true),
                session(today, completed = true, overtime = 60_000),
                session(today, completed = false)
            ),
            today
        )
        assertEquals(3, insights.sessionCount)
        assertEquals(2, insights.completedCount)
        assertEquals(1, insights.overrunCount)
        assertEquals(2f / 3f, insights.completionRate, 0.0001f)
    }

    @Test
    fun `streak counts consecutive days back from today`() {
        val insights = Insights.from(
            listOf(session(today), session(today - 1), session(today - 2), session(today - 4)),
            today
        )
        assertEquals(3, insights.streakDays)
    }

    @Test
    fun `an empty today does not break a streak that is still live`() {
        val insights = Insights.from(
            listOf(session(today - 1), session(today - 2)),
            today
        )
        assertEquals(2, insights.streakDays)
    }

    @Test
    fun `a gap before yesterday ends the streak`() {
        val insights = Insights.from(listOf(session(today - 2), session(today - 3)), today)
        assertEquals(0, insights.streakDays)
    }

    @Test
    fun `streak looks past the chart window`() {
        val sessions = (0..20L).map { session(today - it) }
        val insights = Insights.from(sessions, today)
        assertEquals(21, insights.streakDays)
        assertEquals(Insights.WINDOW_DAYS, insights.days.size)
    }
}
