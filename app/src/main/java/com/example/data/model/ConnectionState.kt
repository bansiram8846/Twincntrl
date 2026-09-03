package com.example.data.model

enum class ConnectionState {
  DISCONNECTED,
  DISCOVERING,
  PAIRING,
  WAITING_AUTHORIZATION,
  CONNECTING,
  CONNECTED,
  RECONNECTING,
  FAILED
}

enum class PairingState {
  IDLE,
  GENERATING_IDENTITY,
  DISPLAYING_CODE,
  SCANNING,
  AUTHENTICATING,
  AUTHORIZED,
  REJECTED,
  EXPIRED
}

enum class AppMode {
  CONTROLLER,
  TARGET
}

enum class GestureMode {
  TAP,
  LONG_PRESS,
  SWIPE,
  SCROLL
}
