package com.bleb.bridge.ble.parser

import android.util.Log
import com.bleb.bridge.util.BleConstants

/**
 * Parser for Xiaomi Mi Band heart rate broadcast via BLE advertising packets.
 *
 * Based on the protocol used by miband-heart-rate (github.com/Tnze/miband-heart-rate):
 * - Manufacturer Data with Company ID 0x0157 (Huami/Xiaomi)
 * - Heart rate value is at byte offset 3 of the manufacturer data
 *
 * Reference: Mi Band 4/5/6/7 "运动心率广播" (sport heart rate broadcast) feature.
 */
class MiBeaconParser : HeartRateParser {

    override fun parse(scanRecord: ByteArray): Int? {
        if (scanRecord.isEmpty()) return null

        var offset = 0
        while (offset < scanRecord.size - 1) {
            val length = scanRecord[offset].toInt() and 0xFF
            if (length == 0 || offset + length >= scanRecord.size) return null

            val type = scanRecord[offset + 1].toInt() and 0xFF
            val data = scanRecord.copyOfRange(offset + 2, offset + 2 + length - 1)

            when (type) {
                AD_TYPE_MANUFACTURER_SPECIFIC -> {
                    if (data.size >= 4) {
                        // Company ID is little-endian in the first 2 bytes
                        val companyId = ((data[1].toInt() and 0xFF) shl 8) or
                            (data[0].toInt() and 0xFF)

                        if (companyId == BleConstants.HUAMI_COMPANY_ID) {
                            // Manufacturer data format (Android AD structure):
                            // data[0..1] = company ID (little-endian, already verified as 0x0157)
                            // data[2..] = payload (same as Rust bluest's manufacturer_data.data)
                            //
                            // Per miband-heart-rate: HR is at manufacturer_data.data[3]
                            // Which is payload byte 3 = Android data[5]
                            val payloadHex = data.joinToString(" ") { "%02X".format(it) }
                            Log.d(TAG, "Mi Band payload (${data.size}B): $payloadHex")

                            if (data.size >= 6) {
                                val hr = data[5].toInt() and 0xFF
                                if (hr in BleConstants.HR_MIN_BPM..BleConstants.HR_MAX_BPM) {
                                    Log.d(TAG, "Mi Band HR from payload[3]: $hr bpm")
                                    return hr
                                }
                            }
                            // Fallback: try other positions
                            for (offset in 2..minOf(data.size - 1, 10)) {
                                val hr = data[offset].toInt() and 0xFF
                                if (hr in BleConstants.HR_MIN_BPM..BleConstants.HR_MAX_BPM) {
                                    Log.d(TAG, "Mi Band HR from data[$offset]: $hr bpm")
                                    return hr
                                }
                            }
                        }
                    }
                }
            }
            offset += length + 1
        }
        return null
    }

    companion object {
        private const val TAG = "BLEB:MiBeacon"
        private const val AD_TYPE_MANUFACTURER_SPECIFIC = 0xFF
    }
}
