package com.example.ui.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TargetViewModel : ViewModel() {

  private val _isMasterOn = MutableStateFlow(true)
  val isMasterOn: StateFlow<Boolean> = _isMasterOn.asStateFlow()

  private val _isRemoteControlActive = MutableStateFlow(true)
  val isRemoteControlActive: StateFlow<Boolean> = _isRemoteControlActive.asStateFlow()

  private val _authorizedControllerName = MutableStateFlow("Pixel 9 Pro")
  val authorizedControllerName: StateFlow<String> = _authorizedControllerName.asStateFlow()

  private val _oneTimePasscode = MutableStateFlow("749 203")
  val oneTimePasscode: StateFlow<String> = _oneTimePasscode.asStateFlow()

  private val _passcodeExpirySeconds = MutableStateFlow(272)
  val passcodeExpirySeconds: StateFlow<Int> = _passcodeExpirySeconds.asStateFlow()

  // Permission toggles
  private val _allowTouchGestures = MutableStateFlow(true)
  val allowTouchGestures: StateFlow<Boolean> = _allowTouchGestures.asStateFlow()

  private val _allowAudioStreaming = MutableStateFlow(true)
  val allowAudioStreaming: StateFlow<Boolean> = _allowAudioStreaming.asStateFlow()

  private val _requireBiometric = MutableStateFlow(true)
  val requireBiometric: StateFlow<Boolean> = _requireBiometric.asStateFlow()

  // Android system permissions audit state
  private val _isAccessibilityGranted = MutableStateFlow(true)
  val isAccessibilityGranted: StateFlow<Boolean> = _isAccessibilityGranted.asStateFlow()

  private val _isMediaProjectionGranted = MutableStateFlow(true)
  val isMediaProjectionGranted: StateFlow<Boolean> = _isMediaProjectionGranted.asStateFlow()

  private val _isMulticastGranted = MutableStateFlow(true)
  val isMulticastGranted: StateFlow<Boolean> = _isMulticastGranted.asStateFlow()

  private var timerJob: Job? = null

  init {
    startExpiryTimer()
  }

  private fun startExpiryTimer() {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
      while (true) {
        delay(1000)
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
    if (!enabled) {
      _isRemoteControlActive.value = false
    }
  }

  fun stopSharingAndDisconnect() {
    _isRemoteControlActive.value = false
  }

  fun resumeSharing() {
    _isRemoteControlActive.value = true
  }

  fun regeneratePasscode() {
    val part1 = (100..999).random()
    val part2 = (100..999).random()
    _oneTimePasscode.value = "$part1 $part2"
    _passcodeExpirySeconds.value = 300
  }

  fun toggleAllowTouch(enabled: Boolean) {
    _allowTouchGestures.value = enabled
  }

  fun toggleAllowAudio(enabled: Boolean) {
    _allowAudioStreaming.value = enabled
  }

  fun toggleRequireBiometric(enabled: Boolean) {
    _requireBiometric.value = enabled
  }
}
