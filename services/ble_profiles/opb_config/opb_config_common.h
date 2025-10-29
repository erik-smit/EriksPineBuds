#ifndef _OPB_CONFIG_COMMON_H_
#define _OPB_CONFIG_COMMON_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Button action codes (must match GATT_SPEC.md and Android app)
typedef enum {
    OPB_ACTION_NONE = 0x0000,
    OPB_ACTION_PLAY_PAUSE = 0x0001,
    OPB_ACTION_NEXT_TRACK = 0x0002,
    OPB_ACTION_PREVIOUS_TRACK = 0x0003,
    OPB_ACTION_VOLUME_UP = 0x0004,
    OPB_ACTION_VOLUME_DOWN = 0x0005,
    OPB_ACTION_TOGGLE_ANC = 0x0006,
    OPB_ACTION_VOICE_ASSISTANT = 0x0007,
    OPB_ACTION_ANSWER_CALL = 0x0008,
    OPB_ACTION_REJECT_CALL = 0x0009,
    OPB_ACTION_END_CALL = 0x000A,
    OPB_ACTION_MUTE_MIC = 0x000B,
    OPB_ACTION_TRANSPARENCY = 0x000C,
    OPB_ACTION_ANC_OFF = 0x000D,
    OPB_ACTION_CUSTOM_1 = 0x000E,
    OPB_ACTION_CUSTOM_2 = 0x000F,
    OPB_ACTION_MAX,
} opb_button_action_t;

// Configuration for one earbud (16 bytes - matches GATT spec)
typedef struct __attribute__((packed)) {
    uint32_t single_tap;
    uint32_t double_tap;
    uint32_t triple_tap;
    uint32_t long_press;
} opb_earbud_config_t;

// Full configuration structure
typedef struct {
    opb_earbud_config_t left;
    opb_earbud_config_t right;
    uint8_t version_major;
    uint8_t version_minor;
    uint8_t version_patch;
    uint8_t reserved;
} opb_config_t;

// TWS sync data structure (includes both button config and device name)
typedef struct {
    opb_config_t config;
    char device_name[32];
} opb_tws_sync_data_t;

// Default configuration for left earbud
#define OPB_CONFIG_DEFAULT_LEFT_INIT { \
    .single_tap = OPB_ACTION_PLAY_PAUSE, \
    .double_tap = OPB_ACTION_PREVIOUS_TRACK, \
    .triple_tap = OPB_ACTION_VOLUME_DOWN, \
    .long_press = OPB_ACTION_TOGGLE_ANC \
}

// Default configuration for right earbud
#define OPB_CONFIG_DEFAULT_RIGHT_INIT { \
    .single_tap = OPB_ACTION_PLAY_PAUSE, \
    .double_tap = OPB_ACTION_NEXT_TRACK, \
    .triple_tap = OPB_ACTION_VOLUME_UP, \
    .long_press = OPB_ACTION_TOGGLE_ANC \
}

#define OPB_CONFIG_VERSION_MAJOR 1
#define OPB_CONFIG_VERSION_MINOR 0
#define OPB_CONFIG_VERSION_PATCH 0

#ifdef __cplusplus
}
#endif

#endif // _OPB_CONFIG_COMMON_H_
