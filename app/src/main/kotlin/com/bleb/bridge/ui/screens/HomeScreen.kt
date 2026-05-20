package com.bleb.bridge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bleb.bridge.bridge.BridgeState
import com.bleb.bridge.ble.model.BleDevice
import com.bleb.bridge.ui.components.ActionButton
import com.bleb.bridge.ui.components.ConnectionStatus
import com.bleb.bridge.ui.components.HeartRateDisplay
import com.bleb.bridge.ui.viewmodel.MainViewModel
import com.bleb.bridge.ui.theme.StatusGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Keep screen on while bridging — MIUI suspends BLE scan when screen goes off
    val context = androidx.compose.ui.platform.LocalContext.current
    val isBridging = uiState.bridgeState is BridgeState.Bridging
    androidx.compose.runtime.DisposableEffect(isBridging) {
        val window = (context as android.app.Activity).window
        if (isBridging) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            android.util.Log.i("BLEB:UI", "Screen ON lock enabled for bridging")
        }
        onDispose {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            android.util.Log.i("BLEB:UI", "Screen ON lock released")
        }
    }

    androidx.compose.runtime.SideEffect {
        android.util.Log.d("BLEB:UI", "HomeScreen: state=${uiState.bridgeState::class.simpleName} hr=${uiState.heartRate}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLEB HR Bridge") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Heart rate display
            HeartRateDisplay(
                heartRate = uiState.heartRate,
                isActive = uiState.bridgeState is BridgeState.Bridging
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status
            ConnectionStatus(
                bridgeState = uiState.bridgeState,
                modifier = Modifier.fillMaxWidth()
            )

            // Hint when bridging but no HR data
            if (uiState.bridgeState is BridgeState.Bridging && uiState.heartRate == 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "Start any workout on your Mi Band to begin heart rate broadcasting. The band only sends real-time HR data during exercise mode.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            val bridgeRunning = uiState.bridgeState !is BridgeState.Idle
                && uiState.bridgeState !is BridgeState.Error

            if (!bridgeRunning) {
                Spacer(modifier = Modifier.height(24.dp))

                // Device selection area
                if (uiState.targetDevice != null) {
                    // Device selected - show info
                    DeviceSelectedCard(
                        device = uiState.targetDevice!!,
                        onClear = { viewModel.clearDevice() },
                        onRescan = { viewModel.startScan() }
                    )
                } else {
                    // No device - show scan area
                    if (uiState.isScanning) {
                        ScanningCard()
                    } else {
                        ScanPromptCard(onScan = { viewModel.startScan() })
                    }

                    // Discovered devices list
                    if (uiState.discoveredDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        DeviceListCard(
                            devices = uiState.discoveredDevices,
                            onSelect = { viewModel.selectDevice(it) }
                        )
                    }
                }

                // Error
                val errorMsg = uiState.error
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action button
            ActionButton(
                bridgeState = uiState.bridgeState,
                hasTargetDevice = uiState.targetDevice != null,
                onStart = { viewModel.startBridge() },
                onStop = { viewModel.stopBridge() }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ScanPromptCard(onScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No device selected",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Make sure your Mi Band has heart rate broadcast enabled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onScan) {
                Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Find Mi Band")
            }
        }
    }
}

@Composable
private fun ScanningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Scanning for devices...",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Make sure your Mi Band is nearby and heart rate broadcast is on",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DeviceListCard(
    devices: List<BleDevice>,
    onSelect: (BleDevice) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Found Devices",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(device) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (device.isMiBand) StatusGreen
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceSelectedCard(
    device: BleDevice,
    onClear: () -> Unit,
    onRescan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = StatusGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                OutlinedButton(onClick = onClear) {
                    Text("Remove")
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(onClick = onRescan) {
                    Text("Rescan")
                }
            }
        }
    }
}
