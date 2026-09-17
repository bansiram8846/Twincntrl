package com.example.network.server

import android.content.Context
import android.util.Log
import com.example.data.model.DeviceInfo
import com.example.network.LocalDeviceManager
import com.example.network.protocol.TwinProtocol
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
import java.net.URLDecoder

class TargetInviteServer private constructor(private val context: Context) {

  companion object {
    private const val TAG = "TargetInviteServer"

    @Volatile
    private var sInstance: TargetInviteServer? = null

    fun getInstance(context: Context): TargetInviteServer {
      return sInstance ?: synchronized(this) {
        sInstance ?: TargetInviteServer(context.applicationContext).also { sInstance = it }
      }
    }
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var serverJob: Job? = null
  private var serverSocket: ServerSocket? = null

  var onTargetJoined: ((DeviceInfo) -> Unit)? = null
  var onTargetPingReceived: ((String) -> Unit)? = null

  fun start(port: Int = TwinProtocol.INVITE_PORT) {
    if (serverSocket != null && serverSocket?.isClosed == false) {
      return
    }

    serverJob?.cancel()
    serverJob = scope.launch {
      try {
        val s = ServerSocket(port)
        s.reuseAddress = true
        serverSocket = s
        Log.i(TAG, "TargetInviteServer listening at http://0.0.0.0:$port")

        while (isActive) {
          try {
            val client = s.accept()
            scope.launch { handleClient(client) }
          } catch (e: Exception) {
            if (!isActive) break
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error starting TargetInviteServer on port $port: ${e.message}")
      }
    }
  }

  fun stop() {
    serverJob?.cancel()
    serverJob = null
    try {
      serverSocket?.close()
    } catch (_: Exception) {}
    serverSocket = null
  }

  private fun handleClient(socket: Socket) {
    try {
      val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
      val out = socket.getOutputStream()

      val requestLine = reader.readLine() ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return

      val method = parts[0]
      val fullPath = parts[1]
      val path = fullPath.substringBefore("?")
      val query = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

      // Read HTTP headers
      var line: String?
      var contentLength = 0
      while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
        if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
          contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
        }
      }

      val remoteIp = socket.inetAddress.hostAddress?.removePrefix("/") ?: "unknown"

      when {
        path == "/join" || path == "/target" || path == "/" -> {
          val localIp = LocalDeviceManager.getLocalIpAddress(context)
          val redirectLocation = "http://$localIp:${TwinProtocol.WEB_PORT}/"
          val redirectResponse = (
            "HTTP/1.1 302 Found\r\n" +
            "Location: $redirectLocation\r\n" +
            "Cache-Control: no-cache, no-store\r\n" +
            "Connection: close\r\n" +
            "Content-Type: text/html\r\n" +
            "Content-Length: 120\r\n\r\n" +
            "<html><head><meta http-equiv=\"refresh\" content=\"0;url=$redirectLocation\"></head><body>Redirecting to <a href=\"$redirectLocation\">Web Remote</a></body></html>"
          ).toByteArray(Charsets.UTF_8)
          out.write(redirectResponse)
          out.flush()
        }
        path.startsWith("/api/target_ready") -> {
          handleTargetReady(remoteIp, query, reader, contentLength, out)
        }
        path.startsWith("/api/target_ping") -> {
          handleTargetPing(remoteIp, query, out)
        }
        else -> {
          val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
          out.write(response.toByteArray())
          out.flush()
        }
      }
    } catch (e: Exception) {
      Log.d(TAG, "Error handling client: ${e.message}")
    } finally {
      try { socket.close() } catch (_: Exception) {}
    }
  }

  private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    val map = mutableMapOf<String, String>()
    query.split("&").forEach { param ->
      val kv = param.split("=", limit = 2)
      if (kv.isNotEmpty()) {
        val key = URLDecoder.decode(kv[0], "UTF-8")
        val value = if (kv.size > 1) URLDecoder.decode(kv[1], "UTF-8") else ""
        map[key] = value
      }
    }
    return map
  }

  private fun handleTargetReady(remoteIp: String, query: String, reader: BufferedReader, contentLength: Int, out: OutputStream) {
    var targetIp = remoteIp
    var targetName = "Android Target Device"
    var targetModel = "Mobile Device"
    var targetPort = TwinProtocol.CONTROL_PORT

    val queryParams = parseQueryParams(query)
    if (queryParams.containsKey("ip")) targetIp = queryParams["ip"] ?: remoteIp
    if (queryParams.containsKey("name")) targetName = queryParams["name"] ?: targetName
    if (queryParams.containsKey("model")) targetModel = queryParams["model"] ?: targetModel
    if (queryParams.containsKey("port")) targetPort = queryParams["port"]?.toIntOrNull() ?: targetPort

    if (contentLength > 0) {
      val bodyChars = CharArray(contentLength)
      reader.read(bodyChars, 0, contentLength)
      val bodyStr = String(bodyChars)
      try {
        val json = JSONObject(bodyStr)
        if (json.has("ip")) targetIp = json.getString("ip")
        if (json.has("name")) targetName = json.getString("name")
        if (json.has("model")) targetModel = json.getString("model")
        if (json.has("port")) targetPort = json.optInt("port", targetPort)
      } catch (_: Exception) {}
    }

    val device = DeviceInfo(
      id = "link-${targetIp.replace(".", "-")}",
      name = targetName,
      model = targetModel,
      ipAddress = targetIp,
      port = targetPort,
      isAuthorized = true,
      isConnected = false,
      locationTag = "Linked via Invite",
      lastSeen = "Just now",
      batteryPercent = 100,
      wifiSsid = "Local Wi-Fi",
      signalDbm = -35,
      silentConnectCapable = true,
      pairingPin = "AUTO_TRUSTED",
    )

    onTargetJoined?.invoke(device)

    val responseJson = JSONObject().apply {
      put("status", "ok")
      put("message", "Target device registered with controller")
      put("targetIp", targetIp)
    }
    val bytes = responseJson.toString().toByteArray()
    val headers = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: application/json\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n\r\n"
    out.write(headers.toByteArray())
    out.write(bytes)
    out.flush()
  }

  private fun handleTargetPing(remoteIp: String, query: String, out: OutputStream) {
    val queryParams = parseQueryParams(query)
    val agent = queryParams["agent"] ?: "Browser"
    onTargetPingReceived?.invoke("$remoteIp ($agent)")

    val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: 15\r\n\r\n{\"status\":\"ok\"}"
    out.write(response.toByteArray())
    out.flush()
  }

  private fun serveJoinHtml(out: OutputStream, remoteIp: String) {
    val controllerIp = LocalDeviceManager.getLocalIpAddress(context)
    val controllerName = LocalDeviceManager.getEffectiveDeviceName(context)
    val deepLink = "twincontrol://target?controllerIp=$controllerIp&controllerName=${java.net.URLEncoder.encode(controllerName, "UTF-8")}"

    val html = """
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <title>TwinControl • Target Activation</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            background: #0f1115;
            color: #f1f3f5;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 20px;
          }
          .card {
            background: #181b20;
            border: 1px solid #2a2e36;
            border-radius: 24px;
            padding: 28px 24px;
            width: 100%;
            max-width: 420px;
            text-align: center;
            box-shadow: 0 16px 40px rgba(0,0,0,0.6);
          }
          .icon-badge {
            width: 64px;
            height: 64px;
            background: rgba(103, 80, 164, 0.15);
            border: 2px solid #6750a4;
            border-radius: 20px;
            margin: 0 auto 16px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
          }
          h1 {
            font-size: 22px;
            font-weight: 700;
            margin-bottom: 8px;
            color: #ffffff;
          }
          .controller-box {
            background: #21252d;
            border-radius: 14px;
            padding: 12px 16px;
            margin: 16px 0;
            display: flex;
            align-items: center;
            justify-content: space-between;
          }
          .controller-info { text-align: left; }
          .controller-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; color: #8c939f; }
          .controller-name { font-size: 15px; font-weight: 600; color: #6750a4; }
          p {
            font-size: 14px;
            color: #a0a6b1;
            line-height: 1.5;
            margin-bottom: 20px;
          }
          .btn-primary {
            display: block;
            background: #6750a4;
            color: #ffffff;
            font-size: 16px;
            font-weight: 700;
            text-decoration: none;
            padding: 16px 20px;
            border-radius: 16px;
            transition: background 0.2s;
            box-shadow: 0 4px 16px rgba(103, 80, 164, 0.4);
          }
          .btn-primary:active {
            background: #543b8c;
          }
          .status-note {
            margin-top: 18px;
            font-size: 12px;
            color: #6b7280;
          }
          .pulse {
            display: inline-block;
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: #22c55e;
            margin-right: 6px;
            animation: blink 1.5s infinite ease-in-out;
          }
          @keyframes blink {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(0.85); }
          }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon-badge">📱</div>
          <h1>Target Mode Activation</h1>
          
          <div class="controller-box">
            <div class="controller-info">
              <div class="controller-label">Monitoring Controller</div>
              <div class="controller-name">$controllerName</div>
            </div>
            <div style="font-family: monospace; font-size: 12px; color: #9ca3af;">$controllerIp</div>
          </div>

          <p>Tap below to start screen sharing. Once opened, this phone will immediately be available on the controller for live monitoring.</p>

          <a href="$deepLink" class="btn-primary" id="launchBtn">
            Launch in TwinControl App
          </a>

          <div class="status-note">
            <span class="pulse"></span>Controller is listening on local network
          </div>
        </div>

        <script>
          // Notify controller immediately that this link was accessed
          try {
            fetch('/api/target_ping?ip=' + encodeURIComponent('$remoteIp') + '&agent=' + encodeURIComponent(navigator.userAgent)).catch(function(){});
          } catch(e) {}

          // Attempt auto-launch into TwinControl App
          setTimeout(function() {
            var btn = document.getElementById('launchBtn');
            if (btn) {
              window.location.href = btn.getAttribute('href');
            }
          }, 600);
        </script>
      </body>
      </html>
    """.trimIndent()

    val bytes = html.toByteArray(Charsets.UTF_8)
    val headers = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: text/html; charset=UTF-8\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n\r\n"
    out.write(headers.toByteArray())
    out.write(bytes)
    out.flush()
  }
}
