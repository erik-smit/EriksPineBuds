package com.erikspinebuds.companion.util

import android.graphics.Bitmap
import android.graphics.Color
import com.erikspinebuds.companion.data.EqConfiguration
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import android.util.Base64

/**
 * Utility class for encoding/decoding EQ configurations to/from QR codes
 */
object EqQrCodeUtils {

    /**
     * Generate a QR code bitmap from an EQ configuration
     * @param config the EQ configuration to encode
     * @param size the size of the QR code bitmap (width and height)
     * @return the QR code bitmap, or null if generation fails
     */
    fun generateQrCode(config: EqConfiguration, size: Int = 512): Bitmap? {
        try {
            // Serialize config to bytes
            val configBytes = config.toBytes()

            // Encode as Base64 for QR code
            val base64String = Base64.encodeToString(configBytes, Base64.NO_WRAP)

            // Add a prefix to identify this as an OpenPineBuds EQ config
            val qrContent = "OPBEQ:$base64String"

            // Configure QR code writer
            val hints = hashMapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )

            // Generate QR code
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size, hints)

            // Convert BitMatrix to Bitmap
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            return bitmap
        } catch (e: Exception) {
            android.util.Log.e("EqQrCodeUtils", "Failed to generate QR code", e)
            return null
        }
    }

    /**
     * Decode an EQ configuration from a QR code string
     * @param qrContent the scanned QR code content
     * @return the decoded EQ configuration, or null if decoding fails
     */
    fun decodeQrCode(qrContent: String): EqConfiguration? {
        try {
            // Check for OpenPineBuds EQ prefix
            if (!qrContent.startsWith("OPBEQ:")) {
                android.util.Log.w("EqQrCodeUtils", "QR code does not have OPBEQ prefix")
                return null
            }

            // Extract Base64 string
            val base64String = qrContent.substring(6)  // Remove "OPBEQ:" prefix

            // Decode Base64 to bytes
            val configBytes = Base64.decode(base64String, Base64.NO_WRAP)

            // Deserialize to EqConfiguration
            return EqConfiguration.fromBytes(configBytes)
        } catch (e: Exception) {
            android.util.Log.e("EqQrCodeUtils", "Failed to decode QR code", e)
            return null
        }
    }

    /**
     * Get a shareable text representation of the QR code content
     * (for sharing via text/email/etc)
     */
    fun getShareableText(config: EqConfiguration): String {
        val configBytes = config.toBytes()
        val base64String = Base64.encodeToString(configBytes, Base64.NO_WRAP)
        return "OPBEQ:$base64String"
    }

    /**
     * Parse shareable text back to EQ configuration
     */
    fun fromShareableText(text: String): EqConfiguration? {
        return decodeQrCode(text.trim())
    }

    /**
     * Get a human-readable description of an EQ configuration
     * (for display with QR code)
     */
    fun getConfigDescription(config: EqConfiguration): String {
        val sb = StringBuilder()
        sb.append("OpenPineBuds Custom EQ\n")
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("Pre-Gain: ${String.format("%.1f", config.gain0)} dB\n")
        sb.append("Post-Gain: ${String.format("%.1f", config.gain1)} dB\n")
        sb.append("Bands: ${config.numBands}\n\n")

        if (config.numBands > 0) {
            sb.append("Band Details:\n")
            config.bands.forEachIndexed { index, band ->
                sb.append("${index + 1}. ${band.type.name}: ")
                sb.append("${String.format("%.0f", band.frequency)}Hz, ")
                sb.append("${String.format("%+.1f", band.gain)}dB, ")
                sb.append("Q=${String.format("%.1f", band.q)}\n")
            }
        }

        return sb.toString()
    }
}
