package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.TwinControlApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TwinControlTheme
import kotlinx.coroutines.flow.MutableStateFlow

data class TargetLinkPayload(
  val controllerIp: String,
  val controllerName: String,
)

class MainActivity : ComponentActivity() {
  companion object {
    var currentActivity: MainActivity? = null
      private set
    val pendingTargetLink = MutableStateFlow<TargetLinkPayload?>(null)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    currentActivity = this
    handleIntent(intent)
    enableEdgeToEdge()
    setContent {
      TwinControlTheme {
        TwinControlApp()
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    val data = intent?.data ?: return
    try {
      val scheme = data.scheme
      val host = data.host
      val path = data.path ?: ""

      var controllerIp = ""
      var controllerName = "Remote Controller"

      if (scheme == "twincontrol" && host == "target") {
        controllerIp = data.getQueryParameter("controllerIp") ?: ""
        controllerName = data.getQueryParameter("controllerName") ?: "Remote Controller"
      } else if (scheme == "http" || scheme == "https") {
        if (path.contains("join") || path.contains("target")) {
          controllerIp = data.getQueryParameter("controllerIp") ?: (host ?: "")
          controllerName = data.getQueryParameter("controllerName") ?: "Remote Controller"
        }
      }

      if (controllerIp.isNotBlank()) {
        pendingTargetLink.value = TargetLinkPayload(controllerIp, controllerName)
      }
    } catch (_: Exception) {}
  }

  override fun onResume() {
    super.onResume()
    currentActivity = this
  }

  override fun onDestroy() {
    super.onDestroy()
    if (currentActivity == this) {
      currentActivity = null
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  TwinControlTheme {
    TwinControlApp()
  }
}
