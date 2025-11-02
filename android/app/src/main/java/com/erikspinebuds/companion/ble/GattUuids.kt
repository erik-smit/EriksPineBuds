package com.erikspinebuds.companion.ble

import java.util.UUID

/**
 * GATT service and characteristic UUIDs for OpenPineBuds configuration
 * Must match the firmware specification (see GATT_SPEC.md)
 */
object GattUuids {
    // Touch Control Service UUID
    val CONFIG_SERVICE: UUID = UUID.fromString("0000FFC0-0000-1000-8000-00805F9B34FB")

    // Touch Control Characteristic UUIDs
    val LEFT_EARBUD_CONFIG: UUID = UUID.fromString("0000FFC1-0000-1000-8000-00805F9B34FB")
    val RIGHT_EARBUD_CONFIG: UUID = UUID.fromString("0000FFC2-0000-1000-8000-00805F9B34FB")
    val CONFIG_VERSION: UUID = UUID.fromString("0000FFC3-0000-1000-8000-00805F9B34FB")
    val DEVICE_NAME: UUID = UUID.fromString("0000FFC4-0000-1000-8000-00805F9B34FB")
    val APPLY_COMMAND: UUID = UUID.fromString("0000FFC5-0000-1000-8000-00805F9B34FB")
    val STATUS_NOTIFICATION: UUID = UUID.fromString("0000FFC6-0000-1000-8000-00805F9B34FB")

    // EQ Service UUID
    val EQ_SERVICE: UUID = UUID.fromString("0000FFD0-0000-1000-8000-00805F9B34FB")

    // EQ Characteristic UUIDs
    val EQ_CONFIG: UUID = UUID.fromString("0000FFD1-0000-1000-8000-00805F9B34FB")
    val EQ_PRESET: UUID = UUID.fromString("0000FFD2-0000-1000-8000-00805F9B34FB")
    val EQ_ENABLE: UUID = UUID.fromString("0000FFD3-0000-1000-8000-00805F9B34FB")
    val EQ_CAPABILITIES: UUID = UUID.fromString("0000FFD4-0000-1000-8000-00805F9B34FB")
    val EQ_APPLY: UUID = UUID.fromString("0000FFD5-0000-1000-8000-00805F9B34FB")
    val EQ_STATUS: UUID = UUID.fromString("0000FFD6-0000-1000-8000-00805F9B34FB")

    // Standard descriptors
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

/**
 * Apply configuration commands
 */
object ApplyCommand {
    const val APPLY_AND_SAVE: Byte = 0x01
    const val APPLY_WITHOUT_SAVE: Byte = 0x02
    const val RESET_TO_DEFAULTS: Byte = 0x03
    const val REBOOT_DEVICE: Byte = 0xFF.toByte()
}

/**
 * Target devices for apply command
 */
object ApplyTarget {
    const val BOTH_EARBUDS: Byte = 0x00
    const val LEFT_EARBUD: Byte = 0x01
    const val RIGHT_EARBUD: Byte = 0x02
}

/**
 * Status codes returned by the device
 */
enum class DeviceStatus(val code: Int) {
    SUCCESS(0x00),
    INVALID_CONFIG(0x01),
    CHECKSUM_ERROR(0x02),
    STORAGE_ERROR(0x03),
    NOT_SUPPORTED(0x04),
    UNKNOWN_ERROR(0xFF);

    companion object {
        fun fromCode(code: Int): DeviceStatus {
            return values().find { it.code == code } ?: UNKNOWN_ERROR
        }
    }
}

/**
 * EQ Presets - must match firmware opb_eq_preset_t enum
 */
enum class EqPreset(val value: Int, val displayName: String) {
    FLAT(0, "Flat"),
    BASS_BOOST(1, "Bass Boost"),
    TREBLE_BOOST(2, "Treble Boost"),
    V_SHAPE(3, "V-Shape"),
    VOCAL(4, "Vocal"),
    CLASSICAL(5, "Classical"),
    ROCK(6, "Rock"),
    JAZZ(7, "Jazz"),
    ELECTRONIC(8, "Electronic"),
    PODCAST(9, "Podcast");

    companion object {
        fun fromValue(value: Int): EqPreset? {
            return values().find { it.value == value }
        }
    }
}

/**
 * EQ Apply commands
 */
object EqApplyCommand {
    const val APPLY_AND_SAVE: Byte = 0x01
    const val APPLY_TEMPORARY: Byte = 0x02
    const val RESET_TO_FLAT: Byte = 0x03
}
