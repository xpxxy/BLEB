package com.bleb.bridge.ble.input

import android.bluetooth.le.ScanResult
import com.bleb.bridge.util.BleConstants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiBandIdentifier @Inject constructor() {

    fun isMiBand(result: ScanResult): Boolean {
        val name = result.device.name ?: return false
        return BleConstants.MI_BAND_NAME_PREFIXES.any { prefix ->
            name.contains(prefix, ignoreCase = true)
        }
    }

    fun isMiBand(name: String): Boolean {
        return BleConstants.MI_BAND_NAME_PREFIXES.any { prefix ->
            name.contains(prefix, ignoreCase = true)
        }
    }

    fun extractAddress(name: String): String? {
        // Parse address from scan result if device name matches
        return null // Cannot extract address from name alone
    }
}
