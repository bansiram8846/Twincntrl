package com.example.ui.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CommandType
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceInfo
import com.example.data.model.GestureMode
import com.example.data.model.PairingState
import com.example.data.model.RemoteCommand
import com.example.data.model.SessionTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ActivityLogEntry(
  val id: String = UUID.randomUUID().toString(),
  val timestamp: String,
  val type: String,
  val detail: String,
  val isSuccess: Boolean = true,
)

class ControllerViewModel : ViewModel() {

  private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _pairingState = MutableStateFlow(PairingState.IDLE)
  val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

  private val _activeDevice = MutableStateFlow<DeviceInfo?>(
    DeviceInfo(
      id = "tc-client-9842",
      name = "Pixel 8 Pro",
      model = "Pixel 8 Pro",
      ipAddress = "192.168.1.135",
      isAuthorized = true,
      isConnected = true,
      locationTag = "Living Room",
      lastSeen = "Active Now",
      batteryPercent = 78,
      isCharging = true,
      wifiSsid = "5 GHz",
      signalDbm = -42,
      osVersion = "Android 14",
      streamResolution = "1080×2400",
      streamFps = 60,
      streamCodec = "H.265",
    )
  )
  val activeDevice: StateFlow<DeviceInfo?> = _activeDevice.asStateFlow()

  private val _telemetry = MutableStateFlow(
    SessionTelemetry(
      latencyMs = 32,
      fps = 60,
      resolutionWidth = 1080,
      resolutionHeight = 2400,
      bitrateMbps = 6.8f,
      codec = "H.265",
      protocol = "TLS 1.3 Direct Socket",
      droppedFrames = 0,
      inputIngestionLatencyMs = 4.2f,
      isMirroringActive = true,
    )
  )
  val telemetry: StateFlow<SessionTelemetry> = _telemetry.asStateFlow()

  private val _gestureMode = MutableStateFlow(GestureMode.TAP)
  val gestureMode: StateFlow<GestureMode> = _gestureMode.asStateFlow()

  private val _lastTouchCoordinate = MutableStateFlow<Pair<Float, Float>?>(Pair(684f, 1190f))
  val lastTouchCoordinate: StateFlow<Pair<Float, Float>?> = _lastTouchCoordinate.asStateFlow()

  // Numeric 6-digit PIN entry state
  private val _pinDigits = MutableStateFlow(listOf("4", "8", "2", "", "", ""))
  val pinDigits: StateFlow<List<String>> = _pinDigits.asStateFlow()

  private val _nearbyDevices = MutableStateFlow(
    listOf(
      DeviceInfo(
        id = "tc-client-7711",
        name = "Pixel 7a",
        model = "Pixel 7a",
        ipAddress = "192.168.1.144",
        isAuthorized = false,
        isConnected = false,
        locationTag = "Nearby Wi-Fi",
        lastSeen = "Just now",
        batteryPercent = 92,
        wifiSsid = "5 GHz",
        signalDbm = -42,
      ),
      DeviceInfo(
        id = "tc-client-4402",
        name = "Telemetry Workstation X",
        model = "Workstation",
        ipAddress = "192.168.1.202",
        isAuthorized = false,
        isConnected = false,
        locationTag = "Local Subnet",
        lastSeen = "Requires Auth Token",
        batteryPercent = 100,
        wifiSsid = "Ethernet / 5 GHz",
        signalDbm = -35,
      ),
    )
  )
  val nearbyDevices: StateFlow<List<DeviceInfo>> = _nearbyDevices.asStateFlow()

  private val _recentDevices = MutableStateFlow(
    listOf(
      DeviceInfo(
        id = "tc-client-9842",
        name = "Pixel 8 Pro",
        model = "Pixel 8 Pro",
        ipAddress = "192.168.1.135",
        isAuthorized = true,
        isConnected = true,
        locationTag = "Living Room",
        lastSeen = "Active now",
        batteryPercent = 78,
      ),
      DeviceInfo(
        id = "tc-client-5521",
        name = "Galaxy S24 Ultra",
        model = "Galaxy S24 Ultra",
        ipAddress = "192.168.1.109",
        isAuthorized = true,
        isConnected = false,
        locationTag = "Office",
        lastSeen = "Last seen 2h ago",
        batteryPercent = 64,
      ),
      DeviceInfo(
        id = "tc-client-3390",
        name = "Pixel Tablet",
        model = "Pixel Tablet",
        ipAddress = "192.168.1.188",
        isAuthorized = false,
        isConnected = false,
        locationTag = "Desk",
        lastSeen = "Same Wi-Fi detected",
        batteryPercent = 88,
      ),
    )
  )
  val recentDevices: StateFlow<List<DeviceInfo>> = _recentDevices.asStateFlow()

  private val _activityLogs = MutableStateFlow(
    listOf(
      ActivityLogEntry(timestamp = "09:40:12", type = "TLS Handshake", detail = "Direct Socket encrypted with TLS 1.3 ECDHE-RSA"),
      ActivityLogEntry(timestamp = "09:40:15", type = "MediaProjection", detail = "Target screen capture stream initiated at 1080x2400 @ 60 FPS"),
      ActivityLogEntry(timestamp = "09:40:18", type = "Accessibility Tap", detail = "Dispatched single tap at (684, 1190) on Target"),
      ActivityLogEntry(timestamp = "09:40:35", type = "Navigation Command", detail = "Dispatched Remote Home intent to Target"),
      ActivityLogEntry(timestamp = "09:41:02", type = "Heartbeat", detail = "Round-trip ping-pong latency 28ms"),
    )
  )
  val activityLogs: StateFlow<List<ActivityLogEntry>> = _activityLogs.asStateFlow()

  private var telemetryTicker: Job? = null

  init {
    startTelemetryTicker()
  }

  private fun startTelemetryTicker() {
    telemetryTicker?.cancel()
    telemetryTicker = viewModelScope.launch {
      while (true) {
        delay(2000)
        if (_connectionState.value == ConnectionState.CONNECTED) {
          val jitterLatency = (26..34).random().toLong()
          _telemetry.value = _telemetry.value.copy(latencyMs = jitterLatency)
        }
      }
    }
  }

  fun setGestureMode(mode: GestureMode) {
    _gestureMode.value = mode
    addLog("Gesture Mode", "Switched mode to ${mode.name}")
  }

  fun onScreenTouched(x: Float, y: Float) {
    _lastTouchCoordinate.value = Pair(x, y)
    val command = RemoteCommand(
      commandId = UUID.randomUUID().toString(),
      timestamp = System.currentTimeMillis(),
      type = when (_gestureMode.value) {
        GestureMode.TAP -> CommandType.TOUCH
        GestureMode.LONG_PRESS -> CommandType.TOUCH
        GestureMode.SWIPE -> CommandType.SWIPE
        GestureMode.SCROLL -> CommandType.SCROLL
      },
      x = x,
      y = y,
    )
    addLog("Input Dispatched", "${_gestureMode.value} at X:${x.toInt()}, Y:${y.toInt()}")
  }

  fun sendNavigationCommand(commandType: CommandType) {
    val command = RemoteCommand(
      commandId = UUID.randomUUID().toString(),
      timestamp = System.currentTimeMillis(),
      type = commandType,
    )
    addLog("Navigation", "Dispatched ${commandType.name} action to Target Accessibility")
  }

  fun sendTextInput(text: String) {
    if (text.isNotBlank()) {
      addLog("Text Input", "Injected text: \"$text\" into focused field")
    }
  }

  fun toggleConnection() {
    if (_connectionState.value == ConnectionState.CONNECTED) {
      _connectionState.value = ConnectionState.DISCONNECTED
      _activeDevice.value = _activeDevice.value?.copy(isConnected = false)
      addLog("Connection", "Disconnected from Target device")
    } else {
      _connectionState.value = ConnectionState.CONNECTING
      viewModelScope.launch {
        delay(600)
        _connectionState.value = ConnectionState.CONNECTED
        _activeDevice.value = _activeDevice.value?.copy(isConnected = true)
        addLog("Connection", "Reconnected securely to Target via Local TLS")
      }
    }
  }

  fun setPinDigit(index: Int, char: String) {
    val list = _pinDigits.value.toMutableList()
    if (index in list.indices) {
      list[index] = char
      _pinDigits.value = list
    }
  }

  fun clearPin() {
    _pinDigits.value = listOf("", "", "", "", "", "")
  }

  fun pairWithDevice(device: DeviceInfo) {
    _connectionState.value = ConnectionState.PAIRING
    viewModelScope.launch {
      delay(800)
      _activeDevice.value = device.copy(isConnected = true, isAuthorized = true)
      _connectionState.value = ConnectionState.CONNECTED
      addLog("Pairing", "Successfully authenticated with ${device.name}")
    }
  }

  private fun addLog(type: String, detail: String) {
    val time = "09:41:${(10..59).random()}"
    _activityLogs.value = listOf(ActivityLogEntry(timestamp = time, type = type, detail = detail)) + _activityLogs.value.take(25)
  }
}
