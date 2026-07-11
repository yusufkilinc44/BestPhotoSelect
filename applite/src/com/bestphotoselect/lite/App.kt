package com.bestphotoselect.lite

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.bestphotoselect.R

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AUTOPILOT,
                getString(R.string.notif_channel_autopilot),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    companion object {
        const val CHANNEL_AUTOPILOT = "autopilot"
    }
}
