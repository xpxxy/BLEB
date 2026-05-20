package com.bleb.bridge.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bleb.bridge.bridge.BridgeState
import com.bleb.bridge.ble.input.DeviceScanner
import com.bleb.bridge.ble.input.ScanResultEvent
import com.bleb.bridge.ble.model.BleDevice
import com.bleb.bridge.data.repository.HeartRateRepository
import com.bleb.bridge.data.repository.PreferencesRepository
import com.bleb.bridge.service.BridgeForegroundService
import com.bleb.bridge.service.ServiceBinder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BridgeUiState(
    val heartRate: Int = 0,
    val bridgeState: BridgeState = BridgeState.Idle,
    val targetDevice: BleDevice? = null,
    val connectedClients: Int = 0,
    val error: String? = null,
    val isScanning: Boolean = false,
    val discoveredDevices: List<BleDevice> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val heartRateRepository: HeartRateRepository,
    private val preferencesRepository: PreferencesRepository,
    private val deviceScanner: DeviceScanner
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BridgeUiState())
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()

    private var serviceBinder: ServiceBinder? = null
    private var bound = false
    private var scanJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            serviceBinder = service as? ServiceBinder
            bound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBinder = null
            bound = false
        }
    }

    init {
        bindToService()
        observeHeartRate()
        loadSavedDevice()
    }

    private fun bindToService() {
        val intent = Intent(getApplication(), BridgeForegroundService::class.java)
        getApplication<Application>().bindService(
            intent, serviceConnection, Context.BIND_AUTO_CREATE
        )
    }

    private fun observeServiceState() {
        val binder = serviceBinder ?: return
        viewModelScope.launch {
            binder.bridgeState.collectLatest { state ->
                _uiState.update { it.copy(bridgeState = state) }
            }
        }
    }

    private fun observeHeartRate() {
        viewModelScope.launch {
            heartRateRepository.latestSample.collectLatest { sample ->
                android.util.Log.d("BLEB:ViewModel", "HR update from repo: ${sample?.bpm}")
                if (sample != null) {
                    _uiState.update { it.copy(heartRate = sample.bpm) }
                }
            }
        }
    }

    private fun loadSavedDevice() {
        viewModelScope.launch {
            preferencesRepository.settings.collectLatest { settings ->
                if (settings.targetDeviceAddress.isNotEmpty() && _uiState.value.targetDevice == null) {
                    _uiState.update {
                        it.copy(
                            targetDevice = BleDevice(
                                name = settings.targetDeviceName.ifEmpty { "Mi Band 5" },
                                address = settings.targetDeviceAddress
                            )
                        )
                    }
                }
            }
        }
    }

    fun startScan() {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true, discoveredDevices = emptyList(), error = null) }

        scanJob = viewModelScope.launch {
            try {
                deviceScanner.scanForDevices().collect { event ->
                    when (event) {
                        is ScanResultEvent.DeviceFound -> {
                            _uiState.update {
                                it.copy(
                                    discoveredDevices = it.discoveredDevices + event.device
                                )
                            }
                        }
                        is ScanResultEvent.Error -> {
                            _uiState.update {
                                it.copy(error = event.message, isScanning = false)
                            }
                        }
                        is ScanResultEvent.Diagnostic -> {
                            _uiState.update {
                                it.copy(error = event.message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isScanning = false) }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun startBridge() {
        val device = _uiState.value.targetDevice ?: return
        stopScan()
        serviceBinder?.setTargetDevice(device.address)
        serviceBinder?.startBridge()
    }

    fun stopBridge() {
        serviceBinder?.stopBridge()
    }

    fun selectDevice(device: BleDevice) {
        _uiState.update { it.copy(targetDevice = device, discoveredDevices = emptyList()) }
        viewModelScope.launch {
            preferencesRepository.updateTargetDevice(device.address, device.name)
        }
    }

    fun clearDevice() {
        _uiState.update { it.copy(targetDevice = null) }
    }

    override fun onCleared() {
        scanJob?.cancel()
        if (bound) {
            getApplication<Application>().unbindService(serviceConnection)
        }
        super.onCleared()
    }
}
