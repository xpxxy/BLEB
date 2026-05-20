package com.bleb.bridge.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bleb.bridge.bridge.BridgeState
import com.bleb.bridge.ui.theme.StatusGray
import com.bleb.bridge.ui.theme.StatusGreen
import com.bleb.bridge.ui.theme.StatusRed
import com.bleb.bridge.ui.theme.StatusYellow

@Composable
fun ConnectionStatus(
    bridgeState: BridgeState,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (bridgeState) {
            BridgeState.Idle -> StatusGray
            is BridgeState.Scanning -> StatusYellow
            is BridgeState.Connected -> StatusYellow
            is BridgeState.Bridging -> StatusGreen
            is BridgeState.Error -> StatusRed
        },
        label = "statusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val showPulse = bridgeState is BridgeState.Scanning

    val statusText = when (bridgeState) {
        BridgeState.Idle -> "Not running"
        is BridgeState.Scanning -> "Searching for Mi Band..."
        is BridgeState.Connected -> "Connected, waiting for HR data"
        is BridgeState.Bridging -> "Broadcasting ${bridgeState.heartRate} BPM"
        is BridgeState.Error -> bridgeState.message
    }

    val connectedClients = when (bridgeState) {
        is BridgeState.Bridging -> bridgeState.connectedClients
        else -> 0
    }

    val deviceInfo = when (bridgeState) {
        is BridgeState.Connected -> bridgeState.device
        is BridgeState.Bridging -> bridgeState.device
        else -> null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .then(if (showPulse) Modifier.alpha(alpha) else Modifier)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (deviceInfo != null) {
                Text(
                    text = "${deviceInfo.name} (${deviceInfo.address})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (connectedClients > 0) {
                Text(
                    text = "$connectedClients device${if (connectedClients > 1) "s" else ""} connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
