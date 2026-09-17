package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RemoteAccessibilityService : AccessibilityService() {

  companion object {
    private const val TAG = "RemoteAccessibility"
    @Volatile
    var instance: RemoteAccessibilityService? = null
      private set

    val isRunning: Boolean
      get() = instance != null
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    Log.d(TAG, "TwinControl RemoteAccessibilityService connected and ready")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // Accessibility events monitoring for active focus
  }

  override fun onInterrupt() {
    Log.w(TAG, "TwinControl RemoteAccessibilityService interrupted")
  }

  override fun onDestroy() {
    super.onDestroy()
    if (instance == this) {
      instance = null
    }
  }

  fun simulateTap(x: Float, y: Float): Boolean {
    val clampedX = x.coerceAtLeast(1f)
    val clampedY = y.coerceAtLeast(1f)
    val path = Path().apply {
      moveTo(clampedX, clampedY)
      lineTo(clampedX, clampedY + 1f) // Ensure non-empty path for Android GestureDescription
    }
    val stroke = GestureDescription.StrokeDescription(path, 0, 50)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGesture(gesture, object : GestureResultCallback() {
      override fun onCompleted(gestureDescription: GestureDescription?) {
        Log.d(TAG, "simulateTap succeeded at ($clampedX, $clampedY)")
      }
      override fun onCancelled(gestureDescription: GestureDescription?) {
        Log.w(TAG, "simulateTap cancelled by system at ($clampedX, $clampedY)")
      }
    }, null)
  }

  fun simulateLongPress(x: Float, y: Float, durationMs: Long = 800): Boolean {
    val clampedX = x.coerceAtLeast(1f)
    val clampedY = y.coerceAtLeast(1f)
    val path = Path().apply {
      moveTo(clampedX, clampedY)
      lineTo(clampedX, clampedY + 1f) // Ensure non-empty path
    }
    val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(500L))
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGesture(gesture, object : GestureResultCallback() {
      override fun onCompleted(gestureDescription: GestureDescription?) {
        Log.d(TAG, "simulateLongPress succeeded at ($clampedX, $clampedY)")
      }
      override fun onCancelled(gestureDescription: GestureDescription?) {
        Log.w(TAG, "simulateLongPress cancelled at ($clampedX, $clampedY)")
      }
    }, null)
  }

  fun simulateSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
    val clampedStartX = startX.coerceAtLeast(1f)
    val clampedStartY = startY.coerceAtLeast(1f)
    var clampedEndX = endX.coerceAtLeast(1f)
    var clampedEndY = endY.coerceAtLeast(1f)

    if (clampedStartX == clampedEndX && clampedStartY == clampedEndY) {
      clampedEndY += 2f
    }

    val path = Path().apply {
      moveTo(clampedStartX, clampedStartY)
      lineTo(clampedEndX, clampedEndY)
    }
    val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(80L, 2000L))
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGesture(gesture, object : GestureResultCallback() {
      override fun onCompleted(gestureDescription: GestureDescription?) {
        Log.d(TAG, "simulateSwipe succeeded: ($clampedStartX,$clampedStartY) -> ($clampedEndX,$clampedEndY)")
      }
      override fun onCancelled(gestureDescription: GestureDescription?) {
        Log.w(TAG, "simulateSwipe cancelled: ($clampedStartX,$clampedStartY) -> ($clampedEndX,$clampedEndY)")
      }
    }, null)
  }

  fun simulateScroll(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 350): Boolean {
    return simulateSwipe(startX, startY, endX, endY, durationMs)
  }

  fun triggerBack(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_BACK)
  }

  fun triggerHome(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_HOME)
  }

  fun triggerRecents(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_RECENTS)
  }

  fun injectText(text: String): Boolean {
    val rootNode = rootInActiveWindow
    if (rootNode != null) {
      val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        ?: rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        ?: findEditableNode(rootNode)
      if (focusedNode != null) {
        val arguments = Bundle().apply {
          putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val res = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (res) return true
      }
    }
    // Clipboard paste fallback
    try {
      val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
      val clip = android.content.ClipData.newPlainText("remote_input", text)
      clipboard?.setPrimaryClip(clip)
      rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    } catch (_: Exception) {}
    return false
  }

  private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    if (node.isEditable) return node
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      val found = findEditableNode(child)
      if (found != null) return found
    }
    return null
  }

  fun showNotifications(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
  }

  fun showQuickSettings(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
  }

  fun lockDevice(): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
      performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    } else {
      false
    }
  }

  fun showPowerDialog(): Boolean {
    return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
  }
}
