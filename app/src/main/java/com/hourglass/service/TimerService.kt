package com.hourglass.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class TimerService : Service() {
    companion object {
        const val ACTION_START = "com.hourglass.action.START_TIMER"
        const val ACTION_UPDATE = "com.hourglass.action.UPDATE_TIMER"
        const val ACTION_STOP = "com.hourglass.action.STOP_TIMER"

        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_TIME_REMAINING = "time_remaining"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_IS_OVERTIME = "is_overtime"
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_UPDATE
        val taskName = intent?.getStringExtra(EXTRA_TASK_NAME) ?: "HOURGLASS"
        val timeRemaining = intent?.getStringExtra(EXTRA_TIME_REMAINING) ?: "00:00"
        val isRunning = intent?.getBooleanExtra(EXTRA_IS_RUNNING, true) ?: true
        val isOverTime = intent?.getBooleanExtra(EXTRA_IS_OVERTIME, false) ?: false

        when (action) {
            ACTION_START -> {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.buildTimerNotification(
                        this,
                        taskName,
                        timeRemaining,
                        isRunning,
                        isOverTime
                    )
                )
            }
            ACTION_UPDATE -> {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.buildTimerNotification(
                        this,
                        taskName,
                        timeRemaining,
                        isRunning,
                        isOverTime
                    )
                )
            }
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
