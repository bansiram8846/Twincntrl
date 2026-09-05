package com.example.ui.controller

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.util.QrCodeUtil
import java.util.concurrent.Executors

@Composable
fun CameraQrScannerView(
  onQrCodeScanned: (String) -> Unit,
  isTorchEnabled: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val hasCameraPermission = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.CAMERA,
  ) == PackageManager.PERMISSION_GRANTED

  var camera by remember { mutableStateOf<Camera?>(null) }
  var hasScanned by remember { mutableStateOf(false) }

  LaunchedEffect(isTorchEnabled, camera) {
    try {
      camera?.cameraControl?.enableTorch(isTorchEnabled)
    } catch (e: Exception) {
      Log.e("CameraQrScannerView", "Torch control error", e)
    }
  }

  if (hasCameraPermission) {
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
      onDispose {
        cameraExecutor.shutdown()
      }
    }

    AndroidView(
      factory = { ctx ->
        val previewView = PreviewView(ctx).apply {
          scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

        cameraProviderFuture.addListener({
          try {
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
              it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()
              .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                  if (hasScanned) {
                    imageProxy.close()
                    return@setAnalyzer
                  }
                  try {
                    val buffer = imageProxy.planes[0].buffer
                    val qrText = QrCodeUtil.decodeQrFromYuv(
                      buffer,
                      imageProxy.width,
                      imageProxy.height,
                    )
                    if (!qrText.isNullOrBlank()) {
                      hasScanned = true
                      onQrCodeScanned(qrText)
                    }
                  } catch (e: Exception) {
                    Log.e("CameraQrScannerView", "Error decoding frame", e)
                  } finally {
                    imageProxy.close()
                  }
                }
              }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
              lifecycleOwner,
              cameraSelector,
              preview,
              imageAnalyzer,
            )
          } catch (e: Exception) {
            Log.e("CameraQrScannerView", "Use case binding failed", e)
          }
        }, ContextCompat.getMainExecutor(ctx))

        previewView
      },
      modifier = modifier.fillMaxSize(),
    )
  } else {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
  }
}
