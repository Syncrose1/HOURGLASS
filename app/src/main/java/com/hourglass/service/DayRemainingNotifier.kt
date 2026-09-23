package com.hourglass.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.hourglass.MainActivity
import com.hourglass.R
import com.hourglass.core.Bedtime
import com.hourglass.core.DayLines
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The standing reminder of how much day is left.
 *
 * Deliberately phrased as the day ending rather than as a bedtime: a bedtime is
 * something you negotiate with, and the end of a day is not. The figure is floored to
 * a quarter of an hour so it steps instead of ticking — "2 hours 15 minutes" holding
 * still and then dropping to "2 hours" lands as a loss, where 2:13 sliding to 2:07
 * lands as nothing at all.
 */
@Singleton
class DayRemainingNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_day_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_day_description)
            setShowBadge(false)
            enableVibration(false)
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    fun showRemaining(minutesUntilBedtime: Int) {
        // The figure, then a word of encouragement for this part of the day.
        val now = Calendar.getInstance()
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val epochDay = TimeUnit.MILLISECONDS.toDays(now.timeInMillis + now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET))
        post(Bedtime.describeDayRemaining(minutesUntilBedtime), DayLines.lineFor(minuteOfDay, epochDay))
    }

    fun showDayOver() {
        post(context.getString(R.string.day_over_body))
    }

    fun cancel() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            NotificationManagerCompat.from(context).cancel(SUMMARY_ID)
        }
    }

    private fun post(text: String, line: String? = null) {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(text)
            .apply {
                if (line != null) {
                    setContentText(line)
                    // Room for the whole line when the notification is expanded.
                    setStyle(NotificationCompat.BigTextStyle().bigText(line))
                }
            }
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Its own group, with its own summary below, so the system does not sweep it
            // into a bundle with a running timer's.
            .setGroup(GROUP)
            .build()

        // Checked inline rather than behind a helper: declining the permission should
        // mean nothing happens, not an exception thrown and swallowed every quarter
        // hour — and lint only credits the guard when it can see it from the call.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            NotificationManagerCompat.from(context).notify(
                SUMMARY_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(text)
                    .setContentIntent(pending)
                    .setGroup(GROUP)
                    .setGroupSummary(true)
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "hourglass_day"
        const val NOTIFICATION_ID = 1003
        private const val SUMMARY_ID = 1005
        private const val GROUP = "hourglass.day"
        private const val REQUEST_CODE = 2
    }
}
