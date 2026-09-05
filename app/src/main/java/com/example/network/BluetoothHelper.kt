package com.example.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothHelper {

  fun getBluetoothName(context: Context): String {
    return try {
      val manager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
      val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
      if (adapter != null && adapter.isEnabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
          context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
          adapter.name ?: "Android Bluetooth"
        } else {
          LocalDeviceManager.getDeviceName() + " (BT)"
        }
      } else {
        LocalDeviceManager.getDeviceName() + " (BT Ready)"
      }
    } catch (_: Exception) {
      LocalDeviceManager.getDeviceName() + " (BT)"
    }
  }

  fun isBluetoothAvailable(context: Context): Boolean {
    return try {
      val manager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
      val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
      adapter != null
    } catch (_: Exception) {
      false
    }
  }

  fun isBluetoothEnabled(context: Context): Boolean {
    return try {
      val manager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
      val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
      adapter?.isEnabled == true
    } catch (_: Exception) {
      false
    }
  }

  fun getBluetoothAddressOrId(context: Context): String {
    val base = LocalDeviceManager.getLocalIpAddress(context).replace(".", ":")
    return "02:00:BT:$base".take(17)
  }
}
