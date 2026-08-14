package com.weatherstation.app.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherstation.app.domain.model.AppTheme
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.ui.components.GlassCard
import com.weatherstation.app.ui.theme.AccentPurple
import com.weatherstation.app.ui.theme.AccentTemp
import com.weatherstation.app.ui.theme.FigmaDarkSkyBrush
import com.weatherstation.app.ui.theme.TextWhitePrimary
import com.weatherstation.app.ui.theme.TextWhiteSecondary
import com.weatherstation.app.ui.theme.TextWhiteTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val unit by viewModel.temperatureUnit.collectAsState()
    val theme by viewModel.appTheme.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()

    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var devInput by remember(deviceId) { mutableStateOf(deviceId) }

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
            Text(
                text = "Preferences",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhitePrimary
            )
            Text(
                text = "Configure server API endpoints and application display",
                fontSize = 12.sp,
                color = TextWhiteSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 1. Connection Settings
            GlassCard(shape = RoundedCornerShape(26.dp), padding = 16.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "API", tint = AccentPurple)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(text = "SERVER & API AUTHENTICATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhiteSecondary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Server API Base URL", color = TextWhiteSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Client API Key", color = TextWhiteSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.updateServerUrl(urlInput)
                            viewModel.updateApiKey(keyInput)
                            viewModel.updateDeviceId(devInput)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) {
                        Text("Save Configuration")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Units & Theme
            GlassCard(shape = RoundedCornerShape(26.dp), padding = 16.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Thermostat, contentDescription = "Units", tint = AccentTemp)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(text = "TEMPERATURE UNIT & THEME", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhiteSecondary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Temperature Unit
                    Text(text = "Temperature Unit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextWhitePrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2E335A).copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TemperatureUnit.values().forEach { u ->
                            val isSel = unit == u
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) AccentPurple else Color.Transparent)
                                    .clickable { viewModel.updateTemperatureUnit(u) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "${u.name} (${u.symbol})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) TextWhitePrimary else TextWhiteSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Theme
                    Text(text = "Color Theme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextWhitePrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2E335A).copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppTheme.values().forEach { t ->
                            val isSel = theme == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) AccentPurple else Color.Transparent)
                                    .clickable { viewModel.updateAppTheme(t) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = t.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) TextWhitePrimary else TextWhiteSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Alerts
            GlassCard(shape = RoundedCornerShape(26.dp), padding = 16.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Alerts", tint = AccentPurple)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(text = "THRESHOLD NOTIFICATIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhiteSecondary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Environmental Alerts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextWhitePrimary)
                            Text(text = "High temp (>35°C) & high humidity (>75%)", fontSize = 11.sp, color = TextWhiteSecondary)
                        }

                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.updateNotificationsEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentPurple)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
