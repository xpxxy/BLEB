package com.bleb.bridge.bridge

import android.util.Log
import com.bleb.bridge.ble.input.InputEvent
import com.bleb.bridge.ble.input.PassiveScanManager
import com.bleb.bridge.ble.model.BleDevice
import com.bleb.bridge.ble.model.HeartRateSample
import com.bleb.bridge.ble.model.HrSource
import com.bleb.bridge.ble.output.OutputBleManager
import com.bleb.bridge.data.repository.HeartRateRepository
import com.bleb.bridge.util.BleConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgeOrchestrator @Inject constructor(
    private val passiveScanManager: PassiveScanManager,
    private val outputManager: OutputBleManager,
    private val heartRateRepository: HeartRateRepository,
    private val appPowerManager: com.bleb.bridge.util.PowerManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BridgeState>(BridgeState.Idle)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var currentDevice: BleDevice? = null
    private var outputStarted = false

    companion object {
        private const val TAG = "BLEB:Orchestrator"
    }

    fun start(targetDevice: BleDevice) {
        if (_state.value !is BridgeState.Idle) return

        currentDevice = targetDevice
        _state.value = BridgeState.Scanning
        appPowerManager.acquireWakeLock("bridging")
        outputStarted = false

        Log.i(TAG, "Bridge starting: ${targetDevice.name} (${targetDevice.address})")
        startPassiveScan(targetDevice)
    }

    fun stop() {
        Log.i(TAG, "Bridge stopping")
        scanJob?.cancel()
        scanJob = null
        outputManager.stop()
        outputStarted = false
        appPowerManager.releaseWakeLock()
        heartRateRepository.clear()
        _state.value = BridgeState.Idle
    }

    private fun startPassiveScan(device: BleDevice) {
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                Log.i(TAG, "Passive scan started")
                passiveScanManager.startScanning(device, useBalancedMode = true)
                    .collect { event ->
                        when (event) {
                            is InputEvent.HeartRateUpdate ->
                                onHeartRateReceived(event.bpm, HrSource.PASSIVE_SCAN)
                            is InputEvent.DeviceFound -> {}
                            is InputEvent.Error -> Log.w(TAG, "Scan: ${event.message}")
                            is InputEvent.DeviceLost -> {}
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Passive scan ended: ${e.message}")
            }
        }
    }

    private fun onHeartRateReceived(bpm: Int, source: HrSource) {
        if (bpm !in BleConstants.HR_MIN_BPM..BleConstants.HR_MAX_BPM) return

        Log.i(TAG, "HR: $bpm bpm ($source)")

        heartRateRepository.updateSample(HeartRateSample(bpm = bpm, source = source))

        if (!outputStarted) {
            scope.launch {
                outputManager.start(bpm)
                    .onSuccess { outputStarted = true; Log.i(TAG, "BLE output started") }
                    .onFailure { Log.e(TAG, "Output start failed: ${it.message}") }
            }
        }
        outputManager.updateHeartRate(bpm)

        currentDevice?.let { device ->
            _state.value = BridgeState.Bridging(
                device = device, heartRate = bpm, connectedClients = 0
            )
        }
    }

    fun setTargetDevice(address: String) {
        Log.d(TAG, "Target device: $address")
    }
}
