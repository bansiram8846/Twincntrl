package com.example.data.model

data class TrustedController(
  val id: String,
  val name: String,
  val ipAddress: String,
  val addedAt: Long = System.currentTimeMillis(),
  val lastSeen: Long = System.currentTimeMillis(),
)
