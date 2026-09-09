package com.example.ui.target

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrustedController
import com.example.service.ScreenCaptureService
import com.example.ui.theme.StreamConnectedGreen
import com.example.ui.theme.StreamWarningAmber

@Composable
fun TargetScreen(
  viewModel: TargetViewModel,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  val isRemoteControlActive by viewModel.isRemoteControlActive.collectAsState()
  val authorizedController by viewModel.authorizedControllerName.collectAsState()
  val passcode by viewModel.oneTimePasscode.collectAsState()
  val expirySeconds by viewModel.passcodeExpirySeconds.collectAsState()
  val qrBitmap by viewModel.qrBitmap.collectAsState()
  val trustedControllers by viewModel.trustedControllers.collectAsState()
  val allowTouch by viewModel.allowTouchGestures.collectAsState()
  val isSilentMode by viewModel.isSilentModeEnabled.collectAsState()

  var showEnlargedQrDialog by remember { mutableStateOf(false) }
  var controllerToRevoke by remember { mutableStateOf<TrustedController?>(null) }
  var showClearAllDialog by remember { mutableStateOf(false) }

  val mediaProjectionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
      val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
        action = ScreenCaptureService.ACTION_START
        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
        putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
      } else {
        context.startService(serviceIntent)
      }
      viewModel.onMediaProjectionStarted()
      Toast.makeText(context, "Screen sharing is active across all apps", Toast.LENGTH_SHORT).show()
    } else {
      viewModel.onMediaProjectionStopped()
      Toast.makeText(context, "Screen capture permission was cancelled", Toast.LENGTH_SHORT).show()
    }
  }

  fun requestFullDeviceCapture() {
    try {
      val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
      if (mpm != null) {
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
      }
    } catch (e: Exception) {
      Log.e("TargetScreen", "Error launching screen capture prompt: ${e.message}")
    }
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { _ -> }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
      ) {
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    if (!ScreenCaptureService.isRunning) {
      requestFullDeviceCapture()
    } else {
      viewModel.onMediaProjectionStarted()
    }
  }

  val webShareUrl = viewModel.getWebShareUrl()
  val minutes = expirySeconds / 60
  val seconds = expirySeconds % 60
  val expiryFormatted = String.format("%02d:%02d", minutes, seconds)

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Status & Primary Action Card (Lightweight)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = if (isRemoteControlActive || ScreenCaptureService.isRunning) {
          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
          MaterialTheme.colorScheme.surfaceContainerHigh
        }
      ),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                  if (ScreenCaptureService.isRunning) StreamConnectedGreen.copy(alpha = 0.2f)
                  else MaterialTheme.colorScheme.surfaceVariant
                ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (ScreenCaptureService.isRunning) Icons.Default.ScreenShare else Icons.Default.StopScreenShare,
                contentDescription = null,
                tint = if (ScreenCaptureService.isRunning) StreamConnectedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
              )
            }
            Column {
              Text(
                text = viewModel.deviceName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (ScreenCaptureService.isRunning) StreamConnectedGreen else StreamWarningAmber)
                )
                Text(
                  text = if (isRemoteControlActive) "Controlled by $authorizedController"
                  else if (ScreenCaptureService.isRunning) "Broadcasting Screen"
                  else "Ready to Share",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          ) {
            Text(
              text = viewModel.localIpAddress,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Primary Stream Toggle Button
        if (ScreenCaptureService.isRunning) {
          Button(
            onClick = {
              viewModel.stopSharingAndDisconnect()
              Toast.makeText(context, "Screen sharing stopped", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("target_stop_sharing_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          ) {
            Icon(Icons.Default.StopScreenShare, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop Screen Sharing", fontWeight = FontWeight.SemiBold)
          }
        } else {
          Button(
            onClick = { requestFullDeviceCapture() },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("target_start_sharing_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          ) {
            Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Screen Sharing", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }

    // 2. Link Sharing Card (Instant Browser Control - No App Needed!)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
          Column {
            Text(
              text = "Share Link (No App Required)",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
              text = "Open this link in Chrome, Safari, or Edge on any device on your Wi-Fi",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = webShareUrl,
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FilledTonalButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(webShareUrl))
              Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
              .weight(1f)
              .height(42.dp)
              .testTag("copy_share_link_button"),
            shape = RoundedCornerShape(10.dp),
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Link")
          }

          Button(
            onClick = { viewModel.shareWebLink(context) },
            modifier = Modifier
              .weight(1f)
              .height(42.dp)
              .testTag("send_share_link_button"),
            shape = RoundedCornerShape(10.dp),
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share Link")
          }
        }
      }
    }

    // 3. TwinControl App Connection (PIN & QR)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Default.PhoneAndroid,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
            Column {
              Text(
                text = "TwinControl App Pairing",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              )
              Text(
                text = "For phones running TwinControl app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          IconButton(onClick = { viewModel.regeneratePasscode() }) {
            Icon(Icons.Default.Refresh, contentDescription = "New PIN", tint = MaterialTheme.colorScheme.primary)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // 6-digit PIN Box
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "ONE-TIME PASSCODE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = passcode,
                style = MaterialTheme.typography.headlineMedium.copy(
                  fontWeight = FontWeight.Black,
                  letterSpacing = 4.sp,
                  fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Expires in $expiryFormatted",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          // QR Code Thumbnail (Tap to enlarge)
          if (qrBitmap != null) {
            Box(
              modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .clickable { showEnlargedQrDialog = true }
                .padding(6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                bitmap = qrBitmap!!.asImageBitmap(),
                contentDescription = "Tap to enlarge QR Code",
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        }
      }
    }

    // 4. Trusted Controllers Section
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.VerifiedUser,
              contentDescription = null,
              tint = StreamConnectedGreen,
              modifier = Modifier.size(22.dp),
            )
            Text(
              text = "Trusted Controllers (${trustedControllers.size})",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
          }

          if (trustedControllers.isNotEmpty()) {
            TextButton(
              onClick = { showClearAllDialog = true },
              colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
              Text("Clear All")
            }
          }
        }

        Text(
          text = "Trusted controllers connect and control your screen automatically without re-entering a PIN.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (trustedControllers.isEmpty()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
              )
              Text(
                text = "No trusted controllers yet. Devices paired with your passcode will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            trustedControllers.forEach { controller ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(StreamConnectedGreen.copy(alpha = 0.15f)),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StreamConnectedGreen,
                        modifier = Modifier.size(20.dp),
                      )
                    }

                    Column {
                      Text(
                        text = controller.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      Text(
                        text = "${controller.ipAddress} • Trusted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }

                  IconButton(
                    onClick = { controllerToRevoke = controller },
                    modifier = Modifier.testTag("revoke_controller_${controller.id}"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Delete,
                      contentDescription = "Revoke access",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // 5. Lightweight Options Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          text = "Preferences",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
              Text("Allow Remote Touch Controls", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
              Text("Allow controllers to tap and scroll on your screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Switch(
            checked = allowTouch,
            onCheckedChange = { viewModel.toggleAllowTouch(it) },
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = StreamConnectedGreen)
            Column {
              Text("Auto-Authorize Trusted Controllers", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
              Text("Connect automatically without re-prompting for a PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Switch(
            checked = isSilentMode,
            onCheckedChange = { viewModel.toggleSilentMode(it) },
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }

  // Revoke Controller Dialog
  controllerToRevoke?.let { controller ->
    AlertDialog(
      onDismissRequest = { controllerToRevoke = null },
      title = { Text("Revoke Controller Access") },
      text = {
        Text("Are you sure you want to revoke trusted access for \"${controller.name}\" (${controller.ipAddress})? They will need to re-enter a PIN to connect.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.removeTrustedController(controller.id)
            controllerToRevoke = null
            Toast.makeText(context, "Trusted access revoked for ${controller.name}", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Revoke")
        }
      },
      dismissButton = {
        TextButton(onClick = { controllerToRevoke = null }) {
          Text("Cancel")
        }
      },
    )
  }

  // Clear All Dialog
  if (showClearAllDialog) {
    AlertDialog(
      onDismissRequest = { showClearAllDialog = false },
      title = { Text("Revoke All Trusted Controllers") },
      text = {
        Text("Remove all saved trusted controllers? Any controller will be required to authenticate with a new PIN.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearAllTrustedControllers()
            showClearAllDialog = false
            Toast.makeText(context, "All trusted controllers cleared", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Revoke All")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearAllDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }

  // Enlarged QR Code Dialog
  if (showEnlargedQrDialog && qrBitmap != null) {
    AlertDialog(
      onDismissRequest = { showEnlargedQrDialog = false },
      title = { Text("Scan to Connect", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(240.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White)
              .padding(12.dp),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              bitmap = qrBitmap!!.asImageBitmap(),
              contentDescription = "Enlarged QR Code",
              modifier = Modifier.fillMaxSize(),
            )
          }
          Text(
            text = "Passcode: $passcode",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 2.sp,
            ),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showEnlargedQrDialog = false }) {
          Text("Close")
        }
      },
    )
  }
}
