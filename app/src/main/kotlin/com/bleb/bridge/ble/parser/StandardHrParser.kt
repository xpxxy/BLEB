package com.bleb.bridge.ble.parser

import android.util.Log
import com.bleb.bridge.util.BleConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StandardHrParser : HeartRateParser {

    override fun parse(scanRecord: ByteArray): Int? {
        if (scanRecord.isEmpty()) return null

        var offset = 0
        while (offset < scanRecord.size - 1) {
            val length = scanRecord[offset].toInt() and 0xFF
            if (length == 0 || offset + length >= scanRecord.size) return null

            val type = scanRecord[offset + 1].toInt() and 0xFF
            val data = scanRecord.copyOfRange(offset + 2, offset + 2 + length - 1)

            when (type) {
                AD_TYPE_SERVICE_DATA_16BIT -> {
                    if (data.size >= 4) {
                        val serviceUuid = ByteBuffer.wrap(data, 0, 2)
                            .order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                        if (serviceUuid == 0x180D) {
                            return parseHrMeasurement(data.copyOfRange(2, data.size))
                        }
                    }
                }
                AD_TYPE_SERVICE_DATA_128BIT -> {
                    if (data.size >= 18) {
                        val uuidBytes = data.copyOfRange(0, 16)
                        val uuid = uuidFromBytes(uuidBytes)
                        if (uuid == BleConstants.HR_SERVICE_UUID) {
                            return parseHrMeasurement(data.copyOfRange(16, data.size))
                        }
                    }
                }
            }
            offset += length + 1
        }
        return null
    }

    fun parseFromCharacteristic(value: ByteArray): Int? {
        return parseHrMeasurement(value)
    }

    private fun parseHrMeasurement(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt() and 0xFF
        val isUint16 = (flags and 0x01) != 0
        return if (isUint16 && data.size >= 3) {
            ((data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8))
        } else if (!isUint16 && data.size >= 2) {
            data[1].toInt() and 0xFF
        } else {
            null
        }
    }

    private fun uuidFromBytes(bytes: ByteArray): java.util.UUID {
        var msb = 0L
        var lsb = 0L
        for (i in 0..7) msb = (msb shl 8) or (bytes[i].toLong() and 0xFF)
        for (i in 8..15) lsb = (lsb shl 8) or (bytes[i].toLong() and 0xFF)
        return java.util.UUID(msb, lsb)
    }

    companion object {
        private const val TAG = "BLEB:StdHrParser"
        private const val AD_TYPE_SERVICE_DATA_16BIT = 0x16
        private const val AD_TYPE_SERVICE_DATA_128BIT = 0x21
    }
}
