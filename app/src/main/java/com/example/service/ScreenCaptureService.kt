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
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.network.server.ScreenStreamServer
import com.example.network.server.TargetControlServer
import com.example.network.server.WebStreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    @Volatile
    var savedResultCode: Int = Activity.RESULT_CANCELED
    @Volatile
    var savedResultData: Intent? = null
  }

  private val binder = LocalBinder()
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var handlerThread: HandlerThread? = null
  private var captureHandler: Handler? = null
  private var lastFrameTimestamp = 0L

  private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
  private var keepaliveJob: Job? = null
  @Volatile
  private var lastCapturedJpeg: ByteArray? = null
  @Volatile
  private var lastCapturedWidth: Int = 720
  @Volatile
  private var lastCapturedHeight: Int = 1280

  inner class LocalBinder : Binder() {
    fun getService(): ScreenCaptureService = this@ScreenCaptureService
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    val thread = HandlerThread("ScreenCaptureThread").apply { start() }
    handlerThread = thread
    captureHandler = Handler(thread.looper)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null) {
      Log.i(TAG, "ScreenCaptureService restarted by system, maintaining active streaming and control servers")
      val notification = buildForegroundNotification()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      ScreenStreamServer.instance.start()
      TargetControlServer.getInstance(this).start()
      WebStreamServer.getInstance(this).start()
      val cachedCode = savedResultCode
      val cachedData = savedResultData
      if (cachedCode == Activity.RESULT_OK && cachedData != null && !isRunning) {
        startMediaProjection(cachedCode, cachedData)
      }
      return START_STICKY
    }

    when (intent.action) {
      ACTION_STOP -> {
        savedResultCode = Activity.RESULT_CANCELED
        savedResultData = null
        stopCapture()
        WebStreamServer.getInstance(this).stop()
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
          savedResultCode = resultCode
          savedResultData = data
          ScreenStreamServer.instance.start()
          TargetControlServer.getInstance(this).start()
          WebStreamServer.getInstance(this).start()
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
        ScreenStreamServer.instance.start()
        TargetControlServer.getInstance(this).start()
        WebStreamServer.getInstance(this).start()
      }
    }

    return START_STICKY
  }

  private fun startMediaProjection(resultCode: Int, data: Intent) {
    try {
      stopCapture()

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

      val isLandscape = screenWidth > screenHeight
      val targetWidth = if (isLandscape) 1280 else 720
      val targetHeight = if (isLandscape) {
        ((screenHeight.toFloat() / screenWidth.toFloat()) * 1280).toInt().coerceAtLeast(720)
      } else {
        ((screenHeight.toFloat() / screenWidth.toFloat()) * 720).toInt().coerceAtLeast(1280)
      }

      val reader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 3)
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
      startKeepaliveLoop()
      Log.i(TAG, "Full-device screen capture active: ${targetWidth}x${targetHeight}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start MediaProjection virtual display: ${e.message}")
    }
  }

  private fun startKeepaliveLoop() {
    keepaliveJob?.cancel()
    keepaliveJob = serviceScope.launch {
      while (isActive && isRunning) {
        delay(250)
        val now = System.currentTimeMillis()
        // If screen is static (no new frame from ImageReader in >300ms), re-broadcast the last frame
        if (now - lastFrameTimestamp >= 300) {
          val cached = lastCapturedJpeg
          if (cached != null) {
            ScreenStreamServer.instance.broadcastFrame(cached, lastCapturedWidth, lastCapturedHeight)
            WebStreamServer.getInstance(this@ScreenCaptureService).broadcastFrame(cached)
          }
        }
      }
    }
  }

  private fun processCapturedFrame(reader: ImageReader, width: Int, height: Int) {
    val image = try { reader.acquireLatestImage() } catch (_: Exception) { null } ?: return
    try {
      val now = System.currentTimeMillis()
      // Limit to ~30 FPS to avoid saturating network buffer
      if (now - lastFrameTimestamp < 33) {
        return
      }
      lastFrameTimestamp = now

      val planes = image.planes
      if (planes.isEmpty()) return
      val buffer: ByteBuffer = planes[0].buffer
      val pixelStride = planes[0].pixelStride
      val rowStride = planes[0].rowStride
      val pixelWidth = rowStride / pixelStride

      val bitmap = Bitmap.createBitmap(
        pixelWidth,
        height,
        Bitmap.Config.ARGB_8888
      )
      bitmap.copyPixelsFromBuffer(buffer)

      val croppedBitmap = if (pixelWidth > width) {
        Bitmap.createBitmap(bitmap, 0, 0, width, height)
      } else {
        bitmap
      }

      val baos = ByteArrayOutputStream()
      croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
      val jpegBytes = baos.toByteArray()

      lastCapturedJpeg = jpegBytes
      lastCapturedWidth = width
      lastCapturedHeight = height

      ScreenStreamServer.instance.broadcastFrame(jpegBytes, width, height)
      WebStreamServer.getInstance(this@ScreenCaptureService).broadcastFrame(jpegBytes)

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
    keepaliveJob?.cancel()
    keepaliveJob = null
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

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    Log.i(TAG, "Target mobile app task swiped away. ScreenCaptureService remains fully alive in foreground.")
    val notification = buildForegroundNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    ScreenStreamServer.instance.start()
    TargetControlServer.getInstance(this).start()
    WebStreamServer.getInstance(this).start()
  }

  override fun onDestroy() {
    stopCapture()
    WebStreamServer.getInstance(this).stop()
    handlerThread?.quitSafely()
    handlerThread = null
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
        setShowBadge(false)
      }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildForegroundNotification(): Notification {
    val pendingIntent = android.app.PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      },
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
      } else {
        android.app.PendingIntent.FLAG_UPDATE_CURRENT
      }
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("TwinControl Live Casting")
      .setContentText("Target screen is streaming live to authorized Controller")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }
}
