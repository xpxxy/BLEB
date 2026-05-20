package com.bleb.bridge.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bleb_settings")

data class AppSettings(
    val targetDeviceAddress: String = "",
    val targetDeviceName: String = "",
    val autoConnect: Boolean = false,
    val keepScreenOn: Boolean = true
)

@Singleton
class AppPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val targetAddressKey = stringPreferencesKey("target_device_address")
    private val targetNameKey = stringPreferencesKey("target_device_name")
    private val autoConnectKey = booleanPreferencesKey("auto_connect")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            targetDeviceAddress = prefs[targetAddressKey] ?: "",
            targetDeviceName = prefs[targetNameKey] ?: "",
            autoConnect = prefs[autoConnectKey] ?: false,
            keepScreenOn = prefs[keepScreenOnKey] ?: true
        )
    }

    suspend fun updateTargetDevice(address: String, name: String = "") {
        context.dataStore.edit { prefs ->
            prefs[targetAddressKey] = address
            prefs[targetNameKey] = name
        }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoConnectKey] = enabled
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keepScreenOnKey] = enabled
        }
    }
}
