package com.bleb.bridge.data.repository

import com.bleb.bridge.data.local.AppPreferencesDataStore
import com.bleb.bridge.data.local.AppSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: AppPreferencesDataStore
) {
    val settings: Flow<AppSettings> = dataStore.settings

    suspend fun updateTargetDevice(address: String, name: String = "") {
        dataStore.updateTargetDevice(address, name)
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        dataStore.setAutoConnect(enabled)
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.setKeepScreenOn(enabled)
    }
}
