package com.example.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.data.model.DeviceInfo
import com.example.network.LocalDeviceManager
import com.example.network.protocol.PeerBeacon
import com.example.network.protocol.TwinProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DiscoveryManager(private val context: Context) {

  companion object {
    private const val TAG = "DiscoveryManager"
    private const val SERVICE_NAME_PREFIX = "TwinControl-"
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

  private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
  val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

  private var registrationListener: NsdManager.RegistrationListener? = null
  private var discoveryListener: NsdManager.DiscoveryListener? = null

  private var udpBroadcastJob: Job? = null
  private var udpListenerJob: Job? = null
  private var udpListeningSocket: DatagramSocket? = null

  // --- Target Advertising ---
  fun startAdvertising(passcode: String, silentMode: Boolean = true, medium: String = "Wi-Fi") {
    stopAdvertising()

    val localIp = LocalDeviceManager.getLocalIpAddress(context)
    val deviceName = LocalDeviceManager.getDeviceName()
    val model = LocalDeviceManager.getDeviceModel()
    val osVersion = LocalDeviceManager.getOsVersion()
    val (battery, isCharging) = LocalDeviceManager.getBatteryInfo(context)
    val wifiSsid = LocalDeviceManager.getWifiSsid(context)

    // 1. Android NSD
    try {
      val serviceInfo = NsdServiceInfo().apply {
        serviceName = "$SERVICE_NAME_PREFIX$deviceName"
        serviceType = TwinProtocol.NSD_SERVICE_TYPE
        port = TwinProtocol.CONTROL_PORT
        setAttribute("model", model)
        setAttribute("ip", localIp)
        setAttribute("os", osVersion)
        setAttribute("silent", if (silentMode) "1" else "0")
        setAttribute("pin", passcode)
        setAttribute("medium", medium)
      }

      val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
          Log.i(TAG, "NSD Service registered: ${serviceInfo?.serviceName}")
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
          Log.w(TAG, "NSD Registration failed: $errorCode")
        }
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
          Log.i(TAG, "NSD Service unregistered")
        }
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
          Log.w(TAG, "NSD Unregistration failed: $errorCode")
        }
      }
      registrationListener = listener
      nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to register NSD service: ${e.message}")
    }

    // 2. UDP Broadcast Beacon (robust fallback across local subnet)
    udpBroadcastJob = scope.launch {
      var socket: DatagramSocket? = null
      try {
        socket = DatagramSocket()
        socket.broadcast = true

        while (isActive) {
          val beacon = PeerBeacon(
            id = "tc-${localIp.replace(".", "-")}",
            name = deviceName,
            model = model,
            ipAddress = localIp,
            port = TwinProtocol.CONTROL_PORT,
            batteryPercent = battery,
            isCharging = isCharging,
            osVersion = osVersion,
            wifiSsid = wifiSsid,
            silentMode = silentMode,
            pairingPin = passcode,
            connectionMedium = medium,
          )

          val jsonBytes = beacon.toJson().toByteArray(Charsets.UTF_8)
          val broadcastAddr = InetAddress.getByName("255.255.255.255")
          val packet = DatagramPacket(jsonBytes, jsonBytes.size, broadcastAddr, TwinProtocol.DISCOVERY_PORT)

          try {
            socket.send(packet)
          } catch (_: Exception) {}

          delay(2500)
        }
      } catch (e: Exception) {
        Log.e(TAG, "UDP broadcast beacon error: ${e.message}")
      } finally {
        socket?.close()
      }
    }
  }

  fun stopAdvertising() {
    udpBroadcastJob?.cancel()
    udpBroadcastJob = null

    registrationListener?.let {
      try {
        nsdManager.unregisterService(it)
      } catch (_: Exception) {}
      registrationListener = null
    }
  }

  // --- Controller Discovery ---
  fun startDiscovery() {
    stopDiscovery()
    _discoveredDevices.value = emptyList()

    // 1. Android NSD Discovery
    try {
      val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String?) {
          Log.d(TAG, "NSD Discovery started")
        }
        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
          if (serviceInfo == null) return
          if (serviceInfo.serviceType == TwinProtocol.NSD_SERVICE_TYPE ||
            serviceInfo.serviceName.startsWith(SERVICE_NAME_PREFIX)) {
            resolveNsdService(serviceInfo)
          }
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
          Log.d(TAG, "NSD Service lost: ${serviceInfo?.serviceName}")
        }
        override fun onDiscoveryStopped(serviceType: String?) {
          Log.d(TAG, "NSD Discovery stopped")
        }
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
          Log.w(TAG, "NSD Start discovery failed: $errorCode")
        }
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
          Log.w(TAG, "NSD Stop discovery failed: $errorCode")
        }
      }
      discoveryListener = listener
      nsdManager.discoverServices(TwinProtocol.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to start NSD discovery: ${e.message}")
    }

    // 2. UDP Beacon Listener
    udpListenerJob = scope.launch {
      try {
        val socket = DatagramSocket(TwinProtocol.DISCOVERY_PORT)
        udpListeningSocket = socket
        socket.broadcast = true
        val buffer = ByteArray(2048)

        while (isActive) {
          val packet = DatagramPacket(buffer, buffer.size)
          socket.receive(packet)

          val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
          val beacon = PeerBeacon.fromJson(message)
          if (beacon != null) {
            val senderIp = packet.address.hostAddress ?: beacon.ipAddress
            addOrUpdateDiscoveredDevice(
              DeviceInfo(
                id = beacon.id,
                name = beacon.name,
                model = beacon.model,
                ipAddress = senderIp,
                port = beacon.port,
                isAuthorized = false,
                isConnected = false,
                locationTag = if (beacon.connectionMedium == "Bluetooth") "Bluetooth Proximity" else "Same Wi-Fi Network",
                lastSeen = "Just now",
                batteryPercent = beacon.batteryPercent,
                isCharging = beacon.isCharging,
                wifiSsid = beacon.wifiSsid,
                signalDbm = -40,
                osVersion = beacon.osVersion,
                silentConnectCapable = beacon.silentMode,
                pairingPin = beacon.pairingPin,
                connectionMedium = beacon.connectionMedium,
              )
            )
          }
        }
      } catch (e: Exception) {
        if (isActive) Log.d(TAG, "UDP listener finished: ${e.message}")
      } finally {
        udpListeningSocket?.close()
        udpListeningSocket = null
      }
    }
  }

  fun stopDiscovery() {
    udpListenerJob?.cancel()
    udpListenerJob = null
    udpListeningSocket?.close()
    udpListeningSocket = null

    discoveryListener?.let {
      try {
        nsdManager.stopServiceDiscovery(it)
      } catch (_: Exception) {}
      discoveryListener = null
    }
  }

  private fun resolveNsdService(serviceInfo: NsdServiceInfo) {
    try {
      nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
          Log.w(TAG, "NSD resolve failed: $errorCode")
        }
        override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
          if (resolvedInfo == null) return
          val host = resolvedInfo.host?.hostAddress ?: return
          val port = resolvedInfo.port
          val name = resolvedInfo.serviceName.removePrefix(SERVICE_NAME_PREFIX)
          val model = resolvedInfo.attributes?.get("model")?.let { String(it) } ?: "Android Device"
          val os = resolvedInfo.attributes?.get("os")?.let { String(it) } ?: "Android"
          val silentAttr = resolvedInfo.attributes?.get("silent")?.let { String(it) }
          val isSilent = silentAttr == null || silentAttr == "1"
          val pinAttr = resolvedInfo.attributes?.get("pin")?.let { String(it) } ?: ""
          val mediumAttr = resolvedInfo.attributes?.get("medium")?.let { String(it) } ?: "Wi-Fi"

          addOrUpdateDiscoveredDevice(
            DeviceInfo(
              id = "nsd-${host.replace(".", "-")}",
              name = name,
              model = model,
              ipAddress = host,
              port = port,
              isAuthorized = false,
              isConnected = false,
              locationTag = if (mediumAttr == "Bluetooth") "Bluetooth Proximity" else "Same Wi-Fi Network",
              lastSeen = "Just now",
              batteryPercent = 100,
              isCharging = false,
              wifiSsid = "Wi-Fi Subnet",
              signalDbm = -42,
              osVersion = os,
              silentConnectCapable = isSilent,
              pairingPin = pinAttr,
              connectionMedium = mediumAttr,
            )
          )
        }
      })
    } catch (_: Exception) {}
  }

  @Synchronized
  fun addOrUpdateDiscoveredDevice(device: DeviceInfo) {
    // Filter out self (Device A controller) from discovery
    if (LocalDeviceManager.isSelfDevice(context, device.ipAddress, device.name, device.id)) {
      Log.d(TAG, "Excluding local device from discovery list: ${device.name} (${device.ipAddress})")
      return
    }

    val current = _discoveredDevices.value.toMutableList()
    val existingIndex = current.indexOfFirst { it.ipAddress == device.ipAddress || it.id == device.id }
    if (existingIndex >= 0) {
      current[existingIndex] = device
    } else {
      current.add(device)
    }
    _discoveredDevices.value = current
  }

  fun addDirectDevice(ip: String, port: Int = TwinProtocol.CONTROL_PORT, name: String = "Direct Device") {
    val device = DeviceInfo(
      id = "direct-${ip.replace(".", "-")}",
      name = name,
      model = "Direct Network Device",
      ipAddress = ip,
      port = port,
      isAuthorized = false,
      isConnected = false,
      locationTag = "Direct IP",
      lastSeen = "Added manually",
      batteryPercent = 100,
      wifiSsid = "Local Subnet",
      signalDbm = -30,
    )
    addOrUpdateDiscoveredDevice(device)
  }
}
