package com.example.ui.controller

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CommandType
import com.example.data.model.GestureMode
import com.example.ui.theme.ActivePillBlue
import com.example.ui.theme.ActivePillBlueText
import com.example.ui.theme.HardwareBezelBorder
import com.example.ui.theme.HardwareSpeakerSlit
import com.example.ui.theme.LightPrimary
import com.example.ui.theme.RemoteViewportDark
import com.example.ui.theme.StreamConnectedBg
import com.example.ui.theme.StreamConnectedGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RemoteScreenView(
  viewModel: ControllerViewModel,
  onDisconnectClicked: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeDevice by viewModel.activeDevice.collectAsState()
  val telemetry by viewModel.telemetry.collectAsState()
  val gestureMode by viewModel.gestureMode.collectAsState()
  val lastTouch by viewModel.lastTouchCoordinate.collectAsState()
  val remoteScreenBitmap by viewModel.remoteScreenBitmap.collectAsState()

  var showTextInputDialog by remember { mutableStateOf(false) }
  var textInputContent by remember { mutableStateOf("") }

  val infiniteTransition = rememberInfiniteTransition(label = "pip")
  val rippleScale by infiniteTransition.animateFloat(
    initialValue = 0.8f,
    targetValue = 2.2f,
    animationSpec = infiniteRepeatable(
      animation = tween(1600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "rippleScale",
  )
  val rippleAlpha by infiniteTransition.animateFloat(
    initialValue = 0.8f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(1600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "rippleAlpha",
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(bottom = 100.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // 1. Live Session Top Bar (Professional Polish Header)
    Surface(
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier
        .fillMaxWidth()
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .height(64.dp)
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // Leading Device Avatar & Active Telemetry Subtitle
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
          ) {
            val deviceInitial = (activeDevice?.name?.split(" ")?.mapNotNull { it.firstOrNull()?.toString() }?.take(2)?.joinToString("")
              ?: "RD").uppercase()
            Text(
              text = deviceInitial,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column {
            Text(
              text = activeDevice?.name ?: "Remote Target",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(top = 1.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(StreamConnectedGreen)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "ACTIVE • ${telemetry.latencyMs}MS",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  letterSpacing = 0.8.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Trailing Action Cluster
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          // HD Quality Chip
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant,
            ),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.HighQuality,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "HD",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          IconButton(
            onClick = { /* Refresh stream */ },
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }

          IconButton(
            onClick = { /* Fullscreen toggle */ },
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Fullscreen,
              contentDescription = "Fullscreen",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }

          // Disconnect Button
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(9999.dp),
            modifier = Modifier
              .clickable(onClick = onDisconnectClicked)
              .testTag("remote_disconnect_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Disconnect",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "End",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Realistic Android Phone Hardware Frame Container (Professional Polish)
    Box(
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .widthIn(max = 380.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(40.dp))
        .background(RemoteViewportDark)
        .border(
          width = 6.dp,
          color = HardwareBezelBorder,
          shape = RoundedCornerShape(40.dp),
        ),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(9f / 18.8f)
          .clip(RoundedCornerShape(34.dp))
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(Color(0xFF121212), Color(0xFF1C1C1E), Color(0xFF242424))
            )
          ),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        // Hardware Speaker Grill & Top Status Bar
        Column(modifier = Modifier.fillMaxWidth()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier = Modifier
                .width(64.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(HardwareSpeakerSlit)
            )
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
            Text(
              text = currentTime,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
              color = Color.White.copy(alpha = 0.8f),
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              // Floating Stream Diagnostic Tag
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(Color.Black.copy(alpha = 0.5f))
                  .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                  .padding(horizontal = 5.dp, vertical = 1.dp)
              ) {
                Text(
                  text = "${telemetry.fps}fps / ${telemetry.resolutionWidth}×${telemetry.resolutionHeight}",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                  ),
                  color = Color.White.copy(alpha = 0.9f),
                )
              }
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "Wi-Fi",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.8f),
              )
            }
          }
        }

        // Live Interactive Target Screen Canvas
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(gestureMode) {
              if (gestureMode == GestureMode.SWIPE || gestureMode == GestureMode.SCROLL) {
                var startX = 0f
                var startY = 0f
                detectDragGestures(
                  onDragStart = { offset ->
                    startX = (offset.x / size.width).coerceIn(0f, 1f)
                    startY = (offset.y / size.height).coerceIn(0f, 1f)
                  },
                  onDrag = { change, _ ->
                    change.consume()
                    val endX = (change.position.x / size.width).coerceIn(0f, 1f)
                    val endY = (change.position.y / size.height).coerceIn(0f, 1f)
                    viewModel.onScreenSwiped(startX, startY, endX, endY, 200L)
                    startX = endX
                    startY = endY
                  }
                )
              } else {
                detectTapGestures(
                  onTap = { offset ->
                    val normX = (offset.x / size.width).coerceIn(0f, 1f)
                    val normY = (offset.y / size.height).coerceIn(0f, 1f)
                    viewModel.onScreenTouched(normX, normY)
                  },
                  onLongPress = { offset ->
                    val normX = (offset.x / size.width).coerceIn(0f, 1f)
                    val normY = (offset.y / size.height).coerceIn(0f, 1f)
                    viewModel.setGestureMode(GestureMode.LONG_PRESS)
                    viewModel.onScreenTouched(normX, normY)
                  }
                )
              }
            },
        ) {
          if (remoteScreenBitmap != null) {
            Image(
              bitmap = remoteScreenBitmap!!.asImageBitmap(),
              contentDescription = "Live Remote Screen",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Fit,
            )
          } else {
            // Live Stream Standby / Awaiting Screen State
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Surface(
                color = Color(0xFF1E222A).copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  Color.White.copy(alpha = 0.15f),
                ),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Box(
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(if (activeDevice != null) StreamConnectedGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = if (activeDevice != null) Icons.Default.ScreenShare else Icons.Default.Sensors,
                      contentDescription = null,
                      tint = if (activeDevice != null) StreamConnectedGreen else Color.White.copy(alpha = 0.6f),
                      modifier = Modifier.size(24.dp),
                    )
                  }
                  Spacer(modifier = Modifier.height(12.dp))
                  Text(
                    text = if (activeDevice != null) "Control Channel Connected" else "No Target Connected",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = if (activeDevice != null)
                      "Target: ${activeDevice?.name}\nIP: ${activeDevice?.ipAddress}:8990\n\nAwaiting video frame broadcast. Tap 'Start Screen Mirroring' on the Target device to cast."
                    else
                      "Open 'Pair Devices' above to discover and connect to a Target device on your local Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }

          // Simulated Remote Touch Ripple & Precision Pointer Tag
          if (lastTouch != null) {
            Box(
              modifier = Modifier
                .align(Alignment.Center)
                .testTag("remote_touch_ripple"),
            ) {
              // Concentric ripple
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .scale(rippleScale)
                  .alpha(rippleAlpha)
                  .clip(CircleShape)
                  .background(LightPrimary.copy(alpha = 0.4f))
                  .border(1.dp, LightPrimary, CircleShape)
              )
              // Center cursor
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .align(Alignment.Center)
                  .clip(CircleShape)
                  .background(LightPrimary)
                  .border(1.5.dp, Color.White, CircleShape)
              )
              // Coordinate bubble
              Text(
                text = "TOUCH (X:${lastTouch!!.first.toInt()}, Y:${lastTouch!!.second.toInt()})",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 8.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                ),
                color = LightPrimary,
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .clip(RoundedCornerShape(4.dp))
                  .background(Color.Black.copy(alpha = 0.85f))
                  .padding(horizontal = 4.dp, vertical = 2.dp),
              )
            }
          }
        }

        // Bottom Gesture Indicator Line
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier
              .width(96.dp)
              .height(3.dp)
              .clip(RoundedCornerShape(9999.dp))
              .background(Color.White.copy(alpha = 0.35f))
          )
        }

        // 3. Virtual Navigation Bar (Back, Home, Recents)
        Surface(
          color = Color(0xFF0F1113),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Back (Triangle)
            IconButton(
              onClick = { viewModel.sendNavigationCommand(CommandType.BACK) },
              modifier = Modifier.testTag("remote_nav_back"),
            ) {
              Icon(
                imageVector = Icons.Default.ArrowBackIosNew,
                contentDescription = "Remote Back",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp),
              )
            }

            // Home (Circle)
            IconButton(
              onClick = { viewModel.sendNavigationCommand(CommandType.HOME) },
              modifier = Modifier.testTag("remote_nav_home"),
            ) {
              Box(
                modifier = Modifier
                  .size(14.dp)
                  .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
              )
            }

            // Recents (Square)
            IconButton(
              onClick = { viewModel.sendNavigationCommand(CommandType.RECENTS) },
              modifier = Modifier.testTag("remote_nav_recents"),
            ) {
              Box(
                modifier = Modifier
                  .size(12.dp)
                  .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Floating Gesture & Remote Utility Toolbar (Professional Polish)
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(20.dp),
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
      ),
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .widthIn(max = 420.dp)
        .fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        // Mode selector container
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(4.dp)
              .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            GestureModeChip(
              label = "Tap",
              icon = Icons.Default.TouchApp,
              selected = gestureMode == GestureMode.TAP,
              onClick = { viewModel.setGestureMode(GestureMode.TAP) },
            )

            GestureModeChip(
              label = "Long Press",
              icon = Icons.Default.PanTool,
              selected = gestureMode == GestureMode.LONG_PRESS,
              onClick = { viewModel.setGestureMode(GestureMode.LONG_PRESS) },
            )

            GestureModeChip(
              label = "Swipe",
              icon = Icons.Default.SwipeVertical,
              selected = gestureMode == GestureMode.SWIPE,
              onClick = { viewModel.setGestureMode(GestureMode.SWIPE) },
            )

            GestureModeChip(
              label = "Scroll",
              icon = Icons.Default.UnfoldMore,
              selected = gestureMode == GestureMode.SCROLL,
              onClick = { viewModel.setGestureMode(GestureMode.SCROLL) },
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Remote Utilities (Keyboard, Sync, Volume, Power)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Text Input / Soft Keyboard
            IconButton(
              onClick = { showTextInputDialog = true },
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            ) {
              Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Send Text",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
              )
            }

            // Notifications Pull-down
            IconButton(
              onClick = { viewModel.sendGlobalAction("NOTIFICATIONS") },
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            ) {
              Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Show Notifications",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
              )
            }

            // Quick Settings / Lock
            IconButton(
              onClick = { viewModel.sendGlobalAction("LOCK") },
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock Device",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          // Volume cluster
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant,
            ),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { viewModel.sendVolume("DOWN") },
                modifier = Modifier.size(38.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.VolumeDown,
                  contentDescription = "Volume Down",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
              }
              IconButton(
                onClick = { viewModel.sendVolume("UP") },
                modifier = Modifier.size(38.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.VolumeUp,
                  contentDescription = "Volume Up",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }

          // Remote Power Menu
          IconButton(
            onClick = { viewModel.sendGlobalAction("POWER") },
            modifier = Modifier
              .size(38.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceContainer)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
          ) {
            Icon(
              imageVector = Icons.Default.PowerSettingsNew,
              contentDescription = "Power Menu",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 5. Professional Polish Footer Telemetry Strip
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(10.dp),
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
      ),
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .widthIn(max = 420.dp)
        .fillMaxWidth()
        .height(36.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Stream: ${telemetry.codec} (${telemetry.resolutionWidth}×${telemetry.resolutionHeight})",
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Bitrate: ${String.format(Locale.US, "%.1f", telemetry.bitrateMbps)} MB/s",
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(if (activeDevice != null) StreamConnectedGreen else LightPrimary)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (activeDevice != null) "LIVE (${telemetry.latencyMs}ms)" else "STANDBY",
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
            ),
            color = if (activeDevice != null) StreamConnectedGreen else LightPrimary,
          )
        }
      }
    }
  }

  // Soft Keyboard Text Input Dialog
  if (showTextInputDialog) {
    AlertDialog(
      onDismissRequest = { showTextInputDialog = false },
      title = { Text("Send Text to Target Device") },
      text = {
        Column {
          Text(
            text = "The Target's RemoteAccessibilityService will insert this text into the active focused field.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = textInputContent,
            onValueChange = { textInputContent = it },
            placeholder = { Text("Enter text to type...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.sendTextInput(textInputContent)
            textInputContent = ""
            showTextInputDialog = false
          },
        ) {
          Text("Send Text")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTextInputDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun GestureModeChip(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    color = if (selected) ActivePillBlue else MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(12.dp),
    border = if (!selected) androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant,
    ) else null,
    modifier = Modifier.clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) ActivePillBlueText else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(15.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        color = if (selected) ActivePillBlueText else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AppIconItem(
  name: String,
  icon: ImageVector,
  tint: Color,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.width(50.dp),
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = name,
        tint = tint,
        modifier = Modifier.size(22.dp),
      )
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = name,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
      color = Color.White.copy(alpha = 0.9f),
    )
  }
}
