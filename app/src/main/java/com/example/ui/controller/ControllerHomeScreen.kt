package com.example.ui.controller

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceInfo
import com.example.ui.theme.ActivePillBlue
import com.example.ui.theme.ActivePillBlueText
import com.example.ui.theme.StreamConnectedBg
import com.example.ui.theme.StreamConnectedGreen
import com.example.ui.theme.StreamErrorBg
import com.example.ui.theme.StreamErrorRed

@Composable
fun ControllerHomeScreen(
  viewModel: ControllerViewModel,
  onNavigateToRemote: () -> Unit,
  onNavigateToPair: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val connectionState by viewModel.connectionState.collectAsState()
  val activeDevice by viewModel.activeDevice.collectAsState()
  val telemetry by viewModel.telemetry.collectAsState()
  val recentDevices by viewModel.recentDevices.collectAsState()

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "pulseAlpha",
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Controller Host Status Banner
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(9999.dp),
      border = androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
      ),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Pixel 9 Pro",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = " · ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Controller Active",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
          )
        }

        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Host #01",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // 2. Active Connected Target Device Card
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp),
      border = androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
      ),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        // Device Header Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = activeDevice?.name ?: "Pixel 8 Pro",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = activeDevice?.locationTag ?: "Living Room",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
              Text(
                text = "Host ID: ${activeDevice?.id ?: "tc-client-9842"} · ${activeDevice?.osVersion ?: "Android 14"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          // Live Connection Status Badge
          val isConnected = connectionState == ConnectionState.CONNECTED
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(9999.dp))
              .background(if (isConnected) StreamConnectedBg else StreamErrorBg)
              .border(
                1.dp,
                if (isConnected) StreamConnectedGreen.copy(alpha = 0.4f) else StreamErrorRed.copy(alpha = 0.4f),
                RoundedCornerShape(9999.dp),
              )
              .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .alpha(if (isConnected) pulseAlpha else 1f)
                .clip(CircleShape)
                .background(if (isConnected) StreamConnectedGreen else StreamErrorRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isConnected) "Connected (Local TLS)" else "Disconnected",
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (isConnected) StreamConnectedGreen else StreamErrorRed,
              ),
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bento Telemetry Grid (2 rows / 5 items)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TelemetryTile(
            title = "BATTERY",
            value = "${activeDevice?.batteryPercent ?: 78}%",
            subtext = "Charging",
            icon = Icons.Default.Bolt,
            iconTint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
          )
          TelemetryTile(
            title = "LATENCY",
            value = "${telemetry.latencyMs}",
            unit = "ms",
            badge = "Low",
            icon = Icons.Default.Speed,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TelemetryTile(
            title = "WI-FI",
            value = activeDevice?.wifiSsid ?: "5 GHz",
            subtext = "Signal ${activeDevice?.signalDbm ?: -42} dBm",
            icon = Icons.Default.Wifi,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          TelemetryTile(
            title = "STREAM",
            value = activeDevice?.streamResolution ?: "1080×2400",
            subtext = "@ ${telemetry.fps} FPS ${telemetry.codec}",
            icon = Icons.Default.Videocam,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          TelemetryTile(
            title = "MIRRORING",
            value = if (telemetry.isMirroringActive) "Active" else "Idle",
            icon = Icons.Default.ScreenShare,
            iconTint = MaterialTheme.colorScheme.secondary,
            showPip = true,
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onNavigateToRemote,
            shape = RoundedCornerShape(9999.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
              .testTag("remote_screen_button")
              .weight(1f)
              .height(44.dp),
          ) {
            Icon(
              imageVector = Icons.Default.SettingsRemote,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Remote Screen",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
          }

          OutlinedButton(
            onClick = { viewModel.toggleConnection() },
            shape = RoundedCornerShape(9999.dp),
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = MaterialTheme.colorScheme.error,
            ),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            ),
            modifier = Modifier
              .testTag("disconnect_button")
              .height(44.dp),
          ) {
            Icon(
              imageVector = if (connectionState == ConnectionState.CONNECTED) Icons.Default.LinkOff else Icons.Default.PowerSettingsNew,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (connectionState == ConnectionState.CONNECTED) "Disconnect" else "Connect",
              style = MaterialTheme.typography.labelLarge,
            )
          }

          IconButton(
            onClick = { /* Tune stream dialog */ },
            modifier = Modifier
              .size(44.dp)
              .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                CircleShape,
              ),
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = "Stream Settings",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }

    // 3. Quick Tools Row
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = "QUICK TOOLS",
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 1.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Pair New Device Pill
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(9999.dp),
          modifier = Modifier
            .testTag("pair_new_device_button")
            .clickable(onClick = onNavigateToPair),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.QrCodeScanner,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Pair New Device",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }

        // Wake on LAN Pill
        QuickToolPill(
          icon = Icons.Default.PowerSettingsNew,
          label = "Wake on LAN",
          iconTint = MaterialTheme.colorScheme.primary,
        )

        // Audio Cast Pill
        QuickToolPill(
          icon = Icons.Default.VolumeUp,
          label = "Audio Cast",
          iconTint = MaterialTheme.colorScheme.secondary,
        )

        // Clipboard Sync Pill
        QuickToolPill(
          icon = Icons.Default.ContentPaste,
          label = "Sync Clipboard",
          iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // 4. Recent Paired Devices Section
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Recent Paired Devices",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "See all",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.clickable { /* See all */ },
        )
      }

      recentDevices.drop(1).forEach { device ->
        RecentDeviceCard(
          device = device,
          onConnectClicked = { viewModel.pairWithDevice(device) },
        )
      }
    }

    // 5. Session Diagnostics Snippet
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(18.dp),
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
      ),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.VerifiedUser,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "End-to-End P2P Encrypted",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Noise Protocol Framework · TLS 1.3 Direct Socket",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
      }
    }

    Spacer(modifier = Modifier.height(60.dp))
  }
}

@Composable
fun TelemetryTile(
  title: String,
  value: String,
  modifier: Modifier = Modifier,
  unit: String? = null,
  subtext: String? = null,
  badge: String? = null,
  icon: ImageVector? = null,
  iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
  showPip: Boolean = false,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(14.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (icon != null) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(15.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Row(verticalAlignment = Alignment.Bottom) {
        if (showPip) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(StreamConnectedGreen)
          )
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
          text = value,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (unit != null) {
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (badge != null) {
          Spacer(modifier = Modifier.weight(1f))
          Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
            ),
            color = ActivePillBlueText,
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(ActivePillBlue)
              .padding(horizontal = 5.dp, vertical = 1.dp),
          )
        }
      }

      if (subtext != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtext,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun QuickToolPill(
  icon: ImageVector,
  label: String,
  iconTint: androidx.compose.ui.graphics.Color,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(9999.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = Modifier.clickable { /* Quick tool */ },
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun RecentDeviceCard(
  device: DeviceInfo,
  onConnectClicked: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(16.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (device.model.contains("Tablet", ignoreCase = true)) Icons.Default.TabletAndroid else Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = device.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${device.locationTag} · ${device.lastSeen}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Button(
        onClick = onConnectClicked,
        shape = RoundedCornerShape(9999.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (device.isAuthorized) ActivePillBlue else MaterialTheme.colorScheme.primary,
          contentColor = if (device.isAuthorized) ActivePillBlueText else MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.height(34.dp),
      ) {
        Text(
          text = if (device.isAuthorized) "Connect" else "Pair",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
      }
    }
  }
}
