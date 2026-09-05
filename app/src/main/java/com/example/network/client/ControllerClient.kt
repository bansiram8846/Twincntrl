package com.example.network.client

import android.util.Log
import com.example.data.model.CommandType
import com.example.network.protocol.TwinProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class ControllerClient(
  private val onPairResponse: (success: Boolean, errorMsg: String?) -> Unit,
  private val onPongReceived: (rttMs: Long) -> Unit,
  private val onDisconnected: (reason: String) -> Unit,
) {

  companion object {
    private const val TAG = "ControllerClient"
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var socket: Socket? = null
  private var writer: PrintWriter? = null
  private var listenJob: Job? = null

  val isConnected: Boolean
    get() = socket?.isConnected == true && socket?.isClosed == false

  suspend fun connect(targetIp: String, targetPort: Int = TwinProtocol.CONTROL_PORT): Boolean {
    return withContext(Dispatchers.IO) {
      try {
        disconnect()
        val s = Socket()
        s.connect(InetSocketAddress(targetIp, targetPort), 4000)
        s.tcpNoDelay = true
        socket = s
        writer = PrintWriter(s.getOutputStream(), true)

        startListening(s)
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to connect to target at $targetIp:$targetPort - ${e.message}")
        false
      }
    }
  }

  private fun startListening(s: Socket) {
    listenJob = scope.launch {
      try {
        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
        while (isActive && !s.isClosed) {
          val line = reader.readLine() ?: break
          val json = try { JSONObject(line) } catch (_: Exception) { continue }
          val type = json.optString("type")

          when (type) {
            CommandType.PAIR_RESPONSE.name -> {
              val payloadStr = json.optString("payload", "{}")
              val payload = try { JSONObject(payloadStr) } catch (_: Exception) { JSONObject() }
              val success = payload.optBoolean("success", false)
              val error = payload.optString("error", null)
              onPairResponse(success, error)
            }

            CommandType.PONG.name -> {
              val sentTimestamp = json.optLong("timestamp", 0L)
              val rtt = if (sentTimestamp > 0) (System.currentTimeMillis() - sentTimestamp).coerceAtLeast(1) else 20L
              onPongReceived(rtt)
            }

            CommandType.DISCONNECT.name -> {
              val reason = json.optString("payload", "Disconnected by Target")
              onDisconnected(reason)
              break
            }
          }
        }
      } catch (e: Exception) {
        if (isActive) Log.d(TAG, "Socket read terminated: ${e.message}")
      } finally {
        onDisconnected("Connection closed")
        disconnect()
      }
    }
  }

  fun requestPair(pin: String, controllerName: String, silentMode: Boolean = false) {
    val payload = JSONObject().apply {
      put("pin", pin)
      put("controllerName", controllerName)
      put("controllerId", UUID.randomUUID().toString())
      put("silentMode", silentMode || pin == "SILENT_AUTO" || pin.isEmpty())
    }

    val cmd = JSONObject().apply {
      put("type", CommandType.PAIR_REQUEST.name)
      put("commandId", UUID.randomUUID().toString())
      put("timestamp", System.currentTimeMillis())
      put("payload", payload.toString())
    }

    sendRaw(cmd.toString())
  }

  fun sendTouch(normX: Float, normY: Float, action: String = "TAP") {
    val cmd = JSONObject().apply {
      put("type", CommandType.TOUCH.name)
      put("commandId", UUID.randomUUID().toString())
      put("timestamp", System.currentTimeMillis())
      put("x", normX)
      put("y", normY)
      put("payload", action)
    }
    sendRaw(cmd.toString())
  }

  fun sendSwipe(normStartX: Float, normStartY: Float, normEndX: Float, normEndY: Float, durationMs: Long = 300L) {
    val cmd = JSONObject().apply {
      put("type", CommandType.SWIPE.name)
      put("commandId", UUID.randomUUID().toString())
      put("timestamp", System.currentTimeMillis())
      put("x", normStartX)
      put("y", normStartY)
      put("endX", normEndX)
      put("endY", normEndY)
      put("durationMs", durationMs)
    }
    sendRaw(cmd.toString())
  }

  fun sendNavigation(commandType: CommandType) {
    val cmd = JSONObject().apply {
      put("type", commandType.name)
      put("commandId", UUID.randomUUID().toString())
      put("timestamp", System.currentTimeMillis())
    }
    sendRaw(cmd.toString())
  }

  fun sendTextInput(text: String) {
    val cmd = JSONObject().apply {
      put("type", CommandType.TEXT.name)
      put("commandId", UUID.randomUUID().toString())
      put("timestamp", System.currentTimeMillis())
      put("payload", text)
    }
    sendRaw(cmd.toString())
  }

  fun sendGlobalAction(action: String) {
    val cmd = JSONObject().apply {
      put("type", "GLOBAL_ACTION")
      put("commandId", UUID.randomUUID().toString())
      put("timestamp", System.currentTimeMillis())
      put("payload", action)
    }
    sendRaw(cmd.toString())
  }

  fun sendVolume(direction: String) {
    val cmd = JSONObject().apply {
      put("type", "VOLUME")
      put("commandId", UUID.randomUUID().toString())
      put("timestamp", System.currentTimeMillis())
      put("payload", direction)
    }
    sendRaw(cmd.toString())
  }

  fun sendPing() {
    val cmd = JSONObject().apply {
      put("type", CommandType.PING.name)
      put("commandId", UUID.randomUUID().toString())
      put("timestamp", System.currentTimeMillis())
    }
    sendRaw(cmd.toString())
  }

  private fun sendRaw(message: String) {
    scope.launch {
      try {
        writer?.println(message)
      } catch (e: Exception) {
        Log.e(TAG, "Error writing to socket: ${e.message}")
      }
    }
  }

  fun disconnect() {
    listenJob?.cancel()
    listenJob = null
    try {
      writer?.close()
      writer = null
      socket?.close()
      socket = null
    } catch (_: Exception) {}
  }
}
