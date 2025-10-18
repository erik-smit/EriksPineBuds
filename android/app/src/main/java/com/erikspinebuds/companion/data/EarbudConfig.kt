package com.erikspinebuds.companion.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Configuration for a single earbud (left or right)
 * Matches the firmware structure (16 bytes)
 */
data class EarbudConfig(
    var singleTap: ButtonAction = ButtonAction.PLAY_PAUSE,
    var doubleTap: ButtonAction = ButtonAction.NEXT_TRACK,
    var tripleTap: ButtonAction = ButtonAction.VOLUME_UP,
    var longPress: ButtonAction = ButtonAction.TOGGLE_ANC
) {
    /**
     * Serialize to bytes for BLE transmission (16 bytes)
     */
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(singleTap.code)
        buffer.putInt(doubleTap.code)
        buffer.putInt(tripleTap.code)
        buffer.putInt(longPress.code)
        return buffer.array()
    }

    companion object {
        /**
         * Deserialize from bytes received via BLE (16 bytes)
         */
        fun fromBytes(data: ByteArray): EarbudConfig {
            require(data.size == 16) { "EarbudConfig data must be 16 bytes" }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            return EarbudConfig(
                singleTap = ButtonAction.fromCode(buffer.int),
                doubleTap = ButtonAction.fromCode(buffer.int),
                tripleTap = ButtonAction.fromCode(buffer.int),
                longPress = ButtonAction.fromCode(buffer.int)
            )
        }

        /**
         * Default configuration for left earbud
         */
        fun defaultLeft() = EarbudConfig(
            singleTap = ButtonAction.PLAY_PAUSE,
            doubleTap = ButtonAction.PREVIOUS_TRACK,
            tripleTap = ButtonAction.VOLUME_DOWN,
            longPress = ButtonAction.TOGGLE_ANC
        )

        /**
         * Default configuration for right earbud
         */
        fun defaultRight() = EarbudConfig(
            singleTap = ButtonAction.PLAY_PAUSE,
            doubleTap = ButtonAction.NEXT_TRACK,
            tripleTap = ButtonAction.VOLUME_UP,
            longPress = ButtonAction.TOGGLE_ANC
        )
    }

    /**
     * Get action for a specific gesture
     */
    fun getAction(gesture: GestureType): ButtonAction {
        return when (gesture) {
            GestureType.SINGLE_TAP -> singleTap
            GestureType.DOUBLE_TAP -> doubleTap
            GestureType.TRIPLE_TAP -> tripleTap
            GestureType.LONG_PRESS -> longPress
        }
    }

    /**
     * Set action for a specific gesture
     */
    fun setAction(gesture: GestureType, action: ButtonAction) {
        when (gesture) {
            GestureType.SINGLE_TAP -> singleTap = action
            GestureType.DOUBLE_TAP -> doubleTap = action
            GestureType.TRIPLE_TAP -> tripleTap = action
            GestureType.LONG_PRESS -> longPress = action
        }
    }
}
