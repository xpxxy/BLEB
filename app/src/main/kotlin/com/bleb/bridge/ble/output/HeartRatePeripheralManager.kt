package com.bleb.bridge.ble.output

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.bleb.bridge.util.BleConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartRatePeripheralManager @Inject constructor(
    @ApplicationContext private val context: Context
) : OutputBleManager {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var hrCharacteristic: BluetoothGattCharacteristic? = null
    private val subscribedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    private val _connectedDevices = MutableStateFlow(0)
    override val connectedDevices: Flow<Int> = _connectedDevices.asStateFlow()

    private var isRunning = false

    override suspend fun start(initialHr: Int): Result<Unit> {
        if (isRunning) return Result.success(Unit)

        val adapter = bluetoothAdapter
        if (adapter == null) {
            return Result.failure(IllegalStateException("Bluetooth not supported"))
        }
        if (!adapter.isMultipleAdvertisementSupported) {
            return Result.failure(IllegalStateException("BLE peripheral mode not supported"))
        }

        // Step 1: Open GATT server
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            ?: return Result.failure(IllegalStateException("Failed to open GATT server"))

        // Step 2: Build and add Heart Rate Service
        val hrService = buildHeartRateService()
        val added = gattServer?.addService(hrService) ?: false
        if (!added) {
            stop()
            return Result.failure(IllegalStateException("Failed to add Heart Rate Service"))
        }

        // Step 3: Start advertising
        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            stop()
            return Result.failure(IllegalStateException("BLE advertiser not available"))
        }

        advertiser?.startAdvertising(
            buildAdvertiseSettings(),
            buildAdvertiseData(),
            advertiseCallback
        )

        isRunning = true
        Log.i(TAG, "Heart Rate peripheral started, initial HR: $initialHr")
        return Result.success(Unit)
    }

    override fun updateHeartRate(bpm: Int) {
        val characteristic = hrCharacteristic ?: return
        val data = HeartRateMeasurementEncoder().encode(bpm)
        characteristic.value = data

        for ((_, device) in subscribedDevices) {
            gattServer?.notifyCharacteristicChanged(device, characteristic, false, data)
        }
    }

    override fun stop() {
        isRunning = false
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {}
        advertiser = null

        try {
            gattServer?.clearServices()
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null

        hrCharacteristic = null
        subscribedDevices.clear()
        _connectedDevices.value = 0
        Log.i(TAG, "Heart Rate peripheral stopped")
    }

    private fun buildHeartRateService(): BluetoothGattService {
        val service = BluetoothGattService(
            BleConstants.HR_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Heart Rate Measurement characteristic
        hrCharacteristic = BluetoothGattCharacteristic(
            BleConstants.HR_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // CCCD descriptor (required for notifications)
        val cccd = BluetoothGattDescriptor(
            BleConstants.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        hrCharacteristic?.addDescriptor(cccd)
        service.addCharacteristic(hrCharacteristic)

        // Body Sensor Location characteristic (optional, but helps compatibility)
        val bslCharacteristic = BluetoothGattCharacteristic(
            BleConstants.BODY_SENSOR_LOCATION_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        bslCharacteristic.value = byteArrayOf(2) // Wrist
        service.addCharacteristic(bslCharacteristic)

        return service
    }

    private fun buildAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0) // advertise indefinitely
            .build()
    }

    private fun buildAdvertiseData(): AdvertiseData {
        return AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(BleConstants.HR_SERVICE_UUID))
            .build()
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT client connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT client disconnected: ${device.address}")
                subscribedDevices.remove(device.address)
                _connectedDevices.value = subscribedDevices.size
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == BleConstants.CCCD_UUID &&
                descriptor.characteristic.uuid == BleConstants.HR_MEASUREMENT_UUID
            ) {
                if (value.size == 2 && value[0] == 0x01.toByte() && value[1] == 0x00.toByte()) {
                    // Client enabled notifications
                    subscribedDevices[device.address] = device
                } else if (value.size == 2 && value[0] == 0x00.toByte() && value[1] == 0x00.toByte()) {
                    // Client disabled notifications
                    subscribedDevices.remove(device.address)
                }
                _connectedDevices.value = subscribedDevices.size
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Notification failed for ${device.address}: $status")
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "Advertising started: mode=${settingsInEffect.mode}, tx=${settingsInEffect.txPowerLevel}")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed: error=$errorCode")
        }
    }

    companion object {
        private const val TAG = "BLEB:HRPeripheral"
    }
}
