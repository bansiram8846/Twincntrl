package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.network.server.ScreenStreamServer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

  companion object {
    private const val TAG = "ScreenCaptureService"
    const val CHANNEL_ID = "twincontrol_screen_capture"
    const val NOTIFICATION_ID = 4042

    const val ACTION_START = "com.example.service.START_CAPTURE"
    const val ACTION_STOP = "com.example.service.STOP_CAPTURE"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"

    var isRunning = false
      private set
  }

  private val binder = LocalBinder()
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var captureHandler: Handler? = null
  private var lastFrameTimestamp = 0L

  inner class LocalBinder : Binder() {
    fun getService(): ScreenCaptureService = this@ScreenCaptureService
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    captureHandler = Handler(Looper.getMainLooper())
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopCapture()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
      }

      ACTION_START -> {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
          startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
          startMediaProjection(resultCode, data)
        } else {
          Log.w(TAG, "Media projection permission not provided or canceled")
        }
      }

      else -> {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
          startForeground(NOTIFICATION_ID, notification)
        }
      }
    }

    return START_STICKY
  }

  private fun startMediaProjection(resultCode: Int, data: Intent) {
    try {
      val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val projection = mpm.getMediaProjection(resultCode, data) ?: return
      mediaProjection = projection

      projection.registerCallback(object : MediaProjection.Callback() {
        override fun onStop() {
          stopCapture()
        }
      }, captureHandler)

      val metrics = resources.displayMetrics
      val screenWidth = metrics.widthPixels
      val screenHeight = metrics.heightPixels
      val density = metrics.densityDpi

      // Target 720p scaling for optimal balance of frame rate and local latency
      val targetWidth = 720
      val targetHeight = ((screenHeight.toFloat() / screenWidth.toFloat()) * targetWidth).toInt().coerceAtLeast(1280)

      val reader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
      imageReader = reader

      reader.setOnImageAvailableListener({ ir ->
        processCapturedFrame(ir, targetWidth, targetHeight)
      }, captureHandler)

      virtualDisplay = projection.createVirtualDisplay(
        "TwinControlVirtualDisplay",
        targetWidth,
        targetHeight,
        density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        captureHandler
      )

      isRunning = true
      Log.i(TAG, "Screen capture active: ${targetWidth}x${targetHeight}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start MediaProjection virtual display: ${e.message}")
    }
  }

  private fun processCapturedFrame(reader: ImageReader, width: Int, height: Int) {
    val image = reader.acquireLatestImage() ?: return
    try {
      val now = System.currentTimeMillis()
      // Limit to ~30 FPS to avoid saturating network buffer
      if (now - lastFrameTimestamp < 33) {
        image.close()
        return
      }
      lastFrameTimestamp = now

      val planes = image.planes
      val buffer: ByteBuffer = planes[0].buffer
      val pixelStride = planes[0].pixelStride
      val rowStride = planes[0].rowStride
      val rowPadding = rowStride - pixelStride * width

      val bitmap = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888
      )
      bitmap.copyPixelsFromBuffer(buffer)

      val croppedBitmap = if (rowPadding > 0) {
        Bitmap.createBitmap(bitmap, 0, 0, width, height)
      } else {
        bitmap
      }

      val baos = ByteArrayOutputStream()
      croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
      val jpegBytes = baos.toByteArray()

      ScreenStreamServer.instance.broadcastFrame(jpegBytes, width, height)

      if (croppedBitmap != bitmap) {
        croppedBitmap.recycle()
      }
      bitmap.recycle()
    } catch (e: Exception) {
      Log.d(TAG, "Frame process error: ${e.message}")
    } finally {
      try {
        image.close()
      } catch (_: Exception) {}
    }
  }

  private fun stopCapture() {
    isRunning = false
    try {
      virtualDisplay?.release()
      virtualDisplay = null
    } catch (_: Exception) {}
    try {
      imageReader?.close()
      imageReader = null
    } catch (_: Exception) {}
    try {
      mediaProjection?.stop()
      mediaProjection = null
    } catch (_: Exception) {}
  }

  override fun onDestroy() {
    stopCapture()
    super.onDestroy()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "TwinControl Live Streaming",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Notifies when screen capture and remote control are active"
      }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildForegroundNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("TwinControl Live Casting")
      .setContentText("Target screen is streaming securely to authorized Controller")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }
}
