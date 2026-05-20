package com.bleb.bridge.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bleb.bridge.BleBridgeApplication
import com.bleb.bridge.MainActivity
import com.bleb.bridge.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgeNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    private val contentIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildNotification(
        bridgeStateText: String,
        heartRate: Int? = null
    ): android.app.Notification {
        val contentText = if (heartRate != null) {
            context.getString(R.string.notification_bridging, heartRate)
        } else {
            bridgeStateText
        }

        return NotificationCompat.Builder(context, BleBridgeApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateNotification(tag: String, id: Int, notification: android.app.Notification) {
        notificationManager.notify(tag, id, notification)
    }

    fun cancel(tag: String, id: Int) {
        notificationManager.cancel(tag, id)
    }
}
