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
    var instance: RemoteAccessibilityService? = null
      private set
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    Log.d(TAG, "TwinControl RemoteAccessibilityService connected")
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
    val path = Path().apply {
      moveTo(x, y)
    }
    val stroke = GestureDescription.StrokeDescription(path, 0, 50)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGesture(gesture, null, null)
  }

  fun simulateSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
    val path = Path().apply {
      moveTo(startX, startY)
      lineTo(endX, endY)
    }
    val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    return dispatchGesture(gesture, null, null)
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
    val rootNode = rootInActiveWindow ?: return false
    val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
    val arguments = Bundle().apply {
      putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    }
    return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
  }
}
