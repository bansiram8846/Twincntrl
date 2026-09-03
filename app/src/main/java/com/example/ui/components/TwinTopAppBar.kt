package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppMode
import com.example.ui.theme.StreamConnectedGreen

@Composable
fun TwinTopAppBar(
  currentMode: AppMode,
  onModeChanged: (AppMode) -> Unit,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
) {
  var showModeDropdown by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Leading Brand Badge: Circle avatar in #0061A4 with white monogram
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "TC",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Status Telemetry Subtitle
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "TwinControl",
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.SemiBold,
            ),
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
              text = (subtitle ?: "ACTIVE • TLS 1.3").uppercase(),
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Mode Switcher Dropdown Pill
        Box {
          Row(
            modifier = Modifier
              .testTag("mode_switcher_button")
              .clip(RoundedCornerShape(9999.dp))
              .background(MaterialTheme.colorScheme.surfaceContainer)
              .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(9999.dp),
              )
              .clickable { showModeDropdown = true }
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                  if (currentMode == AppMode.CONTROLLER) MaterialTheme.colorScheme.primary
                  else StreamConnectedGreen
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (currentMode == AppMode.CONTROLLER) "Controller" else "Target",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
              imageVector = Icons.Default.ArrowDropDown,
              contentDescription = "Change mode",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }

          DropdownMenu(
            expanded = showModeDropdown,
            onDismissRequest = { showModeDropdown = false },
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  "Phone A: Controller Mode",
                  fontWeight = if (currentMode == AppMode.CONTROLLER) FontWeight.Bold else FontWeight.Normal,
                )
              },
              onClick = {
                showModeDropdown = false
                onModeChanged(AppMode.CONTROLLER)
              },
            )
            DropdownMenuItem(
              text = {
                Text(
                  "Phone B: Target Mode (Supervised)",
                  fontWeight = if (currentMode == AppMode.TARGET) FontWeight.Bold else FontWeight.Normal,
                )
              },
              onClick = {
                showModeDropdown = false
                onModeChanged(AppMode.TARGET)
              },
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing Notification Icon Button
        Box {
          IconButton(
            onClick = { /* Notification sheet / status */ },
            modifier = Modifier.size(38.dp).testTag("notifications_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Notifications,
              contentDescription = "Notifications",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
          // Notification badge pip
          Box(
            modifier = Modifier
              .size(6.dp)
              .align(Alignment.TopEnd)
              .padding(top = 8.dp, end = 8.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary)
          )
        }
      }
    }
  }
}
