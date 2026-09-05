package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer

data class QrPairingData(
  val ipAddress: String,
  val port: Int = 8989,
  val pin: String,
  val deviceName: String,
  val deviceModel: String,
)

object QrCodeUtil {

  fun buildPairingUri(
    ipAddress: String,
    port: Int,
    pin: String,
    deviceName: String,
    deviceModel: String,
  ): String {
    val encodedName = URLEncoder.encode(deviceName, "UTF-8")
    val encodedModel = URLEncoder.encode(deviceModel, "UTF-8")
    return "twincontrol://pair?ip=$ipAddress&port=$port&pin=$pin&name=$encodedName&model=$encodedModel"
  }

  fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
      val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
      )
      val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
      val width = bitMatrix.width
      val height = bitMatrix.height
      val pixels = IntArray(width * height)
      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
        }
      }
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
      bitmap
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return try {
      val width = bitmap.width
      val height = bitmap.height
      val pixels = IntArray(width * height)
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
      val source = RGBLuminanceSource(width, height, pixels)
      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
      val result = MultiFormatReader().decode(binaryBitmap)
      result.text
    } catch (e: Exception) {
      null
    }
  }

  fun decodeQrFromYuv(buffer: ByteBuffer, width: Int, height: Int): String? {
    return try {
      val data = ByteArray(buffer.remaining())
      buffer.get(data)
      val source = PlanarYUVLuminanceSource(
        data, width, height,
        0, 0, width, height,
        false,
      )
      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
      val result = MultiFormatReader().decode(binaryBitmap)
      result.text
    } catch (e: Exception) {
      null
    }
  }

  fun parsePairingData(raw: String): QrPairingData? {
    val trimmed = raw.trim()
    // 1. URI Format: twincontrol://pair?ip=...&port=...&pin=...&name=...&model=...
    if (trimmed.startsWith("twincontrol://", ignoreCase = true)) {
      return try {
        val uri = Uri.parse(trimmed)
        val ip = uri.getQueryParameter("ip") ?: return null
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8989
        val pin = uri.getQueryParameter("pin") ?: ""
        val name = uri.getQueryParameter("name")?.let { URLDecoder.decode(it, "UTF-8") } ?: "Remote Device"
        val model = uri.getQueryParameter("model")?.let { URLDecoder.decode(it, "UTF-8") } ?: "Android Target"
        QrPairingData(ipAddress = ip, port = port, pin = pin, deviceName = name, deviceModel = model)
      } catch (e: Exception) {
        null
      }
    }

    // 2. JSON Format: {"ip":"...","port":8989,"pin":"...","name":"...","model":"..."}
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      return try {
        val json = JSONObject(trimmed)
        val ip = json.optString("ip").ifBlank { json.optString("ipAddress") }
        if (ip.isBlank()) return null
        val port = json.optInt("port", 8989)
        val pin = json.optString("pin")
        val name = json.optString("name", "Remote Device")
        val model = json.optString("model", "Android Target")
        QrPairingData(ipAddress = ip, port = port, pin = pin, deviceName = name, deviceModel = model)
      } catch (e: Exception) {
        null
      }
    }

    // 3. Simple comma or colon delimited format: ip:port:pin or ip,port,pin
    val parts = if (trimmed.contains(",")) trimmed.split(",") else trimmed.split(":")
    if (parts.size >= 2) {
      val ip = parts[0].trim()
      val port = parts[1].trim().toIntOrNull() ?: 8989
      val pin = if (parts.size >= 3) parts[2].trim() else ""
      if (ip.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""))) {
        return QrPairingData(
          ipAddress = ip,
          port = port,
          pin = pin,
          deviceName = "Target ($ip)",
          deviceModel = "Android Target",
        )
      }
    }

    return null
  }
}
