package com.openpinebuds.companion.ble

import java.util.UUID

/**
 * GATT service and characteristic UUIDs for OpenPineBuds configuration
 * Must match the firmware specification (see GATT_SPEC.md)
 */
object GattUuids {
    // Service UUID
    val CONFIG_SERVICE: UUID = UUID.fromString("0000FFC0-0000-1000-8000-00805F9B34FB")

    // Characteristic UUIDs
    val LEFT_EARBUD_CONFIG: UUID = UUID.fromString("0000FFC1-0000-1000-8000-00805F9B34FB")
    val RIGHT_EARBUD_CONFIG: UUID = UUID.fromString("0000FFC2-0000-1000-8000-00805F9B34FB")
    val CONFIG_VERSION: UUID = UUID.fromString("0000FFC3-0000-1000-8000-00805F9B34FB")
    val DEVICE_INFO: UUID = UUID.fromString("0000FFC4-0000-1000-8000-00805F9B34FB")
    val APPLY_COMMAND: UUID = UUID.fromString("0000FFC5-0000-1000-8000-00805F9B34FB")
    val STATUS_NOTIFICATION: UUID = UUID.fromString("0000FFC6-0000-1000-8000-00805F9B34FB")

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
