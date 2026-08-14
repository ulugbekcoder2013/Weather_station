package com.weatherstation.app.ui.screens.device

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherstation.app.ui.components.ConnectionStatusBadge
import com.weatherstation.app.ui.components.GlassCard
import com.weatherstation.app.ui.theme.AccentGreen
import com.weatherstation.app.ui.theme.AccentPurple
import com.weatherstation.app.ui.theme.AccentTemp
import com.weatherstation.app.ui.theme.FigmaDarkSkyBrush
import com.weatherstation.app.ui.theme.TextWhitePrimary
import com.weatherstation.app.ui.theme.TextWhiteSecondary
import com.weatherstation.app.ui.theme.TextWhiteTertiary

@Composable
fun DeviceStatusScreen(
    viewModel: DeviceStatusViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FigmaDarkSkyBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hardware & Gateway",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhitePrimary
                    )
                    Text(
                        text = "Real-time microcontroller diagnostic telemetry",
                        fontSize = 12.sp,
                        color = TextWhiteSecondary
                    )
                }

                IconButton(onClick = { viewModel.loadDeviceHealth() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextWhitePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Gateway Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                padding = 18.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ESP32 GATEWAY TELEMETRY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhiteSecondary,
                            letterSpacing = 0.5.sp
                        )

                        ConnectionStatusBadge(isOnline = uiState.health?.isOnline ?: false)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    StatusRow(label = "Device Identifier", value = uiState.deviceId)
                    StatusRow(label = "Server Endpoint", value = uiState.serverUrl)
                    StatusRow(
                        label = "Last Telemetry Seen",
                        value = uiState.health?.let { "${it.lastSeenSecondsAgo}s ago" } ?: "No verified telemetry"
                    )
                    StatusRow(
                        label = "Gateway Health",
                        value = uiState.health?.statusText ?: "Unavailable"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.pingDevice() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        enabled = !uiState.isPinging
                    ) {
                        if (uiState.isPinging) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pinging Station...")
                        } else {
                            Icon(imageVector = Icons.Default.Wifi, contentDescription = "Ping", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ping Station & Sync Telemetry")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "NETWORK & STATION CONNECTIVITY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhiteSecondary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            val isOnline = uiState.health?.isOnline == true
            SubsystemCard(
                name = "Telemetry ingestion link",
                status = if (isOnline) "Verified by the latest station frame" else "No live station frame verified",
                isOk = isOnline
            )
            Spacer(modifier = Modifier.height(8.dp))
            SubsystemCard(
                name = "Sensor truth policy",
                status = "Only fields reported by installed physical sensors are displayed",
                isOk = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            SubsystemCard(name = "Offline telemetry cache", status = "Stores received station frames for offline viewing", isOk = true)

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextWhiteSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextWhitePrimary)
    }
}

@Composable
private fun SubsystemCard(name: String, status: String, isOk: Boolean) {
    GlassCard(shape = RoundedCornerShape(20.dp), padding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
                Text(text = status, fontSize = 11.sp, color = TextWhiteSecondary)
            }

            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = "Status",
                tint = if (isOk) AccentGreen else AccentTemp,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
