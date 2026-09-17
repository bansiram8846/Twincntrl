package com.example.ui.target

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.BluetoothHelper
import com.example.network.LocalDeviceManager
import com.example.network.discovery.DiscoveryManager
import com.example.network.protocol.PeerBeacon
import com.example.network.protocol.TwinProtocol
import com.example.network.server.ScreenStreamServer
import com.example.network.server.TargetControlServer
import com.example.service.RemoteAccessibilityService
import com.example.service.ScreenCaptureService
import com.example.util.QrCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TargetViewModel(application: Application) : AndroidViewModel(application) {

  private val context = application.applicationContext

  private val _isMasterOn = MutableStateFlow(true)
  val isMasterOn: StateFlow<Boolean> = _isMasterOn.asStateFlow()

  private val _isRemoteControlActive = MutableStateFlow(false)
  val isRemoteControlActive: StateFlow<Boolean> = _isRemoteControlActive.asStateFlow()

  private val _authorizedControllerName = MutableStateFlow("None")
  val authorizedControllerName: StateFlow<String> = _authorizedControllerName.asStateFlow()

  private val _oneTimePasscode = MutableStateFlow(LocalDeviceManager.generatePairingPin())
  val oneTimePasscode: StateFlow<String> = _oneTimePasscode.asStateFlow()

  private val _passcodeExpirySeconds = MutableStateFlow(300)
  val passcodeExpirySeconds: StateFlow<Int> = _passcodeExpirySeconds.asStateFlow()

  private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
  val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

  // Hardware & Network identity
  val deviceName: String = LocalDeviceManager.getEffectiveDeviceName(context)
  val deviceModel: String = LocalDeviceManager.getDeviceModel()
  val deviceManufacturer: String = LocalDeviceManager.getDeviceManufacturer()
  val localIpAddress: String = LocalDeviceManager.getLocalIpAddress(context)
  val wifiSsid: String = LocalDeviceManager.getWifiSsid(context)
  val bluetoothName: String = BluetoothHelper.getBluetoothName(context)
  val bluetoothAddress: String = BluetoothHelper.getBluetoothAddressOrId(context)

  // Silent Mode Connection (Unattended Access / Instant Trusted Handshake)
  private val prefs = context.getSharedPreferences("twincontrol_target_prefs", Context.MODE_PRIVATE)
  private val _isSilentModeEnabled = MutableStateFlow(prefs.getBoolean("silent_mode_enabled", true))
  val isSilentModeEnabled: StateFlow<Boolean> = _isSilentModeEnabled.asStateFlow()

  private val trustedControllerManager = com.example.network.TrustedControllerManager.getInstance(context)
  val trustedControllers: StateFlow<List<com.example.data.model.TrustedController>> = trustedControllerManager.trustedControllers

  fun removeTrustedController(id: String) {
    trustedControllerManager.removeTrustedController(id)
  }

  fun clearAllTrustedControllers() {
    trustedControllerManager.revokeAll()
  }

  private val _connectedControllerIp = MutableStateFlow<String?>(null)
  val connectedControllerIp: StateFlow<String?> = _connectedControllerIp.asStateFlow()

  fun connectToControllerFromLink(controllerIp: String, controllerName: String) {
    if (controllerIp.isBlank()) return
    _connectedControllerIp.value = controllerIp
    _authorizedControllerName.value = controllerName

    // Add controller to trusted list
    trustedControllerManager.addTrustedController(controllerName, controllerIp)

    // Notify controller invite server immediately via background HTTP & UDP
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val myIp = LocalDeviceManager.getLocalIpAddress(context)
        val myName = deviceName
        val myModel = deviceModel

        val encodedName = java.net.URLEncoder.encode(myName, "UTF-8")
        val encodedModel = java.net.URLEncoder.encode(myModel, "UTF-8")
        val targetUrl = "http://$controllerIp:${TwinProtocol.INVITE_PORT}/api/target_ready?ip=$myIp&name=$encodedName&model=$encodedModel&port=${TwinProtocol.CONTROL_PORT}"
        val connection = java.net.URL(targetUrl).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.requestMethod = "GET"
        val code = connection.responseCode
        connection.disconnect()
        Log.i("TargetViewModel", "Check-in to controller HTTP server response: $code")
      } catch (e: Exception) {
        Log.w("TargetViewModel", "HTTP check-in failed: ${e.message}")
      }

      // Also send direct UDP beacon packet to controller
      try {
        val socket = java.net.DatagramSocket()
        val beacon = PeerBeacon(
          id = "tc-${LocalDeviceManager.getLocalIpAddress(context).replace(".", "-")}",
          name = deviceName,
          model = deviceModel,
          ipAddress = LocalDeviceManager.getLocalIpAddress(context),
          port = TwinProtocol.CONTROL_PORT,
          batteryPercent = 100,
          isCharging = false,
          osVersion = LocalDeviceManager.getOsVersion(),
          wifiSsid = LocalDeviceManager.getWifiSsid(context),
          silentMode = true,
          pairingPin = "AUTO_TRUSTED",
          connectionMedium = "Invite Link",
        )
        val jsonBytes = beacon.toJson().toByteArray(Charsets.UTF_8)
        val targetAddr = java.net.InetAddress.getByName(controllerIp)
        val packet = java.net.DatagramPacket(jsonBytes, jsonBytes.size, targetAddr, TwinProtocol.DISCOVERY_PORT)
        socket.send(packet)
        socket.close()
      } catch (e: Exception) {
        Log.w("TargetViewModel", "UDP check-in failed: ${e.message}")
      }
    }

    startServerInfrastructure()
  }

  fun getWebShareUrl(): String {
    val ip = if (localIpAddress.isNotBlank() && localIpAddress != "127.0.0.1") localIpAddress else "127.0.0.1"
    return "http://$ip:${TwinProtocol.WEB_PORT}/"
  }

  fun copyWebLink(ctx: Context) {
    val url = getWebShareUrl()
    try {
      val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      val clip = android.content.ClipData.newPlainText("TwinControl Web Link", url)
      clipboard.setPrimaryClip(clip)
      android.widget.Toast.makeText(ctx, "Link copied: $url", android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
  }

  fun openWebRemoteInBrowser(ctx: Context) {
    val url = getWebShareUrl()
    try {
      val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      ctx.startActivity(intent)
    } catch (_: Exception) {}
  }

  fun openAccessibilitySettings(ctx: Context) {
    try {
      val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      ctx.startActivity(intent)
    } catch (_: Exception) {
      try {
        val fallback = Intent("android.settings.ACCESSIBILITY_SETTINGS").apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(fallback)
      } catch (_: Exception) {}
    }
  }

  fun shareWebLink(ctx: Context) {
    val url = getWebShareUrl()
    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, "View and control my screen instantly without installing any app: $url")
      type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Screen Control Link")
    shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    ctx.startActivity(shareIntent)
  }

  private val _selectedMedium = MutableStateFlow("WIFI") // "WIFI", "QR", "BLUETOOTH", "INTERNET"
  val selectedMedium: StateFlow<String> = _selectedMedium.asStateFlow()

  val pairingPayload: String
    get() = QrCodeUtil.buildPairingUri(
      ipAddress = localIpAddress,
      port = 8989,
      pin = _oneTimePasscode.value,
      deviceName = deviceName,
      deviceModel = deviceModel,
    )

  // Permission toggles
  private val _allowTouchGestures = MutableStateFlow(true)
  val allowTouchGestures: StateFlow<Boolean> = _allowTouchGestures.asStateFlow()

  private val _allowAudioStreaming = MutableStateFlow(true)
  val allowAudioStreaming: StateFlow<Boolean> = _allowAudioStreaming.asStateFlow()

  private val _requireBiometric = MutableStateFlow(false)
  val requireBiometric: StateFlow<Boolean> = _requireBiometric.asStateFlow()

  // Android system permissions audit state (defaulted to granted as requested)
  private val _isAccessibilityGranted = MutableStateFlow(true)
  val isAccessibilityGranted: StateFlow<Boolean> = _isAccessibilityGranted.asStateFlow()

  private val _isMediaProjectionGranted = MutableStateFlow(true)
  val isMediaProjectionGranted: StateFlow<Boolean> = _isMediaProjectionGranted.asStateFlow()

  private val _isMulticastGranted = MutableStateFlow(true)
  val isMulticastGranted: StateFlow<Boolean> = _isMulticastGranted.asStateFlow()

  // Network infrastructure
  private val discoveryManager = DiscoveryManager(context)
  private val controlServer = TargetControlServer.getInstance(context)

  private var timerJob: Job? = null

  init {
    controlServer.onControllerAuthorized = { name ->
      viewModelScope.launch {
        _authorizedControllerName.value = name
        _isRemoteControlActive.value = true
        ScreenStreamServer.instance.startCaptureLoop()
      }
    }
    controlServer.onControllerDisconnected = {
      viewModelScope.launch {
        _isRemoteControlActive.value = false
        _authorizedControllerName.value = "None"
      }
    }
    controlServer.onCommandReceived = { _, _ -> }
    controlServer.activePasscodeProvider = { _oneTimePasscode.value }
    controlServer.isSilentModeEnabled = { _isSilentModeEnabled.value }
    controlServer.allowTouchGestures = _allowTouchGestures.value
    updateQrCode()
    startExpiryTimer()
    startServerInfrastructure()
    checkSystemPermissions()
  }

  private fun updateQrCode() {
    viewModelScope.launch(Dispatchers.Default) {
      val payload = pairingPayload
      val bmp = QrCodeUtil.generateQrBitmap(payload, 512)
      _qrBitmap.value = bmp
    }
  }

  private fun startServerInfrastructure() {
    controlServer.start()
    ScreenStreamServer.instance.start()
    discoveryManager.startAdvertising(_oneTimePasscode.value, silentMode = _isSilentModeEnabled.value, medium = _selectedMedium.value)
  }

  private fun stopServerInfrastructure() {
    discoveryManager.stopAdvertising()
    controlServer.stop()
    ScreenStreamServer.instance.stop()
  }

  fun checkSystemPermissions() {
    _isAccessibilityGranted.value = RemoteAccessibilityService.isRunning
    _isMediaProjectionGranted.value = ScreenCaptureService.isRunning
  }

  private fun startExpiryTimer() {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
      while (true) {
        delay(1000)
        checkSystemPermissions()
        if (_passcodeExpirySeconds.value > 0) {
          _passcodeExpirySeconds.value -= 1
        } else {
          regeneratePasscode()
        }
      }
    }
  }

  fun toggleMaster(enabled: Boolean) {
    _isMasterOn.value = enabled
    if (enabled) {
      startServerInfrastructure()
    } else {
      stopServerInfrastructure()
      _isRemoteControlActive.value = false
    }
  }

  fun stopSharingAndDisconnect() {
    controlServer.sendDisconnect()
    _isRemoteControlActive.value = false
    _authorizedControllerName.value = "None"
    try {
      context.stopService(Intent(context, ScreenCaptureService::class.java))
      _isMediaProjectionGranted.value = false
    } catch (_: Exception) {}
  }

  fun resumeSharing() {
    _isRemoteControlActive.value = true
  }

  fun regeneratePasscode() {
    val newPin = LocalDeviceManager.generatePairingPin()
    _oneTimePasscode.value = newPin
    _passcodeExpirySeconds.value = 300
    updateQrCode()
    if (_isMasterOn.value) {
      discoveryManager.startAdvertising(newPin, silentMode = _isSilentModeEnabled.value, medium = _selectedMedium.value)
    }
  }

  fun toggleSilentMode(enabled: Boolean) {
    _isSilentModeEnabled.value = enabled
    prefs.edit().putBoolean("silent_mode_enabled", enabled).apply()
    if (_isMasterOn.value) {
      discoveryManager.startAdvertising(_oneTimePasscode.value, silentMode = enabled, medium = _selectedMedium.value)
    }
  }

  fun setSelectedMedium(medium: String) {
    _selectedMedium.value = medium
    if (_isMasterOn.value) {
      discoveryManager.startAdvertising(_oneTimePasscode.value, silentMode = _isSilentModeEnabled.value, medium = medium)
    }
  }

  fun toggleAllowTouch(enabled: Boolean) {
    _allowTouchGestures.value = enabled
    controlServer.allowTouchGestures = enabled
  }

  fun toggleAllowAudio(enabled: Boolean) {
    _allowAudioStreaming.value = enabled
  }

  fun toggleRequireBiometric(enabled: Boolean) {
    _requireBiometric.value = enabled
  }

  fun onMediaProjectionStarted() {
    _isMediaProjectionGranted.value = true
  }

  fun onMediaProjectionStopped() {
    _isMediaProjectionGranted.value = false
  }

  fun refreshPermissionsStatus() {
    _isAccessibilityGranted.value = true
    _isMediaProjectionGranted.value = true
  }

  override fun onCleared() {
    timerJob?.cancel()
    controlServer.onControllerAuthorized = null
    controlServer.onControllerDisconnected = null
    controlServer.onCommandReceived = null
    if (!ScreenCaptureService.isRunning) {
      stopServerInfrastructure()
    }
    super.onCleared()
  }
}
