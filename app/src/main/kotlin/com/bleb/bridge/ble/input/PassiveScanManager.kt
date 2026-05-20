package com.bleb.bridge.ble.input

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.bleb.bridge.ble.model.BleDevice
import com.bleb.bridge.ble.parser.HeartRateParser
import com.bleb.bridge.util.BleConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassiveScanManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parsers: List<@JvmSuppressWildcards HeartRateParser>
) : InputBleManager {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var isScanning = false

    private val _heartRate = MutableStateFlow(0)
    override val heartRate = _heartRate.asStateFlow()

    private val _deviceRssi = MutableStateFlow(0)
    override val deviceRssi = _deviceRssi.asStateFlow()

    @SuppressLint("MissingPermission")
    override suspend fun startScanning(target: BleDevice, useBalancedMode: Boolean): Flow<InputEvent> = callbackFlow {
        if (isScanning) {
            trySend(InputEvent.Error("Already scanning"))
            awaitClose { }
            return@callbackFlow
        }

        val leScanner = scanner
        if (leScanner == null) {
            trySend(InputEvent.Error("BLE scanner not available"))
            awaitClose { }
            return@callbackFlow
        }

        isScanning = true
        Log.i(TAG, "Starting passive scan for Mi Band (target: ${target.name} @ ${target.address})")

        // REQUIRED for screen-off scanning on Android 8.1+ (Android 14+ needs non-empty criteria):
        // Use device MAC address filter — most reliable on all Android versions.
        // Fall back to manufacturer data filter if address filter fails.
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setDeviceAddress(target.address)
                .build(),
            ScanFilter.Builder()
                .setManufacturerData(
                    BleConstants.HUAMI_COMPANY_ID,
                    ByteArray(0),
                    ByteArray(0)
                )
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        var lastBpm = 0
        var lastBpmTime = 0L

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val bytes = record.bytes ?: return

                // Filter by device address
                if (result.device.address != target.address) return

                // Try all parsers for HR data
                for (parser in parsers) {
                    val bpm = parser.parse(bytes)
                    if (bpm != null && bpm in BleConstants.HR_MIN_BPM..BleConstants.HR_MAX_BPM) {
                        // Deduplicate: skip if same BPM within 1 second
                        val now = System.currentTimeMillis()
                        if (bpm == lastBpm && (now - lastBpmTime) < 1000) return
                        lastBpm = bpm
                        lastBpmTime = now

                        _heartRate.value = bpm
                        _deviceRssi.value = result.rssi
                        trySend(InputEvent.HeartRateUpdate(bpm))
                        return
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: error=$errorCode")
            }
        }

        try {
            leScanner.startScan(scanFilters, scanSettings, scanCallback)
            Log.i(TAG, "Passive scan started (no filter, listening for manufacturer data)")
        } catch (e: SecurityException) {
            trySend(InputEvent.Error("Missing BLE scan permission"))
            isScanning = false
        }

        awaitClose {
            stop()
        }
    }

    override fun stop() {
        isScanning = false
        try {
            scanner?.stopScan(object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {}
            })
        } catch (_: Exception) {}
        Log.i(TAG, "Passive scan stopped")
    }

    companion object {
        private const val TAG = "BLEB:PassiveScan"
    }
}
