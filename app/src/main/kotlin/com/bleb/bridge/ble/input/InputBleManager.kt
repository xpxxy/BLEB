package com.bleb.bridge.ble.input

import com.bleb.bridge.ble.model.BleDevice
import kotlinx.coroutines.flow.Flow

sealed interface InputEvent {
    data class HeartRateUpdate(val bpm: Int) : InputEvent
    data class DeviceFound(val device: BleDevice) : InputEvent
    data object DeviceLost : InputEvent
    data class Error(val message: String) : InputEvent
}

interface InputBleManager {
    val heartRate: Flow<Int>
    val deviceRssi: Flow<Int>

    suspend fun startScanning(target: BleDevice, useBalancedMode: Boolean = false): Flow<InputEvent>
    fun stop()
}
