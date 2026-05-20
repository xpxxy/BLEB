package com.bleb.bridge.util

import android.content.Context
import android.os.PowerManager as AndroidPowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pm: AndroidPowerManager =
        context.getSystemService(Context.POWER_SERVICE) as AndroidPowerManager

    private var wakeLock: AndroidPowerManager.WakeLock? = null

    @Synchronized
    fun acquireWakeLock(reason: String) {
        if (wakeLock?.isHeld == true) return
        wakeLock = pm.newWakeLock(
            AndroidPowerManager.PARTIAL_WAKE_LOCK,
            "BLEB:$reason"
        ).apply {
            acquire(10 * 60 * 1000L) // 10-minute timeout for safety
        }
    }

    @Synchronized
    fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: RuntimeException) {
            // WakeLock already released or invalid
        } finally {
            wakeLock = null
        }
    }

    val isHeld: Boolean
        @Synchronized get() = wakeLock?.isHeld == true
}
