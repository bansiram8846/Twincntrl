package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AppMode
import com.example.data.model.ConnectionState
import com.example.ui.activity.ActivityScreen
import com.example.ui.components.TwinBottomNavBar
import com.example.ui.components.TwinNavigationTab
import com.example.ui.components.TwinTopAppBar
import com.example.ui.controller.ControllerHomeScreen
import com.example.ui.controller.ControllerViewModel
import com.example.ui.controller.PairDeviceScreen
import com.example.ui.controller.RemoteScreenView
import com.example.ui.devices.DevicesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.target.TargetScreen
import com.example.ui.target.TargetViewModel

@Composable
fun TwinControlApp(
  controllerViewModel: ControllerViewModel = viewModel(),
  targetViewModel: TargetViewModel = viewModel(),
) {
  var appMode by remember { mutableStateOf(AppMode.CONTROLLER) }
  var currentTab by remember { mutableStateOf(TwinNavigationTab.HOME) }
  var isPairingSheetOpen by remember { mutableStateOf(false) }

  val connectionState by controllerViewModel.connectionState.collectAsState()
  val isConnected = connectionState == ConnectionState.CONNECTED

  Scaffold(
    topBar = {
      if (!isPairingSheetOpen && currentTab != TwinNavigationTab.REMOTE) {
        TwinTopAppBar(
          currentMode = appMode,
          onModeChanged = { mode ->
            appMode = mode
            if (mode == AppMode.TARGET) {
              currentTab = TwinNavigationTab.HOME
            }
          },
          subtitle = if (appMode == AppMode.CONTROLLER) "Host #01 · TLS 1.3" else "Broadcasting on Local Subnet",
        )
      }
    },
    bottomBar = {
      if (!isPairingSheetOpen) {
        TwinBottomNavBar(
          currentTab = currentTab,
          onTabSelected = { tab ->
            currentTab = tab
          },
          appMode = appMode,
          hasLiveConnection = isConnected,
        )
      }
    },
    modifier = Modifier.fillMaxSize(),
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (isPairingSheetOpen) {
        PairDeviceScreen(
          viewModel = controllerViewModel,
          onNavigateBack = { isPairingSheetOpen = false },
          onSwitchToTargetMode = {
            isPairingSheetOpen = false
            appMode = AppMode.TARGET
            currentTab = TwinNavigationTab.HOME
          },
        )
      } else {
        AnimatedContent(
          targetState = Pair(appMode, currentTab),
          transitionSpec = { fadeIn() togetherWith fadeOut() },
          label = "mode_tab_transition",
        ) { (mode, tab) ->
          if (mode == AppMode.TARGET) {
            when (tab) {
              TwinNavigationTab.SETTINGS -> SettingsScreen()
              TwinNavigationTab.ACTIVITY -> ActivityScreen(viewModel = controllerViewModel)
              TwinNavigationTab.DEVICES -> DevicesScreen(
                viewModel = controllerViewModel,
                onNavigateToPair = { isPairingSheetOpen = true },
              )
              else -> TargetScreen(
                viewModel = targetViewModel,
                onOpenSettings = { currentTab = TwinNavigationTab.SETTINGS },
              )
            }
          } else {
            when (tab) {
              TwinNavigationTab.HOME -> ControllerHomeScreen(
                viewModel = controllerViewModel,
                onNavigateToRemote = { currentTab = TwinNavigationTab.REMOTE },
                onNavigateToPair = { isPairingSheetOpen = true },
              )
              TwinNavigationTab.DEVICES -> DevicesScreen(
                viewModel = controllerViewModel,
                onNavigateToPair = { isPairingSheetOpen = true },
              )
              TwinNavigationTab.REMOTE -> RemoteScreenView(
                viewModel = controllerViewModel,
                onDisconnectClicked = {
                  controllerViewModel.toggleConnection()
                  currentTab = TwinNavigationTab.HOME
                },
              )
              TwinNavigationTab.ACTIVITY -> ActivityScreen(
                viewModel = controllerViewModel,
              )
              TwinNavigationTab.SETTINGS -> SettingsScreen()
            }
          }
        }
      }
    }
  }
}
