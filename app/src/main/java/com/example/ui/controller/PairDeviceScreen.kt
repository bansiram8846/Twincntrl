package com.example.ui.controller

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
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Smartphone
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppMode
import com.example.data.model.DeviceInfo
import com.example.ui.theme.StreamConnectedGreen

@Composable
fun PairDeviceScreen(
  viewModel: ControllerViewModel,
  onNavigateBack: () -> Unit,
  onSwitchToTargetMode: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0 = QR, 1 = PIN
  var isTorchOn by remember { mutableStateOf(false) }

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

        // 3. Segmented Switcher [QR Code] vs [Pairing Code (PIN)]
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(9999.dp),
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
            Button(
              onClick = { selectedTab = 0 },
              shape = RoundedCornerShape(9999.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              elevation = null,
              modifier = Modifier.weight(1f).height(40.dp),
            ) {
              Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "QR Code",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              )
            }

            Button(
              onClick = { selectedTab = 1 },
              shape = RoundedCornerShape(9999.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              elevation = null,
              modifier = Modifier.weight(1f).height(40.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Dialpad,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Pairing Code (PIN)",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              )
            }
          }
        }

        // 4. QR Code Viewfinder Card
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
            // Viewfinder Target Box
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(
              modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
              contentAlignment = Alignment.Center,
            ) {
              // Canvas for Viewfinder Corner Brackets and Laser
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

              // QR Code Graphic Matrix Representation
              Box(
                modifier = Modifier
                  .size(150.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
              ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                  val dotSize = 10.dp.toPx()
                  val cornerFinderSize = 34.dp.toPx()
                  val cornerInset = 12.dp.toPx()

                  // 3 Corner Finders
                  drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(cornerInset, cornerInset),
                    size = Size(cornerFinderSize, cornerFinderSize),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx()),
                  )
                  drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(size.width - cornerInset - cornerFinderSize, cornerInset),
                    size = Size(cornerFinderSize, cornerFinderSize),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx()),
                  )
                  drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(cornerInset, size.height - cornerInset - cornerFinderSize),
                    size = Size(cornerFinderSize, cornerFinderSize),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx()),
                  )

                  // Center Dots
                  for (i in 0..4) {
                    for (j in 0..4) {
                      if ((i + j) % 2 == 0) {
                        drawCircle(
                          color = Color.White.copy(alpha = 0.75f),
                          radius = 3.5.dp.toPx(),
                          center = Offset(
                            size.width * 0.28f + i * dotSize * 0.9f,
                            size.height * 0.28f + j * dotSize * 0.9f,
                          ),
                        )
                      }
                    }
                  }
                }

                // Center Phone Symbol
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Scan QR code displayed on the Target device under Settings > Pair Remote",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 24.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Torch & Album Utility Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedButton(
                onClick = { isTorchOn = !isTorchOn },
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = if (isTorchOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                  contentColor = if (isTorchOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier.height(38.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.FlashlightOn,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (isTorchOn) "Torch On" else "Torch Off",
                  style = MaterialTheme.typography.labelMedium,
                )
              }

              OutlinedButton(
                onClick = { /* Pick image from gallery */ },
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainer,
                  contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier.height(38.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.PhotoLibrary,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Album",
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }
        }

        // 5. 6-Digit Numeric Pairing Code (PIN) Fallback
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
              pinDigits.forEachIndexed { index, digit ->
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
                    )
                    .clickable {
                      // Demo interactive PIN entry
                      if (digit.isEmpty()) {
                        viewModel.setPinDigit(index, "${(1..9).random()}")
                      }
                    },
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

            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Clear & Re-enter PIN",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { viewModel.clearPin() }
                .padding(4.dp),
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
            Text(
              text = "Local Subnet (mDNS)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.outline,
            )
          }

          nearbyDevices.forEach { device ->
            NearbyDeviceItem(
              device = device,
              onRequestAccess = { viewModel.pairWithDevice(device) },
            )
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
        Button(
          onClick = {
            // Authorize and navigate to remote
            viewModel.pairWithDevice(nearbyDevices.first())
            onNavigateBack()
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
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Scan & Authorize",
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
  }
}

@Composable
private fun NearbyDeviceItem(
  device: DeviceInfo,
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

      Button(
        onClick = onRequestAccess,
        shape = RoundedCornerShape(9999.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.height(34.dp),
      ) {
        Text(
          text = if (device.model.contains("Workstation")) "Pair" else "Request Access",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
      }
    }
  }
}
