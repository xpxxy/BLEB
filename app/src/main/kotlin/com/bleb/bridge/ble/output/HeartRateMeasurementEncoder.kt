package com.bleb.bridge.ble.output

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartRateMeasurementEncoder @Inject constructor() {

    fun encode(bpm: Int, contactDetected: Boolean = true): ByteArray {
        var flags = 0
        // Bit 0: Heart Rate Value Format = 0 (UINT8, since BPM <= 255)
        if (bpm <= 255) {
            // UINT8 format already set (bit 0 = 0)
        }
        // Bit 1: Sensor Contact Status = 1 (contact detected)
        if (contactDetected) {
            flags = flags or 0x02
        }
        // Bit 2: Sensor Contact Supported = 1
        flags = flags or 0x04
        // Bits 3-4: Energy Expended Status = 0 (not present)
        // Bit 5-7: RR-Interval = 0 (not present)

        return byteArrayOf(flags.toByte(), bpm.toByte())
    }
}
