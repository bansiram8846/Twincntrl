package com.example.ui.controller

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CommandType
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceInfo
import com.example.data.model.GestureMode
import com.example.data.model.PairingState
import com.example.data.model.SessionTelemetry
import com.example.network.LocalDeviceManager
import com.example.network.client.ControllerClient
import com.example.network.client.StreamReceiver
import com.example.network.discovery.DiscoveryManager
import com.example.network.protocol.TwinProtocol
import com.example.util.QrCodeUtil
import com.example.util.QrPairingData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ActivityLogEntry(
  val id: String = UUID.randomUUID().toString(),
  val timestamp: String,
  val type: String,
  val detail: String,
  val isSuccess: Boolean = true,
)

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

  private val context = application.applicationContext
  private val prefs = context.getSharedPreferences("twincontrol_paired_devices", Context.MODE_PRIVATE)

  // Hardware & Network identity of this phone (Controller)
  val thisDeviceName: String = LocalDeviceManager.getDeviceName()
  val thisDeviceModel: String = LocalDeviceManager.getDeviceModel()
  val thisDeviceManufacturer: String = LocalDeviceManager.getDeviceManufacturer()
  val thisDeviceBrand: String = LocalDeviceManager.getDeviceBrand()
  val thisDeviceHardware: String = LocalDeviceManager.getDeviceHardware()
  val thisDeviceOs: String = LocalDeviceManager.getOsVersion()
  val isEmulator: Boolean = LocalDeviceManager.isRunningInEmulator()
  val localIpAddress: String = LocalDeviceManager.getLocalIpAddress(context)
  val wifiSsid: String = LocalDeviceManager.getWifiSsid(context)

  private val _effectiveDeviceName = MutableStateFlow(LocalDeviceManager.getEffectiveDeviceName(context))
  val effectiveDeviceName: StateFlow<String> = _effectiveDeviceName.asStateFlow()

  fun updateCustomDeviceName(name: String) {
    LocalDeviceManager.setCustomDeviceName(context, name)
    _effectiveDeviceName.value = LocalDeviceManager.getEffectiveDeviceName(context)
  }

  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _pairingState = MutableStateFlow(PairingState.IDLE)
  val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

  private val _activeDevice = MutableStateFlow<DeviceInfo?>(null)
  val activeDevice: StateFlow<DeviceInfo?> = _activeDevice.asStateFlow()

  private val _telemetry = MutableStateFlow(
    SessionTelemetry(
      latencyMs = 0,
      fps = 0,
      resolutionWidth = 1080,
      resolutionHeight = 2400,
      bitrateMbps = 0f,
      codec = "JPEG-Stream",
      protocol = "TLS 1.3 Direct Socket",
      droppedFrames = 0,
      inputIngestionLatencyMs = 0f,
      isMirroringActive = false,
    )
  )
  val telemetry: StateFlow<SessionTelemetry> = _telemetry.asStateFlow()

  // Real remote screen live bitmap
  private val _remoteScreenBitmap = MutableStateFlow<Bitmap?>(null)
  val remoteScreenBitmap: StateFlow<Bitmap?> = _remoteScreenBitmap.asStateFlow()

  private val _gestureMode = MutableStateFlow(GestureMode.TAP)
  val gestureMode: StateFlow<GestureMode> = _gestureMode.asStateFlow()

  private val _lastTouchCoordinate = MutableStateFlow<Pair<Float, Float>?>(null)
  val lastTouchCoordinate: StateFlow<Pair<Float, Float>?> = _lastTouchCoordinate.asStateFlow()

  // Numeric 6-digit PIN entry state (clean empty start)
  private val _pinDigits = MutableStateFlow(listOf("", "", "", "", "", ""))
  val pinDigits: StateFlow<List<String>> = _pinDigits.asStateFlow()

  private val discoveryManager = DiscoveryManager(context)
  val nearbyDevices: StateFlow<List<DeviceInfo>> = discoveryManager.discoveredDevices
    .combine(effectiveDeviceName) { list, effectiveName ->
      val allLocalIps = LocalDeviceManager.getAllLocalIpAddresses()
      val defaultName = thisDeviceName
      list.filter { dev ->
        !allLocalIps.contains(dev.ipAddress) &&
        !dev.name.equals(effectiveName, ignoreCase = true) &&
        !dev.name.equals(defaultName, ignoreCase = true) &&
        !dev.id.contains(localIpAddress.replace(".", "-"))
      }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _recentDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
  val recentDevices: StateFlow<List<DeviceInfo>> = _recentDevices.asStateFlow()

  private val _activityLogs = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
  val activityLogs: StateFlow<List<ActivityLogEntry>> = _activityLogs.asStateFlow()

  // Network clients
  private val controllerClient = ControllerClient(
    onPairResponse = { success, errorMsg ->
      viewModelScope.launch {
        if (success) {
          _pairingState.value = PairingState.AUTHORIZED
          _connectionState.value = ConnectionState.CONNECTED
          val dev = _activeDevice.value?.copy(isConnected = true, isAuthorized = true)
          _activeDevice.value = dev
          if (dev != null) {
            savePairedDevice(dev)
            addLog("Pairing Succeeded", "Authenticated securely with ${dev.name}")
            // Start video stream client
            streamReceiver.start(dev.ipAddress, TwinProtocol.STREAM_PORT)
          }
        } else {
          _pairingState.value = PairingState.REJECTED
          _connectionState.value = ConnectionState.FAILED
          addLog("Auth Rejected", errorMsg ?: "Invalid passcode")
        }
      }
    },
    onPongReceived = { rttMs ->
      _telemetry.value = _telemetry.value.copy(
        latencyMs = rttMs,
        inputIngestionLatencyMs = (rttMs / 2f).coerceAtLeast(1.5f),
      )
    },
    onDisconnected = { reason ->
      viewModelScope.launch {
        if (_connectionState.value == ConnectionState.CONNECTED) {
          _connectionState.value = ConnectionState.DISCONNECTED
          _activeDevice.value = _activeDevice.value?.copy(isConnected = false)
          _remoteScreenBitmap.value = null
          _telemetry.value = _telemetry.value.copy(isMirroringActive = false, fps = 0)
          addLog("Session Disconnected", reason)
        }
      }
    }
  )

  private val streamReceiver = StreamReceiver(
    onFrameReceived = { bitmap, update ->
      _remoteScreenBitmap.value = bitmap
      _telemetry.value = _telemetry.value.copy(
        fps = update.fps,
        latencyMs = if (update.latencyMs > 0) update.latencyMs else _telemetry.value.latencyMs,
        bitrateMbps = update.bitrateMbps,
        resolutionWidth = update.width,
        resolutionHeight = update.height,
        droppedFrames = update.droppedFrames,
        isMirroringActive = true,
      )
    },
    onStreamDisconnected = {
      _remoteScreenBitmap.value = null
      _telemetry.value = _telemetry.value.copy(isMirroringActive = false, fps = 0)
    }
  )

  private var pingJob: Job? = null

  init {
    loadSavedDevices()
    startDiscovery()
    startPingTicker()
    addLog("System Initialized", "TwinControl Controller active on ${LocalDeviceManager.getWifiSsid(context)}")
  }

  fun startDiscovery() {
    discoveryManager.startDiscovery()
  }

  fun refreshNearbyDevices() {
    discoveryManager.startDiscovery()
  }

  fun stopDiscovery() {
    discoveryManager.stopDiscovery()
  }

  fun addDirectDevice(ip: String, port: Int = TwinProtocol.CONTROL_PORT) {
    discoveryManager.addDirectDevice(ip, port)
    addLog("Direct IP Added", "Target candidate $ip:$port added to discovery")
  }

  private fun startPingTicker() {
    pingJob?.cancel()
    pingJob = viewModelScope.launch {
      while (true) {
        delay(2000)
        if (controllerClient.isConnected) {
          controllerClient.sendPing()
        }
      }
    }
  }

  fun setGestureMode(mode: GestureMode) {
    _gestureMode.value = mode
    addLog("Gesture Mode", "Switched mode to ${mode.name}")
  }

  fun onScreenTouched(normX: Float, normY: Float) {
    val displayW = _telemetry.value.resolutionWidth
    val displayH = _telemetry.value.resolutionHeight
    val pixelX = normX * displayW
    val pixelY = normY * displayH
    _lastTouchCoordinate.value = Pair(pixelX, pixelY)

    if (controllerClient.isConnected) {
      val action = when (_gestureMode.value) {
        GestureMode.TAP -> "TAP"
        GestureMode.LONG_PRESS -> "LONG_PRESS"
        GestureMode.SWIPE -> "SWIPE"
        GestureMode.SCROLL -> "SCROLL"
      }
      controllerClient.sendTouch(normX, normY, action)
      addLog("Input Dispatched", "${_gestureMode.value} at (${pixelX.toInt()}, ${pixelY.toInt()})")
    }
  }

  fun onScreenSwiped(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L) {
    if (controllerClient.isConnected) {
      controllerClient.sendSwipe(startX, startY, endX, endY, durationMs)
      addLog("Swipe Dispatched", "Dispatched drag/swipe on Target")
    }
  }

  fun sendNavigationCommand(commandType: CommandType) {
    if (controllerClient.isConnected) {
      controllerClient.sendNavigation(commandType)
      addLog("Navigation", "Dispatched ${commandType.name} action to Target")
    }
  }

  fun sendTextInput(text: String) {
    if (text.isNotBlank() && controllerClient.isConnected) {
      controllerClient.sendTextInput(text)
      addLog("Text Input", "Injected text: \"$text\" into focused field")
    }
  }

  fun sendGlobalAction(action: String) {
    if (controllerClient.isConnected) {
      controllerClient.sendGlobalAction(action)
      addLog("System Action", "Dispatched $action to Target")
    }
  }

  fun sendVolume(direction: String) {
    if (controllerClient.isConnected) {
      controllerClient.sendVolume(direction)
      addLog("Volume Action", "Volume $direction dispatched")
    }
  }

  fun toggleConnection() {
    if (_connectionState.value == ConnectionState.CONNECTED) {
      disconnect()
    } else {
      _activeDevice.value?.let { device ->
        pairWithDevice(device)
      }
    }
  }

  fun disconnect() {
    controllerClient.disconnect()
    streamReceiver.stop()
    _connectionState.value = ConnectionState.DISCONNECTED
    _activeDevice.value = _activeDevice.value?.copy(isConnected = false)
    _remoteScreenBitmap.value = null
    _telemetry.value = _telemetry.value.copy(isMirroringActive = false, fps = 0)
    addLog("Connection", "Disconnected from Target device")
  }

  fun setPinDigit(index: Int, char: String) {
    val list = _pinDigits.value.toMutableList()
    if (index in list.indices) {
      list[index] = char
      _pinDigits.value = list
    }
  }

  fun appendPinDigit(char: String) {
    val list = _pinDigits.value.toMutableList()
    val firstEmpty = list.indexOfFirst { it.isEmpty() }
    if (firstEmpty != -1) {
      list[firstEmpty] = char
      _pinDigits.value = list
    }
  }

  fun popPinDigit() {
    val list = _pinDigits.value.toMutableList()
    val lastFilled = list.indexOfLast { it.isNotEmpty() }
    if (lastFilled != -1) {
      list[lastFilled] = ""
      _pinDigits.value = list
    }
  }

  fun setFullPin(pin: String) {
    val cleaned = pin.filter { it.isDigit() }.take(6)
    val list = MutableList(6) { "" }
    for (i in cleaned.indices) {
      list[i] = cleaned[i].toString()
    }
    _pinDigits.value = list
  }

  fun clearPin() {
    _pinDigits.value = listOf("", "", "", "", "", "")
  }

  fun getEnteredPin(): String {
    return _pinDigits.value.joinToString("")
  }

  fun pairWithDevice(device: DeviceInfo, directPin: String? = null, silent: Boolean = false) {
    val pinToUse = (directPin ?: if (device.silentConnectCapable || silent) device.pairingPin.ifEmpty { "SILENT_AUTO" } else getEnteredPin()).trim()
    _activeDevice.value = device
    _connectionState.value = ConnectionState.PAIRING
    _pairingState.value = PairingState.AUTHENTICATING

    viewModelScope.launch {
      val isSilent = silent || device.silentConnectCapable || pinToUse == "SILENT_AUTO"
      val methodStr = if (isSilent) "Silent Connect" else "Pairing"
      addLog("Connection", "[$methodStr] Connecting to ${device.name} at ${device.ipAddress}:${device.port}...")
      val connected = controllerClient.connect(device.ipAddress, device.port)
      if (connected) {
        addLog("Handshake", if (isSilent) "Performing instant silent handshake..." else "Sending authentication PIN to target...")
        val myName = effectiveDeviceName.value
        controllerClient.requestPair(pinToUse, myName, silentMode = isSilent)
      } else {
        _connectionState.value = ConnectionState.FAILED
        _pairingState.value = PairingState.REJECTED
        addLog("Connection Error", "Could not reach target at ${device.ipAddress}:${device.port}")
      }
    }
  }

  fun connectSilently(device: DeviceInfo) {
    pairWithDevice(device, directPin = device.pairingPin.ifEmpty { "SILENT_AUTO" }, silent = true)
  }

  fun connectOverInternet(host: String, port: Int = TwinProtocol.CONTROL_PORT, tokenOrPin: String = "") {
    val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removePrefix("tcp://")
    val device = DeviceInfo(
      id = "internet_${cleanHost.replace(".", "_")}_$port",
      name = "Remote Device ($cleanHost)",
      model = "Internet Remote Target",
      ipAddress = cleanHost,
      port = port,
      isAuthorized = true,
      isConnected = false,
      locationTag = "Remote Internet (WAN)",
      lastSeen = "Just now",
      connectionMedium = "Internet",
      silentConnectCapable = true,
      pairingPin = tokenOrPin.ifEmpty { "SILENT_AUTO" },
    )
    pairWithDevice(device, directPin = tokenOrPin.ifEmpty { "SILENT_AUTO" }, silent = true)
  }

  fun pairFromQrString(qrString: String): Boolean {
    val qrData = QrCodeUtil.parsePairingData(qrString) ?: return false
    pairFromQrData(qrData)
    return true
  }

  fun pairFromQrData(qrData: QrPairingData) {
    addLog("QR Scanner", "Decoded valid pairing payload for ${qrData.deviceName} (${qrData.ipAddress})")
    val device = DeviceInfo(
      id = "device_${qrData.ipAddress.replace(".", "_")}",
      name = qrData.deviceName,
      model = qrData.deviceModel,
      ipAddress = qrData.ipAddress,
      port = qrData.port,
      batteryPercent = 100,
      wifiSsid = "Direct Network",
      osVersion = "Android",
      isAuthorized = true,
      isConnected = false,
    )
    pairWithDevice(device, qrData.pin)
  }

  fun removeRecentDevice(device: DeviceInfo) {
    val current = _recentDevices.value.toMutableList()
    current.removeAll { it.id == device.id || it.ipAddress == device.ipAddress }
    _recentDevices.value = current
    saveRecentDevicesToStorage(current)
    addLog("Trust Store", "Removed ${device.name} from paired devices")
  }

  private fun savePairedDevice(device: DeviceInfo) {
    val current = _recentDevices.value.toMutableList()
    val existingIndex = current.indexOfFirst { it.id == device.id || it.ipAddress == device.ipAddress }
    if (existingIndex >= 0) {
      current[existingIndex] = device
    } else {
      current.add(0, device)
    }
    _recentDevices.value = current
    saveRecentDevicesToStorage(current)
  }

  private fun saveRecentDevicesToStorage(list: List<DeviceInfo>) {
    val array = JSONArray()
    for (d in list) {
      val obj = JSONObject().apply {
        put("id", d.id)
        put("name", d.name)
        put("model", d.model)
        put("ipAddress", d.ipAddress)
        put("port", d.port)
        put("batteryPercent", d.batteryPercent)
        put("wifiSsid", d.wifiSsid)
        put("osVersion", d.osVersion)
      }
      array.put(obj)
    }
    prefs.edit().putString("saved_devices", array.toString()).apply()
  }

  private fun loadSavedDevices() {
    val jsonStr = prefs.getString("saved_devices", null) ?: return
    try {
      val array = JSONArray(jsonStr)
      val list = mutableListOf<DeviceInfo>()
      val allLocalIps = LocalDeviceManager.getAllLocalIpAddresses()
      val myName = effectiveDeviceName.value
      val defaultName = thisDeviceName
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val ip = obj.getString("ipAddress")
        val devName = obj.getString("name")
        if (allLocalIps.contains(ip) || devName.equals(myName, ignoreCase = true) || devName.equals(defaultName, ignoreCase = true)) {
          continue
        }
        list.add(
          DeviceInfo(
            id = obj.getString("id"),
            name = devName,
            model = obj.optString("model", "Android Device"),
            ipAddress = ip,
            port = obj.optInt("port", TwinProtocol.CONTROL_PORT),
            isAuthorized = true,
            isConnected = false,
            locationTag = "Paired Machine",
            lastSeen = "Previously Paired",
            batteryPercent = obj.optInt("batteryPercent", 80),
            wifiSsid = obj.optString("wifiSsid", "Wi-Fi"),
            osVersion = obj.optString("osVersion", "Android"),
          )
        )
      }
      _recentDevices.value = list
    } catch (_: Exception) {}
  }

  private fun addLog(type: String, detail: String) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    _activityLogs.value = listOf(ActivityLogEntry(timestamp = time, type = type, detail = detail)) + _activityLogs.value.take(40)
  }

  override fun onCleared() {
    pingJob?.cancel()
    disconnect()
    stopDiscovery()
    super.onCleared()
  }
}
