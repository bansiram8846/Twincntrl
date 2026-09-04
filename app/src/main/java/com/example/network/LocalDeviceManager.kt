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

  fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    val model = Build.MODEL
    return if (model.startsWith(manufacturer, ignoreCase = true)) {
      model
    } else {
      "$manufacturer $model"
    }
  }

  fun getDeviceModel(): String = Build.MODEL

  fun getOsVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

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
