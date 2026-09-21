package com.hourglass.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hourglass.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "hourglass_timers"
    const val CHANNEL_NAME = "Hourglass Timers"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active HOURGLASS timer and remaining time"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildTimerNotification(
        context: Context,
        taskName: String,
        timeRemaining: String,
        isRunning: Boolean,
        isOverTime: Boolean = false
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val status = when {
            isOverTime -> "OVERTIME +$timeRemaining"
            isRunning -> "$timeRemaining remaining"
            else -> "Paused at $timeRemaining"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("HOURGLASS — $taskName")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }
}
