package com.example.network.server

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
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
          path == "/" || path == "/index.html" -> {
            serveWebConsole(out)
            socket.close()
          }

          path == "/stream.mjpg" -> {
            serveMjpegStream(socket, out)
            // Do not close socket; keep-alive stream
          }

          path.startsWith("/api/nav") -> {
            val cmd = parseQueryParam(query, "cmd").uppercase()
            handleNavigationCommand(cmd)
            sendJsonResponse(out, """{"status":"ok","action":"$cmd"}""")
            socket.close()
          }

          path == "/api/touch" && method == "POST" -> {
            val body = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
              val r = reader.read(body, read, contentLength - read)
              if (r == -1) break
              read += r
            }
            val bodyStr = String(body, 0, read)
            handleTouchInput(bodyStr)
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

    // Send latest cached frame immediately so user sees screen without delay
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
        val realX = xRatio * metrics.widthPixels
        val realY = yRatio * metrics.heightPixels
        val service = RemoteAccessibilityService.instance
        if (service != null) {
          when (action) {
            "LONG_PRESS" -> service.simulateLongPress(realX, realY, 800L)
            else -> service.simulateTap(realX, realY)
          }
        }
      }
    } catch (_: Exception) {}
  }

  private fun handleNavigationCommand(cmd: String) {
    val service = RemoteAccessibilityService.instance
    when (cmd) {
      "BACK" -> {
        if (service == null || !service.triggerBack()) {
          // Intent fallback
        }
      }
      "HOME" -> {
        if (service == null || !service.triggerHome()) {
          val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(homeIntent)
        }
      }
      "RECENTS" -> service?.triggerRecents()
      "NOTIFICATIONS" -> service?.showNotifications()
      "QUICK_SETTINGS" -> service?.showQuickSettings()
      "LOCK" -> service?.lockDevice()
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
    val html = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>TwinControl · Live Web Remote</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: #0d0f14;
      color: #e2e8f0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      display: flex;
      flex-direction: column;
      height: 100vh;
      overflow: hidden;
    }
    header {
      background: #171b26;
      padding: 10px 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid #23293a;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 700;
      font-size: 16px;
      letter-spacing: 0.5px;
    }
    .badge {
      background: #059669;
      color: #fff;
      font-size: 10px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 999px;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .badge::before {
      content: '';
      width: 6px;
      height: 6px;
      background: #34d399;
      border-radius: 50%;
      display: inline-block;
      animation: pulse 1.5s infinite;
    }
    @keyframes pulse { 0%,100%{opacity:1;} 50%{opacity:0.3;} }
    main {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 8px;
      background: radial-gradient(circle at center, #171b26 0%, #0d0f14 100%);
      position: relative;
    }
    .screen-wrapper {
      position: relative;
      max-height: 82vh;
      max-width: 95vw;
      border-radius: 18px;
      overflow: hidden;
      box-shadow: 0 16px 40px rgba(0,0,0,0.8), 0 0 0 1px #2d3748;
      background: #000;
      display: flex;
    }
    #screen {
      display: block;
      max-height: 82vh;
      max-width: 95vw;
      object-fit: contain;
      cursor: crosshair;
      user-select: none;
      -webkit-user-drag: none;
    }
    footer {
      background: #171b26;
      border-top: 1px solid #23293a;
      padding: 10px 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .btn {
      background: #23293a;
      color: #f1f5f9;
      border: 1px solid #333d54;
      border-radius: 8px;
      padding: 8px 14px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.15s ease;
      touch-action: manipulation;
    }
    .btn:hover { background: #333d54; border-color: #4b5563; }
    .btn:active { transform: scale(0.96); background: #3b82f6; }
    .btn-primary { background: #2563eb; border-color: #3b82f6; }
    .btn-primary:hover { background: #1d4ed8; }
    .indicator {
      position: absolute;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: rgba(59, 130, 246, 0.6);
      border: 2px solid #60a5fa;
      transform: translate(-50%, -50%);
      pointer-events: none;
      opacity: 0;
      transition: opacity 0.3s, transform 0.3s;
    }
  </style>
</head>
<body>
  <header>
    <div class="brand">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>
      <span>TwinControl Web Viewer</span>
    </div>
    <div class="badge">Live P2P Stream</div>
  </header>

  <main>
    <div class="screen-wrapper" id="container">
      <img id="screen" src="/stream.mjpg" alt="Target Screen Stream" />
      <div id="touchIndicator" class="indicator"></div>
    </div>
  </main>

  <footer>
    <button class="btn" onclick="sendNav('BACK')">◀ Back</button>
    <button class="btn btn-primary" onclick="sendNav('HOME')">● Home</button>
    <button class="btn" onclick="sendNav('RECENTS')">■ Recents</button>
    <button class="btn" onclick="sendNav('NOTIFICATIONS')">🔔 Pull Down</button>
    <button class="btn" onclick="sendNav('VOL_UP')">🔊 Vol +</button>
    <button class="btn" onclick="sendNav('VOL_DOWN')">🔉 Vol -</button>
  </footer>

  <script>
    const screenImg = document.getElementById('screen');
    const indicator = document.getElementById('touchIndicator');

    screenImg.addEventListener('click', function(e) {
      const rect = screenImg.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width;
      const y = (e.clientY - rect.top) / rect.height;

      // Visual feedback
      indicator.style.left = (e.clientX - rect.left) + 'px';
      indicator.style.top = (e.clientY - rect.top) + 'px';
      indicator.style.opacity = '1';
      setTimeout(() => { indicator.style.opacity = '0'; }, 200);

      fetch('/api/touch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ x: x, y: y, action: 'TAP' })
      }).catch(err => console.log(err));
    });

    function sendNav(action) {
      fetch('/api/nav?cmd=' + action).catch(err => console.log(err));
    }

    // Auto reconnect on stream error
    screenImg.onerror = function() {
      setTimeout(() => {
        screenImg.src = '/stream.mjpg?t=' + Date.now();
      }, 1000);
    };
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
