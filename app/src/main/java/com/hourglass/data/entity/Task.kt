package com.hourglass.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A recurring sand timer with an allocation the user set, plus its running totals. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationMillis: Long,
    /** `#AARRGGBB`, chosen from the timer palette. */
    val colour: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Archived timers stay in the table so their session history keeps its name. */
    val isActive: Boolean = true,
    val totalTimeTracked: Long = 0L,
    val totalOvertimeMillis: Long = 0L,
    val sessionsCompleted: Int = 0,
    val sessionsOverrun: Int = 0,
    val lastCompletedAt: Long = 0L
)
