package com.bleb.bridge.ble.model

data class HeartRateSample(
    val bpm: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val source: HrSource = HrSource.UNKNOWN
)

enum class HrSource {
    PASSIVE_SCAN,
    GATT_CONNECTION,
    UNKNOWN
}
