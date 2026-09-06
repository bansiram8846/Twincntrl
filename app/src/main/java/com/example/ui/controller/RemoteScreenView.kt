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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import com.example.data.model.DeviceInfo
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
            TargetModeDeviceScreenView(
              device = activeDevice,
              onTouch = { x, y -> viewModel.onScreenTouched(x, y) },
            )
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

        // 3. High-Contrast Tactile Navigation Bar (Back, Home, Recents)
        Surface(
          color = Color(0xFF0F1113),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Back (◀)
            Surface(
              onClick = { viewModel.sendNavigationCommand(CommandType.BACK) },
              shape = RoundedCornerShape(10.dp),
              color = Color.White.copy(alpha = 0.08f),
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .height(40.dp)
                .testTag("remote_nav_back"),
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.ArrowBackIosNew,
                  contentDescription = "Remote Back",
                  tint = Color.White.copy(alpha = 0.9f),
                  modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Back",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White.copy(alpha = 0.9f),
                )
              }
            }

            // Home (●)
            Surface(
              onClick = { viewModel.sendNavigationCommand(CommandType.HOME) },
              shape = RoundedCornerShape(10.dp),
              color = Color.White.copy(alpha = 0.08f),
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .height(40.dp)
                .testTag("remote_nav_home"),
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier = Modifier
                    .size(13.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Home",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White.copy(alpha = 0.9f),
                )
              }
            }

            // Recents (◼)
            Surface(
              onClick = { viewModel.sendNavigationCommand(CommandType.RECENTS) },
              shape = RoundedCornerShape(10.dp),
              color = Color.White.copy(alpha = 0.08f),
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .height(40.dp)
                .testTag("remote_nav_recents"),
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier = Modifier
                    .size(11.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Recents",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White.copy(alpha = 0.9f),
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Enhanced Visual Remote Control Menu & Gesture Center
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
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Section: Gesture Mode Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "INPUT GESTURE MODE",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
          )

          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
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
        }

        // Section: Visual Remote Menu Action Tiles
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "REMOTE ACTIONS MENU",
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
              ),
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Instant P2P execution",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          // Row 1: Text, Notifications, Quick Settings, Lock
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            VisualRemoteActionTile(
              label = "Keyboard",
              sublabel = "Send Text",
              icon = Icons.Default.Keyboard,
              accentColor = Color(0xFF4361EE),
              onClick = { showTextInputDialog = true },
              modifier = Modifier.weight(1f),
            )
            VisualRemoteActionTile(
              label = "Notify",
              sublabel = "Pull Down",
              icon = Icons.Default.Notifications,
              accentColor = Color(0xFFF72585),
              onClick = { viewModel.sendGlobalAction("NOTIFICATIONS") },
              modifier = Modifier.weight(1f),
            )
            VisualRemoteActionTile(
              label = "Settings",
              sublabel = "Quick Tiles",
              icon = Icons.Default.Tune,
              accentColor = Color(0xFF4CC9F0),
              onClick = { viewModel.sendGlobalAction("QUICK_SETTINGS") },
              modifier = Modifier.weight(1f),
            )
            VisualRemoteActionTile(
              label = "Lock",
              sublabel = "Sleep Screen",
              icon = Icons.Default.Lock,
              accentColor = Color(0xFFFFB703),
              onClick = { viewModel.sendGlobalAction("LOCK") },
              modifier = Modifier.weight(1f),
            )
          }

          // Row 2: Volume Up, Volume Down, Mute, Power Menu
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            VisualRemoteActionTile(
              label = "Vol +",
              sublabel = "Louder",
              icon = Icons.Default.VolumeUp,
              accentColor = Color(0xFF2EC4B6),
              onClick = { viewModel.sendVolume("UP") },
              modifier = Modifier.weight(1f),
            )
            VisualRemoteActionTile(
              label = "Vol -",
              sublabel = "Softer",
              icon = Icons.Default.VolumeDown,
              accentColor = Color(0xFF2EC4B6),
              onClick = { viewModel.sendVolume("DOWN") },
              modifier = Modifier.weight(1f),
            )
            VisualRemoteActionTile(
              label = "Mute",
              sublabel = "Silent",
              icon = Icons.Default.VolumeOff,
              accentColor = Color(0xFFE63946),
              onClick = { viewModel.sendVolume("MUTE") },
              modifier = Modifier.weight(1f),
            )
            VisualRemoteActionTile(
              label = "Power",
              sublabel = "Power Menu",
              icon = Icons.Default.PowerSettingsNew,
              accentColor = Color(0xFFD90429),
              onClick = { viewModel.sendGlobalAction("POWER") },
              modifier = Modifier.weight(1f),
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
private fun VisualRemoteActionTile(
  label: String,
  sublabel: String? = null,
  icon: ImageVector,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(14.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    ),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = accentColor,
          modifier = Modifier.size(18.dp),
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
      if (sublabel != null) {
        Text(
          text = sublabel,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun TargetModeDeviceScreenView(
  device: DeviceInfo?,
  onTouch: (Float, Float) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFF0D1B2A),
            Color(0xFF1B263B),
            Color(0xFF101924),
          )
        )
      )
      .padding(10.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // 1. Top Target System Status Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(StreamConnectedGreen)
        )
        Text(
          text = "TwinControl • Target Mode",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
          ),
          color = StreamConnectedGreen,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
        Text(
          text = "${device?.batteryPercent ?: 95}%",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
          color = Color.White.copy(alpha = 0.9f),
        )
        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = StreamConnectedGreen, modifier = Modifier.size(12.dp))
      }
    }

    // 2. Target Device Identification Card
    Surface(
      color = Color.White.copy(alpha = 0.08f),
      shape = RoundedCornerShape(14.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, StreamConnectedGreen.copy(alpha = 0.4f)),
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onTouch(0.5f, 0.2f) },
    ) {
      Column(
        modifier = Modifier.padding(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(StreamConnectedGreen.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(Icons.Default.Sensors, contentDescription = null, tint = StreamConnectedGreen, modifier = Modifier.size(16.dp))
            }
            Column {
              Text(
                text = device?.name ?: "Remote Target Android",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
              )
              Text(
                text = "${device?.ipAddress ?: "192.168.1.45"}:8989",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 9.sp,
                  fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                color = Color.White.copy(alpha = 0.7f),
              )
            }
          }

          // Active Badge
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(9999.dp))
              .background(StreamConnectedGreen.copy(alpha = 0.2f))
              .border(1.dp, StreamConnectedGreen.copy(alpha = 0.6f), RoundedCornerShape(9999.dp))
              .padding(horizontal = 7.dp, vertical = 2.dp),
          ) {
            Text(
              text = "Connected",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
              ),
              color = StreamConnectedGreen,
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Passcode Authorization",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White.copy(alpha = 0.75f),
          )
          Text(
            text = "Silent Auto-Paired",
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
            ),
            color = StreamConnectedGreen,
          )
        }
      }
    }

    // 3. System Permissions Audit Section (Target Device Screen)
    Surface(
      color = Color.White.copy(alpha = 0.05f),
      shape = RoundedCornerShape(12.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onTouch(0.5f, 0.45f) },
    ) {
      Column(
        modifier = Modifier.padding(9.dp),
      ) {
        Text(
          text = "System Permissions Audit",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
          ),
          color = Color.White.copy(alpha = 0.9f),
        )
        Spacer(modifier = Modifier.height(6.dp))

        AuditStatusRow(
          label = "Accessibility Service",
          status = "Granted (Active)",
          isGranted = true,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AuditStatusRow(
          label = "MediaProjection",
          status = "Granted (Active)",
          isGranted = true,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AuditStatusRow(
          label = "Local Network Multicast",
          status = "Granted",
          isGranted = true,
        )
      }
    }

    // 4. Remote Live Activity Feed
    Surface(
      color = Color.Black.copy(alpha = 0.4f),
      shape = RoundedCornerShape(10.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onTouch(0.5f, 0.75f) },
    ) {
      Column(
        modifier = Modifier.padding(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Target Activity Telemetry",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 9.sp,
            ),
            color = Color.White.copy(alpha = 0.8f),
          )
          Text(
            text = "Live Stream",
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
            ),
            color = StreamConnectedGreen,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "• Touch & Navigation ready\n• Streaming frames active\n• Controller linked: ${device?.name ?: "Device"}",
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            lineHeight = 13.sp,
          ),
          color = Color.White.copy(alpha = 0.75f),
        )
      }
    }

    // 5. Bottom Touch Interaction Bar
    Surface(
      color = StreamConnectedGreen.copy(alpha = 0.15f),
      shape = RoundedCornerShape(8.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, StreamConnectedGreen.copy(alpha = 0.35f)),
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onTouch(0.5f, 0.95f) },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Default.TouchApp, contentDescription = null, tint = StreamConnectedGreen, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = "Tap or swipe anywhere to control target",
          style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
          ),
          color = StreamConnectedGreen,
        )
      }
    }
  }
}

@Composable
private fun AuditStatusRow(
  label: String,
  status: String,
  isGranted: Boolean,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
      color = Color.White.copy(alpha = 0.8f),
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Box(
        modifier = Modifier
          .size(5.dp)
          .clip(CircleShape)
          .background(if (isGranted) StreamConnectedGreen else Color.Red)
      )
      Text(
        text = status,
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
        ),
        color = if (isGranted) StreamConnectedGreen else Color.Red,
      )
    }
  }
}
