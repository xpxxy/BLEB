package com.bleb.bridge.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bleb.bridge.bridge.BridgeState

@Composable
fun ActionButton(
    bridgeState: BridgeState,
    hasTargetDevice: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = bridgeState !is BridgeState.Idle && bridgeState !is BridgeState.Error

    Button(
        onClick = { if (isRunning) onStop() else onStart() },
        modifier = modifier.size(width = 240.dp, height = 56.dp),
        shape = RoundedCornerShape(28.dp),
        enabled = isRunning || hasTargetDevice,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning)
                ButtonDefaults.buttonColors().disabledContainerColor
            else
                ButtonDefaults.buttonColors().containerColor
        )
    ) {
        Text(
            text = when {
                bridgeState is BridgeState.Scanning -> "Scanning…"
                isRunning -> "Stop Bridge"
                else -> "Start Bridge"
            },
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
        )
    }
}
