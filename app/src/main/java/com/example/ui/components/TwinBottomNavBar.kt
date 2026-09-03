package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppMode

enum class TwinNavigationTab {
  HOME,
  DEVICES,
  REMOTE,
  ACTIVITY,
  SETTINGS
}

@Composable
fun TwinBottomNavBar(
  currentTab: TwinNavigationTab,
  onTabSelected: (TwinNavigationTab) -> Unit,
  appMode: AppMode = AppMode.CONTROLLER,
  hasLiveConnection: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TwinNavItem(
        label = if (appMode == AppMode.TARGET) "Status" else "Home",
        selected = currentTab == TwinNavigationTab.HOME,
        activeIcon = if (appMode == AppMode.TARGET) Icons.Filled.ScreenShare else Icons.Filled.Home,
        inactiveIcon = if (appMode == AppMode.TARGET) Icons.Outlined.ScreenShare else Icons.Outlined.Home,
        testTag = "nav_home",
        onClick = { onTabSelected(TwinNavigationTab.HOME) },
      )

      TwinNavItem(
        label = "Devices",
        selected = currentTab == TwinNavigationTab.DEVICES,
        activeIcon = Icons.Filled.Devices,
        inactiveIcon = Icons.Outlined.Devices,
        testTag = "nav_devices",
        onClick = { onTabSelected(TwinNavigationTab.DEVICES) },
      )

      TwinNavItem(
        label = "Remote",
        selected = currentTab == TwinNavigationTab.REMOTE,
        activeIcon = Icons.Filled.SettingsRemote,
        inactiveIcon = Icons.Outlined.SettingsRemote,
        hasDot = hasLiveConnection && currentTab != TwinNavigationTab.REMOTE,
        testTag = "nav_remote",
        onClick = { onTabSelected(TwinNavigationTab.REMOTE) },
      )

      TwinNavItem(
        label = "Activity",
        selected = currentTab == TwinNavigationTab.ACTIVITY,
        activeIcon = Icons.Filled.History,
        inactiveIcon = Icons.Outlined.History,
        testTag = "nav_activity",
        onClick = { onTabSelected(TwinNavigationTab.ACTIVITY) },
      )

      TwinNavItem(
        label = "Settings",
        selected = currentTab == TwinNavigationTab.SETTINGS,
        activeIcon = Icons.Filled.Settings,
        inactiveIcon = Icons.Outlined.Settings,
        testTag = "nav_settings",
        onClick = { onTabSelected(TwinNavigationTab.SETTINGS) },
      )
    }
  }
}

@Composable
private fun TwinNavItem(
  label: String,
  selected: Boolean,
  activeIcon: ImageVector,
  inactiveIcon: ImageVector,
  testTag: String,
  hasDot: Boolean = false,
  onClick: () -> Unit,
) {
  val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
  else androidx.compose.ui.graphics.Color.Transparent
  val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
  else MaterialTheme.colorScheme.onSurfaceVariant

  Box(
    modifier = Modifier
      .testTag(testTag)
      .clip(RoundedCornerShape(16.dp))
      .background(containerColor)
      .clickable(onClick = onClick)
      .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box {
        Icon(
          imageVector = if (selected) activeIcon else inactiveIcon,
          contentDescription = label,
          tint = contentColor,
          modifier = Modifier.size(22.dp),
        )
        if (hasDot) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .align(Alignment.TopEnd)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary)
          )
        }
      }
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 10.sp,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        ),
        color = contentColor,
      )
    }
  }
}
