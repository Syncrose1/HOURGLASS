package com.hourglass.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.data.repository.HourglassRepository
import com.hourglass.service.DayRemainingNotifier
import com.hourglass.timer.DayReset
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Keeps the "your day ends in…" notification current.
 *
 * Fifteen minutes is both the shortest interval WorkManager will schedule and exactly
 * the granularity the notification is rounded to, so the two line up: the worker wakes
 * precisely often enough to catch each step down and never more.
 */
@HiltWorker
class DayRemainingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: HourglassRepository,
    private val notifier: DayRemainingNotifier,
    private val dayReset: DayReset
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The same quarter-hour wake-up ends the day on time, app open or not.
        dayReset.runIfDue()

        val enabled = repository.getSetting(HourglassRepository.KEY_DAY_NOTICE) != "off"
        if (!enabled) {
            notifier.cancel()
            return Result.success()
        }

        val bedtime = TimeOfDay.parseOr(
            repository.getSetting(HourglassRepository.KEY_BEDTIME),
            TimeOfDay.DEFAULT_BEDTIME
        )
        val wake = TimeOfDay.parseOr(
            repository.getSetting(HourglassRepository.KEY_WAKE_TIME),
            TimeOfDay.DEFAULT_WAKE
        )

        val now = Calendar.getInstance()
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        if (Bedtime.isPastBedtime(minuteOfDay, bedtime, wake)) {
            // The day is over; saying so once is enough, and a countdown to nothing
            // would just be noise on the lock screen all night.
            notifier.showDayOver()
        } else {
            notifier.showRemaining(Bedtime.minutesUntil(minuteOfDay, bedtime))
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "day_remaining"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DayRemainingWorker>(
                Bedtime.ROUNDING_MINUTES.toLong(),
                TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                // Keep, not replace: replacing on every launch would reset the period
                // and the notification would drift.
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
