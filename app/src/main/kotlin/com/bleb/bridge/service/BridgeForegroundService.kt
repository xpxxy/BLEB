package com.bleb.bridge.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.bleb.bridge.R
import com.bleb.bridge.bridge.BridgeOrchestrator
import com.bleb.bridge.bridge.BridgeState
import com.bleb.bridge.ble.model.BleDevice
import com.bleb.bridge.data.repository.HeartRateRepository
import com.bleb.bridge.util.PowerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BridgeForegroundService : Service() {

    @Inject lateinit var powerManager: PowerManager
    @Inject lateinit var notificationManager: BridgeNotificationManager
    @Inject lateinit var orchestrator: BridgeOrchestrator
    @Inject lateinit var heartRateRepository: HeartRateRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bridgeState = MutableStateFlow<BridgeState>(BridgeState.Idle)
    val bridgeState: StateFlow<BridgeState> = _bridgeState.asStateFlow()

    private var targetDeviceAddress: String = ""
    private var targetDeviceName: String = ""
    private lateinit var binder: ServiceBinder

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_TAG = "bridge_service"
        private const val TAG = "BLEB:ForegroundService"
    }

    override fun onCreate() {
        super.onCreate()
        binder = ServiceBinder(
            bridgeState = bridgeState,
            heartRate = heartRateRepository.latestSample,
            onStart = { startBridging() },
            onStop = { stopBridging() },
            onSetTargetDevice = { addr ->
                targetDeviceAddress = addr
                orchestrator.setTargetDevice(addr)
            }
        )

        // Observe orchestrator state and forward to local state + notifications
        serviceScope.launch {
            orchestrator.state.collect { state ->
                _bridgeState.value = state
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationManager.buildNotification(
            bridgeStateText = getString(R.string.notification_stopped)
        )
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopBridging()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startBridging() {
        if (targetDeviceAddress.isEmpty()) {
            Log.w(TAG, "No target device set")
            return
        }

        val device = BleDevice(
            name = targetDeviceName.ifEmpty { "Mi Band 5" },
            address = targetDeviceAddress
        )

        Log.i(TAG, "Starting bridge for ${device.name} (${device.address})")
        orchestrator.start(device)
    }

    private fun stopBridging() {
        Log.i(TAG, "Stopping bridge")
        orchestrator.stop()
        updateNotification(BridgeState.Idle)
    }

    private fun updateNotification(state: BridgeState) {
        val hr = heartRateRepository.latestSample.value?.bpm
        val stateText = getStateText(state)
        val notification = notificationManager.buildNotification(
            bridgeStateText = stateText,
            heartRate = hr
        )
        notificationManager.updateNotification(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
    }

    private fun getStateText(state: BridgeState): String = when (state) {
        BridgeState.Idle -> getString(R.string.notification_stopped)
        is BridgeState.Scanning -> getString(R.string.notification_scanning)
        is BridgeState.Connected -> getString(R.string.notification_connected)
        is BridgeState.Bridging -> getString(R.string.notification_bridging, state.heartRate)
        is BridgeState.Error -> state.message
    }
}
