package com.hourglass

import android.app.Application
import com.hourglass.service.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HourglassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Created up front so the channel exists before the first timer needs it.
        NotificationHelper.createChannel(this)
    }
}
