package com.example.data.model

enum class CommandType {
  SCREEN_FRAME,
  TOUCH,
  SWIPE,
  SCROLL,
  TEXT,
  BACK,
  HOME,
  RECENTS,
  PING,
  PONG,
  DEVICE_STATUS,
  PAIR_REQUEST,
  PAIR_RESPONSE,
  AUTHORIZATION,
  DISCONNECT
}

data class RemoteCommand(
  val commandId: String,
  val timestamp: Long,
  val type: CommandType,
  val payload: String = "",
  val x: Float? = null,
  val y: Float? = null,
  val endX: Float? = null,
  val endY: Float? = null,
  val durationMs: Long? = null,
  val deltaX: Float? = null,
  val deltaY: Float? = null,
)
