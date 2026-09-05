package com.example.network.server

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.data.model.CommandType
import com.example.data.model.RemoteCommand
import com.example.network.protocol.TwinProtocol
import com.example.service.RemoteAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class TargetControlServer(
  private val context: Context,
  private val onControllerAuthorized: (controllerName: String) -> Unit,
  private val onControllerDisconnected: () -> Unit,
  private val onCommandReceived: (type: String, detail: String) -> Unit,
) {

  companion object {
    private const val TAG = "TargetControlServer"
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  private var activeClientSocket: Socket? = null
  private var activeWriter: PrintWriter? = null

  var activePasscodeProvider: () -> String = { "" }
  var isSilentModeEnabled: () -> Boolean = { true }
  var allowTouchGestures: Boolean = true

  fun start() {
    stop()
    serverJob = scope.launch {
      try {
        val server = ServerSocket(TwinProtocol.CONTROL_PORT)
        server.reuseAddress = true
        serverSocket = server
        Log.i(TAG, "Control server listening on port ${TwinProtocol.CONTROL_PORT}")

        while (isActive) {
          val client = server.accept()
          handleClient(client)
        }
      } catch (e: Exception) {
        if (isActive) Log.e(TAG, "Control server socket error: ${e.message}")
      }
    }
  }

  fun stop() {
    serverJob?.cancel()
    serverJob = null
    try {
      activeClientSocket?.close()
      activeClientSocket = null
    } catch (_: Exception) {}
    try {
      serverSocket?.close()
      serverSocket = null
    } catch (_: Exception) {}
  }

  private fun handleClient(client: Socket) {
    scope.launch {
      activeClientSocket = client
      val reader = BufferedReader(InputStreamReader(client.getInputStream()))
      val writer = PrintWriter(client.getOutputStream(), true)
      activeWriter = writer

      var isAuthorized = false
      var connectedControllerName = "Remote Controller"

      try {
        while (isActive && !client.isClosed) {
          val line = reader.readLine() ?: break
          val json = try {
            JSONObject(line)
          } catch (e: Exception) {
            continue
          }

          val typeStr = json.optString("type", "")
          val commandId = json.optString("commandId", UUID.randomUUID().toString())
          val timestamp = json.optLong("timestamp", System.currentTimeMillis())
          val payload = json.optString("payload", "")

          when (typeStr) {
            CommandType.PAIR_REQUEST.name -> {
              val pairJson = try { JSONObject(payload) } catch (_: Exception) { JSONObject() }
              val pin = pairJson.optString("pin", "").replace(" ", "").trim()
              val controllerName = pairJson.optString("controllerName", "Controller")
              val currentPin = activePasscodeProvider().replace(" ", "").trim()
              val isSilentPairRequested = pairJson.optBoolean("silentMode", false) || pin == "SILENT_AUTO"
              val silentAllowed = isSilentModeEnabled()
              val isPinMatch = pin.isNotEmpty() && pin == currentPin

              if (isPinMatch || (silentAllowed && (isSilentPairRequested || pin.isEmpty() || pin == currentPin))) {
                isAuthorized = true
                connectedControllerName = controllerName
                val response = JSONObject().apply {
                  put("type", CommandType.PAIR_RESPONSE.name)
                  put("commandId", commandId)
                  put("timestamp", System.currentTimeMillis())
                  put("payload", JSONObject().apply {
                    put("success", true)
                    put("token", UUID.randomUUID().toString())
                    put("silent", silentAllowed && (isSilentPairRequested || pin.isEmpty()))
                  }.toString())
                }
                writer.println(response.toString())
                onControllerAuthorized(connectedControllerName)
                val methodDesc = if (silentAllowed && isSilentPairRequested) "Silent Connect (Auto-Trust)" else "Passcode / QR"
                onCommandReceived("Authorized", "Paired with $connectedControllerName via $methodDesc")
              } else {
                val response = JSONObject().apply {
                  put("type", CommandType.PAIR_RESPONSE.name)
                  put("commandId", commandId)
                  put("timestamp", System.currentTimeMillis())
                  put("payload", JSONObject().apply {
                    put("success", false)
                    put("error", "Invalid PIN. Code does not match target.")
                  }.toString())
                }
                writer.println(response.toString())
                onCommandReceived("Auth Failed", "Rejected connection from $controllerName (Wrong PIN)")
              }
            }

            CommandType.PING.name -> {
              val pong = JSONObject().apply {
                put("type", CommandType.PONG.name)
                put("commandId", commandId)
                put("timestamp", timestamp) // Return original timestamp to measure RTT
              }
              writer.println(pong.toString())
            }

            CommandType.TOUCH.name -> {
              if (isAuthorized && allowTouchGestures) {
                val xRatio = json.optDouble("x", -1.0).toFloat()
                val yRatio = json.optDouble("y", -1.0).toFloat()
                if (xRatio in 0f..1f && yRatio in 0f..1f) {
                  val metrics = context.resources.displayMetrics
                  val realX = xRatio * metrics.widthPixels
                  val realY = yRatio * metrics.heightPixels

                  val service = RemoteAccessibilityService.instance
                  if (service != null) {
                    service.simulateTap(realX, realY)
                    onCommandReceived("Touch Tap", "X:${realX.toInt()} Y:${realY.toInt()}")
                  } else {
                    onCommandReceived("Accessibility Warning", "Service not enabled in Android Settings")
                  }
                }
              }
            }

            CommandType.SWIPE.name -> {
              if (isAuthorized && allowTouchGestures) {
                val startXRatio = json.optDouble("x", 0.0).toFloat()
                val startYRatio = json.optDouble("y", 0.0).toFloat()
                val endXRatio = json.optDouble("endX", 0.0).toFloat()
                val endYRatio = json.optDouble("endY", 0.0).toFloat()
                val durationMs = json.optLong("durationMs", 300L)

                val metrics = context.resources.displayMetrics
                val startX = startXRatio * metrics.widthPixels
                val startY = startYRatio * metrics.heightPixels
                val endX = endXRatio * metrics.widthPixels
                val endY = endYRatio * metrics.heightPixels

                val service = RemoteAccessibilityService.instance
                if (service != null) {
                  service.simulateSwipe(startX, startY, endX, endY, durationMs)
                  onCommandReceived("Touch Swipe", "From (${startX.toInt()}, ${startY.toInt()}) to (${endX.toInt()}, ${endY.toInt()})")
                }
              }
            }

            CommandType.BACK.name -> {
              if (isAuthorized) {
                RemoteAccessibilityService.instance?.triggerBack()
                onCommandReceived("Navigation", "BACK button dispatched")
              }
            }

            CommandType.HOME.name -> {
              if (isAuthorized) {
                RemoteAccessibilityService.instance?.triggerHome()
                onCommandReceived("Navigation", "HOME button dispatched")
              }
            }

            CommandType.RECENTS.name -> {
              if (isAuthorized) {
                RemoteAccessibilityService.instance?.triggerRecents()
                onCommandReceived("Navigation", "RECENTS button dispatched")
              }
            }

            CommandType.TEXT.name -> {
              if (isAuthorized) {
                RemoteAccessibilityService.instance?.injectText(payload)
                onCommandReceived("Text Injection", "\"$payload\" injected into focus")
              }
            }

            "GLOBAL_ACTION" -> {
              if (isAuthorized) {
                val service = RemoteAccessibilityService.instance
                when (payload) {
                  "NOTIFICATIONS" -> service?.showNotifications()
                  "QUICK_SETTINGS" -> service?.showQuickSettings()
                  "LOCK" -> service?.lockDevice()
                  "POWER" -> service?.showPowerDialog()
                }
                onCommandReceived("System Action", payload)
              }
            }

            "VOLUME" -> {
              if (isAuthorized) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (payload == "UP") {
                  audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                  onCommandReceived("Volume", "Volume UP")
                } else if (payload == "DOWN") {
                  audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                  onCommandReceived("Volume", "Volume DOWN")
                }
              }
            }

            CommandType.DISCONNECT.name -> {
              onCommandReceived("Disconnect", "Controller requested session end")
              break
            }
          }
        }
      } catch (e: Exception) {
        Log.d(TAG, "Client connection handler ended: ${e.message}")
      } finally {
        try { client.close() } catch (_: Exception) {}
        if (activeClientSocket == client) {
          activeClientSocket = null
          activeWriter = null
          onControllerDisconnected()
        }
      }
    }
  }

  fun sendDisconnect() {
    activeWriter?.println(JSONObject().apply {
      put("type", CommandType.DISCONNECT.name)
      put("payload", "Session closed by Target")
    }.toString())
  }
}
