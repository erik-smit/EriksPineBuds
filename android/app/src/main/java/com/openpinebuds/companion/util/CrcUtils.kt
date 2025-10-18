package com.openpinebuds.companion.util

/**
 * CRC-16/XMODEM implementation for apply command checksum
 * Polynomial: 0x1021, Init: 0x0000
 */
object CrcUtils {
    private const val POLYNOMIAL = 0x1021
    private const val INIT_VALUE = 0x0000

    /**
     * Calculate CRC-16/XMODEM checksum
     */
    fun calculateCrc16(data: ByteArray): Int {
        var crc = INIT_VALUE

        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)

            for (i in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor POLYNOMIAL
                } else {
                    crc shl 1
                }
            }
        }

        return crc and 0xFFFF
    }

    /**
     * Calculate checksum for apply configuration command
     * Includes: command byte, target byte, left config (16 bytes), right config (16 bytes)
     */
    fun calculateApplyCommandChecksum(
        command: Byte,
        target: Byte,
        leftConfig: ByteArray,
        rightConfig: ByteArray
    ): Int {
        require(leftConfig.size == 16) { "Left config must be 16 bytes" }
        require(rightConfig.size == 16) { "Right config must be 16 bytes" }

        val data = ByteArray(34)
        data[0] = command
        data[1] = target
        System.arraycopy(leftConfig, 0, data, 2, 16)
        System.arraycopy(rightConfig, 0, data, 18, 16)

        return calculateCrc16(data)
    }
}
