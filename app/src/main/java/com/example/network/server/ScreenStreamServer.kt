package com.example.network.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
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

  @Volatile
  private var cachedStandbyFrame: ByteArray? = null
  private val mainHandler = Handler(Looper.getMainLooper())

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
          try {
            val client = server.accept()
            client.tcpNoDelay = true
            client.sendBufferSize = 256 * 1024
            activeClients.add(client)
            Log.i(TAG, "New stream client connected: ${client.inetAddress.hostAddress}")

            // Immediately deliver the latest cached frame or standby frame so controller receives immediate display
            val initialBytes = lastFramePayload ?: getOrCreateStandbyFrame(720, 1280)
            scope.launch {
              sendFrameToClient(client, initialBytes, lastFrameWidth, lastFrameHeight)
            }

            startCaptureLoop()
          } catch (e: Exception) {
            if (!isActive) break
          }
        }
      } catch (t: Throwable) {
        if (isActive) Log.e(TAG, "Stream server socket error: ${t.message}")
      }
    }
  }

  fun startCaptureLoop() {
    if (captureLoopJob?.isActive == true) return
    captureLoopJob = scope.launch {
      while (isActive && activeClients.isNotEmpty()) {
        try {
          if (!ScreenCaptureService.isRunning) {
            captureAndBroadcastCurrentTargetScreen()
          }
        } catch (t: Throwable) {
          Log.w(TAG, "Safe capture loop exception: ${t.message}")
        }
        delay(66) // ~15 FPS smooth stream
      }
      captureLoopJob = null
    }
  }

  private fun captureAndBroadcastCurrentTargetScreen() {
    val activity = MainActivity.currentActivity
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
      val standby = getOrCreateStandbyFrame(720, 1280)
      broadcastFrame(standby, 720, 1280)
      return
    }

    val window = try { activity.window } catch (_: Throwable) { null }
    val decorView = window?.peekDecorView()
    if (window == null || decorView == null || !decorView.isAttachedToWindow) {
      val standby = getOrCreateStandbyFrame(720, 1280)
      broadcastFrame(standby, 720, 1280)
      return
    }

    val width = decorView.width
    val height = decorView.height
    if (width <= 0 || height <= 0) {
      val standby = getOrCreateStandbyFrame(720, 1280)
      broadcastFrame(standby, 720, 1280)
      return
    }

    val scale = (720f / width.toFloat()).coerceAtMost(1f)
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var copySuccess = false

        PixelCopy.request(
          window,
          bitmap,
          { result ->
            copySuccess = (result == PixelCopy.SUCCESS)
            latch.countDown()
          },
          mainHandler
        )

        val completed = latch.await(100, TimeUnit.MILLISECONDS)
        if (completed && copySuccess && !bitmap.isRecycled) {
          val baos = ByteArrayOutputStream()
          bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
          val bytes = baos.toByteArray()
          broadcastFrame(bytes, targetWidth, targetHeight)
          bitmap.recycle()
        } else if (completed) {
          if (!bitmap.isRecycled) bitmap.recycle()
          val standby = getOrCreateStandbyFrame(targetWidth, targetHeight)
          broadcastFrame(standby, targetWidth, targetHeight)
        } else {
          // Timed out: do NOT recycle bitmap as PixelCopy may still complete writing asynchronously.
          val standby = getOrCreateStandbyFrame(targetWidth, targetHeight)
          broadcastFrame(standby, targetWidth, targetHeight)
        }
      } catch (t: Throwable) {
        Log.d(TAG, "Safe PixelCopy frame capture note: ${t.message}")
        val standby = getOrCreateStandbyFrame(targetWidth, targetHeight)
        broadcastFrame(standby, targetWidth, targetHeight)
      }
    } else {
      val standby = getOrCreateStandbyFrame(targetWidth, targetHeight)
      broadcastFrame(standby, targetWidth, targetHeight)
    }
  }

  private fun getOrCreateStandbyFrame(width: Int, height: Int): ByteArray {
    cachedStandbyFrame?.let { return it }
    return try {
      val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
      val canvas = Canvas(bmp)
      val bgPaint = Paint().apply { color = Color.rgb(15, 23, 42) }
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

      val titlePaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
      }
      val subPaint = Paint().apply {
        color = Color.rgb(148, 163, 184)
        textSize = 22f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
      }
      val accentPaint = Paint().apply {
        color = Color.rgb(34, 197, 94)
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
      }

      val centerY = height / 2f
      canvas.drawText("TwinControl Target Connected", width / 2f, centerY - 50f, titlePaint)
      canvas.drawText("• Remote Control Link Active •", width / 2f, centerY, accentPaint)
      canvas.drawText("Tap 'Start Screen Sharing' on target device", width / 2f, centerY + 50f, subPaint)
      canvas.drawText("for full live screen mirroring", width / 2f, centerY + 85f, subPaint)

      val baos = ByteArrayOutputStream()
      bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos)
      val bytes = baos.toByteArray()
      bmp.recycle()
      cachedStandbyFrame = bytes
      bytes
    } catch (_: Throwable) {
      ByteArray(0)
    }
  }

  fun stop() {
    listenJob?.cancel()
    listenJob = null
    captureLoopJob?.cancel()
    captureLoopJob = null
    for (client in activeClients) {
      try { client.close() } catch (_: Throwable) {}
    }
    activeClients.clear()
    try {
      serverSocket?.close()
      serverSocket = null
    } catch (_: Throwable) {}
  }

  fun broadcastFrame(jpegBytes: ByteArray, width: Int, height: Int) {
    if (jpegBytes.isEmpty()) return
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
    } catch (e: Throwable) {
      Log.d(TAG, "Client dropped during frame transmit: ${e.message}")
      try { client.close() } catch (_: Throwable) {}
      activeClients.remove(client)
    }
  }
}
