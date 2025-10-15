#include "app_opb_config.h"
#include "nvrecord_env.h"
#include "hal_trace.h"
#include <string.h>

// Current runtime configuration
static opb_config_t current_config;
static bool config_initialized = false;

// Forward declaration
static bool validate_config(const opb_config_t *config);

// Initialize configuration from NV storage
int app_opb_config_init(void) {
    opb_config_t *nv_config = NULL;

    TRACE(0, "[OPB_CFG] Initializing button configuration");

    if (nv_record_get_button_config(&nv_config) == 0) {
        current_config = *nv_config;
        config_initialized = true;
        TRACE(0, "[OPB_CFG] Loaded from NV storage");
        TRACE(4, "[OPB_CFG] Left: ST=%d DT=%d TT=%d LP=%d",
              nv_config->left.single_tap,
              nv_config->left.double_tap,
              nv_config->left.triple_tap,
              nv_config->left.long_press);
        TRACE(4, "[OPB_CFG] Right: ST=%d DT=%d TT=%d LP=%d",
              nv_config->right.single_tap,
              nv_config->right.double_tap,
              nv_config->right.triple_tap,
              nv_config->right.long_press);
        return 0;
    }

    // Use defaults if NV read failed
    opb_earbud_config_t default_left = OPB_CONFIG_DEFAULT_LEFT_INIT;
    opb_earbud_config_t default_right = OPB_CONFIG_DEFAULT_RIGHT_INIT;
    current_config.left = default_left;
    current_config.right = default_right;
    current_config.version_major = OPB_CONFIG_VERSION_MAJOR;
    current_config.version_minor = OPB_CONFIG_VERSION_MINOR;
    current_config.version_patch = OPB_CONFIG_VERSION_PATCH;
    current_config.reserved = 0;
    config_initialized = true;

    TRACE(0, "[OPB_CFG] Initialized with defaults");
    return 0;
}

// Get action for a specific gesture
opb_button_action_t app_opb_config_get_action(bool is_left, opb_gesture_t gesture) {
    if (!config_initialized) {
        app_opb_config_init();
    }

    opb_earbud_config_t *earbud = is_left ? &current_config.left : &current_config.right;

    switch (gesture) {
        case OPB_GESTURE_SINGLE_TAP:
            return (opb_button_action_t)earbud->single_tap;
        case OPB_GESTURE_DOUBLE_TAP:
            return (opb_button_action_t)earbud->double_tap;
        case OPB_GESTURE_TRIPLE_TAP:
            return (opb_button_action_t)earbud->triple_tap;
        case OPB_GESTURE_LONG_PRESS:
            return (opb_button_action_t)earbud->long_press;
        default:
            return OPB_ACTION_NONE;
    }
}

// Get current configuration
int app_opb_config_get(opb_config_t *config) {
    if (!config || !config_initialized)
        return -1;

    *config = current_config;
    return 0;
}

// Set and save configuration
int app_opb_config_set(const opb_config_t *config, bool save_to_nv) {
    if (!config) {
        TRACE(0, "[OPB_CFG] Set failed: NULL config");
        return -1;
    }

    // Validate action codes
    if (!validate_config(config)) {
        TRACE(0, "[OPB_CFG] Set failed: invalid config");
        return -2;
    }

    current_config = *config;
    TRACE(1, "[OPB_CFG] Configuration updated (save=%d)", save_to_nv);

    if (save_to_nv) {
        if (nv_record_set_button_config(&current_config) != 0) {
            TRACE(0, "[OPB_CFG] Failed to save config to NV");
            return -3;
        }
        TRACE(0, "[OPB_CFG] Config saved to NV");
    }

    return 0;
}

// Reset to factory defaults
int app_opb_config_reset(void) {
    TRACE(0, "[OPB_CFG] Resetting to defaults");

    opb_earbud_config_t default_left = OPB_CONFIG_DEFAULT_LEFT_INIT;
    opb_earbud_config_t default_right = OPB_CONFIG_DEFAULT_RIGHT_INIT;
    current_config.left = default_left;
    current_config.right = default_right;
    current_config.version_major = OPB_CONFIG_VERSION_MAJOR;
    current_config.version_minor = OPB_CONFIG_VERSION_MINOR;
    current_config.version_patch = OPB_CONFIG_VERSION_PATCH;

    return app_opb_config_set(&current_config, true);
}

// Validate configuration
static bool validate_config(const opb_config_t *config) {
    // Check version compatibility
    if (config->version_major != OPB_CONFIG_VERSION_MAJOR) {
        TRACE(2, "[OPB_CFG] Version mismatch: expected %d, got %d",
              OPB_CONFIG_VERSION_MAJOR, config->version_major);
        return false;
    }

    // Validate all action codes
    uint32_t actions[8] = {
        config->left.single_tap, config->left.double_tap,
        config->left.triple_tap, config->left.long_press,
        config->right.single_tap, config->right.double_tap,
        config->right.triple_tap, config->right.long_press
    };

    for (int i = 0; i < 8; i++) {
        if (actions[i] >= OPB_ACTION_MAX) {
            TRACE(2, "[OPB_CFG] Invalid action code: %d", actions[i]);
            return false;
        }
    }

    return true;
}
