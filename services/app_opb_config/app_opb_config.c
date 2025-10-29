#include "app_opb_config.h"
#include "nvrecord_env.h"
#include "hal_trace.h"
#include <string.h>

#ifdef TWS_SYSTEM_ENABLED
#include "app_tws_if.h"
#endif

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

#ifdef TWS_SYSTEM_ENABLED
// TWS sync prepare handler - serialize config and device name to send to peer earbud
static void opb_config_tws_sync_info_prepare_handler(uint8_t *buf, uint16_t *len) {
    TRACE(0, "[OPB_CFG_TWS] Preparing config to sync to peer");

    opb_tws_sync_data_t sync_data;

    // Copy button config
    memcpy(&sync_data.config, &current_config, sizeof(opb_config_t));

    // Copy device name from NV storage
    char *nv_device_name = NULL;
    if (nv_record_get_device_name(&nv_device_name) == 0 && nv_device_name != NULL) {
        strncpy(sync_data.device_name, nv_device_name, sizeof(sync_data.device_name) - 1);
        sync_data.device_name[sizeof(sync_data.device_name) - 1] = '\0';
    } else {
        // If we can't get device name, send empty string
        sync_data.device_name[0] = '\0';
    }

    // Copy to output buffer
    memcpy(buf, &sync_data, sizeof(opb_tws_sync_data_t));
    *len = sizeof(opb_tws_sync_data_t);

    TRACE(4, "[OPB_CFG_TWS] Prepared: Left ST=%d DT=%d TT=%d LP=%d",
          current_config.left.single_tap,
          current_config.left.double_tap,
          current_config.left.triple_tap,
          current_config.left.long_press);
    TRACE(4, "[OPB_CFG_TWS] Prepared: Right ST=%d DT=%d TT=%d LP=%d",
          current_config.right.single_tap,
          current_config.right.double_tap,
          current_config.right.triple_tap,
          current_config.right.long_press);
    TRACE(1, "[OPB_CFG_TWS] Prepared: Device name='%s'", sync_data.device_name);
}

// TWS sync received handler - deserialize and apply config and device name from peer earbud
static void opb_config_tws_sync_info_received_handler(uint8_t *buf, uint16_t len) {
    TRACE(2, "[OPB_CFG_TWS] Received config from peer, len=%d", len);

    if (len != sizeof(opb_tws_sync_data_t)) {
        TRACE(2, "[OPB_CFG_TWS] ERROR: Invalid length %d, expected %d",
              len, sizeof(opb_tws_sync_data_t));
        return;
    }

    opb_tws_sync_data_t *sync_data = (opb_tws_sync_data_t *)buf;

    TRACE(4, "[OPB_CFG_TWS] Received: Left ST=%d DT=%d TT=%d LP=%d",
          sync_data->config.left.single_tap,
          sync_data->config.left.double_tap,
          sync_data->config.left.triple_tap,
          sync_data->config.left.long_press);
    TRACE(4, "[OPB_CFG_TWS] Received: Right ST=%d DT=%d TT=%d LP=%d",
          sync_data->config.right.single_tap,
          sync_data->config.right.double_tap,
          sync_data->config.right.triple_tap,
          sync_data->config.right.long_press);
    TRACE(1, "[OPB_CFG_TWS] Received: Device name='%s'", sync_data->device_name);

    // Apply the received button config (save to both RAM and NV)
    if (app_opb_config_set(&sync_data->config, true) == 0) {
        TRACE(0, "[OPB_CFG_TWS] Button config applied successfully from peer");
    } else {
        TRACE(0, "[OPB_CFG_TWS] ERROR: Failed to apply button config from peer");
    }

    // Apply the received device name (save to NV)
    if (nv_record_set_device_name(sync_data->device_name) == 0) {
        TRACE(0, "[OPB_CFG_TWS] Device name applied successfully from peer");

        // Update BT/BLE device names immediately
        extern void bt_init_device_names(void);
        bt_init_device_names();
    } else {
        TRACE(0, "[OPB_CFG_TWS] ERROR: Failed to apply device name from peer");
    }
}

// Initialize TWS sync for OPB config
void app_opb_config_tws_sync_init(void) {
    TRACE(0, "[OPB_CFG_TWS] Initializing TWS sync");

    TWS_SYNC_USER_T user_opb_config = {
        .sync_info_prepare_handler = opb_config_tws_sync_info_prepare_handler,
        .sync_info_received_handler = opb_config_tws_sync_info_received_handler,
        .sync_info_prepare_rsp_handler = opb_config_tws_sync_info_prepare_handler,
        .sync_info_rsp_received_handler = opb_config_tws_sync_info_received_handler,
        .sync_info_rsp_timeout_handler = NULL,
    };

    app_tws_if_register_sync_user(TWS_SYNC_USER_OPB_CONFIG, &user_opb_config);
    TRACE(0, "[OPB_CFG_TWS] TWS sync initialized");
}
#endif // TWS_SYSTEM_ENABLED
