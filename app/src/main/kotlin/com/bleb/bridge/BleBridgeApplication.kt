package com.bleb.bridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class  BleBridgeApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "heart_rate_bridge"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Heart Rate Bridge",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing notification for BLE Heart Rate Bridge service"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
