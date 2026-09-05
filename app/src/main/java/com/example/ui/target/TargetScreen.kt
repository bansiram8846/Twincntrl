package com.example.ui.target

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StreamConnectedBg
import com.example.ui.theme.StreamConnectedGreen
import com.example.ui.theme.StreamWarningAmber
import com.example.ui.theme.StreamWarningBg

@Composable
fun TargetScreen(
  viewModel: TargetViewModel,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isMasterOn by viewModel.isMasterOn.collectAsState()
  val isRemoteControlActive by viewModel.isRemoteControlActive.collectAsState()
  val authorizedController by viewModel.authorizedControllerName.collectAsState()
  val passcode by viewModel.oneTimePasscode.collectAsState()
  val expirySeconds by viewModel.passcodeExpirySeconds.collectAsState()

  val allowTouch by viewModel.allowTouchGestures.collectAsState()
  val allowAudio by viewModel.allowAudioStreaming.collectAsState()
  val requireBiometric by viewModel.requireBiometric.collectAsState()

  val isAccessibilityGranted by viewModel.isAccessibilityGranted.collectAsState()
  val isMediaProjectionGranted by viewModel.isMediaProjectionGranted.collectAsState()
  val isMulticastGranted by viewModel.isMulticastGranted.collectAsState()
  val qrBitmap by viewModel.qrBitmap.collectAsState()

  var showEnlargedQrDialog by remember { mutableStateOf(false) }
  val clipboardManager = LocalClipboardManager.current

  val minutes = expirySeconds / 60
  val seconds = expirySeconds % 60
  val expiryFormatted = String.format("%02d:%02d", minutes, seconds)

  Box(modifier = modifier.fillMaxSize()) {
    // Ambient Android OS Cast Halo Border indicator
    if (isRemoteControlActive) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .border(2.dp, StreamWarningAmber.copy(alpha = 0.35f))
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(bottom = 100.dp),
    ) {
      // 1. Android Native Privacy Status Overlay Bar
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "09:41",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "• 5G",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          // OS Native Privacy Indicators (Screen Cast & Mic)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (isRemoteControlActive) {
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(9999.dp))
                  .background(StreamWarningBg)
                  .border(1.dp, StreamWarningAmber.copy(alpha = 0.4f), RoundedCornerShape(9999.dp))
                  .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.ScreenShare,
                  contentDescription = "Screen Cast Active",
                  tint = StreamWarningAmber,
                  modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Casting",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                  ),
                  color = StreamWarningAmber,
                )
              }
            }

            Box(
              modifier = Modifier
                .clip(CircleShape)
                .background(StreamConnectedBg)
                .border(1.dp, StreamConnectedGreen.copy(alpha = 0.4f), CircleShape)
                .padding(4.dp),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Input Active",
                tint = StreamConnectedGreen,
                modifier = Modifier.size(12.dp),
              )
            }
          }
        }
      }

      // 2. Target Mode Header
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.ScreenShare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "TwinControl",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "Target Mode (Supervised)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(9999.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.secondary)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = if (isRemoteControlActive) "Active" else "Standby",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }

            IconButton(onClick = onOpenSettings) {
              Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // 3. High-Visibility Android Transparency Banner (Security Alert)
        AnimatedVisibility(visible = isRemoteControlActive) {
          Surface(
            color = Color(0xFF2C1600),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StreamWarningAmber.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(verticalAlignment = Alignment.Top) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StreamWarningAmber.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StreamWarningAmber,
                    modifier = Modifier.size(22.dp),
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = "Remote Control Active",
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                      color = StreamWarningAmber,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "LIVE",
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                      ),
                      color = Color.Black,
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StreamWarningAmber)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                  }
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = "Authorized Controller: $authorizedController is currently viewing and simulating touch gestures on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFE0B2),
                    lineHeight = 18.sp,
                  )
                }
              }

              // Immediate Emergency Severance Action
              Button(
                onClick = { viewModel.stopSharingAndDisconnect() },
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.error,
                  contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                  .testTag("stop_sharing_button")
                  .fillMaxWidth()
                  .height(44.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Cancel,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Stop Sharing & Disconnect",
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
              }
            }
          }
        }

        // 4. Main Device Status Card
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          shape = RoundedCornerShape(20.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // Header row with Master Switch
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "TRANSMITTER HARDWARE",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 10.sp,
                      letterSpacing = 0.8.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = "${viewModel.deviceName} (This Device)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }

              Column(horizontalAlignment = Alignment.End) {
                Switch(
                  checked = isMasterOn,
                  onCheckedChange = { viewModel.toggleMaster(it) },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                  ),
                )
                Text(
                  text = if (isMasterOn) "Master ON" else "Master OFF",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isMasterOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry Grid
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                ),
                modifier = Modifier.weight(1f),
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.WifiTethering,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Transmission",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Wi-Fi 5 GHz (Local)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "12ms • 60 FPS • 1080p",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.secondary,
                  )
                }
              }

              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                ),
                modifier = Modifier.weight(1f),
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.VerifiedUser,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Security State",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Encrypted TLS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "End-to-end mirror",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SHA-256 Fingerprint Token
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text(
                      text = "HARDWARE AUTHORIZATION FINGERPRINT",
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = "SHA-256: 9F:2A:3B:88:C1:44:E2:B0",
                      style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                      ),
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }

                Text(
                  text = "Verified",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                  ),
                  color = StreamConnectedGreen,
                  modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(StreamConnectedBg)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
          }
        }

        // 5. Active Pairing & Connection Credentials Bento Card
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(20.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Pairing & Session Credentials",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "Scan or enter this temporary token on your secondary controller device.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Timer,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Expires in $expiryFormatted",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }

            // QR & Passcode Display Box
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLowest,
              shape = RoundedCornerShape(14.dp),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                // Real QR Code Box
                Box(
                  modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { showEnlargedQrDialog = true }
                    .padding(4.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  if (qrBitmap != null) {
                    Image(
                      bitmap = qrBitmap!!.asImageBitmap(),
                      contentDescription = "Pairing QR Code. Tap to view larger.",
                      modifier = Modifier.fillMaxSize(),
                    )
                  } else {
                    CircularProgressIndicator(
                      modifier = Modifier.size(24.dp),
                      strokeWidth = 2.dp,
                    )
                  }
                }

                // Passcode & Regenerate
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = "ONE-TIME PASSCODE",
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                      ),
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = "Tap QR to zoom",
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                      color = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = passcode,
                    style = MaterialTheme.typography.headlineMedium.copy(
                      fontFamily = FontFamily.Monospace,
                      fontWeight = FontWeight.Bold,
                      letterSpacing = 2.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Row(
                    modifier = Modifier.clickable { viewModel.regeneratePasscode() },
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Default.Refresh,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Regenerate Code",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.secondary,
                    )
                  }
                }
              }
            }

            // Enlarged QR Code Dialog
            if (showEnlargedQrDialog) {
              AlertDialog(
                onDismissRequest = { showEnlargedQrDialog = false },
                title = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.QrCode,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Scan to Pair",
                      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                  }
                },
                text = {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = "Scan this QR code using the Controller app viewfinder or copy the direct pairing code.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                      modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      if (qrBitmap != null) {
                        Image(
                          bitmap = qrBitmap!!.asImageBitmap(),
                          contentDescription = "Enlarged Pairing QR Code",
                          modifier = Modifier.fillMaxSize(),
                        )
                      } else {
                        CircularProgressIndicator()
                      }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                      color = MaterialTheme.colorScheme.surfaceContainerHigh,
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                          Text(
                            text = "Target IP: ${viewModel.localIpAddress}:8989",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                          )
                          Text(
                            text = "PIN: $passcode",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                          )
                        }
                      }
                    }
                  }
                },
                confirmButton = {
                  Button(
                    onClick = {
                      clipboardManager.setText(AnnotatedString(viewModel.pairingPayload))
                      showEnlargedQrDialog = false
                    },
                  ) {
                    Icon(
                      imageVector = Icons.Default.ContentCopy,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Pairing Link")
                  }
                },
                dismissButton = {
                  TextButton(onClick = { showEnlargedQrDialog = false }) {
                    Text("Close")
                  }
                },
              )
            }

            // Granular Session Permissions Checkboxes
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              PermissionSwitchItem(
                title = "Allow Remote Touch Gestures",
                subtitle = "Enables injection of taps, scrolls, and typing",
                icon = Icons.Default.TouchApp,
                checked = allowTouch,
                onCheckedChange = { viewModel.toggleAllowTouch(it) },
              )

              PermissionSwitchItem(
                title = "Allow Remote Audio Streaming",
                subtitle = "Routes device media & notification sounds",
                icon = Icons.Default.VolumeUp,
                checked = allowAudio,
                onCheckedChange = { viewModel.toggleAllowAudio(it) },
              )

              PermissionSwitchItem(
                title = "Require Biometric Confirmation",
                subtitle = "Class 3 Fingerprint required for incoming sessions",
                icon = Icons.Default.Fingerprint,
                checked = requireBiometric,
                onCheckedChange = { viewModel.toggleRequireBiometric(it) },
              )
            }
          }
        }

        // 6. System Permissions Audit Card
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Policy,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "System Permissions Audit",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              Text(
                text = "Android 14 API 34",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AuditRow(
              label = "Accessibility Service",
              statusText = if (isAccessibilityGranted) "Granted (Active)" else "Permission Required",
              isGranted = isAccessibilityGranted,
            )

            Spacer(modifier = Modifier.height(8.dp))

            AuditRow(
              label = "MediaProjection (Screen Capture)",
              statusText = if (isMediaProjectionGranted) "Foreground Active" else "Requires User Consent",
              isGranted = isMediaProjectionGranted,
            )

            Spacer(modifier = Modifier.height(8.dp))

            AuditRow(
              label = "Local Network Multicast",
              statusText = if (isMulticastGranted) "Granted" else "Denied",
              isGranted = isMulticastGranted,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionSwitchItem(
  title: String,
  subtitle: String,
  icon: ImageVector,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onCheckedChange(!checked) },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = CheckboxDefaults.colors(
        checkedColor = MaterialTheme.colorScheme.primary,
        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
      ),
    )
  }
}

@Composable
private fun AuditRow(
  label: String,
  statusText: String,
  isGranted: Boolean,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = if (isGranted) StreamConnectedGreen else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    Text(
      text = statusText,
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
      ),
      color = if (isGranted) StreamConnectedGreen else MaterialTheme.colorScheme.error,
      modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(if (isGranted) StreamConnectedBg else Color(0x338C1D18))
        .padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}
