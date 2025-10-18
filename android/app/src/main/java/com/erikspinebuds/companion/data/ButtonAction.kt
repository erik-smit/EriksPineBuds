package com.erikspinebuds.companion.data

/**
 * Button actions that can be assigned to gestures.
 * Must match the firmware action codes (see GATT_SPEC.md)
 */
enum class ButtonAction(val code: Int, val displayName: String) {
    NONE(0x0000, "None"),
    PLAY_PAUSE(0x0001, "Play/Pause"),
    NEXT_TRACK(0x0002, "Next Track"),
    PREVIOUS_TRACK(0x0003, "Previous Track"),
    VOLUME_UP(0x0004, "Volume Up"),
    VOLUME_DOWN(0x0005, "Volume Down"),
    TOGGLE_ANC(0x0006, "Toggle ANC"),
    VOICE_ASSISTANT(0x0007, "Voice Assistant"),
    ANSWER_CALL(0x0008, "Answer Call"),
    REJECT_CALL(0x0009, "Reject Call"),
    END_CALL(0x000A, "End Call"),
    MUTE_MIC(0x000B, "Mute Microphone"),
    TRANSPARENCY(0x000C, "Transparency Mode"),
    ANC_OFF(0x000D, "ANC Off");

    companion object {
        fun fromCode(code: Int): ButtonAction {
            return values().find { it.code == code } ?: NONE
        }
    }
}
