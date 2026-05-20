package com.bleb.bridge.ble.model

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val isMiBand: Boolean = false
)
