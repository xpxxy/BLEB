package com.bleb.bridge.ble.output

import kotlinx.coroutines.flow.Flow

interface OutputBleManager {
    val connectedDevices: Flow<Int>

    suspend fun start(initialHr: Int): Result<Unit>
    fun updateHeartRate(bpm: Int)
    fun stop()
}
