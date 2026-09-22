package com.hourglass.core

/** One finished run, reduced to what the insights screen needs. */
data class SessionSummary(
    /** Days since the epoch, in the viewer's own timezone — the caller does that conversion. */
    val epochDay: Long,
    val taskName: String,
    val sand: TimerSand,
    val elapsedMillis: Long,
    val overtimeMillis: Long,
    val completed: Boolean
)

/** Focus banked on one day. */
data class DayTotal(val epochDay: Long, val millis: Long)

/** Focus banked against one task over the window. */
data class TaskTotal(
    val name: String,
    val sand: TimerSand,
    val millis: Long,
    val sessions: Int,
    /** True for the synthetic row the tail folds into. */
    val isOther: Boolean = false
)

/**
 * Everything the insights screen reads, derived in one pass over the session log.
 *
 * Pure: the caller supplies the day boundaries it has already resolved, so none of
 * this needs a clock or a timezone and all of it is testable.
 */
data class Insights(
    val todayMillis: Long,
    val windowMillis: Long,
    /** One entry per day in the window, oldest first, zero-filled. */
    val days: List<DayTotal>,
    /** Largest daily total in the window; 0 when the window is empty. */
    val bestDayMillis: Long,
    /** Tasks by time banked, descending, with the tail folded into an "Other" row. */
    val tasks: List<TaskTotal>,
    val sessionCount: Int,
    val completedCount: Int,
    val overrunCount: Int,
    /** Consecutive days with any focus, counting back from today. */
    val streakDays: Int
) {
    val isEmpty: Boolean get() = sessionCount == 0

    /** Share of sessions that reached their allocation, as `0f..1f`. */
    val completionRate: Float
        get() = if (sessionCount == 0) 0f else completedCount.toFloat() / sessionCount

    companion object {
        /** Days shown in the column chart. */
        const val WINDOW_DAYS = 7

        /**
         * Tasks shown before the tail folds into "Other" — the point at which
         * a categorical palette stops being reliably distinguishable.
         */
        const val MAX_TASK_ROWS = 6

        fun from(
            sessions: List<SessionSummary>,
            today: Long,
            windowDays: Int = WINDOW_DAYS,
            maxTaskRows: Int = MAX_TASK_ROWS
        ): Insights {
            val firstDay = today - (windowDays - 1)
            val inWindow = sessions.filter { it.epochDay in firstDay..today }

            val byDay = inWindow.groupBy { it.epochDay }
            val days = (firstDay..today).map { day ->
                DayTotal(day, byDay[day]?.sumOf { it.elapsedMillis } ?: 0L)
            }

            return Insights(
                todayMillis = byDay[today]?.sumOf { it.elapsedMillis } ?: 0L,
                windowMillis = inWindow.sumOf { it.elapsedMillis },
                days = days,
                bestDayMillis = days.maxOfOrNull { it.millis } ?: 0L,
                tasks = foldTasks(inWindow, maxTaskRows),
                sessionCount = inWindow.size,
                completedCount = inWindow.count { it.completed },
                overrunCount = inWindow.count { it.overtimeMillis > 0 },
                streakDays = streak(sessions, today)
            )
        }

        private fun foldTasks(sessions: List<SessionSummary>, maxRows: Int): List<TaskTotal> {
            if (sessions.isEmpty()) return emptyList()

            val ranked = sessions
                .groupBy { it.taskName }
                .map { (name, rows) ->
                    TaskTotal(
                        name = name,
                        // A task's colour can change; the most recent run wins.
                        sand = rows.last().sand,
                        millis = rows.sumOf { it.elapsedMillis },
                        sessions = rows.size
                    )
                }
                .sortedWith(compareByDescending<TaskTotal> { it.millis }.thenBy { it.name })

            if (ranked.size <= maxRows) return ranked

            val head = ranked.take(maxRows - 1)
            val tail = ranked.drop(maxRows - 1)
            return head + TaskTotal(
                name = OTHER_ROW,
                sand = TimerSand.DEFAULT,
                millis = tail.sumOf { it.millis },
                sessions = tail.sumOf { it.sessions },
                isOther = true
            )
        }

        /**
         * Counts back from [today] while every day has focus on it. Today being empty
         * does not break a streak that is still live — the day is not over yet — so
         * the count resumes from yesterday.
         */
        private fun streak(sessions: List<SessionSummary>, today: Long): Int {
            if (sessions.isEmpty()) return 0
            val active = sessions.filter { it.elapsedMillis > 0 }.map { it.epochDay }.toSet()
            if (active.isEmpty()) return 0

            var day = if (today in active) today else today - 1
            var count = 0
            while (day in active) {
                count++
                day--
            }
            return count
        }

        const val OTHER_ROW = "Other"
    }
}
