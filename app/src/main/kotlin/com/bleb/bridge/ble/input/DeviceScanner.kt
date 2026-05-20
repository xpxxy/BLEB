
package com.bleb.bridge.ble.input

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.util.Log
import com.bleb.bridge.ble.model.BleDevice
import com.bleb.bridge.util.BleConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var isScanning = false

    @SuppressLint("MissingPermission")
    fun scanForDevices(scanDurationMs: Long = 20_000L): Flow<ScanResultEvent> = callbackFlow {
        // ---- Diagnostic checks ----
        Log.i(TAG, "========== SCAN DIAGNOSTICS ==========")

        // Check Bluetooth state
        val btEnabled = bluetoothAdapter?.isEnabled ?: false
        Log.i(TAG, "Bluetooth enabled: $btEnabled")
        if (!btEnabled) {
            trySend(ScanResultEvent.Error("Bluetooth is not enabled"))
            close()
            return@callbackFlow
        }

        // Check location state (critical for BLE scanning on many devices)
        val locationEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        Log.i(TAG, "Location services enabled: $locationEnabled (required for BLE scan on many OEMs)")

        val scanner: BluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        Log.i(TAG, "BLEScanner obtained: ${scanner != null}")

        if (isScanning) {
            trySend(ScanResultEvent.Error("Already scanning"))
            close()
            return@callbackFlow
        }

        val foundDevices = mutableSetOf<String>()
        var totalResults = 0
        val startTime = System.currentTimeMillis()

        // Scan with NO filter to catch all manufacturer data packets
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                totalResults++
                val device = result.device
                val name = device.name
                val address = device.address
                val record = result.scanRecord
                val bytes = record?.bytes

                // Log EVERY scan result for debugging
                if (totalResults <= 5 || totalResults % 20 == 0) {
                    val hexBytes = bytes?.joinToString(" ") { "%02X".format(it) } ?: "null"
                    val mfrData = extractManufacturerData(bytes)
                    Log.i(TAG, "[#${totalResults}] $name / $address rssi=${result.rssi}")
                    Log.i(TAG, "  raw bytes (${bytes?.size ?: 0}): ${hexBytes.take(200)}")
                    Log.i(TAG, "  manufacturer data: $mfrData")
                }

                if (address in foundDevices) return

                // Check for Huami manufacturer data
                val isMiBand = isMiBandDevice(bytes, name)
                if (isMiBand) {
                    Log.i(TAG, "*** Mi Band DETECTED: $name ($address) ***")
                    foundDevices.add(address)
                    trySend(
                        ScanResultEvent.DeviceFound(
                            BleDevice(
                                name = name ?: "Mi Band",
                                address = address,
                                rssi = result.rssi,
                                isMiBand = true
                            )
                        )
                    )
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                Log.i(TAG, "Batch scan: ${results.size} results")
                for (result in results) {
                    onScanResult(0, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val msg = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    else -> "Unknown error: $errorCode"
                }
                Log.e(TAG, "SCAN FAILED: $msg")
                trySend(ScanResultEvent.Error("Scan failed: $msg"))
            }
        }

        try {
            scanner.startScan(emptyList(), scanSettings, scanCallback)
            isScanning = true
            Log.i(TAG, "SCAN STARTED SUCCESSFULLY - waiting for results...")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            trySend(ScanResultEvent.Error("Missing BLE scan permission"))
            close()
            return@callbackFlow
        }

        // Periodic status log to detect "stuck" scan
        launch {
            delay(5000)
            if (isScanning && totalResults == 0) {
                Log.w(TAG, "NO RESULTS after 5 seconds! Possible issues:")
                Log.w(TAG, "  - Location services enabled? $locationEnabled")
                Log.w(TAG, "  - Bluetooth enabled? $btEnabled")
                Log.w(TAG, "  - Mi Band nearby and broadcasting?")
                Log.w(TAG, "  - Scan callback is registered (no error from startScan)")
                trySend(ScanResultEvent.Diagnostic(
                    "No devices found after 5s. Location=$locationEnabled BT=$btEnabled"
                ))
            }
            delay(5000)
            if (isScanning && totalResults == 0) {
                Log.w(TAG, "NO RESULTS after 10 seconds!")
                trySend(ScanResultEvent.Diagnostic(
                    "Still no devices after 10s. Ensure Mi Band is in heart rate broadcast mode."
                ))
            }
            delay(10000)
            if (isScanning) {
                Log.i(TAG, "Scan complete: $totalResults total, ${foundDevices.size} Mi Bands found")
            }
        }

        awaitClose {
            try {
                scanner.stopScan(scanCallback)
            } catch (_: Exception) {}
            isScanning = false
            Log.i(TAG, "Scan stopped. Total results: $totalResults, Mi Bands: ${foundDevices.size}")
        }
    }

    private fun extractManufacturerData(bytes: ByteArray?): String {
        if (bytes == null) return "null"
        var offset = 0
        val results = mutableListOf<String>()
        while (offset < bytes.size - 1) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0 || offset + length >= bytes.size) break
            val type = bytes[offset + 1].toInt() and 0xFF
            if (type == 0xFF && length >= 4) { // Manufacturer data
                val data = bytes.copyOfRange(offset + 2, offset + length)
                val companyId = ((data[1].toInt() and 0xFF) shl 8) or
                    (data[0].toInt() and 0xFF)
                val hexData = data.joinToString(" ") { "%02X".format(it) }
                results.add("companyId=0x%04X data=[$hexData]".format(companyId))
            }
            offset += length + 1
        }
        return if (results.isEmpty()) "(none)" else results.joinToString("; ")
    }

    private fun isMiBandDevice(bytes: ByteArray?, name: String?): Boolean {
        if (bytes == null) return false

        var offset = 0
        while (offset < bytes.size - 1) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0 || offset + length >= bytes.size) break
            val type = bytes[offset + 1].toInt() and 0xFF
            if (type == 0xFF) { // Manufacturer Specific Data
                val data = bytes.copyOfRange(offset + 2, offset + 2 + length - 1)
                if (data.size >= 2) {
                    val companyId = ((data[1].toInt() and 0xFF) shl 8) or
                        (data[0].toInt() and 0xFF)
                    if (companyId == BleConstants.HUAMI_COMPANY_ID) {
                        Log.d(TAG, "Huami device found: name=$name companyId=0x0157")
                        return true
                    }
                    // Also log any manufacturer data for diagnostics
                    if (companyId in 0x0001..0xFFFF) {
                        Log.d(TAG, "Mfr data found: companyId=0x%04X name=$name".format(companyId))
                    }
                }
            }
            offset += length + 1
        }

        // Fall back to name matching
        if (name != null) {
            return BleConstants.MI_BAND_NAME_PREFIXES.any { prefix ->
                name.contains(prefix, ignoreCase = true)
            }
        }

        return false
    }

    companion object {
        private const val TAG = "BLEB:DeviceScanner"
    }
}

sealed interface ScanResultEvent {
    data class DeviceFound(val device: BleDevice) : ScanResultEvent
    data class Error(val message: String) : ScanResultEvent
    data class Diagnostic(val message: String) : ScanResultEvent
}
