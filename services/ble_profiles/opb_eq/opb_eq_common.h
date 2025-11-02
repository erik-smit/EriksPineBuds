#ifndef _OPB_EQ_COMMON_H_
#define _OPB_EQ_COMMON_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Maximum number of EQ bands supported by BES2300P hardware codec
#define OPB_EQ_MAX_BANDS 8

// Filter type codes (must match EQ_GATT_SPEC.md and Android app)
typedef enum {
    OPB_EQ_FILTER_LOW_SHELF = 0,
    OPB_EQ_FILTER_PEAK = 1,
    OPB_EQ_FILTER_HIGH_SHELF = 2,
    OPB_EQ_FILTER_LOW_PASS = 3,
    OPB_EQ_FILTER_HIGH_PASS = 4,
    OPB_EQ_FILTER_MAX
} opb_eq_filter_type_t;

// EQ preset codes (must match EQ_GATT_SPEC.md and Android app)
typedef enum {
    OPB_EQ_PRESET_FLAT = 0x00,
    OPB_EQ_PRESET_BASS_BOOST = 0x01,
    OPB_EQ_PRESET_TREBLE_BOOST = 0x02,
    OPB_EQ_PRESET_V_SHAPE = 0x03,
    OPB_EQ_PRESET_VOCAL = 0x04,
    OPB_EQ_PRESET_CLASSICAL = 0x05,
    OPB_EQ_PRESET_ROCK = 0x06,
    OPB_EQ_PRESET_JAZZ = 0x07,
    OPB_EQ_PRESET_ELECTRONIC = 0x08,
    OPB_EQ_PRESET_PODCAST = 0x09,
    OPB_EQ_PRESET_CUSTOM = 0xFF
} opb_eq_preset_t;

#define OPB_EQ_PRESET_MAX 10  // Number of built-in presets (0-9)

// Single EQ band configuration (16 bytes - matches GATT spec)
typedef struct __attribute__((packed)) {
    uint32_t type;          // opb_eq_filter_type_t
    float gain;             // dB, range: -12.0 to +12.0
    float frequency;        // Hz, range: 20.0 to 20000.0
    float q;                // Q factor, range: 0.3 to 10.0
} opb_eq_band_t;

// Complete EQ configuration (144 bytes - matches GATT spec)
typedef struct __attribute__((packed)) {
    float gain0;                        // Pre-EQ gain (dB)
    float gain1;                        // Post-EQ gain (dB)
    uint32_t num_bands;                 // 0-8
    uint32_t reserved;
    opb_eq_band_t bands[OPB_EQ_MAX_BANDS];  // Up to 8 bands
} opb_eq_config_t;

// EQ capabilities structure (16 bytes - matches GATT spec)
typedef struct __attribute__((packed)) {
    uint8_t version_major;
    uint8_t version_minor;
    uint8_t version_patch;
    uint8_t max_bands;                  // OPB_EQ_MAX_BANDS
    uint32_t supported_filter_types;    // Bitmask of supported filter types
    float min_frequency;                // Hz
    float max_frequency;                // Hz
} opb_eq_capabilities_t;

// Parameter validation ranges
#define OPB_EQ_GAIN_MIN     -12.0f
#define OPB_EQ_GAIN_MAX     +12.0f
#define OPB_EQ_FREQ_MIN     20.0f
#define OPB_EQ_FREQ_MAX     20000.0f
#define OPB_EQ_Q_MIN        0.3f
#define OPB_EQ_Q_MAX        10.0f

// Default flat EQ (bypass)
#define OPB_EQ_CONFIG_DEFAULT_FLAT { \
    .gain0 = 0.0f, \
    .gain1 = 0.0f, \
    .num_bands = 0, \
    .reserved = 0 \
}

// Version
#define OPB_EQ_VERSION_MAJOR 1
#define OPB_EQ_VERSION_MINOR 0
#define OPB_EQ_VERSION_PATCH 0

// Supported filter types bitmask
#define OPB_EQ_SUPPORTED_FILTERS ( \
    (1 << OPB_EQ_FILTER_LOW_SHELF) | \
    (1 << OPB_EQ_FILTER_PEAK) | \
    (1 << OPB_EQ_FILTER_HIGH_SHELF) | \
    (1 << OPB_EQ_FILTER_LOW_PASS) | \
    (1 << OPB_EQ_FILTER_HIGH_PASS) \
)

#ifdef __cplusplus
}
#endif

#endif // _OPB_EQ_COMMON_H_
