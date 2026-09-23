package com.hourglass

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hourglass.data.souls.SoulLedger
import com.hourglass.service.DayRemainingNotifier
import com.hourglass.service.NotificationHelper
import com.hourglass.work.DayRemainingWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HourglassApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var dayNotifier: DayRemainingNotifier

    /** WorkManager initialises on demand so it can be handed Hilt's worker factory. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Channels exist before anything needs to post to them.
        NotificationHelper.createChannel(this)
        dayNotifier.createChannel()
        DayRemainingWorker.schedule(this)
        DayRemainingWorker.refreshNow(this)
        SoulLedger.init(this)
    }
}
