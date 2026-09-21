package com.hourglass.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationMillis: Long,
    val colour: String, // hex color string for customisation
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val totalTimeTracked: Long = 0L, // cumulative time spent across sessions
    val totalOvertimeMillis: Long = 0L,
    val sessionsCompleted: Int = 0,
    val sessionsOverrun: Int = 0,
    val lastCompletedAt: Long = 0L
)
