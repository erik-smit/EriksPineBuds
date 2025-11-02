#include "app_opb_eq.h"
#include "nvrecord_env.h"
#include "hal_trace.h"
#include "iir_process.h"
#include "hw_codec_iir_process.h"
#include "hal_aud.h"
#include <string.h>
#include <math.h>

#ifdef TWS_SYSTEM_ENABLED
#include "app_tws_if.h"
#endif

// External reference to global config from tgt_hardware.c
extern IIR_CFG_T audio_eq_sw_iir_cfg;

// Current runtime configuration
static opb_eq_config_t current_eq_config;
static opb_eq_preset_t current_preset = OPB_EQ_PRESET_FLAT;
static bool eq_enabled = false;
static bool eq_initialized = false;
static bool hw_iir_opened = false;

// Forward declarations
static bool validate_config(const opb_eq_config_t *config);
static void load_preset_config(opb_eq_preset_t preset, opb_eq_config_t *config);
static int apply_config_to_hardware_internal(const opb_eq_config_t *config, uint32_t sample_rate);

// EQ preset configurations
static const opb_eq_config_t eq_presets[] = {
    // FLAT (bypass)
    {
        .gain0 = 0.0f,
        .gain1 = 0.0f,
        .num_bands = 0,
        .reserved = 0
    },

    // BASS_BOOST - EXTREME (should sound BOOMY)
    {
        .gain0 = 3.0f,
        .gain1 = 3.0f,
        .num_bands = 3,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_LOW_SHELF, 18.0f, 80.0f, 0.7f},    // MASSIVE bass
            {OPB_EQ_FILTER_PEAK, 12.0f, 200.0f, 1.5f},        // Extra mid-bass
            {OPB_EQ_FILTER_HIGH_SHELF, -12.0f, 6000.0f, 0.7f}, // Cut treble heavily
        }
    },

    // TREBLE_BOOST - EXTREME (should sound BRIGHT/TINNY)
    {
        .gain0 = 3.0f,
        .gain1 = 3.0f,
        .num_bands = 3,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_LOW_SHELF, -18.0f, 300.0f, 0.7f},  // Kill bass completely
            {OPB_EQ_FILTER_PEAK, -10.0f, 1000.0f, 1.5f},      // Cut mids
            {OPB_EQ_FILTER_HIGH_SHELF, 18.0f, 6000.0f, 0.7f}, // MASSIVE treble boost
        }
    },

    // V_SHAPE - EXTREME (bass + treble, hollow mids)
    {
        .gain0 = 3.0f,
        .gain1 = 3.0f,
        .num_bands = 5,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_LOW_SHELF, 15.0f, 80.0f, 0.7f},    // Huge bass
            {OPB_EQ_FILTER_PEAK, -12.0f, 800.0f, 2.0f},       // Scoop mids heavily
            {OPB_EQ_FILTER_PEAK, -10.0f, 2000.0f, 2.0f},      // More mid scoop
            {OPB_EQ_FILTER_PEAK, 12.0f, 6000.0f, 1.5f},       // Big treble peak
            {OPB_EQ_FILTER_HIGH_SHELF, 15.0f, 12000.0f, 0.7f}, // Huge air
        }
    },

    // VOCAL
    {
        .gain0 = 0.0f,
        .gain1 = 0.0f,
        .num_bands = 5,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_PEAK, -2.0f, 150.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 3.0f, 800.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 4.0f, 2000.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 2.0f, 3500.0f, 1.0f},
            {OPB_EQ_FILTER_HIGH_SHELF, -3.0f, 10000.0f, 0.7f},
        }
    },

    // CLASSICAL
    {
        .gain0 = 0.0f,
        .gain1 = 0.0f,
        .num_bands = 6,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_LOW_SHELF, 3.0f, 60.0f, 0.7f},
            {OPB_EQ_FILTER_PEAK, -1.0f, 250.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 1.5f, 1000.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 2.0f, 3000.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 1.0f, 8000.0f, 1.0f},
            {OPB_EQ_FILTER_HIGH_SHELF, 2.0f, 14000.0f, 0.7f},
        }
    },

    // ROCK
    {
        .gain0 = 0.0f,
        .gain1 = 0.0f,
        .num_bands = 6,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_LOW_SHELF, 4.0f, 70.0f, 0.7f},
            {OPB_EQ_FILTER_PEAK, 2.0f, 200.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 3.0f, 800.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, -1.0f, 2000.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 2.0f, 5000.0f, 1.0f},
            {OPB_EQ_FILTER_HIGH_SHELF, 3.0f, 12000.0f, 0.7f},
        }
    },

    // JAZZ
    {
        .gain0 = 0.0f,
        .gain1 = 0.0f,
        .num_bands = 6,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_LOW_SHELF, 2.0f, 70.0f, 0.7f},
            {OPB_EQ_FILTER_PEAK, 1.0f, 400.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 2.0f, 1500.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 3.0f, 4000.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 1.0f, 8000.0f, 1.0f},
            {OPB_EQ_FILTER_HIGH_SHELF, 1.0f, 14000.0f, 0.7f},
        }
    },

    // ELECTRONIC
    {
        .gain0 = 0.0f,
        .gain1 = 0.0f,
        .num_bands = 7,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_LOW_SHELF, 7.0f, 50.0f, 0.7f},
            {OPB_EQ_FILTER_PEAK, 4.0f, 120.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, -2.0f, 500.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, -1.0f, 1500.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 3.0f, 4000.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 5.0f, 8000.0f, 1.0f},
            {OPB_EQ_FILTER_HIGH_SHELF, 6.0f, 14000.0f, 0.7f},
        }
    },

    // PODCAST
    {
        .gain0 = 0.0f,
        .gain1 = 0.0f,
        .num_bands = 4,
        .reserved = 0,
        .bands = {
            {OPB_EQ_FILTER_HIGH_PASS, 0.0f, 80.0f, 0.7f},
            {OPB_EQ_FILTER_PEAK, 4.0f, 1000.0f, 1.0f},
            {OPB_EQ_FILTER_PEAK, 5.0f, 2500.0f, 1.0f},
            {OPB_EQ_FILTER_HIGH_SHELF, -4.0f, 8000.0f, 0.7f},
        }
    }
};

// Initialize EQ from NV storage
int app_opb_eq_init(void) {
    TRACE(0, "[OPB_EQ] Initializing EQ configuration");

    opb_eq_config_t *nv_config = NULL;
    bool *nv_enabled = NULL;
    opb_eq_preset_t *nv_preset = NULL;

    // Try to load from NV storage
    if (nv_record_get_eq_config(&nv_config) == 0 && nv_config != NULL) {
        current_eq_config = *nv_config;
        TRACE(0, "[OPB_EQ] Loaded config from NV storage");
    } else {
        // Use flat/bypass default
        opb_eq_config_t default_config = OPB_EQ_CONFIG_DEFAULT_FLAT;
        current_eq_config = default_config;
        TRACE(0, "[OPB_EQ] Initialized with flat (bypass) default");
    }

    // Load enabled state
    if (nv_record_get_eq_enabled(&nv_enabled) == 0 && nv_enabled != NULL) {
        eq_enabled = *nv_enabled;
    } else {
        eq_enabled = false;
    }

    // Load preset
    if (nv_record_get_eq_preset(&nv_preset) == 0 && nv_preset != NULL) {
        current_preset = *nv_preset;
    } else {
        current_preset = OPB_EQ_PRESET_FLAT;
    }

    TRACE(3, "[OPB_EQ] Init complete: bands=%d, enabled=%d, preset=%d",
          current_eq_config.num_bands, eq_enabled, current_preset);

    eq_initialized = true;

    // DIRECTLY update global config (bypass function)
    TRACE(0, "[OPB_EQ] *** DIRECTLY updating global audio_eq_sw_iir_cfg ***");
    audio_eq_sw_iir_cfg.gain0 = current_eq_config.gain0;
    audio_eq_sw_iir_cfg.gain1 = current_eq_config.gain1;
    audio_eq_sw_iir_cfg.num = current_eq_config.num_bands;
    for (uint32_t i = 0; i < current_eq_config.num_bands && i < 8; i++) {
        audio_eq_sw_iir_cfg.param[i].type = (IIR_TYPE_T)current_eq_config.bands[i].type;
        audio_eq_sw_iir_cfg.param[i].gain = current_eq_config.bands[i].gain;
        audio_eq_sw_iir_cfg.param[i].fc = current_eq_config.bands[i].frequency;
        audio_eq_sw_iir_cfg.param[i].Q = current_eq_config.bands[i].q;
        TRACE(4, "[OPB_EQ] Band %d: type=%d gain=%.1f fc=%.0f Q=%.2f",
              i, (int)audio_eq_sw_iir_cfg.param[i].type,
              (double)audio_eq_sw_iir_cfg.param[i].gain,
              (double)audio_eq_sw_iir_cfg.param[i].fc,
              (double)audio_eq_sw_iir_cfg.param[i].Q);
    }
    TRACE(2, "[OPB_EQ] Global config updated: bands=%d", audio_eq_sw_iir_cfg.num);

    return 0;
}

// Get current EQ configuration
int app_opb_eq_get_config(opb_eq_config_t *config) {
    if (!config) {
        return -1;
    }

    if (!eq_initialized) {
        app_opb_eq_init();
    }

    *config = current_eq_config;
    return 0;
}

// Set EQ configuration
int app_opb_eq_set_config(const opb_eq_config_t *config, bool save_to_nv) {
    if (!eq_initialized) {
        app_opb_eq_init();
    }

    if (!config) {
        TRACE(0, "[OPB_EQ] Set failed: NULL config");
        return -1;
    }

    // Validate configuration
    if (!validate_config(config)) {
        TRACE(0, "[OPB_EQ] Set failed: invalid config");
        return -2;
    }

    current_eq_config = *config;
    current_preset = OPB_EQ_PRESET_CUSTOM;

    TRACE(2, "[OPB_EQ] Configuration updated (bands=%d, save=%d)",
          config->num_bands, save_to_nv);

    if (save_to_nv) {
        if (nv_record_set_eq_config(&current_eq_config) != 0) {
            TRACE(0, "[OPB_EQ] Failed to save config to NV");
            return -3;
        }
        if (nv_record_set_eq_preset(&current_preset) != 0) {
            TRACE(0, "[OPB_EQ] Failed to save preset to NV");
        }
        TRACE(0, "[OPB_EQ] Config saved to NV");
    }

    // DIRECTLY update global config
    audio_eq_sw_iir_cfg.gain0 = current_eq_config.gain0;
    audio_eq_sw_iir_cfg.gain1 = current_eq_config.gain1;
    audio_eq_sw_iir_cfg.num = current_eq_config.num_bands;
    for (uint32_t i = 0; i < current_eq_config.num_bands && i < 8; i++) {
        audio_eq_sw_iir_cfg.param[i].type = (IIR_TYPE_T)current_eq_config.bands[i].type;
        audio_eq_sw_iir_cfg.param[i].gain = current_eq_config.bands[i].gain;
        audio_eq_sw_iir_cfg.param[i].fc = current_eq_config.bands[i].frequency;
        audio_eq_sw_iir_cfg.param[i].Q = current_eq_config.bands[i].q;
    }
    TRACE(2, "[OPB_EQ] Config updated in global: bands=%d", audio_eq_sw_iir_cfg.num);

    return 0;
}

// Get current preset
opb_eq_preset_t app_opb_eq_get_preset(void) {
    if (!eq_initialized) {
        app_opb_eq_init();
    }
    return current_preset;
}

// Set EQ preset
int app_opb_eq_set_preset(opb_eq_preset_t preset, bool save_to_nv) {
    if (!eq_initialized) {
        app_opb_eq_init();
    }

    if (preset >= OPB_EQ_PRESET_PODCAST && preset != OPB_EQ_PRESET_CUSTOM) {
        TRACE(1, "[OPB_EQ] Invalid preset: %d", preset);
        return -1;
    }

    TRACE(2, "[OPB_EQ] Setting preset %d (save=%d)", preset, save_to_nv);

    // Load preset configuration
    if (preset != OPB_EQ_PRESET_CUSTOM) {
        load_preset_config(preset, &current_eq_config);
        current_preset = preset;
    }

    if (save_to_nv) {
        if (nv_record_set_eq_config(&current_eq_config) != 0) {
            TRACE(0, "[OPB_EQ] Failed to save config to NV");
            return -2;
        }
        if (nv_record_set_eq_preset(&current_preset) != 0) {
            TRACE(0, "[OPB_EQ] Failed to save preset to NV");
        }
        TRACE(0, "[OPB_EQ] Preset saved to NV");
    }

    // DIRECTLY update global config
    audio_eq_sw_iir_cfg.gain0 = current_eq_config.gain0;
    audio_eq_sw_iir_cfg.gain1 = current_eq_config.gain1;
    audio_eq_sw_iir_cfg.num = current_eq_config.num_bands;
    for (uint32_t i = 0; i < current_eq_config.num_bands && i < 8; i++) {
        audio_eq_sw_iir_cfg.param[i].type = (IIR_TYPE_T)current_eq_config.bands[i].type;
        audio_eq_sw_iir_cfg.param[i].gain = current_eq_config.bands[i].gain;
        audio_eq_sw_iir_cfg.param[i].fc = current_eq_config.bands[i].frequency;
        audio_eq_sw_iir_cfg.param[i].Q = current_eq_config.bands[i].q;
    }
    TRACE(2, "[OPB_EQ] Preset updated in global: bands=%d", audio_eq_sw_iir_cfg.num);

    return 0;
}

// Get enabled state
bool app_opb_eq_is_enabled(void) {
    if (!eq_initialized) {
        app_opb_eq_init();
    }
    return eq_enabled;
}

// Set enabled state
int app_opb_eq_set_enabled(bool enabled, bool save_to_nv) {
    if (!eq_initialized) {
        app_opb_eq_init();
    }

    TRACE(2, "[OPB_EQ] Set enabled=%d (save=%d)", enabled, save_to_nv);

    bool was_enabled = eq_enabled;
    eq_enabled = enabled;

    // When disabling, clear the global config bands so audio_process.c picks up flat EQ
    // When enabling, restore from current_eq_config
    if (!enabled && was_enabled) {
        // Disabling: Set global config to flat (0 bands)
        TRACE(0, "[OPB_EQ] Disabling EQ - clearing global config");
        audio_eq_sw_iir_cfg.num = 0;
        audio_eq_sw_iir_cfg.gain0 = 0.0f;
        audio_eq_sw_iir_cfg.gain1 = 0.0f;
    } else if (enabled && !was_enabled) {
        // Enabling: Restore global config from current_eq_config
        TRACE(0, "[OPB_EQ] Enabling EQ - restoring global config");
        audio_eq_sw_iir_cfg.gain0 = current_eq_config.gain0;
        audio_eq_sw_iir_cfg.gain1 = current_eq_config.gain1;
        audio_eq_sw_iir_cfg.num = current_eq_config.num_bands;
        for (uint32_t i = 0; i < current_eq_config.num_bands && i < 8; i++) {
            audio_eq_sw_iir_cfg.param[i].type = (IIR_TYPE_T)current_eq_config.bands[i].type;
            audio_eq_sw_iir_cfg.param[i].gain = current_eq_config.bands[i].gain;
            audio_eq_sw_iir_cfg.param[i].fc = current_eq_config.bands[i].frequency;
            audio_eq_sw_iir_cfg.param[i].Q = current_eq_config.bands[i].q;
        }
    }

    if (save_to_nv) {
        if (nv_record_set_eq_enabled(&eq_enabled) != 0) {
            TRACE(0, "[OPB_EQ] Failed to save enabled state to NV");
            return -1;
        }
    }

    TRACE(1, "[OPB_EQ] EQ %s (global config bands=%d)",
          enabled ? "enabled" : "disabled", audio_eq_sw_iir_cfg.num);

    return 0;
}

// Get capabilities
int app_opb_eq_get_capabilities(opb_eq_capabilities_t *caps) {
    if (!caps) {
        return -1;
    }

    caps->version_major = OPB_EQ_VERSION_MAJOR;
    caps->version_minor = OPB_EQ_VERSION_MINOR;
    caps->version_patch = OPB_EQ_VERSION_PATCH;
    caps->max_bands = OPB_EQ_MAX_BANDS;
    caps->supported_filter_types = OPB_EQ_SUPPORTED_FILTERS;
    caps->min_frequency = OPB_EQ_FREQ_MIN;
    caps->max_frequency = OPB_EQ_FREQ_MAX;

    return 0;
}

// Validate configuration
int app_opb_eq_validate_config(const opb_eq_config_t *config) {
    if (!validate_config(config)) {
        return -1;
    }
    return 0;
}

// Forward declare IIR functions
int iir_set_cfg(const IIR_CFG_T *cfg);

// Get current EQ as IIR_CFG_T for audio processing integration
int app_opb_eq_get_iir_cfg(struct _IIR_CFG_T *iir_cfg_param, int sample_rate) {
    // Cast to proper type since header uses forward declaration
    IIR_CFG_T *iir_cfg = (IIR_CFG_T *)iir_cfg_param;

    if (!iir_cfg) {
        TRACE(0, "[OPB_EQ] get_iir_cfg: NULL pointer!");
        return -1;
    }

    if (!eq_initialized) {
        app_opb_eq_init();
    }

    // Convert current config to IIR_CFG_T structure
    memset(iir_cfg, 0, sizeof(IIR_CFG_T));

    iir_cfg->gain0 = current_eq_config.gain0;
    iir_cfg->gain1 = current_eq_config.gain1;
    iir_cfg->num = current_eq_config.num_bands;

    for (uint32_t i = 0; i < current_eq_config.num_bands && i < AUD_DAC_IIR_NUM_EQ; i++) {
        iir_cfg->param[i].type = (IIR_TYPE_T)current_eq_config.bands[i].type;
        iir_cfg->param[i].gain = current_eq_config.bands[i].gain;
        iir_cfg->param[i].fc = current_eq_config.bands[i].frequency;
        iir_cfg->param[i].Q = current_eq_config.bands[i].q;
    }

    return 0;
}

// Reset to flat
int app_opb_eq_reset(bool save_to_nv) {
    TRACE(0, "[OPB_EQ] Resetting to flat");

    opb_eq_config_t flat_config = OPB_EQ_CONFIG_DEFAULT_FLAT;
    current_eq_config = flat_config;
    current_preset = OPB_EQ_PRESET_FLAT;

    if (save_to_nv) {
        nv_record_set_eq_config(&current_eq_config);
        nv_record_set_eq_preset(&current_preset);
    }

    return 0;
}

// Apply to hardware
int app_opb_eq_apply_to_hardware(uint32_t sample_rate) {
    if (!eq_enabled) {
        TRACE(0, "[OPB_EQ] Skipping apply - EQ disabled");
        return 0;
    }

    return apply_config_to_hardware_internal(&current_eq_config, sample_rate);
}

// Internal helpers
static bool validate_config(const opb_eq_config_t *config) {
    if (!config) {
        return false;
    }

    if (config->num_bands > OPB_EQ_MAX_BANDS) {
        TRACE(2, "[OPB_EQ] Too many bands: %d (max %d)",
              config->num_bands, OPB_EQ_MAX_BANDS);
        return false;
    }

    // Validate global gains
    if (config->gain0 < OPB_EQ_GAIN_MIN || config->gain0 > OPB_EQ_GAIN_MAX ||
        config->gain1 < OPB_EQ_GAIN_MIN || config->gain1 > OPB_EQ_GAIN_MAX) {
        TRACE(0, "[OPB_EQ] Global gain out of range");
        return false;
    }

    // Validate each band
    for (uint32_t i = 0; i < config->num_bands; i++) {
        const opb_eq_band_t *band = &config->bands[i];

        // Check filter type
        if (band->type >= OPB_EQ_FILTER_MAX) {
            TRACE(2, "[OPB_EQ] Invalid filter type: band %d, type %d", i, band->type);
            return false;
        }

        // Check gain
        if (band->gain < OPB_EQ_GAIN_MIN || band->gain > OPB_EQ_GAIN_MAX) {
            TRACE(2, "[OPB_EQ] Gain out of range: band %d", i);
            return false;
        }

        // Check frequency
        if (band->frequency < OPB_EQ_FREQ_MIN || band->frequency > OPB_EQ_FREQ_MAX) {
            TRACE(2, "[OPB_EQ] Frequency out of range: band %d, freq=%d Hz", i, (int)band->frequency);
            return false;
        }

        // Check Q
        if (band->q < OPB_EQ_Q_MIN || band->q > OPB_EQ_Q_MAX) {
            TRACE(2, "[OPB_EQ] Q out of range: band %d", i);
            return false;
        }
    }

    return true;
}

static void load_preset_config(opb_eq_preset_t preset, opb_eq_config_t *config) {
    if (preset < sizeof(eq_presets) / sizeof(eq_presets[0])) {
        *config = eq_presets[preset];
        TRACE(2, "[OPB_EQ] Loaded preset %d with %d bands", preset, config->num_bands);
    }
}

static int apply_config_to_hardware_internal(const opb_eq_config_t *config, uint32_t sample_rate) {
    TRACE(2, "[OPB_EQ] Applying EQ to hardware (rate=%d, bands=%d)",
          sample_rate, config->num_bands);

    // Convert to IIR_CFG_T structure
    IIR_CFG_T iir_cfg;
    memset(&iir_cfg, 0, sizeof(iir_cfg));

    iir_cfg.gain0 = config->gain0;
    iir_cfg.gain1 = config->gain1;
    iir_cfg.num = config->num_bands;

    for (uint32_t i = 0; i < config->num_bands; i++) {
        iir_cfg.param[i].type = (IIR_TYPE_T)config->bands[i].type;
        iir_cfg.param[i].gain = config->bands[i].gain;
        iir_cfg.param[i].fc = config->bands[i].frequency;
        iir_cfg.param[i].Q = config->bands[i].q;

        TRACE(4, "[OPB_EQ]   Band %d: type=%d, gain=%.1f dB, fc=%.0f Hz, Q=%.2f",
              i, iir_cfg.param[i].type, iir_cfg.param[i].gain,
              iir_cfg.param[i].fc, iir_cfg.param[i].Q);
    }

    // Apply to hardware codec IIR
    enum AUD_SAMPRATE_T aud_rate;

    // Map sample rate (simplified - should be more comprehensive)
    switch (sample_rate) {
        case 8000: aud_rate = AUD_SAMPRATE_8000; break;
        case 16000: aud_rate = AUD_SAMPRATE_16000; break;
        case 22050: aud_rate = AUD_SAMPRATE_22050; break;
        case 24000: aud_rate = AUD_SAMPRATE_24000; break;
        case 44100: aud_rate = AUD_SAMPRATE_44100; break;
        case 48000: aud_rate = AUD_SAMPRATE_48000; break;
        case 96000: aud_rate = AUD_SAMPRATE_96000; break;
        case 192000: aud_rate = AUD_SAMPRATE_192000; break;
        default: aud_rate = AUD_SAMPRATE_48000; break;
    }

    HW_CODEC_IIR_CFG_T *hw_cfg = hw_codec_iir_get_cfg(aud_rate, &iir_cfg);
    if (!hw_cfg) {
        TRACE(0, "[OPB_EQ] Failed to get hardware IIR config");
        return -1;
    }

    int ret;
    // Open/enable the hardware IIR first (mono - each earbud is one channel)
    // Only open if not already opened
    if (!hw_iir_opened) {
        ret = hw_codec_iir_open(aud_rate, HW_CODEC_IIR_DAC, AUD_CHANNEL_MAP_CH0);
        if (ret != 0) {
            TRACE(1, "[OPB_EQ] Failed to open hardware IIR: %d", ret);
            return -2;
        }
        hw_iir_opened = true;
        TRACE(0, "[OPB_EQ] Hardware IIR opened successfully");
    } else {
        TRACE(0, "[OPB_EQ] Hardware IIR already open, skipping");
    }

    // Now set the configuration
    ret = hw_codec_iir_set_cfg(hw_cfg, aud_rate, HW_CODEC_IIR_DAC);
    if (ret != 0) {
        TRACE(1, "[OPB_EQ] Failed to set hardware IIR config: %d", ret);
        hw_codec_iir_close(HW_CODEC_IIR_DAC);  // Clean up
        return -3;
    }

    TRACE(0, "[OPB_EQ] Successfully applied EQ to hardware");
    return 0;
}

// Apply current EQ immediately to running audio stream
void app_opb_eq_apply_immediately(void) {
    if (!eq_initialized) {
        TRACE(0, "[OPB_EQ] Cannot apply - not initialized");
        return;
    }

    TRACE(2, "[OPB_EQ] Applying EQ immediately: preset=%d, enabled=%d, bands=%d",
          current_preset, eq_enabled, audio_eq_sw_iir_cfg.num);

    // If EQ is disabled, apply a flat config (num_bands = 0)
    if (!eq_enabled) {
        IIR_CFG_T flat_cfg;
        memset(&flat_cfg, 0, sizeof(IIR_CFG_T));
        flat_cfg.gain0 = 0.0f;
        flat_cfg.gain1 = 0.0f;
        flat_cfg.num = 0;  // No bands = flat/disabled

        TRACE(0, "[OPB_EQ] EQ is disabled, applying flat config");
        int ret = iir_set_cfg(&flat_cfg);
        if (ret != 0) {
            TRACE(1, "[OPB_EQ] Failed to apply flat config: %d", ret);
        } else {
            TRACE(0, "[OPB_EQ] EQ disabled successfully!");
        }
    } else {
        // EQ is enabled, apply the current config
        int ret = iir_set_cfg(&audio_eq_sw_iir_cfg);
        if (ret != 0) {
            TRACE(1, "[OPB_EQ] Failed to apply IIR config immediately: %d", ret);
        } else {
            TRACE(0, "[OPB_EQ] EQ applied successfully!");
        }
    }
}

#ifdef TWS_SYSTEM_ENABLED
// TWS sync functions (similar to button config)
static void opb_eq_tws_sync_info_prepare_handler(uint8_t *buf, uint16_t *len) {
    TRACE(0, "[OPB_EQ_TWS] Preparing EQ config to sync to peer");

    // Prepare sync data (config + enabled + preset)
    memcpy(buf, &current_eq_config, sizeof(opb_eq_config_t));
    buf += sizeof(opb_eq_config_t);

    *buf++ = eq_enabled ? 1 : 0;
    *buf++ = (uint8_t)current_preset;

    *len = sizeof(opb_eq_config_t) + 2;
    TRACE(2, "[OPB_EQ_TWS] Prepared: bands=%d, enabled=%d",
          current_eq_config.num_bands, eq_enabled);
}

static void opb_eq_tws_sync_info_received_handler(uint8_t *buf, uint16_t len) {
    TRACE(2, "[OPB_EQ_TWS] Received EQ config from peer, len=%d", len);

    if (len < sizeof(opb_eq_config_t) + 2) {
        TRACE(0, "[OPB_EQ_TWS] ERROR: Invalid length");
        return;
    }

    memcpy(&current_eq_config, buf, sizeof(opb_eq_config_t));
    buf += sizeof(opb_eq_config_t);

    eq_enabled = (*buf++ != 0);
    current_preset = (opb_eq_preset_t)*buf;

    TRACE(3, "[OPB_EQ_TWS] Applied from peer: bands=%d, enabled=%d, preset=%d",
          current_eq_config.num_bands, eq_enabled, current_preset);

    // Apply to hardware
    if (eq_enabled) {
        app_opb_eq_apply_to_hardware(48000);
    }
}

void app_opb_eq_tws_sync_init(void) {
    TRACE(0, "[OPB_EQ_TWS] Initializing TWS sync");

    // Register TWS sync handlers
    TWS_SYNC_USER_T user_opb_eq = {
        .sync_info_prepare_handler = opb_eq_tws_sync_info_prepare_handler,
        .sync_info_received_handler = opb_eq_tws_sync_info_received_handler,
        .sync_info_prepare_rsp_handler = opb_eq_tws_sync_info_prepare_handler,
        .sync_info_rsp_received_handler = opb_eq_tws_sync_info_received_handler,
        .sync_info_rsp_timeout_handler = NULL,
    };

    app_tws_if_register_sync_user(TWS_SYNC_USER_OPB_EQ, &user_opb_eq);
    TRACE(0, "[OPB_EQ_TWS] TWS sync initialized");
}
#else
void app_opb_eq_tws_sync_init(void) {
    // TWS not enabled, do nothing
}
#endif
