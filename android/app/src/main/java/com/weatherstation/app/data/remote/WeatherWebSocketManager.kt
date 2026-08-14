package com.weatherstation.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.domain.model.WeatherReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * High-Throughput Real-Time WebSocket Streaming Manager for Android.
 * Connects directly to the FastAPI server (/ws/live) for sub-millisecond, zero-lag updates.
 * Features automated keepalive ping/pong, exponential backoff reconnection, and JSON parsing.
 */
class WeatherWebSocketManager(
    private val preferencesManager: UserPreferencesManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val onReadingReceived: (suspend (WeatherReading) -> Unit)? = null
) {
    companion object {
        private const val TAG = "WeatherWS"
        private const val INITIAL_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var isManuallyClosed = false
    private var currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var reconnectJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _latestReading = MutableStateFlow<WeatherReading?>(null)
    val latestReading: StateFlow<WeatherReading?> = _latestReading.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for persistent stream
        .build()

    fun start() {
        isManuallyClosed = false
        connect()
    }

    fun stop() {
        isManuallyClosed = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client stopped")
        webSocket = null
        _isConnected.value = false
    }

    private fun getWebSocketUrl(): String {
        val httpUrl = preferencesManager.serverUrl.value.trim().removeSuffix("/")
        val wsBase = when {
            httpUrl.startsWith("https://") -> httpUrl.replaceFirst("https://", "wss://")
            httpUrl.startsWith("http://") -> httpUrl.replaceFirst("http://", "ws://")
            else -> "ws://$httpUrl"
        }
        return "$wsBase/ws/live"
    }

    @Synchronized
    private fun connect() {
        if (isManuallyClosed) return

        val wsUrl = getWebSocketUrl()
        Log.i(TAG, "Initiating WebSocket connection to: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("User-Agent", "WeatherStation-Android-Live/2.0")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully to $wsUrl")
                _isConnected.value = true
                currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    handleIncomingMessage(text)
                } catch (e: Exception) {
                    Log.w(TAG, "Error handling incoming WebSocket payload: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing (Code $code: $reason)")
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed (Code $code: $reason)")
                _isConnected.value = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket transport failure: ${t.message}")
                _isConnected.value = false
                scheduleReconnect()
            }
        })
    }

    private fun handleIncomingMessage(jsonText: String) {
        val root = JsonParser.parseString(jsonText).asJsonObject
        val msgType = root.get("type")?.asString

        if (msgType == "telemetry_update" || root.has("data")) {
            val dataObj = root.getAsJsonObject("data") ?: return
            val reading = parseReadingFromJson(dataObj) ?: return

            // Filter by deviceId if configured
            val targetDeviceId = preferencesManager.deviceId.value.trim()
            if (targetDeviceId.isNotEmpty() && reading.deviceId.isNotEmpty() && !targetDeviceId.equals(reading.deviceId, ignoreCase = true)) {
                return
            }

            _latestReading.value = reading
            scope.launch {
                onReadingReceived?.invoke(reading)
            }
        }
    }

    private fun parseReadingFromJson(obj: JsonObject): WeatherReading? {
        return try {
            val id = if (obj.has("id")) obj.get("id").asLong else System.currentTimeMillis()
            val deviceId = if (obj.has("device_id")) obj.get("device_id").asString else "WS-001"
            val temp = if (obj.has("temperature_c")) obj.get("temperature_c").asFloat 
                       else if (obj.has("temperature")) obj.get("temperature").asFloat else 20.0f
            val hum = if (obj.has("humidity_pct")) obj.get("humidity_pct").asFloat 
                      else if (obj.has("humidity")) obj.get("humidity").asFloat else 50.0f
            val light = if (obj.has("light_pct")) obj.get("light_pct").asFloat 
                        else if (obj.has("sun_activity")) obj.get("sun_activity").asFloat else 50.0f
            val pressure = if (obj.has("pressure") && !obj.get("pressure").isJsonNull) obj.get("pressure").asFloat else null
            val wind = if (obj.has("wind_speed") && !obj.get("wind_speed").isJsonNull) obj.get("wind_speed").asFloat else null
            val batt = if (obj.has("batt_voltage") && !obj.get("batt_voltage").isJsonNull) obj.get("batt_voltage").asFloat else null
            val rain = if (obj.has("rain_detected") && !obj.get("rain_detected").isJsonNull) obj.get("rain_detected").asBoolean else null
            val lightCond = if (obj.has("light_condition") && !obj.get("light_condition").isJsonNull) obj.get("light_condition").asString else "Moderate"
            val condSummary = if (obj.has("condition_summary") && !obj.get("condition_summary").isJsonNull) obj.get("condition_summary").asString else "Normal"
            val recordedAt = if (obj.has("recorded_at") && !obj.get("recorded_at").isJsonNull) obj.get("recorded_at").asString 
                             else if (obj.has("timestamp") && !obj.get("timestamp").isJsonNull) obj.get("timestamp").asString else ""

            WeatherReading(
                id = id,
                deviceId = deviceId,
                temperatureC = temp,
                humidityPct = hum,
                lightPct = light,
                pressureHpa = pressure,
                windSpeedKmh = wind,
                battVoltage = batt,
                rainDetected = rain,
                lightCondition = lightCond,
                conditionSummary = condSummary,
                recordedAt = recordedAt,
                isStale = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WeatherReading from JSON", e)
            null
        }
    }

    private fun scheduleReconnect() {
        if (isManuallyClosed) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.i(TAG, "Reconnecting in ${currentReconnectDelay / 1000}s...")
            delay(currentReconnectDelay)
            currentReconnectDelay = (currentReconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            if (isActive && !isManuallyClosed) {
                connect()
            }
        }
    }
}
