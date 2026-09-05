package com.example.network.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.network.protocol.TwinProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Arrays

data class StreamTelemetryUpdate(
  val fps: Int,
  val latencyMs: Long,
  val bitrateMbps: Float,
  val droppedFrames: Int,
  val width: Int,
  val height: Int,
)

class StreamReceiver(
  private val onFrameReceived: (bitmap: Bitmap, telemetry: StreamTelemetryUpdate) -> Unit,
  private val onStreamDisconnected: () -> Unit,
) {

  companion object {
    private const val TAG = "StreamReceiver"
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var streamJob: Job? = null
  private var socket: Socket? = null

  // Telemetry calculation trackers
  private var frameCountInSecond = 0
  private var lastSecondTimestamp = System.currentTimeMillis()
  private var bytesInCurrentSecond = 0L
  private var currentFps = 0
  private var currentBitrateMbps = 0f
  private var lastFrameId = -1L
  private var totalDroppedFrames = 0

  fun start(targetIp: String, targetPort: Int = TwinProtocol.STREAM_PORT) {
    stop()
    streamJob = scope.launch {
      var s: Socket? = null
      try {
        s = Socket()
        s.tcpNoDelay = true
        s.receiveBufferSize = 512 * 1024
        s.connect(InetSocketAddress(targetIp, targetPort), 4000)
        socket = s
        Log.i(TAG, "Connected to stream socket at $targetIp:$targetPort")

        val dis = DataInputStream(s.getInputStream())
        val magicBuffer = ByteArray(4)

        while (isActive && !s.isClosed) {
          dis.readFully(magicBuffer)
          if (!Arrays.equals(magicBuffer, TwinProtocol.FRAME_MAGIC)) {
            Log.w(TAG, "Frame magic mismatch, resyncing...")
            continue
          }

          val timestamp = dis.readLong()
          val frameId = dis.readLong()
          val width = dis.readInt()
          val height = dis.readInt()
          val payloadLength = dis.readInt()

          if (payloadLength <= 0 || payloadLength > 5 * 1024 * 1024) {
            Log.w(TAG, "Invalid payload length: $payloadLength")
            continue
          }

          val payload = ByteArray(payloadLength)
          dis.readFully(payload)

          // Decode bitmap
          val bitmap = BitmapFactory.decodeByteArray(payload, 0, payload.size)
          if (bitmap != null) {
            val now = System.currentTimeMillis()
            val latency = (now - timestamp).coerceIn(1, 1000)

            // Dropped frame check
            if (lastFrameId >= 0 && frameId > lastFrameId + 1) {
              totalDroppedFrames += (frameId - lastFrameId - 1).toInt()
            }
            lastFrameId = frameId

            // Rate calculation
            frameCountInSecond++
            bytesInCurrentSecond += (payloadLength + TwinProtocol.HEADER_SIZE)

            if (now - lastSecondTimestamp >= 1000) {
              currentFps = frameCountInSecond
              currentBitrateMbps = (bytesInCurrentSecond * 8f) / 1_000_000f
              frameCountInSecond = 0
              bytesInCurrentSecond = 0
              lastSecondTimestamp = now
            }

            val telemetry = StreamTelemetryUpdate(
              fps = if (currentFps > 0) currentFps else 30,
              latencyMs = latency,
              bitrateMbps = currentBitrateMbps,
              droppedFrames = totalDroppedFrames,
              width = width,
              height = height,
            )

            withContext(Dispatchers.Main) {
              onFrameReceived(bitmap, telemetry)
            }
          }
        }
      } catch (e: Exception) {
        if (isActive) Log.d(TAG, "Stream receiver finished: ${e.message}")
      } finally {
        try { s?.close() } catch (_: Exception) {}
        withContext(Dispatchers.Main) {
          onStreamDisconnected()
        }
      }
    }
  }

  fun stop() {
    streamJob?.cancel()
    streamJob = null
    try {
      socket?.close()
      socket = null
    } catch (_: Exception) {}
  }
}
