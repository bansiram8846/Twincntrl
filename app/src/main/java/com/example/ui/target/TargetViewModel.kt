package com.example.ui.target

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.LocalDeviceManager
import com.example.network.discovery.DiscoveryManager
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

  private val _requireBiometric = MutableStateFlow(true)
  val requireBiometric: StateFlow<Boolean> = _requireBiometric.asStateFlow()

  // Android system permissions audit state
  private val _isAccessibilityGranted = MutableStateFlow(RemoteAccessibilityService.instance != null)
  val isAccessibilityGranted: StateFlow<Boolean> = _isAccessibilityGranted.asStateFlow()

  private val _isMediaProjectionGranted = MutableStateFlow(ScreenCaptureService.isRunning)
  val isMediaProjectionGranted: StateFlow<Boolean> = _isMediaProjectionGranted.asStateFlow()

  private val _isMulticastGranted = MutableStateFlow(true)
  val isMulticastGranted: StateFlow<Boolean> = _isMulticastGranted.asStateFlow()

  // Network infrastructure
  private val discoveryManager = DiscoveryManager(context)
  private val controlServer = TargetControlServer(
    context = context,
    onControllerAuthorized = { name ->
      viewModelScope.launch {
        _authorizedControllerName.value = name
        _isRemoteControlActive.value = true
      }
    },
    onControllerDisconnected = {
      viewModelScope.launch {
        _isRemoteControlActive.value = false
        _authorizedControllerName.value = "None"
      }
    },
    onCommandReceived = { _, _ -> }
  )

  private var timerJob: Job? = null

  init {
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
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val isA11yOn = RemoteAccessibilityService.instance != null ||
      (am?.isEnabled == true && am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
      })
    _isAccessibilityGranted.value = isA11yOn
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
    _isRemoteControlActive.value = true
  }

  override fun onCleared() {
    timerJob?.cancel()
    stopServerInfrastructure()
    super.onCleared()
  }
}
