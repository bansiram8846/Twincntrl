package com.example.network.protocol

import org.json.JSONObject

object TwinProtocol {
  const val CONTROL_PORT = 8989
  const val STREAM_PORT = 8990
  const val DISCOVERY_PORT = 8988
  const val NSD_SERVICE_TYPE = "_twincontrol._tcp"

  // Frame streaming protocol constants
  val FRAME_MAGIC = byteArrayOf(0x54, 0x57, 0x49, 0x4E) // "TWIN"
  const val HEADER_SIZE = 32 // 4 magic + 8 timestamp + 8 frameId + 4 width + 4 height + 4 length
}

data class PeerBeacon(
  val id: String,
  val name: String,
  val model: String,
  val ipAddress: String,
  val port: Int = TwinProtocol.CONTROL_PORT,
  val batteryPercent: Int = 100,
  val isCharging: Boolean = false,
  val osVersion: String = "Android",
  val wifiSsid: String = "Local Network",
  val silentMode: Boolean = true,
  val pairingPin: String = "",
  val connectionMedium: String = "Wi-Fi",
) {
  fun toJson(): String {
    val json = JSONObject()
    json.put("id", id)
    json.put("name", name)
    json.put("model", model)
    json.put("ipAddress", ipAddress)
    json.put("port", port)
    json.put("batteryPercent", batteryPercent)
    json.put("isCharging", isCharging)
    json.put("osVersion", osVersion)
    json.put("wifiSsid", wifiSsid)
    json.put("silentMode", silentMode)
    json.put("pairingPin", pairingPin)
    json.put("connectionMedium", connectionMedium)
    return json.toString()
  }

  companion object {
    fun fromJson(jsonStr: String): PeerBeacon? {
      return try {
        val json = JSONObject(jsonStr)
        PeerBeacon(
          id = json.getString("id"),
          name = json.getString("name"),
          model = json.optString("model", "Android Device"),
          ipAddress = json.getString("ipAddress"),
          port = json.optInt("port", TwinProtocol.CONTROL_PORT),
          batteryPercent = json.optInt("batteryPercent", 100),
          isCharging = json.optBoolean("isCharging", false),
          osVersion = json.optString("osVersion", "Android"),
          wifiSsid = json.optString("wifiSsid", "Local Network"),
          silentMode = json.optBoolean("silentMode", true),
          pairingPin = json.optString("pairingPin", ""),
          connectionMedium = json.optString("connectionMedium", "Wi-Fi"),
        )
      } catch (_: Exception) {
        null
      }
    }
  }
}
