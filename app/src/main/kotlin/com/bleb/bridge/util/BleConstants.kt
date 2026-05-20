package com.bleb.bridge.util

import java.util.UUID

object BleConstants {
    // Standard BLE Heart Rate Profile UUIDs (128-bit full form)
    val HR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    val BODY_SENSOR_LOCATION_UUID: UUID = UUID.fromString("00002A38-0000-1000-8000-00805F9B34FB")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // 16-bit UUIDs for advertising
    val HR_SERVICE_16BIT: java.util.UUID = java.util.UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")

    // Xiaomi MiBeacon service UUID (16-bit: 0xFE95)
    val MIBEACON_SERVICE_16BIT: java.util.UUID = java.util.UUID.fromString("0000FE95-0000-1000-8000-00805F9B34FB")

    // Xiaomi/Huami company ID for Mi Band manufacturer data (0x0157)
    const val HUAMI_COMPANY_ID = 0x0157

    // Known Mi Band device name prefixes
    val MI_BAND_NAME_PREFIXES = listOf(
        "Mi Smart Band",
        "Mi Band",
        "MiBand",
        "Xiaomi Band",
        "Xiaomi Smart Band"
    )

    // Scan timeouts (milliseconds)
    const val SCAN_TIMEOUT_INITIAL = 30_000L
    const val SCAN_TIMEOUT_RECOVERY = 15_000L

    // Retry backoff (milliseconds)
    const val RETRY_BASE_DELAY = 1_000L
    const val RETRY_MAX_DELAY = 30_000L

    // Data validation
    const val HR_MIN_BPM = 30
    const val HR_MAX_BPM = 220

    // Throttling: max heart rate updates per second to output
    const val OUTPUT_THROTTLE_MS = 250L
}
