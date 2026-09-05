package com.example.network.server

import android.util.Log
import com.example.network.protocol.TwinProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class ScreenStreamServer {

  companion object {
    private const val TAG = "ScreenStreamServer"
    val instance = ScreenStreamServer()
  }

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var serverSocket: ServerSocket? = null
  private var listenJob: Job? = null
  private val activeClients = CopyOnWriteArrayList<Socket>()
  private val frameCounter = AtomicLong(0)

  fun start() {
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
        }
      } catch (e: Exception) {
        if (isActive) Log.e(TAG, "Stream server socket error: ${e.message}")
      }
    }
  }

  fun stop() {
    listenJob?.cancel()
    listenJob = null
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
    if (activeClients.isEmpty()) return

    val frameId = frameCounter.incrementAndGet()
    val timestamp = System.currentTimeMillis()

    for (client in activeClients) {
      if (client.isClosed) {
        activeClients.remove(client)
        continue
      }
      try {
        val dos = DataOutputStream(client.getOutputStream())
        // Magic
        dos.write(TwinProtocol.FRAME_MAGIC)
        // Timestamp
        dos.writeLong(timestamp)
        // Frame ID
        dos.writeLong(frameId)
        // Dimensions
        dos.writeInt(width)
        dos.writeInt(height)
        // Payload size
        dos.writeInt(jpegBytes.size)
        // Data
        dos.write(jpegBytes)
        dos.flush()
      } catch (e: Exception) {
        Log.d(TAG, "Client dropped during broadcast: ${e.message}")
        try { client.close() } catch (_: Exception) {}
        activeClients.remove(client)
      }
    }
  }
}
