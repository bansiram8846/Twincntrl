package com.example.ui.target

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ScreenCaptureService
import com.example.ui.theme.StreamConnectedGreen

@Composable
fun TargetScreen(
  viewModel: TargetViewModel,
  onSwitchToControllerMode: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isRemoteControlActive by viewModel.isRemoteControlActive.collectAsState()
  val authorizedController by viewModel.authorizedControllerName.collectAsState()
  val connectedControllerIp by viewModel.connectedControllerIp.collectAsState()

  var isServiceStreaming by remember { mutableStateOf(ScreenCaptureService.isRunning) }

  // Periodic poll to keep service state accurate
  LaunchedEffect(Unit) {
    while (true) {
      isServiceStreaming = ScreenCaptureService.isRunning
      kotlinx.coroutines.delay(1000)
    }
  }

  val isActivelySharing = isServiceStreaming || isRemoteControlActive

  // Launcher for standard Android screen capture consent
  val mediaProjectionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
      val intent = Intent(context, ScreenCaptureService::class.java).apply {
        action = ScreenCaptureService.ACTION_START
        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
        putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
      isServiceStreaming = true
      Toast.makeText(context, "Screen Sharing Started", Toast.LENGTH_SHORT).show()
    } else {
      Toast.makeText(context, "Screen sharing permission denied", Toast.LENGTH_SHORT).show()
    }
  }

  // Subtle pulsing animation for active streaming
  val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 28.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // 1. Top Minimal Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(if (isActivelySharing) StreamConnectedGreen else MaterialTheme.colorScheme.outline)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "TARGET MODE",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.2.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          )
        }

        if (connectedControllerIp != null || (authorizedController.isNotBlank() && authorizedController != "None")) {
          Surface(
            shape = RoundedCornerShape(100.dp),
            color = StreamConnectedGreen.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, StreamConnectedGreen.copy(alpha = 0.4f)),
          ) {
            Text(
              text = "Linked to Controller",
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = StreamConnectedGreen,
              ),
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }

      // 2. Central Ultra-Lightweight Status Element
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 32.dp),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.size(170.dp)
        ) {
          if (isActivelySharing) {
            Box(
              modifier = Modifier
                .size(160.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(StreamConnectedGreen.copy(alpha = 0.15f))
            )
          }

          Box(
            modifier = Modifier
              .size(120.dp)
              .clip(CircleShape)
              .background(
                if (isActivelySharing) StreamConnectedGreen.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant
              )
              .border(
                width = 2.dp,
                color = if (isActivelySharing) StreamConnectedGreen else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isActivelySharing) Icons.Default.ScreenShare else Icons.Default.StopScreenShare,
              contentDescription = "Sharing Status",
              tint = if (isActivelySharing) StreamConnectedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(54.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = if (isActivelySharing) "Screen Sharing Active" else "Target Standby",
          style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = when {
            isActivelySharing && authorizedController.isNotBlank() && authorizedController != "None" ->
              "Broadcasting screen live. Monitored by $authorizedController."
            isActivelySharing ->
              "Broadcasting screen live and ready to be monitored."
            connectedControllerIp != null ->
              "Connected to controller ($connectedControllerIp). Tap Start to begin screen sharing."
            else ->
              "This phone is ready. Open a controller link or tap Start to share screen."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Main Single Action Button
        Button(
          onClick = {
            if (isActivelySharing) {
              val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
              }
              context.startService(stopIntent)
              isServiceStreaming = false
              Toast.makeText(context, "Screen Sharing Stopped", Toast.LENGTH_SHORT).show()
            } else {
              val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
              mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isActivelySharing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (isActivelySharing) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("target_toggle_sharing_button")
        ) {
          Icon(
            imageVector = if (isActivelySharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = if (isActivelySharing) "Stop Screen Sharing" else "Start Screen Sharing",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        }
      }

      // 3. Bottom Minimal Identity & Switch Mode
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = viewModel.deviceName,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = viewModel.localIpAddress,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
          onClick = onSwitchToControllerMode,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("switch_to_controller_mode_button")
        ) {
          Icon(
            imageVector = Icons.Default.SettingsRemote,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Switch to Controller Mode", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }
}
