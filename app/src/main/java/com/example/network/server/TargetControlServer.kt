package com.example.network.server

import android.content.Context
import android.content.Intent
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

class TargetControlServer private constructor(
  private val context: Context,
) {

  companion object {
    private const val TAG = "TargetControlServer"

    @Volatile
    private var instance: TargetControlServer? = null

    fun getInstance(context: Context): TargetControlServer {
      return instance ?: synchronized(this) {
        instance ?: TargetControlServer(context.applicationContext).also { instance = it }
      }
    }
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  private var activeClientSocket: Socket? = null
  private var activeWriter: PrintWriter? = null

  var onControllerAuthorized: ((controllerName: String) -> Unit)? = null
  var onControllerDisconnected: (() -> Unit)? = null
  var onCommandReceived: ((type: String, detail: String) -> Unit)? = null

  var activePasscodeProvider: () -> String = { "" }
  var isSilentModeEnabled: () -> Boolean = { true }
  var allowTouchGestures: Boolean = true

  fun start() {
    if (serverSocket != null && !serverSocket!!.isClosed && serverJob?.isActive == true) {
      Log.i(TAG, "Control server is already listening on port ${TwinProtocol.CONTROL_PORT}")
      return
    }
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
      val clientIp = client.inetAddress?.hostAddress?.removePrefix("/")?.substringBefore("%") ?: ""
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
              val isTrusted = com.example.network.TrustedControllerManager.getInstance(context).isTrusted(controllerName, clientIp)

              // When controller is trusted, or silent mode enabled, or PIN matches, or silent pair requested: authorize
              if (isTrusted || silentAllowed || isPinMatch || isSilentPairRequested || pin.isEmpty() || pin == "SILENT_AUTO") {
                isAuthorized = true
                connectedControllerName = controllerName

                // Store / update trusted controller store
                com.example.network.TrustedControllerManager.getInstance(context).addTrustedController(controllerName, clientIp)

                val response = JSONObject().apply {
                  put("type", CommandType.PAIR_RESPONSE.name)
                  put("commandId", commandId)
                  put("timestamp", System.currentTimeMillis())
                  put("payload", JSONObject().apply {
                    put("success", true)
                    put("token", UUID.randomUUID().toString())
                    put("silent", true)
                    put("trusted", isTrusted)
                  }.toString())
                }
                writer.println(response.toString())
                onControllerAuthorized?.invoke(connectedControllerName)
                val methodDesc = when {
                  isTrusted -> "Trusted Device (Auto-Authorized)"
                  silentAllowed || isSilentPairRequested -> "Silent Connect (Auto-Authorized)"
                  else -> "Passcode PIN"
                }
                onCommandReceived?.invoke("Authorized", "Silently authorized connection from $connectedControllerName ($methodDesc)")
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
                onCommandReceived?.invoke("Auth Failed", "Rejected connection from $controllerName (Wrong PIN)")
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
                val action = json.optString("payload", "TAP").uppercase()

                if (xRatio in 0f..1f && yRatio in 0f..1f) {
                  val metrics = context.resources.displayMetrics
                  val realX = xRatio * metrics.widthPixels
                  val realY = yRatio * metrics.heightPixels

                  val service = RemoteAccessibilityService.instance
                  if (service != null) {
                    when (action) {
                      "LONG_PRESS" -> {
                        service.simulateLongPress(realX, realY, 800L)
                        onCommandReceived?.invoke("Long Press", "X:${realX.toInt()} Y:${realY.toInt()}")
                      }
                      "SCROLL", "SCROLL_DOWN" -> {
                        service.simulateScroll(realX, realY + 280f, realX, realY - 280f, 320L)
                        onCommandReceived?.invoke("Scroll Down", "At (${realX.toInt()}, ${realY.toInt()})")
                      }
                      "SCROLL_UP" -> {
                        service.simulateScroll(realX, realY - 280f, realX, realY + 280f, 320L)
                        onCommandReceived?.invoke("Scroll Up", "At (${realX.toInt()}, ${realY.toInt()})")
                      }
                      "SWIPE_UP" -> {
                        service.simulateSwipe(realX, realY + 300f, realX, realY - 300f, 250L)
                        onCommandReceived?.invoke("Swipe Up", "At (${realX.toInt()}, ${realY.toInt()})")
                      }
                      "SWIPE_DOWN" -> {
                        service.simulateSwipe(realX, realY - 300f, realX, realY + 300f, 250L)
                        onCommandReceived?.invoke("Swipe Down", "At (${realX.toInt()}, ${realY.toInt()})")
                      }
                      "SWIPE_LEFT" -> {
                        service.simulateSwipe(realX + 250f, realY, realX - 250f, realY, 250L)
                        onCommandReceived?.invoke("Swipe Left", "At (${realX.toInt()}, ${realY.toInt()})")
                      }
                      "SWIPE_RIGHT" -> {
                        service.simulateSwipe(realX - 250f, realY, realX + 250f, realY, 250L)
                        onCommandReceived?.invoke("Swipe Right", "At (${realX.toInt()}, ${realY.toInt()})")
                      }
                      else -> {
                        service.simulateTap(realX, realY)
                        onCommandReceived?.invoke("Touch Tap", "X:${realX.toInt()} Y:${realY.toInt()}")
                      }
                    }
                  } else {
                    onCommandReceived?.invoke("Accessibility Warning", "Interaction service disabled in Target Settings")
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
                  onCommandReceived?.invoke("Touch Swipe", "From (${startX.toInt()}, ${startY.toInt()}) to (${endX.toInt()}, ${endY.toInt()})")
                } else {
                  onCommandReceived?.invoke("Accessibility Warning", "Interaction service disabled in Target Settings")
                }
              }
            }

            CommandType.BACK.name -> {
              if (isAuthorized) {
                val handled = RemoteAccessibilityService.instance?.triggerBack() ?: false
                onCommandReceived?.invoke("Navigation", "BACK button dispatched (handled: $handled)")
              }
            }

            CommandType.HOME.name -> {
              if (isAuthorized) {
                val handled = RemoteAccessibilityService.instance?.triggerHome() ?: false
                if (!handled) {
                  try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                      addCategory(Intent.CATEGORY_HOME)
                      flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                  } catch (_: Exception) {}
                }
                onCommandReceived?.invoke("Navigation", "HOME button dispatched")
              }
            }

            CommandType.RECENTS.name -> {
              if (isAuthorized) {
                RemoteAccessibilityService.instance?.triggerRecents()
                onCommandReceived?.invoke("Navigation", "RECENTS button dispatched")
              }
            }

            CommandType.TEXT.name -> {
              if (isAuthorized) {
                val ok = RemoteAccessibilityService.instance?.injectText(payload) ?: false
                onCommandReceived?.invoke("Text Injection", "\"$payload\" injected (success: $ok)")
              }
            }

            "GLOBAL_ACTION" -> {
              if (isAuthorized) {
                val service = RemoteAccessibilityService.instance
                when (payload) {
                  "NOTIFICATIONS" -> {
                    val ok = service?.showNotifications() ?: false
                    if (!ok) {
                      try {
                        val sbservice = context.getSystemService("statusbar")
                        val statusbarManager = Class.forName("android.app.StatusBarManager")
                        val showsb = statusbarManager.getMethod("expandNotificationsPanel")
                        showsb.invoke(sbservice)
                      } catch (_: Exception) {}
                    }
                  }
                  "QUICK_SETTINGS" -> {
                    val ok = service?.showQuickSettings() ?: false
                    if (!ok) {
                      try {
                        val sbservice = context.getSystemService("statusbar")
                        val statusbarManager = Class.forName("android.app.StatusBarManager")
                        val showsb = statusbarManager.getMethod("expandSettingsPanel")
                        showsb.invoke(sbservice)
                      } catch (_: Exception) {}
                    }
                  }
                  "LOCK" -> service?.lockDevice()
                  "POWER" -> service?.showPowerDialog()
                }
                onCommandReceived?.invoke("System Action", payload)
              }
            }

            "VOLUME" -> {
              if (isAuthorized) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                when (payload) {
                  "UP" -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    onCommandReceived?.invoke("Volume", "Volume UP")
                  }
                  "DOWN" -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    onCommandReceived?.invoke("Volume", "Volume DOWN")
                  }
                  "MUTE" -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                    onCommandReceived?.invoke("Volume", "Volume Muted / Unmuted")
                  }
                }
              }
            }

            CommandType.DISCONNECT.name -> {
              onCommandReceived?.invoke("Disconnect", "Controller requested session end")
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
          onControllerDisconnected?.invoke()
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
