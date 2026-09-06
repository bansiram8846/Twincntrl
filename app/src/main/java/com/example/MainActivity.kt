package com.example

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

class MainActivity : ComponentActivity() {
  companion object {
    var currentActivity: MainActivity? = null
      private set
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    currentActivity = this
    enableEdgeToEdge()
    setContent {
      TwinControlTheme {
        TwinControlApp()
      }
    }
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
