package com.example.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.LocalDeviceManager

@Composable
fun SettingsScreen(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hardwareAcceleration by remember { mutableStateOf(true) }
  var lowLatencyAudio by remember { mutableStateOf(true) }
  var autoReconnect by remember { mutableStateOf(true) }
  var mDnsDiscovery by remember { mutableStateOf(true) }

  var customNickname by remember { mutableStateOf(LocalDeviceManager.getCustomDeviceName(context) ?: "") }
  var showRenameDialog by remember { mutableStateOf(false) }
  var renameInput by remember { mutableStateOf("") }

  val detectedModel = remember { LocalDeviceManager.getDeviceModel() }
  val detectedManufacturer = remember { LocalDeviceManager.getDeviceManufacturer() }
  val osVersion = remember { LocalDeviceManager.getOsVersion() }
  val hardware = remember { LocalDeviceManager.getDeviceHardware() }
  val isEmulator = remember { LocalDeviceManager.isRunningInEmulator() }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Top Security Badge
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
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "TwinControl Security Protocol",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "TLS 1.3 Direct Socket · Curve25519 · Ed25519 Signed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Video & Streaming Engine Group
    SettingsGroup(title = "STREAMING ENGINE") {
      SettingsRow(
        title = "Video Codec",
        subtitle = "H.265 (HEVC) Hardware Encoder",
        icon = Icons.Default.Videocam,
        trailing = {
          Text(
            text = "H.265 / 60 FPS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
          )
        },
      )
      SettingsRow(
        title = "Hardware Accelerated Decoding",
        subtitle = "Use SurfaceView direct GPU zero-copy buffer",
        icon = Icons.Default.Speed,
        trailing = {
          Switch(
            checked = hardwareAcceleration,
            onCheckedChange = { hardwareAcceleration = it },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
          )
        },
      )
      SettingsRow(
        title = "Low-Latency Audio Sink",
        subtitle = "AAudio engine with Opus codec compression",
        icon = Icons.Default.HighQuality,
        trailing = {
          Switch(
            checked = lowLatencyAudio,
            onCheckedChange = { lowLatencyAudio = it },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
          )
        },
      )
    }

    // Network & Discovery Group
    SettingsGroup(title = "NETWORK & DISCOVERY") {
      SettingsRow(
        title = "Local mDNS Discovery",
        subtitle = "Publish and browse _twincontrol._tcp service",
        icon = Icons.Default.Router,
        trailing = {
          Switch(
            checked = mDnsDiscovery,
            onCheckedChange = { mDnsDiscovery = it },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
          )
        },
      )
      SettingsRow(
        title = "Direct Socket Port",
        subtitle = "Local TLS bind port: 8443",
        icon = Icons.Default.NetworkCheck,
        trailing = {
          Text(
            text = ":8443",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.secondary,
          )
        },
      )
      SettingsRow(
        title = "Auto-Reconnect on Wi-Fi drop",
        subtitle = "Exponential backoff reconnect daemon",
        icon = Icons.Default.Lock,
        trailing = {
          Switch(
            checked = autoReconnect,
            onCheckedChange = { autoReconnect = it },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
          )
        },
      )
    }

    // Hardware & Device Identity Group (Auto-detected from Android OS)
    SettingsGroup(title = "DEVICE & HARDWARE IDENTITY") {
      SettingsRow(
        title = "Detected Phone Model",
        subtitle = "Dynamically read from android.os.Build.MODEL",
        icon = Icons.Default.PhoneAndroid,
        trailing = {
          Text(
            text = detectedModel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
          )
        },
      )
      SettingsRow(
        title = "Manufacturer",
        subtitle = "android.os.Build.MANUFACTURER",
        icon = Icons.Default.Memory,
        trailing = {
          Text(
            text = detectedManufacturer,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        },
      )
      SettingsRow(
        title = "Android OS & API",
        subtitle = "Operating System & Kernel",
        icon = Icons.Default.Info,
        trailing = {
          Text(
            text = osVersion,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
      )
      SettingsRow(
        title = "Device Nickname",
        subtitle = if (customNickname.isNotBlank()) "Custom: $customNickname" else "Using system model",
        icon = Icons.Default.Edit,
        trailing = {
          TextButton(
            onClick = {
              renameInput = customNickname
              showRenameDialog = true
            }
          ) {
            Text(if (customNickname.isNotBlank()) "Edit" else "Set")
          }
        },
      )

      if (isEmulator) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Running on Android Virtual Device / Cloud Emulator. When installed on your physical smartphone (Samsung, Pixel, Xiaomi, OnePlus, etc.), your phone's real model auto-detects dynamically without any hardcoding.",
              style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    if (showRenameDialog) {
      AlertDialog(
        onDismissRequest = { showRenameDialog = false },
        title = { Text("Set Device Nickname") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Give this device a custom friendly name across TwinControl sessions (leave empty to reset to detected system model '$detectedModel'):",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = renameInput,
              onValueChange = { renameInput = it },
              label = { Text("Device Nickname") },
              placeholder = { Text(detectedModel) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              LocalDeviceManager.setCustomDeviceName(context, renameInput.trim())
              customNickname = renameInput.trim()
              showRenameDialog = false
            }
          ) {
            Text("Save")
          }
        },
        dismissButton = {
          TextButton(onClick = { showRenameDialog = false }) {
            Text("Cancel")
          }
        },
      )
    }

    Spacer(modifier = Modifier.height(40.dp))
  }
}

@Composable
private fun SettingsGroup(
  title: String,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
      ),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp),
    )
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(18.dp),
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
      ),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(vertical = 4.dp),
        content = { content() },
      )
    }
  }
}

@Composable
private fun SettingsRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  trailing: @Composable () -> Unit,
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
      modifier = Modifier.weight(1f),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    trailing()
  }
}
