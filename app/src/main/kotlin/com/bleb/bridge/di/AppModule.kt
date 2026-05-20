package com.bleb.bridge.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.bleb.bridge.ble.input.GattClientManager
import com.bleb.bridge.ble.input.InputBleManager
import com.bleb.bridge.ble.input.PassiveScanManager
import com.bleb.bridge.ble.output.HeartRatePeripheralManager
import com.bleb.bridge.ble.output.OutputBleManager
import com.bleb.bridge.ble.parser.HeartRateParser
import com.bleb.bridge.ble.parser.MiBeaconParser
import com.bleb.bridge.ble.parser.StandardHrParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBluetoothManager(
        @ApplicationContext context: Context
    ): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    @Provides
    @Singleton
    fun provideBluetoothAdapter(
        bluetoothManager: BluetoothManager
    ): BluetoothAdapter {
        return bluetoothManager.adapter
    }

    @Provides
    @Singleton
    fun provideHeartRateParsers(): List<@JvmSuppressWildcards HeartRateParser> {
        return listOf(
            StandardHrParser(),
            MiBeaconParser()
        )
    }

    @Provides
    @Singleton
    fun provideInputBleManager(
        passiveScanManager: PassiveScanManager
    ): InputBleManager {
        return passiveScanManager
    }

    @Provides
    @Singleton
    fun provideOutputBleManager(
        peripheralManager: HeartRatePeripheralManager
    ): OutputBleManager {
        return peripheralManager
    }
}
