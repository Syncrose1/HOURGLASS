package com.hourglass.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quicksand_tasks")
data class QuicksandTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationMillis: Long,
    val colour: String = "#A1887F",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val totalTimeTracked: Long = 0L,
    val totalOvertimeMillis: Long = 0L,
    val sessionsOverrun: Int = 0,
    val lastCompletedAt: Long = 0L
)
