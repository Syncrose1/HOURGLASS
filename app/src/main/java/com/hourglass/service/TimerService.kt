package com.hourglass.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.hourglass.core.ActiveTimer
import com.hourglass.timer.TimerController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the running timer alive and on screen.
 *
 * The service is a renderer, not a source of truth: it subscribes to
 * [TimerController.state] and redraws the notification only when the displayed second
 * actually changes, instead of being handed a fresh intent on every tick the way the
 * old `Handler`-driven version was.
 */
@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var controller: TimerController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True once [startForeground] has been called; the platform requires exactly one. */
    private var foregrounded = false

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The platform gives a startForegroundService only a few seconds to promote
        // itself, so post before doing anything that could take a turn of the loop.
        render(controller.state.value)
        if (!foregrounded) {
            // Nothing to show — promote with a placeholder purely to satisfy the
            // contract, then shut down cleanly on the next line.
            startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.buildIdle(this))
            foregrounded = true
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_TOGGLE -> controller.toggle()
            ACTION_STOP -> controller.stop()
        }

        observeTimer()
        observeCompletions()
        return START_NOT_STICKY
    }

    private var observing = false

    private fun observeTimer() {
        if (observing) return
        observing = true
        scope.launch {
            controller.state
                // Collapse the engine's 4Hz ticks down to the once-a-second the
                // notification text can actually show.
                .map { timer -> timer?.let(NotificationSnapshot::of) }
                .distinctUntilChanged()
                .collect { render(controller.state.value) }
        }
    }

    private var observingCompletions = false

    private fun observeCompletions() {
        if (observingCompletions) return
        observingCompletions = true
        scope.launch {
            controller.completions.collect { timer ->
                runCatching {
                    NotificationManagerCompat.from(this@TimerService)
                        .notify(
                            NotificationHelper.NOTIFICATION_DONE_ID,
                            NotificationHelper.buildCompletion(this@TimerService, timer)
                        )
                }
            }
        }
    }

    private fun render(timer: ActiveTimer?) {
        if (timer == null) {
            if (foregrounded) stopForegroundAndSelf()
            return
        }
        val notification = NotificationHelper.build(this, timer)
        if (!foregrounded) {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            foregrounded = true
        } else {
            // Without POST_NOTIFICATIONS this quietly does nothing, which is the right
            // outcome: the timer itself keeps running either way.
            runCatching {
                NotificationManagerCompat.from(this)
                    .notify(NotificationHelper.NOTIFICATION_ID, notification)
            }
        }
    }

    private fun stopForegroundAndSelf() {
        // The alert is about a moment that has passed; it should not outlive the timer.
        runCatching {
            NotificationManagerCompat.from(this).cancel(NotificationHelper.NOTIFICATION_DONE_ID)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        foregrounded = false
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** The parts of a timer the notification renders, at second resolution. */
    private data class NotificationSnapshot(
        val name: String,
        val second: Long,
        val isRunning: Boolean,
        val isOvertime: Boolean
    ) {
        companion object {
            fun of(timer: ActiveTimer) = NotificationSnapshot(
                name = timer.name,
                second = timer.remainingMillis / 1000,
                isRunning = timer.isRunning,
                isOvertime = timer.isOvertime
            )
        }
    }

    companion object {
        const val ACTION_SHOW = "com.hourglass.action.SHOW_TIMER"
        const val ACTION_TOGGLE = "com.hourglass.action.TOGGLE_TIMER"
        const val ACTION_STOP = "com.hourglass.action.STOP_TIMER"
    }
}
