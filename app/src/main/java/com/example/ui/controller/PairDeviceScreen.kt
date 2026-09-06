package com.example.ui.controller

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.AppMode
import com.example.data.model.DeviceInfo
import com.example.network.LocalDeviceManager
import com.example.network.protocol.TwinProtocol
import com.example.ui.theme.StreamConnectedGreen
import com.example.util.QrCodeUtil

@Composable
fun PairDeviceScreen(
  viewModel: ControllerViewModel,
  onNavigateBack: () -> Unit,
  onSwitchToTargetMode: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Silent Wi-Fi, 1 = QR & PIN, 2 = Bluetooth, 3 = Internet
  var isTorchOn by remember { mutableStateOf(false) }
  var showDirectIpDialog by remember { mutableStateOf(false) }
  var showPasteQrDialog by remember { mutableStateOf(false) }
  var pasteQrInput by remember { mutableStateOf("") }
  var targetIpInput by remember { mutableStateOf("") }
  var targetPortInput by remember { mutableStateOf("8888") }
  var internetHostInput by remember { mutableStateOf("") }
  var internetPortInput by remember { mutableStateOf("8888") }
  var internetPinInput by remember { mutableStateOf("") }

  val context = LocalContext.current
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
    )
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasCameraPermission = granted
    if (!granted) {
      Toast.makeText(context, "Camera permission needed for scanning QR code", Toast.LENGTH_SHORT).show()
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
          @Suppress("DEPRECATION")
          MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val decoded = QrCodeUtil.decodeQrFromBitmap(bitmap)
        if (decoded != null) {
          val success = viewModel.pairFromQrString(decoded)
          if (success) {
            Toast.makeText(context, "QR code recognized! Connecting...", Toast.LENGTH_SHORT).show()
            onNavigateBack()
          } else {
            Toast.makeText(context, "QR code detected but format is invalid", Toast.LENGTH_LONG).show()
          }
        } else {
          Toast.makeText(context, "No QR code could be found in this image", Toast.LENGTH_LONG).show()
        }
      } catch (e: Exception) {
        Toast.makeText(context, "Failed to read image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  val pinDigits by viewModel.pinDigits.collectAsState()
  val nearbyDevices by viewModel.nearbyDevices.collectAsState()

  // Scanline animation
  val infiniteTransition = rememberInfiniteTransition(label = "scanline")
  val scanOffset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(2200, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "scanlineY",
  )

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(bottom = 120.dp),
    ) {
      // 1. Top Navigation Bar
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.testTag("pair_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
              Text(
                text = "Pair New Device",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(StreamConnectedGreen)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = "Encrypted Channel",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          }

          IconButton(onClick = { /* Security info dialog */ }) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = "Security Info",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp),
            )
          }
        }
      }

      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // 2. Hardware-Backed Security Notice Card
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Hardware-Backed Security",
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "TLS 1.3",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                  ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "TwinControl uses end-to-end TLS encryption with hardware-backed keys. Both devices must be on the same local network or explicitly authorized via secure QR code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
              )
            }
          }
        }

        // 3. Segmented Switcher [Silent Wi-Fi, QR & PIN, Bluetooth, Internet]
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            val tabs = listOf(
              Triple(0, "Silent Wi-Fi", Icons.Default.Bolt),
              Triple(1, "QR / PIN", Icons.Default.QrCodeScanner),
              Triple(2, "Bluetooth", Icons.Default.Bluetooth),
              Triple(3, "Internet", Icons.Default.Public),
            )
            tabs.forEach { (index, title, icon) ->
              val isSelected = selectedTab == index
              Button(
                onClick = { selectedTab = index },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                  contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                elevation = null,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(46.dp),
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center,
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                      fontSize = 9.sp,
                    ),
                    maxLines = 1,
                  )
                }
              }
            }
          }
        }

        // 4. Content based on Selected Tab [0 = Silent Wi-Fi, 1 = QR / PIN, 2 = Bluetooth, 3 = Internet]
        when (selectedTab) {
          0 -> {
            SilentWifiConnectTab(
              nearbyDevices = nearbyDevices,
              onSilentConnect = { device ->
                viewModel.connectSilently(device)
                Toast.makeText(context, "Silent connection established with ${device.name}!", Toast.LENGTH_SHORT).show()
                onNavigateBack()
              },
              onRequestAccess = { device ->
                viewModel.pairWithDevice(device)
                onNavigateBack()
              },
              onRefresh = { viewModel.refreshNearbyDevices() },
              onDirectIpFallback = { showDirectIpDialog = true },
            )
          }

          1 -> {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              val primaryColor = MaterialTheme.colorScheme.primary

              // Viewfinder Target Box
              Box(
                modifier = Modifier
                  .size(240.dp)
                  .clip(RoundedCornerShape(20.dp))
                  .background(Color.Black),
                contentAlignment = Alignment.Center,
              ) {
                if (hasCameraPermission) {
                  CameraQrScannerView(
                    onQrCodeScanned = { qrText ->
                      val success = viewModel.pairFromQrString(qrText)
                      if (success) {
                        Toast.makeText(context, "QR code recognized! Connecting...", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                      }
                    },
                    isTorchEnabled = isTorchOn,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                      .fillMaxSize()
                      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                      .padding(16.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.CameraAlt,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = "Camera Access Needed",
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "Grant access to scan QR codes live",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                      onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                      shape = RoundedCornerShape(9999.dp),
                      modifier = Modifier.height(34.dp),
                    ) {
                      Text("Enable Camera", style = MaterialTheme.typography.labelMedium)
                    }
                  }
                }

                // Canvas for Viewfinder Corner Brackets and Animated Laser
                Canvas(modifier = Modifier.fillMaxSize()) {
                  val strokeW = 4.dp.toPx()
                  val cornerLen = 28.dp.toPx()
                  val pad = 12.dp.toPx()

                  // Top-Left corner
                  drawLine(primaryColor, Offset(pad, pad), Offset(pad + cornerLen, pad), strokeW)
                  drawLine(primaryColor, Offset(pad, pad), Offset(pad, pad + cornerLen), strokeW)

                  // Top-Right corner
                  drawLine(primaryColor, Offset(size.width - pad, pad), Offset(size.width - pad - cornerLen, pad), strokeW)
                  drawLine(primaryColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + cornerLen), strokeW)

                  // Bottom-Left corner
                  drawLine(primaryColor, Offset(pad, size.height - pad), Offset(pad + cornerLen, size.height - pad), strokeW)
                  drawLine(primaryColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - cornerLen), strokeW)

                  // Bottom-Right corner
                  drawLine(primaryColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - cornerLen, size.height - pad), strokeW)
                  drawLine(primaryColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - cornerLen), strokeW)

                  // Animated Laser scan line
                  val laserY = pad + (size.height - (pad * 2)) * scanOffset
                  drawLine(
                    color = primaryColor,
                    start = Offset(pad + 8.dp.toPx(), laserY),
                    end = Offset(size.width - pad - 8.dp.toPx(), laserY),
                    strokeWidth = 3.dp.toPx(),
                  )
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              Text(
                text = "Point camera at the QR code displayed on the Target device or pick an image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              )

              Spacer(modifier = Modifier.height(14.dp))

              // Torch, Album & Paste QR Utility Buttons
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
              ) {
                OutlinedButton(
                  onClick = { isTorchOn = !isTorchOn },
                  shape = RoundedCornerShape(9999.dp),
                  colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isTorchOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (isTorchOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                  ),
                  modifier = Modifier.height(38.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.FlashlightOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isTorchOn) "Torch On" else "Torch",
                    style = MaterialTheme.typography.labelMedium,
                  )
                }

                OutlinedButton(
                  onClick = {
                    photoPickerLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                  },
                  shape = RoundedCornerShape(9999.dp),
                  colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                  ),
                  modifier = Modifier.height(38.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Album",
                    style = MaterialTheme.typography.labelMedium,
                  )
                }

                OutlinedButton(
                  onClick = { showPasteQrDialog = true },
                  shape = RoundedCornerShape(9999.dp),
                  colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                  ),
                  modifier = Modifier.height(38.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Paste",
                    style = MaterialTheme.typography.labelMedium,
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Direct Test / Emulator quick scan helper
              OutlinedButton(
                onClick = {
                  val localIp = LocalDeviceManager.getLocalIpAddress(context)
                  val pin = LocalDeviceManager.generatePairingPin()
                  val testPayload = QrCodeUtil.buildPairingUri(
                    ipAddress = localIp,
                    port = TwinProtocol.CONTROL_PORT,
                    pin = pin,
                    deviceName = LocalDeviceManager.getDeviceName(),
                    deviceModel = LocalDeviceManager.getDeviceModel(),
                  )
                  val ok = viewModel.pairFromQrString(testPayload)
                  if (ok) {
                    Toast.makeText(context, "Pairing with local target ($localIp)...", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                  }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                  contentColor = MaterialTheme.colorScheme.secondary,
                ),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(
                  imageVector = Icons.Default.PlayArrow,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Pair with Local Target (Test / Emulator)",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
              }
            }
          }

          // 5. 6-Digit Numeric Pairing Code (PIN)
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Pin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "6-Digit Pairing PIN",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Text(
                  text = "Direct fallback",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.outline,
                )
              }

              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Or enter 6-digit numeric pairing code shown on machine display:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              Spacer(modifier = Modifier.height(14.dp))

              // 6 Digit Boxes
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
              ) {
                pinDigits.forEachIndexed { _, digit ->
                  val isEntered = digit.isNotEmpty()
                  Box(
                    modifier = Modifier
                      .size(46.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(if (isEntered) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer)
                      .border(
                        width = if (isEntered) 2.dp else 1.dp,
                        color = if (isEntered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                      ),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = digit.ifEmpty { "•" },
                      style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                      ),
                      color = if (isEntered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              // Real On-Screen Numeric Keypad
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                val rows = listOf(
                  listOf("1", "2", "3"),
                  listOf("4", "5", "6"),
                  listOf("7", "8", "9"),
                  listOf("C", "0", "⌫"),
                )
                rows.forEach { rowKeys ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    rowKeys.forEach { key ->
                      Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                          1.dp,
                          MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier
                          .weight(1f)
                          .height(42.dp)
                          .clickable {
                            when (key) {
                              "C" -> viewModel.clearPin()
                              "⌫" -> {
                                val lastIdx = pinDigits.indexOfLast { it.isNotEmpty() }
                                if (lastIdx != -1) viewModel.setPinDigit(lastIdx, "")
                              }
                              else -> {
                                val firstEmpty = pinDigits.indexOfFirst { it.isEmpty() }
                                if (firstEmpty != -1) viewModel.setPinDigit(firstEmpty, key)
                              }
                            }
                          },
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Text(
                            text = key,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (key == "C") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

          2 -> {
            BluetoothConnectTab(
              nearbyDevices = nearbyDevices,
              onSilentConnect = { device ->
                viewModel.connectSilently(device)
                Toast.makeText(context, "Bluetooth connection established with ${device.name}!", Toast.LENGTH_SHORT).show()
                onNavigateBack()
              },
              onRequestAccess = { device ->
                viewModel.pairWithDevice(device)
                onNavigateBack()
              },
              onQuickConnect = {
                val localIp = LocalDeviceManager.getLocalIpAddress(context)
                viewModel.connectOverInternet(localIp, TwinProtocol.CONTROL_PORT, "")
                Toast.makeText(context, "Connecting to target via Bluetooth proximity...", Toast.LENGTH_SHORT).show()
                onNavigateBack()
              },
            )
          }

          3 -> {
            InternetConnectTab(
              hostInput = internetHostInput,
              onHostChange = { internetHostInput = it },
              portInput = internetPortInput,
              onPortChange = { internetPortInput = it },
              pinInput = internetPinInput,
              onPinChange = { internetPinInput = it },
              onConnect = { host, port, pin ->
                if (host.isNotBlank()) {
                  viewModel.connectOverInternet(host, port, pin)
                  Toast.makeText(context, "Connecting over internet to $host:$port...", Toast.LENGTH_SHORT).show()
                  onNavigateBack()
                } else {
                  Toast.makeText(context, "Please enter target IP address or hostname", Toast.LENGTH_SHORT).show()
                }
              },
            )
          }
        }

        // 6. Nearby Discoverable Devices Section (mDNS)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.secondary)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Nearby Discoverable Devices",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            TextButton(onClick = { showDirectIpDialog = true }) {
              Icon(
                imageVector = Icons.Default.Lan,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Direct IP",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              )
            }
          }

          if (nearbyDevices.isEmpty()) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(
                  imageVector = Icons.Default.Sensors,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Scanning Local Subnet via mDNS / UDP...",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Ensure Target Mode is active on the other device on this Wi-Fi network.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                  onClick = { showDirectIpDialog = true },
                  shape = RoundedCornerShape(9999.dp),
                ) {
                  Icon(imageVector = Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Enter Target IP Address")
                }
              }
            }
          } else {
            nearbyDevices.forEach { device ->
              NearbyDeviceItem(
                device = device,
                onSilentConnect = {
                  viewModel.connectSilently(device)
                  Toast.makeText(context, "Silent connection established with ${device.name}!", Toast.LENGTH_SHORT).show()
                  onNavigateBack()
                },
                onRequestAccess = {
                  viewModel.pairWithDevice(device)
                  onNavigateBack()
                },
              )
            }
          }
        }
      }
    }

    // 7. Sticky Bottom Confirmation Action Dock
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      tonalElevation = 8.dp,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val hasDevices = nearbyDevices.isNotEmpty()
        Button(
          onClick = {
            if (hasDevices) {
              viewModel.pairWithDevice(nearbyDevices.first())
              onNavigateBack()
            } else {
              showDirectIpDialog = true
            }
          },
          shape = RoundedCornerShape(9999.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        ) {
          Icon(
            imageVector = if (hasDevices) Icons.Default.Smartphone else Icons.Default.Lan,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (hasDevices) "Connect to ${nearbyDevices.first().name}" else "Connect via Direct IP",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          )
        }

        OutlinedButton(
          onClick = onSwitchToTargetMode,
          shape = RoundedCornerShape(9999.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Sensors,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Switch to Target Mode",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          )
        }
      }
    }

    // Direct IP Connection Dialog
    if (showDirectIpDialog) {
      AlertDialog(
        onDismissRequest = { showDirectIpDialog = false },
        title = { Text("Connect to Target by IP") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Enter the local Wi-Fi IP address shown on the Target device (e.g., 192.168.1.50):",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = targetIpInput,
              onValueChange = { targetIpInput = it },
              label = { Text("Target IP Address") },
              placeholder = { Text("192.168.1.x") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = targetPortInput,
              onValueChange = { targetPortInput = it },
              label = { Text("Control Port") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (targetIpInput.isNotBlank()) {
                val dev = DeviceInfo(
                  id = "direct_${targetIpInput.trim()}",
                  name = "Target (${targetIpInput.trim()})",
                  model = "Direct Wi-Fi Target",
                  ipAddress = targetIpInput.trim(),
                  port = targetPortInput.toIntOrNull() ?: 8989,
                  isAuthorized = true,
                  isConnected = true,
                )
                viewModel.pairWithDevice(dev)
                showDirectIpDialog = false
                onNavigateBack()
              }
            },
            enabled = targetIpInput.isNotBlank(),
          ) {
            Text("Connect")
          }
        },
        dismissButton = {
          TextButton(onClick = { showDirectIpDialog = false }) {
            Text("Cancel")
          }
        },
      )
    }

    // Paste QR Code / URI Dialog
    if (showPasteQrDialog) {
      AlertDialog(
        onDismissRequest = { showPasteQrDialog = false },
        title = { Text("Paste Pairing Code / Link") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Paste the 'twincontrol://pair?...' URI, pairing payload, or target address to pair:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = pasteQrInput,
              onValueChange = { pasteQrInput = it },
              label = { Text("Pairing URI or Data") },
              placeholder = { Text("twincontrol://pair?ip=192.168.1.5&pin=123456") },
              singleLine = false,
              maxLines = 4,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (pasteQrInput.isNotBlank()) {
                val ok = viewModel.pairFromQrString(pasteQrInput.trim())
                if (ok) {
                  Toast.makeText(context, "Pairing payload accepted! Connecting...", Toast.LENGTH_SHORT).show()
                  showPasteQrDialog = false
                  onNavigateBack()
                } else {
                  Toast.makeText(context, "Invalid pairing payload. Check format and try again.", Toast.LENGTH_LONG).show()
                }
              }
            },
            enabled = pasteQrInput.isNotBlank(),
          ) {
            Text("Connect")
          }
        },
        dismissButton = {
          TextButton(onClick = { showPasteQrDialog = false }) {
            Text("Cancel")
          }
        },
      )
    }
  }
}

@Composable
private fun NearbyDeviceItem(
  device: DeviceInfo,
  onSilentConnect: () -> Unit,
  onRequestAccess: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(16.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
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
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (device.model.contains("Workstation", ignoreCase = true)) Icons.Default.LaptopWindows else Icons.Default.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = device.name,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (device.silentConnectCapable) {
              Text(
                text = "⚡ SILENT",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = StreamConnectedGreen,
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(StreamConnectedGreen.copy(alpha = 0.15f))
                  .padding(horizontal = 5.dp, vertical = 2.dp),
              )
            } else {
              Text(
                text = device.locationTag,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                  .padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = device.ipAddress,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "•",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
              imageVector = Icons.Default.Wifi,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "${device.signalDbm} dBm",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (device.silentConnectCapable) {
          Button(
            onClick = onSilentConnect,
            shape = RoundedCornerShape(9999.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = StreamConnectedGreen,
              contentColor = Color.Black,
            ),
            modifier = Modifier.height(34.dp),
          ) {
            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Connect",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        OutlinedButton(
          onClick = onRequestAccess,
          shape = RoundedCornerShape(9999.dp),
          modifier = Modifier.height(34.dp),
        ) {
          Text(
            text = if (device.silentConnectCapable) "PIN" else "Pair",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          )
        }
      }
    }
  }
}
