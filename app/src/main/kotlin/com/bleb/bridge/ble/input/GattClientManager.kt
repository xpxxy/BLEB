package com.bleb.bridge.ble.input

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bleb.bridge.ble.model.BleDevice
import com.bleb.bridge.ble.parser.HeartRateParser
import com.bleb.bridge.ble.parser.StandardHrParser
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
class GattClientManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parsers: List<@JvmSuppressWildcards HeartRateParser>
) : InputBleManager {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val standardHrParser = parsers.filterIsInstance<StandardHrParser>().firstOrNull()

    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var retryCount = 0
    private var targetDevice: BleDevice? = null

    private val _heartRate = MutableStateFlow(0)
    override val heartRate = _heartRate.asStateFlow()

    private val _deviceRssi = MutableStateFlow(0)
    override val deviceRssi = _deviceRssi.asStateFlow()

    override suspend fun startScanning(target: BleDevice, useBalancedMode: Boolean): Flow<InputEvent> = callbackFlow {
        targetDevice = target
        retryCount = 0

        try {
            val device = bluetoothAdapter?.getRemoteDevice(target.address)
            if (device != null) {
                connectToDevice(device)
            } else {
                trySend(InputEvent.Error("Device not found: ${target.address}"))
            }
        } catch (e: Exception) {
            trySend(InputEvent.Error("Failed to start scanning: ${e.message}"))
        }

        awaitClose {
            disconnect()
        }
    }

    override fun stop() {
        disconnect()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception connecting: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun disconnect() {
        cancelReconnect()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        val shift = (1 shl retryCount).toLong()
        val maxDelayRatio = BleConstants.RETRY_MAX_DELAY / BleConstants.RETRY_BASE_DELAY
        val delay = BleConstants.RETRY_BASE_DELAY * minOf(shift, maxDelayRatio)
        retryCount++
        Log.i(TAG, "Scheduling reconnect ${retryCount} in ${delay}ms")

        reconnectRunnable = Runnable {
            targetDevice?.let { device ->
                try {
                    val btDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                    if (btDevice != null) connectToDevice(btDevice)
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect failed: ${e.message}")
                    scheduleReconnect()
                }
            }
        }
        handler.postDelayed(reconnectRunnable!!, delay)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun parseHrValue(data: ByteArray): Int? {
        // Try StandardHrParser first for GATT characteristic data
        standardHrParser?.parseFromCharacteristic(data)?.let { return it }
        // Fall back to generic parser chain
        for (parser in parsers) {
            parser.parse(data)?.let { return it }
        }
        return null
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT connected, discovering services...")
                retryCount = 0
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT disconnected, status=$status")
                scheduleReconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed: $status")
                return
            }

            val hrService = gatt.getService(BleConstants.HR_SERVICE_UUID)
            if (hrService == null) {
                Log.w(TAG, "Heart Rate Service (0x180D) not found")
                return
            }

            val hrCharacteristic = hrService.getCharacteristic(BleConstants.HR_MEASUREMENT_UUID)
            if (hrCharacteristic == null) {
                Log.w(TAG, "HR Measurement characteristic (0x2A37) not found")
                return
            }

            val enabled = gatt.setCharacteristicNotification(hrCharacteristic, true)
            if (!enabled) {
                Log.w(TAG, "Failed to enable notification")
                return
            }

            val cccd = hrCharacteristic.getDescriptor(BleConstants.CCCD_UUID)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
                Log.i(TAG, "CCCD notification enabled")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == BleConstants.HR_MEASUREMENT_UUID) {
                val bpm = parseHrValue(value)
                if (bpm != null && bpm in BleConstants.HR_MIN_BPM..BleConstants.HR_MAX_BPM) {
                    _heartRate.value = bpm
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _deviceRssi.value = rssi
            }
        }
    }

    companion object {
        private const val TAG = "BLEB:GattClient"
    }
}
