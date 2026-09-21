package com.hourglass.viewmodel

data class TimerState(
    val taskId: Long = -1,
    val timeRemaining: Long = 0,
    val totalDuration: Long = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isOverTime: Boolean = false
)
