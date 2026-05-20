package com.bleb.bridge.service

import android.os.Binder
import com.bleb.bridge.bridge.BridgeState
import com.bleb.bridge.ble.model.HeartRateSample
import kotlinx.coroutines.flow.StateFlow

internal class ServiceBinder(
    val bridgeState: StateFlow<BridgeState>,
    val heartRate: StateFlow<HeartRateSample?>,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val onSetTargetDevice: (String) -> Unit
) : Binder() {

    fun startBridge() = onStart()
    fun stopBridge() = onStop()
    fun setTargetDevice(address: String) = onSetTargetDevice(address)
}
