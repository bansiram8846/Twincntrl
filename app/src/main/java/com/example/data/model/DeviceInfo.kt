package com.example.data.model

data class DeviceInfo(
  val id: String,
  val name: String,
  val model: String,
  val ipAddress: String,
  val port: Int = 8443,
  val isAuthorized: Boolean = false,
  val isConnected: Boolean = false,
  val locationTag: String = "Local Network",
  val lastSeen: String = "Now",
  val batteryPercent: Int = 85,
  val isCharging: Boolean = false,
  val wifiSsid: String = "5 GHz",
  val signalDbm: Int = -42,
  val osVersion: String = "Android 14",
  val streamResolution: String = "1080×2400",
  val streamFps: Int = 60,
  val streamCodec: String = "H.265",
)

data class SessionTelemetry(
  val latencyMs: Long = 28,
  val fps: Int = 60,
  val resolutionWidth: Int = 1080,
  val resolutionHeight: Int = 2400,
  val bitrateMbps: Float = 6.4f,
  val codec: String = "H.265",
  val protocol: String = "TLS 1.3 Direct Socket",
  val droppedFrames: Int = 0,
  val inputIngestionLatencyMs: Float = 4.2f,
  val isMirroringActive: Boolean = true,
)
