package com.bleb.bridge.bridge

import com.bleb.bridge.ble.model.BleDevice

sealed interface BridgeState {
    data object Idle : BridgeState
    data object Scanning : BridgeState
    data class Connected(val device: BleDevice) : BridgeState
    data class Bridging(
        val device: BleDevice,
        val heartRate: Int,
        val connectedClients: Int
    ) : BridgeState
    data class Error(val message: String) : BridgeState
}
