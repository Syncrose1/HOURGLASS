package com.hourglass.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One finished run, kept for habit feedback. */
@Entity(
    tableName = "timer_sessions",
    indices = [Index("taskId"), Index("endedAt")]
)
data class TimerSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    /** Denormalised so history survives the timer being archived or renamed. */
    val taskName: String,
    val isQuicksand: Boolean,
    val plannedDurationMillis: Long,
    val elapsedMillis: Long,
    val overtimeMillis: Long,
    val startedAt: Long,
    val endedAt: Long,
    val completed: Boolean
)
