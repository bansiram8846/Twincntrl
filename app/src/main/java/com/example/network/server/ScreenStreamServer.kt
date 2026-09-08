package com.example.network.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import com.example.MainActivity
import com.example.network.protocol.TwinProtocol
import com.example.service.ScreenCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ScreenStreamServer {

  companion object {
    private const val TAG = "ScreenStreamServer"
    val instance = ScreenStreamServer()
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var serverSocket: ServerSocket? = null
  private var listenJob: Job? = null
  private var captureLoopJob: Job? = null
  private val activeClients = CopyOnWriteArrayList<Socket>()
  private val frameCounter = AtomicLong(0)

  @Volatile
  private var lastFramePayload: ByteArray? = null
  @Volatile
  private var lastFrameWidth: Int = 720
  @Volatile
  private var lastFrameHeight: Int = 1280

  fun start() {
    if (serverSocket != null && !serverSocket!!.isClosed && listenJob?.isActive == true) {
      Log.i(TAG, "ScreenStreamServer already listening on port ${TwinProtocol.STREAM_PORT}")
      return
    }
    stop()
    listenJob = scope.launch {
      try {
        val server = ServerSocket(TwinProtocol.STREAM_PORT)
        server.reuseAddress = true
        serverSocket = server
        Log.i(TAG, "ScreenStreamServer listening on port ${TwinProtocol.STREAM_PORT}")

        while (isActive) {
          val client = server.accept()
          client.tcpNoDelay = true
          client.sendBufferSize = 256 * 1024
          activeClients.add(client)
          Log.i(TAG, "New stream client connected: ${client.inetAddress.hostAddress}")

          // Immediately deliver the latest cached frame so the controller displays the target screen instantly
          val cachedBytes = lastFramePayload
          if (cachedBytes != null) {
            scope.launch {
              sendFrameToClient(client, cachedBytes, lastFrameWidth, lastFrameHeight)
            }
          }

          startCaptureLoop()
        }
      } catch (e: Exception) {
        if (isActive) Log.e(TAG, "Stream server socket error: ${e.message}")
      }
    }
  }

  fun startCaptureLoop() {
    if (captureLoopJob?.isActive == true) return
    captureLoopJob = scope.launch {
      while (isActive && activeClients.isNotEmpty()) {
        if (!ScreenCaptureService.isRunning) {
          captureAndBroadcastCurrentTargetScreen()
        }
        delay(66) // ~15 FPS smooth stream
      }
    }
  }

  private fun captureAndBroadcastCurrentTargetScreen() {
    val activity = MainActivity.currentActivity ?: return
    if (activity.isFinishing || activity.isDestroyed) return

    val decorView = activity.window.decorView
    val width = decorView.width
    val height = decorView.height
    if (width <= 0 || height <= 0) return

    val scale = (720f / width.toFloat()).coerceAtMost(1f)
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)

    try {
      val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      canvas.scale(scale, scale)

      val latch = CountDownLatch(1)
      activity.runOnUiThread {
        try {
          decorView.draw(canvas)
        } catch (e: Exception) {
          Log.d(TAG, "Draw error: ${e.message}")
        } finally {
          latch.countDown()
        }
      }
      latch.await(80, TimeUnit.MILLISECONDS)

      val baos = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
      val bytes = baos.toByteArray()
      bitmap.recycle()

      broadcastFrame(bytes, targetWidth, targetHeight)
    } catch (e: Exception) {
      Log.d(TAG, "Frame capture error: ${e.message}")
    }
  }

  fun stop() {
    listenJob?.cancel()
    listenJob = null
    captureLoopJob?.cancel()
    captureLoopJob = null
    for (client in activeClients) {
      try { client.close() } catch (_: Exception) {}
    }
    activeClients.clear()
    try {
      serverSocket?.close()
      serverSocket = null
    } catch (_: Exception) {}
  }

  fun broadcastFrame(jpegBytes: ByteArray, width: Int, height: Int) {
    lastFramePayload = jpegBytes
    lastFrameWidth = width
    lastFrameHeight = height

    if (activeClients.isEmpty()) return

    val frameId = frameCounter.incrementAndGet()
    val timestamp = System.currentTimeMillis()

    for (client in activeClients) {
      if (client.isClosed) {
        activeClients.remove(client)
        continue
      }
      sendFrameToClient(client, jpegBytes, width, height, frameId, timestamp)
    }
  }

  private fun sendFrameToClient(
    client: Socket,
    jpegBytes: ByteArray,
    width: Int,
    height: Int,
    frameId: Long = frameCounter.incrementAndGet(),
    timestamp: Long = System.currentTimeMillis(),
  ) {
    try {
      val dos = DataOutputStream(client.getOutputStream())
      dos.write(TwinProtocol.FRAME_MAGIC)
      dos.writeLong(timestamp)
      dos.writeLong(frameId)
      dos.writeInt(width)
      dos.writeInt(height)
      dos.writeInt(jpegBytes.size)
      dos.write(jpegBytes)
      dos.flush()
    } catch (e: Exception) {
      Log.d(TAG, "Client dropped during frame transmit: ${e.message}")
      try { client.close() } catch (_: Exception) {}
      activeClients.remove(client)
    }
  }
}
