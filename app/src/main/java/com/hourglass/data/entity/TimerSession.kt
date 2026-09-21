package com.hourglass.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timer_sessions")
data class TimerSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val taskName: String,
    val isQuicksand: Boolean,
    val plannedDurationMillis: Long,
    val elapsedMillis: Long,
    val overtimeMillis: Long,
    val startedAt: Long,
    val endedAt: Long,
    val completed: Boolean
)
