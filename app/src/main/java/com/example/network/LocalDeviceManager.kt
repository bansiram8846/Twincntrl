package com.example.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections

object LocalDeviceManager {

  private const val PREFS_NAME = "twincontrol_device_prefs"
  private const val KEY_CUSTOM_DEVICE_NAME = "custom_device_name"

  fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER?.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
    }?.trim() ?: ""
    val model = Build.MODEL?.trim() ?: ""
    if (model.isBlank()) {
      return if (manufacturer.isNotBlank()) manufacturer else "Android Device"
    }
    return if (manufacturer.isNotBlank() && model.startsWith(manufacturer, ignoreCase = true)) {
      model
    } else if (manufacturer.isNotBlank()) {
      "$manufacturer $model"
    } else {
      model
    }
  }

  fun getEffectiveDeviceName(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val custom = prefs.getString(KEY_CUSTOM_DEVICE_NAME, null)?.trim()
    if (!custom.isNullOrEmpty()) {
      return custom
    }
    return getDeviceName()
  }

  fun setCustomDeviceName(context: Context, name: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (name.isBlank()) {
      prefs.edit().remove(KEY_CUSTOM_DEVICE_NAME).apply()
    } else {
      prefs.edit().putString(KEY_CUSTOM_DEVICE_NAME, name.trim()).apply()
    }
  }

  fun getCustomDeviceName(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_CUSTOM_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
  }

  fun getAllLocalIpAddresses(): Set<String> {
    val result = mutableSetOf("127.0.0.1", "0.0.0.0", "localhost")
    try {
      val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
      for (intf in interfaces) {
        val addrs = Collections.list(intf.inetAddresses)
        for (addr in addrs) {
          addr.hostAddress?.let { ip ->
            val cleanIp = ip.substringBefore("%").trim()
            if (cleanIp.isNotBlank()) {
              result.add(cleanIp)
            }
          }
        }
      }
    } catch (_: Exception) {}
    return result
  }

  fun isSelfDevice(context: Context, ipAddress: String, deviceName: String, deviceId: String = ""): Boolean {
    val allLocalIps = getAllLocalIpAddresses()
    val cleanIp = ipAddress.substringBefore("%").trim()
    if (cleanIp.isBlank() || allLocalIps.contains(cleanIp)) return true

    val myName = getDeviceName().trim()
    val myEffectiveName = getEffectiveDeviceName(context).trim()
    val targetName = deviceName.trim()

    if (targetName.equals(myName, ignoreCase = true) || targetName.equals(myEffectiveName, ignoreCase = true)) {
      return true
    }

    val myIp = getLocalIpAddress(context)
    if (deviceId.isNotBlank() && deviceId.contains(myIp.replace(".", "-"))) {
      return true
    }

    return false
  }

  fun getDeviceModel(): String = Build.MODEL ?: "Android Device"

  fun getDeviceManufacturer(): String = Build.MANUFACTURER ?: "Unknown"

  fun getDeviceBrand(): String = Build.BRAND ?: "Unknown"

  fun getDeviceHardware(): String = "${Build.BOARD} / ${Build.HARDWARE}"

  fun getOsVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

  fun isRunningInEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT ?: ""
    val model = Build.MODEL ?: ""
    val brand = Build.BRAND ?: ""
    val device = Build.DEVICE ?: ""
    val product = Build.PRODUCT ?: ""
    val hardware = Build.HARDWARE ?: ""
    return fingerprint.startsWith("generic") ||
      fingerprint.startsWith("unknown") ||
      model.contains("google_sdk") ||
      model.contains("Emulator") ||
      model.contains("Android SDK built for") ||
      hardware.contains("goldfish") ||
      hardware.contains("ranchu") ||
      product.contains("sdk") ||
      product.contains("google_sdk") ||
      brand.startsWith("generic") && device.startsWith("generic")
  }

  fun getLocalIpAddress(context: Context): String {
    try {
      // 1. Try finding IPv4 from network interfaces (wlan0, ap0, eth0)
      val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
      for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue
        val addrs = Collections.list(intf.inetAddresses)
        for (addr in addrs) {
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            val ip = addr.hostAddress ?: continue
            // Prefer 192.168.x.x, 10.x.x.x, 172.16-31.x.x
            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
              return ip
            }
          }
        }
      }

      // 2. Fallback to any non-loopback IPv4
      for (intf in interfaces) {
        val addrs = Collections.list(intf.inetAddresses)
        for (addr in addrs) {
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            return addr.hostAddress ?: "127.0.0.1"
          }
        }
      }
    } catch (_: Exception) {}

    return "127.0.0.1"
  }

  fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
    return try {
      val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
      val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
      val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
      val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

      val percent = if (level >= 0 && scale > 0) {
        ((level.toFloat() / scale.toFloat()) * 100).toInt()
      } else {
        100
      }
      Pair(percent, isCharging)
    } catch (_: Exception) {
      Pair(100, false)
    }
  }

  fun getWifiSsid(context: Context): String {
    return try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      val network = cm?.activeNetwork
      val caps = cm?.getNetworkCapabilities(network)

      if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = wm?.connectionInfo
        val ssid = info?.ssid?.replace("\"", "")
        if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
          return ssid
        }
        return "Wi-Fi (Connected)"
      } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
        "Local Ethernet"
      } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
        "Cellular Data"
      } else {
        "Local Subnet"
      }
    } catch (_: Exception) {
      "Local Network"
    }
  }

  fun generatePairingPin(): String {
    val random = SecureRandom()
    val part1 = 100 + random.nextInt(900)
    val part2 = 100 + random.nextInt(900)
    return "$part1 $part2"
  }
}
