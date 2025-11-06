package com.erikspinebuds.companion.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * EQ filter types - must match firmware opb_eq_filter_type_t enum
 */
enum class EqFilterType(val value: Int) {
    LOW_SHELF(0),
    PEAK(1),
    HIGH_SHELF(2),
    LOW_PASS(3),
    HIGH_PASS(4);

    companion object {
        fun fromValue(value: Int): EqFilterType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Single EQ band configuration (16 bytes - matches firmware opb_eq_band_t)
 */
data class EqBand(
    var type: EqFilterType = EqFilterType.PEAK,
    var gain: Float = 0.0f,           // dB, range: -12.0 to +12.0
    var frequency: Float = 1000.0f,   // Hz, range: 20.0 to 20000.0
    var q: Float = 1.0f                // Q factor, range: 0.3 to 10.0
) {
    /**
     * Serialize to 16 bytes (little-endian)
     */
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(type.value)
        buffer.putFloat(gain)
        buffer.putFloat(frequency)
        buffer.putFloat(q)
        return buffer.array()
    }

    companion object {
        /**
         * Deserialize from 16 bytes (little-endian)
         */
        fun fromBytes(bytes: ByteArray, offset: Int = 0): EqBand {
            val buffer = ByteBuffer.wrap(bytes, offset, 16).order(ByteOrder.LITTLE_ENDIAN)
            val typeValue = buffer.getInt()
            val gain = buffer.getFloat()
            val frequency = buffer.getFloat()
            val q = buffer.getFloat()

            return EqBand(
                type = EqFilterType.fromValue(typeValue) ?: EqFilterType.PEAK,
                gain = gain,
                frequency = frequency,
                q = q
            )
        }
    }

    /**
     * Validate band parameters
     */
    fun isValid(): Boolean {
        return gain in -12.0f..12.0f &&
                frequency in 20.0f..20000.0f &&
                q in 0.3f..10.0f
    }

    /**
     * Create a copy with clamped values
     */
    fun clamp(): EqBand {
        return copy(
            gain = gain.coerceIn(-12.0f, 12.0f),
            frequency = frequency.coerceIn(20.0f, 20000.0f),
            q = q.coerceIn(0.3f, 10.0f)
        )
    }
}

/**
 * Complete EQ configuration (144 bytes - matches firmware opb_eq_config_t)
 */
data class EqConfiguration(
    var gain0: Float = 0.0f,                    // Pre-EQ gain (dB)
    var gain1: Float = 0.0f,                    // Post-EQ gain (dB)
    var numBands: Int = 0,                      // 0-8
    var bands: MutableList<EqBand> = mutableListOf()
) {
    companion object {
        const val MAX_BANDS = 8

        /**
         * Deserialize from 144 bytes (little-endian)
         */
        fun fromBytes(bytes: ByteArray): EqConfiguration? {
            if (bytes.size < 144) return null

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val gain0 = buffer.getFloat()
            val gain1 = buffer.getFloat()
            val numBands = buffer.getInt()
            val reserved = buffer.getInt()  // Skip reserved field

            val bands = mutableListOf<EqBand>()
            for (i in 0 until MAX_BANDS) {
                val bandBytes = ByteArray(16)
                buffer.get(bandBytes)
                if (i < numBands) {
                    bands.add(EqBand.fromBytes(bandBytes))
                }
            }

            return EqConfiguration(
                gain0 = gain0,
                gain1 = gain1,
                numBands = numBands.coerceIn(0, MAX_BANDS),
                bands = bands
            )
        }

        /**
         * Create a flat (bypass) EQ configuration
         */
        fun flat(): EqConfiguration {
            return EqConfiguration(
                gain0 = 0.0f,
                gain1 = 0.0f,
                numBands = 0,
                bands = mutableListOf()
            )
        }
    }

    /**
     * Serialize to 144 bytes (little-endian)
     */
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(144).order(ByteOrder.LITTLE_ENDIAN)

        // Write header (16 bytes)
        buffer.putFloat(gain0)
        buffer.putFloat(gain1)
        buffer.putInt(numBands.coerceIn(0, MAX_BANDS))
        buffer.putInt(0)  // reserved

        // Write bands (128 bytes = 8 bands * 16 bytes each)
        for (i in 0 until MAX_BANDS) {
            if (i < bands.size && i < numBands) {
                buffer.put(bands[i].toBytes())
            } else {
                // Pad unused bands with zeros
                buffer.put(ByteArray(16))
            }
        }

        return buffer.array()
    }

    /**
     * Validate entire configuration
     */
    fun isValid(): Boolean {
        if (numBands < 0 || numBands > MAX_BANDS) {
            android.util.Log.e("EqConfiguration", "Invalid numBands: $numBands (must be 0-$MAX_BANDS)")
            return false
        }
        if (numBands != bands.size) {
            android.util.Log.e("EqConfiguration", "Band count mismatch: numBands=$numBands, bands.size=${bands.size}")
            return false
        }
        val invalidBands = bands.filterIndexed { index, band ->
            val valid = band.isValid()
            if (!valid) {
                android.util.Log.e("EqConfiguration", "Invalid band $index: type=${band.type}, freq=${band.frequency}, gain=${band.gain}, q=${band.q}")
            }
            !valid
        }
        return invalidBands.isEmpty()
    }

    /**
     * Add a band (up to MAX_BANDS)
     */
    fun addBand(band: EqBand): Boolean {
        if (bands.size >= MAX_BANDS) return false
        bands.add(band)
        numBands = bands.size
        return true
    }

    /**
     * Remove a band at index
     */
    fun removeBand(index: Int): Boolean {
        if (index < 0 || index >= bands.size) return false
        bands.removeAt(index)
        numBands = bands.size
        return true
    }

    /**
     * Create a deep copy
     */
    fun copy(): EqConfiguration {
        return EqConfiguration(
            gain0 = gain0,
            gain1 = gain1,
            numBands = numBands,
            bands = bands.map { it.copy() }.toMutableList()
        )
    }
}

/**
 * EQ Capabilities (16 bytes - matches firmware opb_eq_capabilities_t)
 */
data class EqCapabilities(
    val versionMajor: Int,
    val versionMinor: Int,
    val versionPatch: Int,
    val maxBands: Int,
    val supportedFilterTypes: Int,    // Bitmask
    val minFrequency: Float,
    val maxFrequency: Float
) {
    companion object {
        /**
         * Deserialize from 16 bytes (little-endian)
         */
        fun fromBytes(bytes: ByteArray): EqCapabilities? {
            if (bytes.size < 16) return null

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return EqCapabilities(
                versionMajor = buffer.get().toInt() and 0xFF,
                versionMinor = buffer.get().toInt() and 0xFF,
                versionPatch = buffer.get().toInt() and 0xFF,
                maxBands = buffer.get().toInt() and 0xFF,
                supportedFilterTypes = buffer.getInt(),
                minFrequency = buffer.getFloat(),
                maxFrequency = buffer.getFloat()
            )
        }
    }

    /**
     * Check if a filter type is supported
     */
    fun supportsFilterType(type: EqFilterType): Boolean {
        return (supportedFilterTypes and (1 shl type.value)) != 0
    }
}
