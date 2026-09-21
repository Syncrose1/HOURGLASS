package com.hourglass.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hourglass.data.dao.TimerSessionDao
import com.hourglass.data.database.HourglassDatabase
import com.hourglass.data.entity.TaskEntity
import com.hourglass.data.entity.QuicksandTaskEntity
import com.hourglass.data.entity.TimerSessionEntity
import com.hourglass.service.NotificationHelper
import com.hourglass.service.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

data class UiTask(
    val id: Long,
    val name: String,
    val durationMillis: Long,
    val colourHex: String,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val timeRemaining: Long = 0,
    val isOverTime: Boolean = false,
    val totalDuration: Long = 0,
    val isQuicksand: Boolean = false
)

class HourglassViewModel(application: Application) : AndroidViewModel(application) {
    private val taskDao = HourglassDatabase.getInstance(application).taskDao()
    private val quicksandDao = HourglassDatabase.getInstance(application).quicksandDao()
    private val sessionDao = HourglassDatabase.getInstance(application).timerSessionDao()
    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val appContext = getApplication<Application>()

    private val _activeTimer = MutableStateFlow<TimerState?>(null)
    val activeTimer: StateFlow<TimerState?> = _activeTimer

    private val _tasksWithTimer = MutableStateFlow<List<UiTask>>(emptyList())
    val tasksWithTimer: StateFlow<List<UiTask>> = _tasksWithTimer.asStateFlow()

    private var currentRunningTaskId: Long = -1
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var pausedRemaining: Long = 0
    private var sessionStartedAt: Long = 0
    private var notificationStarted = false

    init {
        viewModelScope.launch {
            combine(
                taskDao.observeActiveTasks(),
                quicksandDao.observeActiveTasks(),
                _activeTimer
            ) { tasks, quicksands, timer ->
                buildUiTaskList(tasks, quicksands, timer)
            }.collect { list ->
                _tasksWithTimer.value = list
            }
        }
    }

    private fun buildUiTaskList(
        tasks: List<TaskEntity>,
        quicksands: List<QuicksandTaskEntity>,
        timer: TimerState?
    ): List<UiTask> {
        val result = mutableListOf<UiTask>()

        tasks.forEach { task ->
            result.add(
                UiTask(
                    id = task.id,
                    name = task.name,
                    durationMillis = task.durationMillis,
                    colourHex = task.colour,
                    isRunning = timer?.taskId == task.id && timer.isRunning,
                    isPaused = timer?.taskId == task.id && timer.isPaused,
                    timeRemaining = if (timer?.taskId == task.id) timer.timeRemaining else task.durationMillis,
                    isOverTime = timer?.taskId == task.id && timer.isOverTime,
                    totalDuration = if (timer?.taskId == task.id) timer.totalDuration else task.durationMillis,
                    isQuicksand = false
                )
            )
        }

        quicksands.forEach { task ->
            result.add(
                UiTask(
                    id = task.id,
                    name = task.name,
                    durationMillis = task.durationMillis,
                    colourHex = task.colour,
                    isRunning = timer?.taskId == task.id && timer.isRunning,
                    isPaused = timer?.taskId == task.id && timer.isPaused,
                    timeRemaining = if (timer?.taskId == task.id) timer.timeRemaining else task.durationMillis,
                    isOverTime = timer?.taskId == task.id && timer.isOverTime,
                    totalDuration = if (timer?.taskId == task.id) timer.totalDuration else task.durationMillis,
                    isQuicksand = true
                )
            )
        }

        return result.sortedBy { it.isQuicksand }
    }

    fun addTask(name: String, hours: Int, minutes: Int, colourHex: String) {
        viewModelScope.launch {
            val totalMillis = (hours * 3600L + minutes * 60L) * 1000L
            taskDao.insertTask(TaskEntity(name = name, durationMillis = totalMillis, colour = colourHex))
        }
    }

    fun addQuicksandTask(name: String, hours: Int, minutes: Int, colourHex: String) {
        viewModelScope.launch {
            val totalMillis = (hours * 3600L + minutes * 60L) * 1000L
            quicksandDao.insert(QuicksandTaskEntity(name = name, durationMillis = totalMillis, colour = colourHex))
        }
    }

    fun deleteTask(id: Long) = viewModelScope.launch {
        taskDao.getTaskById(id)?.let { taskDao.deleteTask(it) }
    }

    fun deleteQuicksand(id: Long) = viewModelScope.launch {
        quicksandDao.getById(id)?.let { quicksandDao.delete(it) }
    }

    fun startTimer(taskId: Long) {
        if (currentRunningTaskId != -1L && currentRunningTaskId != taskId) {
            stopTimerInternal()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val task = taskDao.getTaskById(taskId)
            if (task != null) {
                beginTimer(taskId, task.durationMillis)
                return@launch
            }
            val quicksand = quicksandDao.getById(taskId)
            if (quicksand != null) {
                beginTimer(taskId, quicksand.durationMillis)
            }
        }
    }

    private suspend fun beginTimer(taskId: Long, duration: Long) {
        withContext(Dispatchers.Main) {
            pausedRemaining = duration
            sessionStartedAt = System.currentTimeMillis()
            currentRunningTaskId = taskId
            _activeTimer.value = TimerState(
                taskId = taskId,
                timeRemaining = duration,
                totalDuration = duration,
                isRunning = true,
                isPaused = false,
                isOverTime = false
            )
            startTick(duration)
        }
    }

    fun pauseTimer() {
        val timer = _activeTimer.value ?: return
        if (!timer.isRunning) return
        val runnable = timerRunnable
        if (runnable != null) {
            timerHandler?.removeCallbacks(runnable)
        }
        pausedRemaining = timer.timeRemaining
        _activeTimer.value = timer.copy(isRunning = false, isPaused = true)
        updateNotification()
    }

    fun resumeTimer(taskId: Long) {
        val current = _activeTimer.value ?: return
        if (current.taskId == taskId) {
            _activeTimer.value = current.copy(isRunning = true, isPaused = false)
            startTick(pausedRemaining)
        }
    }

    fun stopTimer() {
        stopTimerInternal()
    }

    private fun stopTimerInternal() {
        val handler = timerHandler
        val runnable = timerRunnable
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable)
        }
        timerHandler = null
        timerRunnable = null

        val timer = _activeTimer.value
        val startedAt = sessionStartedAt
        if (timer != null && timer.taskId != -1L) {
            viewModelScope.launch(Dispatchers.IO) {
                val elapsed = max(0, timer.totalDuration - timer.timeRemaining)
                recordSession(timer.taskId, timer.totalDuration, elapsed, startedAt)
            }
        }

        currentRunningTaskId = -1
        pausedRemaining = 0
        sessionStartedAt = 0
        _activeTimer.value = null
        notificationStarted = false
        sendServiceAction(TimerService.ACTION_STOP)
        notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
    }

    private suspend fun recordSession(taskId: Long, plannedDuration: Long, elapsed: Long, startedAt: Long) {
        val overtime = max(0, elapsed - plannedDuration)
        val endedAt = System.currentTimeMillis()
        val task = try { taskDao.getTaskById(taskId) } catch (_: Exception) { null }
        val quicksand = try { quicksandDao.getById(taskId) } catch (_: Exception) { null }

        if (task != null) {
            taskDao.updateTask(
                task.copy(
                    totalTimeTracked = task.totalTimeTracked + elapsed,
                    totalOvertimeMillis = task.totalOvertimeMillis + overtime,
                    sessionsCompleted = task.sessionsCompleted + if (elapsed >= plannedDuration) 1 else 0,
                    sessionsOverrun = task.sessionsOverrun + if (overtime > 0) 1 else 0,
                    lastCompletedAt = endedAt
                )
            )
            sessionDao.insert(
                TimerSessionEntity(
                    taskId = taskId,
                    taskName = task.name,
                    isQuicksand = false,
                    plannedDurationMillis = plannedDuration,
                    elapsedMillis = elapsed,
                    overtimeMillis = overtime,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    completed = elapsed >= plannedDuration
                )
            )
        } else if (quicksand != null) {
            quicksandDao.update(
                quicksand.copy(
                    totalTimeTracked = quicksand.totalTimeTracked + elapsed,
                    totalOvertimeMillis = quicksand.totalOvertimeMillis + overtime,
                    sessionsOverrun = quicksand.sessionsOverrun + if (overtime > 0) 1 else 0,
                    lastCompletedAt = endedAt
                )
            )
            sessionDao.insert(
                TimerSessionEntity(
                    taskId = taskId,
                    taskName = quicksand.name,
                    isQuicksand = true,
                    plannedDurationMillis = plannedDuration,
                    elapsedMillis = elapsed,
                    overtimeMillis = overtime,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    completed = elapsed >= plannedDuration
                )
            )
        }
    }

    private fun startTick(duration: Long) {
        val handler = Handler(Looper.getMainLooper())
        timerHandler = handler
        val startTime = System.currentTimeMillis()
        val initialRemaining = duration

        val runnable = object : Runnable {
            override fun run() {
                val current = _activeTimer.value ?: return
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = initialRemaining - elapsed
                _activeTimer.value = current.copy(
                    timeRemaining = remaining,
                    isRunning = true,
                    isPaused = false,
                    isOverTime = remaining < 0
                )
                updateNotification()
                handler.postDelayed(this, 100)
            }
        }
        timerRunnable = runnable
        handler.post(runnable)
    }

    private fun updateNotification() {
        val timer = _activeTimer.value ?: return
        if (timer.taskId == -1L) return

        viewModelScope.launch(Dispatchers.IO) {
            val taskName = try {
                taskDao.getTaskById(timer.taskId)?.name
                    ?: quicksandDao.getById(timer.taskId)?.name
                    ?: "HOURGLASS"
            } catch (_: Exception) {
                "HOURGLASS"
            }
            withContext(Dispatchers.Main) {
                val timeStr = formatNotificationTime(timer.timeRemaining)
                sendNotificationUpdate(taskName, timeStr, timer.isRunning, timer.isOverTime)
            }
        }
    }

    private fun sendNotificationUpdate(
        taskName: String,
        timeRemaining: String,
        isRunning: Boolean,
        isOverTime: Boolean
    ) {
        val action = if (notificationStarted) TimerService.ACTION_UPDATE else TimerService.ACTION_START
        notificationStarted = true
        sendServiceAction(action, taskName, timeRemaining, isRunning, isOverTime)
    }

    private fun sendServiceAction(
        action: String,
        taskName: String = "",
        timeRemaining: String = "",
        isRunning: Boolean = false,
        isOverTime: Boolean = false
    ) {
        val intent = Intent(appContext, TimerService::class.java).apply {
            this.action = action
            putExtra(TimerService.EXTRA_TASK_NAME, taskName)
            putExtra(TimerService.EXTRA_TIME_REMAINING, timeRemaining)
            putExtra(TimerService.EXTRA_IS_RUNNING, isRunning)
            putExtra(TimerService.EXTRA_IS_OVERTIME, isOverTime)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {
            // Notification permission or background-start restrictions can prevent this.
        }
    }

    private fun formatNotificationTime(millis: Long): String {
        val absoluteMillis = absMillis(millis)
        val totalSeconds = max(0, absoluteMillis) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val value = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
        return if (millis < 0) "+$value" else value
    }

    private fun absMillis(value: Long): Long = if (value < 0) -value else value

    override fun onCleared() {
        super.onCleared()
        val r = timerRunnable
        if (r != null) {
            timerHandler?.removeCallbacks(r)
        }
    }
}
