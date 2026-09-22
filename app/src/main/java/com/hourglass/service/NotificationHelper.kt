package com.hourglass.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.hourglass.MainActivity
import com.hourglass.R
import com.hourglass.core.ActiveTimer
import com.hourglass.core.TimeFormat

/** Builds the ongoing timer notification and its channel. */
object NotificationHelper {
    /** The quiet, ongoing "a timer is running" notification. */
    const val CHANNEL_ID = "hourglass_timers"

    /** The one-shot "time is up" alert. Separate channel so it can be silenced alone. */
    const val CHANNEL_DONE_ID = "hourglass_complete"

    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_DONE_ID = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_timers_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_timers_description)
                setShowBadge(false)
                enableVibration(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE_ID,
                context.getString(R.string.channel_complete_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_complete_description)
                setShowBadge(true)
                enableVibration(true)
            }
        )
    }

    /**
     * Fired once when a timer reaches its allocation. The ongoing notification keeps
     * counting into overtime behind this one — the user chose the allocation, they did
     * not agree to be stopped at it.
     */
    fun buildCompletion(context: Context, timer: ActiveTimer): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE_ID)
            .setContentTitle(context.getString(R.string.notification_complete_title, timer.name))
            .setContentText(
                context.getString(
                    R.string.notification_complete_body,
                    TimeFormat.compact(timer.totalDurationMillis)
                )
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(
                0,
                context.getString(R.string.stop),
                serviceIntent(context, TimerService.ACTION_STOP)
            )
            .build()

    fun build(context: Context, timer: ActiveTimer): Notification {
        val remaining = TimeFormat.signedClock(timer.remainingMillis)
        val status = when {
            timer.isOvertime -> context.getString(R.string.notification_overtime, remaining)
            timer.isRunning -> context.getString(R.string.notification_remaining, remaining)
            else -> context.getString(R.string.notification_paused, remaining)
        }

        val toggleLabel = if (timer.isRunning) R.string.pause else R.string.resume

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(timer.name)
            .setContentText(status)
            .setSubText(context.getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(toggleLabel), serviceIntent(context, TimerService.ACTION_TOGGLE))
            .addAction(0, context.getString(R.string.stop), serviceIntent(context, TimerService.ACTION_STOP))
            .build()
    }

    /**
     * Placeholder used only when the service is promoted with nothing to show, so the
     * platform's "must call startForeground" contract is met before shutting down.
     */
    fun buildIdle(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun serviceIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private const val REQUEST_CONTENT = 0
}
