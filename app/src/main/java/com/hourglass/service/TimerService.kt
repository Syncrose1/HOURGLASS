package com.hourglass.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hourglass.core.ActiveTimer
import com.hourglass.timer.TimerController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    // The permission guard is repeated at each notify rather than factored into a
    // helper: catching the SecurityException also works, but a check states the
    // intent better, and it is the only form the platform's lint credits — it will
    // not follow the test through a function call.

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
        if (!foregrounded && controller.state.value == null && intent?.action != ACTION_STOP) {
            // Restarted by the system after the process was killed, with nothing in
            // memory yet: promote with a placeholder, bring the timer back from its
            // journal, and carry on showing it — or bow out if there is none.
            startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.buildIdle(this))
            foregrounded = true
            controller.restore()
            scope.launch {
                val restored = withTimeoutOrNull(RESTORE_TIMEOUT_MILLIS) {
                    controller.state.first { it != null }
                }
                if (restored == null) {
                    stopForegroundAndSelf()
                } else {
                    render(restored)
                    observeTimer()
                    observeCompletions()
                }
            }
            return START_STICKY
        }
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
        // Sticky, so a timer outlives the process being killed: the system restarts
        // the service and the notification comes back.
        return START_STICKY
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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this@TimerService,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
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
        val promoting = !foregrounded
        if (promoting) {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            foregrounded = true
        }
        // Without POST_NOTIFICATIONS this quietly does nothing, which is the right
        // outcome: the timer itself keeps running either way.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val manager = NotificationManagerCompat.from(this)
            if (!promoting) manager.notify(NotificationHelper.NOTIFICATION_ID, notification)
            // Its own group summary, so the system does not bundle it with anything.
            if (promoting) manager.notify(NotificationHelper.SUMMARY_ID, NotificationHelper.buildSummary(this))
        }
    }

    private fun stopForegroundAndSelf() {
        // The alert is about a moment that has passed; it should not outlive the timer.
        runCatching {
            NotificationManagerCompat.from(this).cancel(NotificationHelper.SUMMARY_ID)
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

        /** How long a restarted service waits for the journal before giving up. */
        private const val RESTORE_TIMEOUT_MILLIS = 3_000L
    }
}
