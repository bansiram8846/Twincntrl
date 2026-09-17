package com.example.network.server

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.network.LocalDeviceManager
import com.example.network.protocol.TwinProtocol
import com.example.service.RemoteAccessibilityService
import com.example.service.ScreenCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class WebStreamServer private constructor(private val context: Context) {

  companion object {
    private const val TAG = "WebStreamServer"

    @Volatile
    private var sInstance: WebStreamServer? = null

    fun getInstance(context: Context): WebStreamServer {
      return sInstance ?: synchronized(this) {
        sInstance ?: WebStreamServer(context.applicationContext).also { sInstance = it }
      }
    }
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var serverJob: Job? = null
  private var serverSocket: ServerSocket? = null

  // Active MJPEG HTTP client output streams
  private val mjpegClients = CopyOnWriteArrayList<OutputStream>()

  @Volatile
  private var lastFramePayload: ByteArray? = null

  fun start(port: Int = TwinProtocol.WEB_PORT) {
    if (serverSocket != null && serverSocket?.isClosed == false) {
      Log.d(TAG, "WebStreamServer already active on port $port")
      return
    }

    serverJob?.cancel()
    serverJob = scope.launch {
      try {
        val s = ServerSocket(port)
        s.reuseAddress = true
        serverSocket = s
        Log.i(TAG, "WebStreamServer running at http://0.0.0.0:$port")

        while (isActive && !s.isClosed) {
          try {
            val clientSocket = s.accept()
            clientSocket.tcpNoDelay = true
            handleClient(clientSocket)
          } catch (e: Exception) {
            if (!isActive || s.isClosed) break
            Log.w(TAG, "Web client accept exception: ${e.message}")
          }
        }
      } catch (e: Exception) {
        if (isActive) Log.e(TAG, "WebStreamServer error: ${e.message}")
      }
    }
  }

  fun stop() {
    serverJob?.cancel()
    serverJob = null
    mjpegClients.forEach {
      try { it.close() } catch (_: Exception) {}
    }
    mjpegClients.clear()
    try {
      serverSocket?.close()
      serverSocket = null
    } catch (_: Exception) {}
  }

  fun broadcastFrame(jpegBytes: ByteArray) {
    lastFramePayload = jpegBytes
    if (mjpegClients.isEmpty()) return

    val boundaryHeader = (
      "--frame\r\n" +
      "Content-Type: image/jpeg\r\n" +
      "Content-Length: ${jpegBytes.size}\r\n\r\n"
    ).toByteArray(Charsets.US_ASCII)
    val newline = "\r\n".toByteArray(Charsets.US_ASCII)

    for (os in mjpegClients) {
      try {
        os.write(boundaryHeader)
        os.write(jpegBytes)
        os.write(newline)
        os.flush()
      } catch (_: Exception) {
        mjpegClients.remove(os)
        try { os.close() } catch (_: Exception) {}
      }
    }
  }

  private fun handleClient(socket: Socket) {
    scope.launch {
      try {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val requestLine = reader.readLine() ?: return@launch
        val parts = requestLine.split(" ")
        if (parts.size < 2) {
          socket.close()
          return@launch
        }

        val method = parts[0].uppercase()
        val pathWithQuery = parts[1]
        val path = pathWithQuery.substringBefore("?")
        val query = pathWithQuery.substringAfter("?", "")

        // Read remaining headers
        var contentLength = 0
        var headerLine: String?
        while (reader.readLine().also { headerLine = it } != null && headerLine!!.isNotEmpty()) {
          if (headerLine!!.startsWith("Content-Length:", ignoreCase = true)) {
            contentLength = headerLine!!.substringAfter(":").trim().toIntOrNull() ?: 0
          }
        }

        val out = socket.getOutputStream()

        when {
          path == "/" || path == "/index.html" || path == "/target" || path == "/join" -> {
            serveWebConsole(out)
            socket.close()
          }

          path == "/stream.mjpg" -> {
            serveMjpegStream(socket, out)
            // Do not close socket; keep-alive stream
          }

          path == "/api/status" -> {
            val statusJson = JSONObject().apply {
              put("status", "ok")
              put("accessibilityActive", RemoteAccessibilityService.isRunning)
              put("streamingActive", ScreenCaptureService.isRunning)
              put("deviceName", LocalDeviceManager.getEffectiveDeviceName(context))
              put("model", Build.MODEL)
              val metrics = context.resources.displayMetrics
              put("width", metrics.widthPixels)
              put("height", metrics.heightPixels)
            }
            sendJsonResponse(out, statusJson.toString())
            socket.close()
          }

          path.startsWith("/api/nav") -> {
            val cmd = parseQueryParam(query, "cmd").uppercase()
            handleNavigationCommand(cmd)
            sendJsonResponse(out, """{"status":"ok","action":"$cmd"}""")
            socket.close()
          }

          path == "/api/touch" && method == "POST" -> {
            val body = readBody(reader, contentLength)
            handleTouchInput(body)
            sendJsonResponse(out, """{"status":"ok"}""")
            socket.close()
          }

          path == "/api/swipe" && method == "POST" -> {
            val body = readBody(reader, contentLength)
            handleSwipeInput(body)
            sendJsonResponse(out, """{"status":"ok"}""")
            socket.close()
          }

          path == "/api/text" && method == "POST" -> {
            val body = readBody(reader, contentLength)
            handleTextInput(body)
            sendJsonResponse(out, """{"status":"ok"}""")
            socket.close()
          }

          else -> {
            val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found"
            out.write(notFound.toByteArray())
            out.flush()
            socket.close()
          }
        }
      } catch (_: Exception) {
        try { socket.close() } catch (_: Exception) {}
      }
    }
  }

  private fun readBody(reader: BufferedReader, contentLength: Int): String {
    if (contentLength <= 0) return ""
    val body = CharArray(contentLength)
    var read = 0
    while (read < contentLength) {
      val r = reader.read(body, read, contentLength - read)
      if (r == -1) break
      read += r
    }
    return String(body, 0, read)
  }

  private fun serveMjpegStream(socket: Socket, out: OutputStream) {
    val httpHeader = (
      "HTTP/1.1 200 OK\r\n" +
      "Server: TwinControlWeb/1.0\r\n" +
      "Connection: close\r\n" +
      "Cache-Control: no-cache, no-store, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
      "Pragma: no-cache\r\n" +
      "Content-Type: multipart/x-mixed-replace; boundary=--frame\r\n\r\n"
    ).toByteArray(Charsets.US_ASCII)

    out.write(httpHeader)
    out.flush()

    // Send latest cached frame immediately
    val cached = lastFramePayload
    if (cached != null) {
      try {
        val boundaryHeader = (
          "--frame\r\n" +
          "Content-Type: image/jpeg\r\n" +
          "Content-Length: ${cached.size}\r\n\r\n"
        ).toByteArray(Charsets.US_ASCII)
        out.write(boundaryHeader)
        out.write(cached)
        out.write("\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
      } catch (_: Exception) {}
    }

    mjpegClients.add(out)
  }

  private fun handleTouchInput(jsonStr: String) {
    try {
      val json = JSONObject(jsonStr)
      val xRatio = json.optDouble("x", -1.0).toFloat()
      val yRatio = json.optDouble("y", -1.0).toFloat()
      val action = json.optString("action", "TAP").uppercase()

      if (xRatio in 0f..1f && yRatio in 0f..1f) {
        val metrics = context.resources.displayMetrics
        val realX = (xRatio * metrics.widthPixels).coerceIn(1f, metrics.widthPixels - 1f)
        val realY = (yRatio * metrics.heightPixels).coerceIn(1f, metrics.heightPixels - 1f)
        val service = RemoteAccessibilityService.instance
        if (service != null) {
          when (action) {
            "LONG_PRESS" -> service.simulateLongPress(realX, realY, 800L)
            else -> service.simulateTap(realX, realY)
          }
        } else {
          Log.w(TAG, "Touch received but RemoteAccessibilityService is not enabled")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling touch input: ${e.message}")
    }
  }

  private fun handleSwipeInput(jsonStr: String) {
    try {
      val json = JSONObject(jsonStr)
      val startXRatio = json.optDouble("startX", -1.0).toFloat()
      val startYRatio = json.optDouble("startY", -1.0).toFloat()
      val endXRatio = json.optDouble("endX", -1.0).toFloat()
      val endYRatio = json.optDouble("endY", -1.0).toFloat()
      val durationMs = json.optLong("durationMs", 280L)

      if (startXRatio in 0f..1f && startYRatio in 0f..1f && endXRatio in 0f..1f && endYRatio in 0f..1f) {
        val metrics = context.resources.displayMetrics
        val startX = (startXRatio * metrics.widthPixels).coerceIn(1f, metrics.widthPixels - 1f)
        val startY = (startYRatio * metrics.heightPixels).coerceIn(1f, metrics.heightPixels - 1f)
        val endX = (endXRatio * metrics.widthPixels).coerceIn(1f, metrics.widthPixels - 1f)
        val endY = (endYRatio * metrics.heightPixels).coerceIn(1f, metrics.heightPixels - 1f)

        val service = RemoteAccessibilityService.instance
        if (service != null) {
          service.simulateSwipe(startX, startY, endX, endY, durationMs)
        } else {
          Log.w(TAG, "Swipe received but RemoteAccessibilityService is not enabled")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling swipe input: ${e.message}")
    }
  }

  private fun handleTextInput(jsonStr: String) {
    try {
      val json = JSONObject(jsonStr)
      val text = json.optString("text", "")
      if (text.isNotBlank()) {
        val service = RemoteAccessibilityService.instance
        service?.injectText(text)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error injecting text: ${e.message}")
    }
  }

  private fun handleNavigationCommand(cmd: String) {
    val service = RemoteAccessibilityService.instance
    val metrics = context.resources.displayMetrics
    val centerX = metrics.widthPixels / 2f
    val centerY = metrics.heightPixels / 2f

    when (cmd) {
      "BACK" -> {
        val handled = service?.triggerBack() ?: false
        if (!handled) {
          Log.w(TAG, "Back button could not be handled (AccessibilityService not running)")
        }
      }
      "HOME" -> {
        val handled = service?.triggerHome() ?: false
        if (!handled) {
          try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
              addCategory(Intent.CATEGORY_HOME)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
          } catch (_: Exception) {}
        }
      }
      "RECENTS" -> service?.triggerRecents()
      "NOTIFICATIONS" -> {
        val ok = service?.showNotifications() ?: false
        if (!ok) {
          try {
            val sb = context.getSystemService("statusbar")
            val sbm = Class.forName("android.app.StatusBarManager")
            sbm.getMethod("expandNotificationsPanel").invoke(sb)
          } catch (_: Exception) {}
        }
      }
      "QUICK_SETTINGS" -> {
        val ok = service?.showQuickSettings() ?: false
        if (!ok) {
          try {
            val sb = context.getSystemService("statusbar")
            val sbm = Class.forName("android.app.StatusBarManager")
            sbm.getMethod("expandSettingsPanel").invoke(sb)
          } catch (_: Exception) {}
        }
      }
      "LOCK" -> service?.lockDevice()
      "POWER" -> service?.showPowerDialog()
      "VOL_UP" -> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
      }
      "VOL_DOWN" -> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
      }
      "VOL_MUTE" -> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
      }
      "SWIPE_UP" -> {
        service?.simulateSwipe(centerX, metrics.heightPixels * 0.75f, centerX, metrics.heightPixels * 0.25f, 250L)
      }
      "SWIPE_DOWN" -> {
        service?.simulateSwipe(centerX, metrics.heightPixels * 0.25f, centerX, metrics.heightPixels * 0.75f, 250L)
      }
      "SWIPE_LEFT" -> {
        service?.simulateSwipe(metrics.widthPixels * 0.8f, centerY, metrics.widthPixels * 0.2f, centerY, 250L)
      }
      "SWIPE_RIGHT" -> {
        service?.simulateSwipe(metrics.widthPixels * 0.2f, centerY, metrics.widthPixels * 0.8f, centerY, 250L)
      }
      "SCROLL_UP" -> {
        service?.simulateSwipe(centerX, metrics.heightPixels * 0.4f, centerX, metrics.heightPixels * 0.7f, 320L)
      }
      "SCROLL_DOWN" -> {
        service?.simulateSwipe(centerX, metrics.heightPixels * 0.7f, centerX, metrics.heightPixels * 0.4f, 320L)
      }
    }
  }

  private fun sendJsonResponse(out: OutputStream, json: String) {
    val bytes = json.toByteArray(Charsets.UTF_8)
    val header = (
      "HTTP/1.1 200 OK\r\n" +
      "Content-Type: application/json; charset=utf-8\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Content-Length: ${bytes.size}\r\n\r\n"
    ).toByteArray(Charsets.US_ASCII)
    out.write(header)
    out.write(bytes)
    out.flush()
  }

  private fun parseQueryParam(query: String, param: String): String {
    val pairs = query.split("&")
    for (p in pairs) {
      val kv = p.split("=")
      if (kv.size == 2 && kv[0].equals(param, ignoreCase = true)) {
        return kv[1]
      }
    }
    return ""
  }

  private fun serveWebConsole(out: OutputStream) {
    val deviceName = LocalDeviceManager.getEffectiveDeviceName(context)
    val html = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>TwinControl · Live Remote Controller</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: #0b0d12;
      color: #e2e8f0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      display: flex;
      flex-direction: column;
      height: 100vh;
      overflow: hidden;
      user-select: none;
    }
    header {
      background: #151822;
      padding: 10px 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid #242a38;
      z-index: 10;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 700;
      font-size: 15px;
      color: #fff;
    }
    .brand-icon {
      width: 28px;
      height: 28px;
      background: #6366f1;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      font-size: 16px;
    }
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 999px;
      font-size: 11px;
      font-weight: 600;
      background: rgba(34, 197, 94, 0.15);
      color: #4ade80;
      border: 1px solid rgba(34, 197, 94, 0.3);
    }
    .status-badge.warning {
      background: rgba(245, 158, 11, 0.15);
      color: #fbbf24;
      border: 1px solid rgba(245, 158, 11, 0.3);
    }
    .dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: currentColor;
      animation: blink 1.6s infinite ease-in-out;
    }
    @keyframes blink { 0%,100%{opacity:1;} 50%{opacity:0.35;} }

    /* Service warning banner */
    #warningBanner {
      display: none;
      background: #78350f;
      color: #fef3c7;
      padding: 8px 14px;
      font-size: 12px;
      text-align: center;
      line-height: 1.4;
      border-bottom: 1px solid #b45309;
    }
    #warningBanner b { color: #fff; }

    main {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 6px;
      background: radial-gradient(circle at center, #171b26 0%, #0b0d12 100%);
      position: relative;
      overflow: hidden;
    }
    .screen-wrapper {
      position: relative;
      max-height: calc(100vh - 210px);
      max-width: 96vw;
      border-radius: 18px;
      overflow: hidden;
      box-shadow: 0 16px 40px rgba(0,0,0,0.85), 0 0 0 1px #2d3748;
      background: #000;
      display: flex;
      align-items: center;
      justify-content: center;
      touch-action: none;
    }
    #screen {
      display: block;
      max-height: calc(100vh - 210px);
      max-width: 96vw;
      object-fit: contain;
      cursor: crosshair;
      pointer-events: auto;
      user-select: none;
      -webkit-user-drag: none;
    }
    .touch-ripple {
      position: absolute;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: rgba(99, 102, 241, 0.4);
      border: 2px solid #818cf8;
      transform: translate(-50%, -50%) scale(0.6);
      pointer-events: none;
      opacity: 0;
      transition: transform 0.25s ease-out, opacity 0.25s ease-out;
      z-index: 100;
    }

    /* Gesture Mode selector bar */
    .controls-panel {
      width: 100%;
      max-width: 600px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding: 0 12px;
      margin-top: 4px;
    }
    .gesture-bar {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      overflow-x: auto;
      padding-bottom: 2px;
    }
    .mode-chip {
      background: #1e2433;
      color: #94a3b8;
      border: 1px solid #333d54;
      border-radius: 20px;
      padding: 5px 12px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 5px;
      white-space: nowrap;
      transition: all 0.15s;
    }
    .mode-chip.active {
      background: #4f46e5;
      color: #fff;
      border-color: #6366f1;
      box-shadow: 0 2px 8px rgba(99, 102, 241, 0.4);
    }
    .quick-btn {
      background: #1a1e2a;
      color: #cbd5e1;
      border: 1px solid #2e364a;
      border-radius: 8px;
      padding: 5px 9px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      white-space: nowrap;
      transition: all 0.12s;
    }
    .quick-btn:active { background: #4f46e5; color: #fff; transform: scale(0.95); }

    /* Footer: Navigation & Actions */
    footer {
      background: #13161f;
      border-top: 1px solid #242a38;
      padding: 8px 12px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      align-items: center;
      z-index: 10;
    }
    .nav-bar {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      width: 100%;
      max-width: 440px;
    }
    .nav-btn {
      flex: 1;
      background: #202636;
      color: #f1f5f9;
      border: 1px solid #333f57;
      border-radius: 12px;
      padding: 10px 14px;
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      transition: all 0.15s;
      box-shadow: 0 2px 6px rgba(0,0,0,0.3);
    }
    .nav-btn:active { transform: scale(0.96); background: #4f46e5; }
    .nav-btn.home-btn { background: #3730a3; border-color: #4f46e5; color: #fff; }

    .action-bar {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      overflow-x: auto;
      width: 100%;
      max-width: 580px;
      padding-bottom: 2px;
    }
    .action-btn {
      background: #1b202c;
      color: #94a3b8;
      border: 1px solid #2c3547;
      border-radius: 8px;
      padding: 6px 10px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      white-space: nowrap;
      transition: all 0.12s;
    }
    .action-btn:hover { color: #f8fafc; border-color: #475569; }
    .action-btn:active { background: #4f46e5; color: #fff; transform: scale(0.95); }

    /* Text modal dialog */
    #textModal {
      display: none;
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.7);
      backdrop-filter: blur(4px);
      z-index: 999;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .modal-box {
      background: #1a1e2a;
      border: 1px solid #333d54;
      border-radius: 16px;
      padding: 20px;
      width: 100%;
      max-width: 400px;
      box-shadow: 0 20px 40px rgba(0,0,0,0.8);
    }
    .modal-title { font-size: 16px; font-weight: 700; margin-bottom: 12px; color: #fff; }
    .modal-input {
      width: 100%;
      background: #0f121a;
      border: 1px solid #2e384e;
      border-radius: 10px;
      padding: 12px;
      color: #fff;
      font-size: 14px;
      outline: none;
      margin-bottom: 16px;
    }
    .modal-input:focus { border-color: #6366f1; }
    .modal-actions { display: flex; justify-content: flex-end; gap: 8px; }
  </style>
</head>
<body>
  <header>
    <div class="brand">
      <div class="brand-icon">📱</div>
      <div>
        <div>$deviceName</div>
        <div style="font-size: 10px; font-weight: 400; color: #818cf8;">Target Live Stream & Controller</div>
      </div>
    </div>
    <div id="statusBadge" class="status-badge">
      <span class="dot"></span>
      <span id="statusText">Checking...</span>
    </div>
  </header>

  <div id="warningBanner">
    ⚠️ <b>Remote Interaction Disabled on Phone:</b> Please open TwinControl on the target device and tap <b>"Enable Interaction Service"</b> in Accessibility Settings so taps, swipes, and navigation will work.
  </div>

  <main>
    <div class="screen-wrapper" id="container">
      <img id="screen" src="/stream.mjpg" alt="Target Screen" />
      <div id="touchRipple" class="touch-ripple"></div>
    </div>

    <div class="controls-panel">
      <!-- Input Gesture Mode chips -->
      <div class="gesture-bar">
        <button class="mode-chip active" id="chipTap" onclick="setMode('TAP')">👆 Tap Mode</button>
        <button class="mode-chip" id="chipSwipe" onclick="setMode('SWIPE')">↔️ Swipe Mode</button>
        <button class="mode-chip" id="chipScroll" onclick="setMode('SCROLL')">↕️ Scroll Mode</button>
        <span style="color: #475569; font-size: 10px;">|</span>
        <button class="quick-btn" onclick="sendNav('SWIPE_UP')">⬆️ Up</button>
        <button class="quick-btn" onclick="sendNav('SWIPE_DOWN')">⬇️ Down</button>
        <button class="quick-btn" onclick="sendNav('SWIPE_LEFT')">⬅️ Left</button>
        <button class="quick-btn" onclick="sendNav('SWIPE_RIGHT')">➡️ Right</button>
        <button class="quick-btn" onclick="sendNav('SCROLL_UP')">🔼 PgUp</button>
        <button class="quick-btn" onclick="sendNav('SCROLL_DOWN')">🔽 PgDn</button>
      </div>
    </div>
  </main>

  <footer>
    <!-- 1. Tactile 3-Button Navigation Bar -->
    <div class="nav-bar">
      <button class="nav-btn" onclick="sendNav('BACK')">◀ Back</button>
      <button class="nav-btn home-btn" onclick="sendNav('HOME')">● Home</button>
      <button class="nav-btn" onclick="sendNav('RECENTS')">■ Recents</button>
    </div>

    <!-- 2. Remote Actions Menu Bar -->
    <div class="action-bar">
      <button class="action-btn" onclick="openTextModal()">⌨️ Text Input</button>
      <button class="action-btn" onclick="sendNav('NOTIFICATIONS')">🔔 Notifications</button>
      <button class="action-btn" onclick="sendNav('QUICK_SETTINGS')">⚙️ Quick Settings</button>
      <button class="action-btn" onclick="sendNav('VOL_UP')">🔊 Vol +</button>
      <button class="action-btn" onclick="sendNav('VOL_DOWN')">🔉 Vol -</button>
      <button class="action-btn" onclick="sendNav('VOL_MUTE')">🔇 Mute</button>
      <button class="action-btn" onclick="sendNav('LOCK')">🔒 Lock</button>
      <button class="action-btn" onclick="sendNav('POWER')">⚡ Power</button>
    </div>
  </footer>

  <!-- Text Input Modal -->
  <div id="textModal">
    <div class="modal-box">
      <div class="modal-title">Type Text on Target Device</div>
      <input type="text" id="targetTextInput" class="modal-input" placeholder="Type text here and hit send..." />
      <div class="modal-actions">
        <button class="action-btn" onclick="closeTextModal()">Cancel</button>
        <button class="nav-btn home-btn" style="flex: 0; padding: 6px 18px; font-size: 12px;" onclick="submitText()">Send Text</button>
      </div>
    </div>
  </div>

  <script>
    const screenImg = document.getElementById('screen');
    const ripple = document.getElementById('touchRipple');
    const statusBadge = document.getElementById('statusBadge');
    const statusText = document.getElementById('statusText');
    const warningBanner = document.getElementById('warningBanner');

    let currentMode = 'TAP';
    let pointerDownTime = 0;
    let startX = 0;
    let startY = 0;
    let isPointerDown = false;

    function setMode(mode) {
      currentMode = mode;
      document.getElementById('chipTap').classList.toggle('active', mode === 'TAP');
      document.getElementById('chipSwipe').classList.toggle('active', mode === 'SWIPE');
      document.getElementById('chipScroll').classList.toggle('active', mode === 'SCROLL');
    }

    // Helper to calculate exact normalized coordinates taking object-fit letterboxing into account
    function getNormalizedCoords(e) {
      const rect = screenImg.getBoundingClientRect();
      const imgWidth = screenImg.naturalWidth || 1080;
      const imgHeight = screenImg.naturalHeight || 2400;

      const elementWidth = rect.width;
      const elementHeight = rect.height;

      const imgAspect = imgWidth / imgHeight;
      const elemAspect = elementWidth / elementHeight;

      let renderedWidth, renderedHeight, offsetX, offsetY;

      if (elemAspect > imgAspect) {
        renderedHeight = elementHeight;
        renderedWidth = elementHeight * imgAspect;
        offsetX = (elementWidth - renderedWidth) / 2;
        offsetY = 0;
      } else {
        renderedWidth = elementWidth;
        renderedHeight = elementWidth / imgAspect;
        offsetX = 0;
        offsetY = (elementHeight - renderedHeight) / 2;
      }

      const clientX = e.clientX || (e.touches && e.touches[0] ? e.touches[0].clientX : e.clientX);
      const clientY = e.clientY || (e.touches && e.touches[0] ? e.touches[0].clientY : e.clientY);

      const clickX = clientX - rect.left - offsetX;
      const clickY = clientY - rect.top - offsetY;

      const normX = Math.max(0, Math.min(1, clickX / renderedWidth));
      const normY = Math.max(0, Math.min(1, clickY / renderedHeight));

      return { normX, normY, clientX, clientY };
    }

    function showRipple(clientX, clientY) {
      const rect = document.getElementById('container').getBoundingClientRect();
      ripple.style.left = (clientX - rect.left) + 'px';
      ripple.style.top = (clientY - rect.top) + 'px';
      ripple.style.opacity = '1';
      ripple.style.transform = 'translate(-50%, -50%) scale(1.2)';
      setTimeout(() => {
        ripple.style.opacity = '0';
        ripple.style.transform = 'translate(-50%, -50%) scale(0.6)';
      }, 250);
    }

    // Pointer events for natural touch, drag, and swipe
    screenImg.addEventListener('pointerdown', function(e) {
      isPointerDown = true;
      pointerDownTime = Date.now();
      const coords = getNormalizedCoords(e);
      startX = coords.normX;
      startY = coords.normY;
      showRipple(coords.clientX, coords.clientY);
      try { screenImg.setPointerCapture(e.pointerId); } catch(_) {}
    });

    screenImg.addEventListener('pointerup', function(e) {
      if (!isPointerDown) return;
      isPointerDown = false;
      const coords = getNormalizedCoords(e);
      const endX = coords.normX;
      const endY = coords.normY;
      const elapsed = Date.now() - pointerDownTime;

      const dx = endX - startX;
      const dy = endY - startY;
      const distance = Math.hypot(dx, dy);

      if (currentMode === 'SWIPE' || currentMode === 'SCROLL' || distance > 0.04) {
        // Perform swipe/drag
        const duration = Math.min(Math.max(elapsed, 120), 500);
        fetch('/api/swipe', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            startX: startX,
            startY: startY,
            endX: endX,
            endY: endY,
            durationMs: duration
          })
        }).catch(function(){});
      } else {
        // Perform Tap or Long Press
        const action = elapsed > 550 ? 'LONG_PRESS' : 'TAP';
        fetch('/api/touch', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ x: endX, y: endY, action: action })
        }).catch(function(){});
      }
    });

    screenImg.addEventListener('pointercancel', function() {
      isPointerDown = false;
    });

    // Mouse wheel scrolling
    screenImg.addEventListener('wheel', function(e) {
      e.preventDefault();
      if (e.deltaY > 0) {
        sendNav('SCROLL_DOWN');
      } else if (e.deltaY < 0) {
        sendNav('SCROLL_UP');
      }
    }, { passive: false });

    function sendNav(action) {
      fetch('/api/nav?cmd=' + encodeURIComponent(action)).catch(function(){});
    }

    // Polling status
    function checkStatus() {
      fetch('/api/status').then(r => r.json()).then(data => {
        if (data.accessibilityActive) {
          statusBadge.className = 'status-badge';
          statusText.textContent = 'Remote Control Active';
          warningBanner.style.display = 'none';
        } else {
          statusBadge.className = 'status-badge warning';
          statusText.textContent = 'Accessibility Required';
          warningBanner.style.display = 'block';
        }
      }).catch(function(){
        statusBadge.className = 'status-badge warning';
        statusText.textContent = 'Reconnecting...';
      });
    }

    setInterval(checkStatus, 2500);
    checkStatus();

    // Auto reconnect stream
    screenImg.onerror = function() {
      setTimeout(() => {
        screenImg.src = '/stream.mjpg?t=' + Date.now();
      }, 1000);
    };

    // Modal dialog for typing text
    function openTextModal() {
      document.getElementById('textModal').style.display = 'flex';
      setTimeout(() => document.getElementById('targetTextInput').focus(), 100);
    }
    function closeTextModal() {
      document.getElementById('textModal').style.display = 'none';
      document.getElementById('targetTextInput').value = '';
    }
    function submitText() {
      const text = document.getElementById('targetTextInput').value;
      if (text.trim()) {
        fetch('/api/text', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ text: text })
        }).catch(function(){});
      }
      closeTextModal();
    }
    document.getElementById('targetTextInput').addEventListener('keydown', function(e) {
      if (e.key === 'Enter') submitText();
      if (e.key === 'Escape') closeTextModal();
    });
  </script>
</body>
</html>
    """.trimIndent()

    val bytes = html.toByteArray(Charsets.UTF_8)
    val header = (
      "HTTP/1.1 200 OK\r\n" +
      "Content-Type: text/html; charset=utf-8\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n\r\n"
    ).toByteArray(Charsets.US_ASCII)

    out.write(header)
    out.write(bytes)
    out.flush()
  }
}
