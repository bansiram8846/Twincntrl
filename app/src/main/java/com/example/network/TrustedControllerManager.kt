package com.example.network

import android.content.Context
import com.example.data.model.TrustedController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class TrustedControllerManager(private val context: Context) {

  companion object {
    private const val PREFS_NAME = "twincontrol_trusted_prefs"
    private const val KEY_TRUSTED_LIST = "trusted_controllers_json"

    @Volatile
    private var instance: TrustedControllerManager? = null

    fun getInstance(context: Context): TrustedControllerManager {
      return instance ?: synchronized(this) {
        instance ?: TrustedControllerManager(context.applicationContext).also { instance = it }
      }
    }
  }

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _trustedControllers = MutableStateFlow<List<TrustedController>>(emptyList())
  val trustedControllers: StateFlow<List<TrustedController>> = _trustedControllers.asStateFlow()

  init {
    loadTrustedControllers()
  }

  private fun loadTrustedControllers() {
    val jsonStr = prefs.getString(KEY_TRUSTED_LIST, null) ?: return
    try {
      val array = JSONArray(jsonStr)
      val list = mutableListOf<TrustedController>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
          TrustedController(
            id = obj.getString("id"),
            name = obj.getString("name"),
            ipAddress = obj.optString("ipAddress", ""),
            addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
            lastSeen = obj.optLong("lastSeen", System.currentTimeMillis()),
          )
        )
      }
      _trustedControllers.value = list
    } catch (_: Exception) {}
  }

  private fun saveTrustedControllers(list: List<TrustedController>) {
    val array = JSONArray()
    for (tc in list) {
      val obj = JSONObject().apply {
        put("id", tc.id)
        put("name", tc.name)
        put("ipAddress", tc.ipAddress)
        put("addedAt", tc.addedAt)
        put("lastSeen", tc.lastSeen)
      }
      array.put(obj)
    }
    prefs.edit().putString(KEY_TRUSTED_LIST, array.toString()).apply()
    _trustedControllers.value = list
  }

  fun isTrusted(controllerName: String, ipAddress: String = ""): Boolean {
    val cleanName = controllerName.trim()
    val cleanIp = ipAddress.trim().removePrefix("/").substringBefore("%")
    return _trustedControllers.value.any { tc ->
      (tc.name.isNotBlank() && tc.name.equals(cleanName, ignoreCase = true)) ||
      (cleanIp.isNotBlank() && tc.ipAddress.isNotBlank() && tc.ipAddress.equals(cleanIp, ignoreCase = true))
    }
  }

  fun addTrustedController(name: String, ipAddress: String = "") {
    val cleanName = name.trim()
    val cleanIp = ipAddress.trim().removePrefix("/").substringBefore("%")
    val current = _trustedControllers.value.toMutableList()
    val existingIdx = current.indexOfFirst {
      it.name.equals(cleanName, ignoreCase = true) || (cleanIp.isNotBlank() && it.ipAddress.equals(cleanIp, ignoreCase = true))
    }
    val now = System.currentTimeMillis()
    if (existingIdx >= 0) {
      current[existingIdx] = current[existingIdx].copy(
        ipAddress = if (cleanIp.isNotBlank()) cleanIp else current[existingIdx].ipAddress,
        lastSeen = now,
      )
    } else {
      current.add(
        TrustedController(
          id = "tc_${System.currentTimeMillis()}_${(1000..9999).random()}",
          name = if (cleanName.isNotBlank()) cleanName else "Controller Device",
          ipAddress = cleanIp,
          addedAt = now,
          lastSeen = now,
        )
      )
    }
    saveTrustedControllers(current)
  }

  fun removeTrustedController(id: String) {
    val current = _trustedControllers.value.filter { it.id != id }
    saveTrustedControllers(current)
  }

  fun revokeAll() {
    saveTrustedControllers(emptyList())
  }
}
