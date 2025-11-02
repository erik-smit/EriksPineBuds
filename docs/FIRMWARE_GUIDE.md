# OpenPineBuds Firmware Implementation Guide

## Overview

This guide provides step-by-step instructions for implementing the BLE GATT configuration service in the OpenPineBuds firmware. The implementation will allow a companion app to configure touch button actions.

## Prerequisites

- Familiarity with C/C++ programming
- Understanding of BLE GATT concepts
- OpenPineBuds development environment set up
- Read `docs/GATT_SPEC.md` for the service specification

## Architecture

The implementation consists of four main components:

1. **BLE GATT Service** - Exposes configuration characteristics
2. **Configuration Storage** - Persists settings in NV memory
3. **Key Handler** - Executes configured actions
4. **Action Dispatcher** - Maps gestures to actions

## Implementation Steps

### Step 1: Define Configuration Data Structures

**Location**: Create `OpenPineBuds/services/ble_profiles/opb_config/opb_config_common.h`

```c
#ifndef _OPB_CONFIG_COMMON_H_
#define _OPB_CONFIG_COMMON_H_

#include <stdint.h>

// Button action codes (must match GATT_SPEC.md)
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

// Default configuration
#define OPB_CONFIG_DEFAULT_LEFT { \
    .single_tap = OPB_ACTION_PLAY_PAUSE, \
    .double_tap = OPB_ACTION_PREVIOUS_TRACK, \
    .triple_tap = OPB_ACTION_VOLUME_DOWN, \
    .long_press = OPB_ACTION_TOGGLE_ANC \
}

#define OPB_CONFIG_DEFAULT_RIGHT { \
    .single_tap = OPB_ACTION_PLAY_PAUSE, \
    .double_tap = OPB_ACTION_NEXT_TRACK, \
    .triple_tap = OPB_ACTION_VOLUME_UP, \
    .long_press = OPB_ACTION_TOGGLE_ANC \
}

#define OPB_CONFIG_VERSION_MAJOR 1
#define OPB_CONFIG_VERSION_MINOR 0
#define OPB_CONFIG_VERSION_PATCH 0

#endif // _OPB_CONFIG_COMMON_H_
```

### Step 2: Add NV Storage Support

**Location**: Modify `OpenPineBuds/services/nvrecord/nvrecord_env.h`

Add to the `nvrecord_env_t` structure (around line 76):

```c
#include "opb_config_common.h"  // Add this include

struct nvrecord_env_t
{
    // ... existing fields ...

    uint8_t  flag_value[8];
    AI_MANAGER_INFO_T   aiManagerInfo;

    // Add this field:
    opb_config_t button_config;
};
```

**Location**: Modify `OpenPineBuds/services/nvrecord/nvrecord_env.c`

In `nv_record_env_new()` function, initialize default config (around line 50):

```c
// Add initialization for button config
nvrecord_env_p->button_config.left = (opb_earbud_config_t)OPB_CONFIG_DEFAULT_LEFT;
nvrecord_env_p->button_config.right = (opb_earbud_config_t)OPB_CONFIG_DEFAULT_RIGHT;
nvrecord_env_p->button_config.version_major = OPB_CONFIG_VERSION_MAJOR;
nvrecord_env_p->button_config.version_minor = OPB_CONFIG_VERSION_MINOR;
nvrecord_env_p->button_config.version_patch = OPB_CONFIG_VERSION_PATCH;
```

Create helper functions at the end of the file:

```c
// Get button configuration
int nv_record_get_button_config(opb_config_t **config) {
    if (!nvrecord_env_p)
        return -1;

    *config = &nvrecord_env_p->button_config;
    return 0;
}

// Set button configuration and mark for save
int nv_record_set_button_config(const opb_config_t *config) {
    if (!nvrecord_env_p || !config)
        return -1;

    nvrecord_env_p->button_config = *config;
    nv_record_update_runtime_userdata();
    return 0;
}
```

Add declarations to header file:

```c
// Add to nvrecord_env.h
int nv_record_get_button_config(opb_config_t **config);
int nv_record_set_button_config(const opb_config_t *config);
```

### Step 3: Create Configuration Manager

**Location**: Create `OpenPineBuds/services/app_opb_config/app_opb_config.c`

```c
#include "app_opb_config.h"
#include "opb_config_common.h"
#include "nvrecord_env.h"
#include "hal_trace.h"
#include "app_ibrt_if.h"

// Current runtime configuration
static opb_config_t current_config;
static bool config_initialized = false;

// Initialize configuration from NV storage
int app_opb_config_init(void) {
    opb_config_t *nv_config = NULL;

    if (nv_record_get_button_config(&nv_config) == 0) {
        current_config = *nv_config;
        config_initialized = true;
        TRACE(0, "Button config loaded from NV");
        return 0;
    }

    // Use defaults if NV read failed
    current_config.left = (opb_earbud_config_t)OPB_CONFIG_DEFAULT_LEFT;
    current_config.right = (opb_earbud_config_t)OPB_CONFIG_DEFAULT_RIGHT;
    current_config.version_major = OPB_CONFIG_VERSION_MAJOR;
    current_config.version_minor = OPB_CONFIG_VERSION_MINOR;
    current_config.version_patch = OPB_CONFIG_VERSION_PATCH;
    config_initialized = true;

    TRACE(0, "Button config initialized with defaults");
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
    if (!config)
        return -1;

    // Validate action codes
    if (!validate_config(config))
        return -2;

    current_config = *config;

    if (save_to_nv) {
        if (nv_record_set_button_config(&current_config) != 0) {
            TRACE(0, "Failed to save config to NV");
            return -3;
        }
        TRACE(0, "Config saved to NV");
    }

    return 0;
}

// Reset to factory defaults
int app_opb_config_reset(void) {
    current_config.left = (opb_earbud_config_t)OPB_CONFIG_DEFAULT_LEFT;
    current_config.right = (opb_earbud_config_t)OPB_CONFIG_DEFAULT_RIGHT;

    return app_opb_config_set(&current_config, true);
}

// Validate configuration
static bool validate_config(const opb_config_t *config) {
    // Check version compatibility
    if (config->version_major != OPB_CONFIG_VERSION_MAJOR)
        return false;

    // Validate all action codes
    uint32_t *actions = (uint32_t *)config;
    for (int i = 0; i < 8; i++) {  // 8 gestures total (4 per earbud)
        if (actions[i] >= OPB_ACTION_MAX)
            return false;
    }

    return true;
}
```

**Header file**: `OpenPineBuds/services/app_opb_config/app_opb_config.h`

```c
#ifndef _APP_OPB_CONFIG_H_
#define _APP_OPB_CONFIG_H_

#include "opb_config_common.h"

typedef enum {
    OPB_GESTURE_SINGLE_TAP,
    OPB_GESTURE_DOUBLE_TAP,
    OPB_GESTURE_TRIPLE_TAP,
    OPB_GESTURE_LONG_PRESS,
} opb_gesture_t;

int app_opb_config_init(void);
opb_button_action_t app_opb_config_get_action(bool is_left, opb_gesture_t gesture);
int app_opb_config_get(opb_config_t *config);
int app_opb_config_set(const opb_config_t *config, bool save_to_nv);
int app_opb_config_reset(void);

#endif // _APP_OPB_CONFIG_H_
```

### Step 4: Modify Key Handler

**Location**: Modify `OpenPineBuds/apps/main/key_handler.cpp`

Add includes:

```cpp
#include "app_opb_config.h"
```

Add action dispatcher function:

```cpp
// Execute configured action
void execute_button_action(opb_button_action_t action) {
    switch (action) {
        case OPB_ACTION_NONE:
            break;
        case OPB_ACTION_PLAY_PAUSE:
            send_play_pause();
            break;
        case OPB_ACTION_NEXT_TRACK:
            send_next_track();
            break;
        case OPB_ACTION_PREVIOUS_TRACK:
            send_prev_track();
            break;
        case OPB_ACTION_VOLUME_UP:
            send_vol_up();
            break;
        case OPB_ACTION_VOLUME_DOWN:
            send_vol_down();
            break;
        case OPB_ACTION_TOGGLE_ANC:
            send_enable_disable_anc();
            break;
        // TODO: Implement other actions
        default:
            TRACE(1, "Unimplemented action: %d", action);
            break;
    }
}
```

Modify gesture handlers to use configuration:

```cpp
void app_key_single_tap(APP_KEY_STATUS *status, void *param) {
    TRACE(2, "%s event %d", __func__, status->event);

    bool is_left = app_tws_is_left_side();
    opb_button_action_t action = app_opb_config_get_action(is_left, OPB_GESTURE_SINGLE_TAP);
    execute_button_action(action);
}

void app_key_double_tap(APP_KEY_STATUS *status, void *param) {
    TRACE(2, "%s event %d", __func__, status->event);

    // Check if single bud mode
    if (!app_tws_ibrt_tws_link_connected()) {
        // In single bud mode, use default action (next track)
        send_next_track();
    } else {
        bool is_left = app_tws_is_left_side();
        opb_button_action_t action = app_opb_config_get_action(is_left, OPB_GESTURE_DOUBLE_TAP);
        execute_button_action(action);
    }
}

void app_key_triple_tap(APP_KEY_STATUS *status, void *param) {
    TRACE(2, "%s event %d", __func__, status->event);

    if (!app_tws_ibrt_tws_link_connected()) {
        send_vol_up();
    } else {
        bool is_left = app_tws_is_left_side();
        opb_button_action_t action = app_opb_config_get_action(is_left, OPB_GESTURE_TRIPLE_TAP);
        execute_button_action(action);
    }
}

void app_key_long_press_down(APP_KEY_STATUS *status, void *param) {
    TRACE(2, "%s event %d", __func__, status->event);

    if (!app_tws_ibrt_tws_link_connected()) {
        send_prev_track();
    } else {
        bool is_left = app_tws_is_left_side();
        opb_button_action_t action = app_opb_config_get_action(is_left, OPB_GESTURE_LONG_PRESS);
        execute_button_action(action);
    }
}
```

Initialize config in `app_key_init()`:

```cpp
void app_key_init(void) {
    uint8_t i = 0;
    TRACE(1, "%s", __func__);

    // Initialize button configuration
    app_opb_config_init();

    // ... rest of existing code ...
}
```

### Step 5: Create BLE GATT Service

**Note**: This is a complex task. Use the datapath profile as a template.

**Location**: Create directory structure:
```
OpenPineBuds/services/ble_profiles/opb_config/
  opb_configps/
    api/
      opb_configps_task.h
    src/
      opb_configps.h
      opb_configps.c
      opb_configps_task.c
```

Key points for implementation:

1. Define service UUID: `0000FFC0-0000-1000-8000-00805F9B34FB`
2. Create 6 characteristics (see GATT_SPEC.md)
3. Implement read handlers that return current config
4. Implement write handlers that update config via `app_opb_config_set()`
5. Implement apply command handler with CRC-16 validation
6. Register service in BLE stack during initialization

**Service structure** (similar to datapathps):

```c
enum {
    OPB_CFG_IDX_SVC,

    OPB_CFG_IDX_LEFT_CHAR,
    OPB_CFG_IDX_LEFT_VAL,

    OPB_CFG_IDX_RIGHT_CHAR,
    OPB_CFG_IDX_RIGHT_VAL,

    OPB_CFG_IDX_VERSION_CHAR,
    OPB_CFG_IDX_VERSION_VAL,

    OPB_CFG_IDX_DEVICE_INFO_CHAR,
    OPB_CFG_IDX_DEVICE_INFO_VAL,

    OPB_CFG_IDX_APPLY_CMD_CHAR,
    OPB_CFG_IDX_APPLY_CMD_VAL,

    OPB_CFG_IDX_STATUS_CHAR,
    OPB_CFG_IDX_STATUS_VAL,
    OPB_CFG_IDX_STATUS_NTF_CFG,

    OPB_CFG_IDX_NB,
};
```

### Step 6: Register Service in BLE Stack

**Location**: Modify `OpenPineBuds/services/ble_app/app_ble_mode_switch.c` (or similar BLE app file)

Add service registration:

```c
#if (BLE_OPB_CONFIG_SERVER)
#include "opb_configps.h"
#endif

// In service registration function:
prf_register_atthdl2gatt(&app_env.prf_serv, app_task);

#if (BLE_OPB_CONFIG_SERVER)
app_opb_configps_add_opb_configps();
#endif
```

### Step 7: Update Build System

**Location**: Modify `OpenPineBuds/services/ble_profiles/Makefile`

Add:

```makefile
BLE_PROFILES_INCLUDES += \
    -Iservices/ble_profiles/opb_config/opb_configps/api \
    -Iservices/ble_profiles/opb_config/opb_configps/src

ble_profiles_sources += \
    services/ble_profiles/opb_config/opb_configps/src/opb_configps.c \
    services/ble_profiles/opb_config/opb_configps/src/opb_configps_task.c
```

**Location**: Modify build configuration to enable the profile

Add to `OpenPineBuds/config/besXXXX/target.mk` or similar:

```makefile
export BLE_OPB_CONFIG_SERVER ?= 1
```

### Step 8: Testing

1. **Build and flash firmware**:
   ```bash
   ./build.sh
   ./download.sh
   ```

2. **Test with nRF Connect**:
   - Scan for OpenPineBuds
   - Connect to device
   - Verify service UUID `0000FFC0...` is present
   - Read configuration characteristics
   - Write new configuration
   - Write apply command
   - Verify config persists after reboot

3. **Test touch controls**:
   - Configure different actions via BLE
   - Test each gesture on both earbuds
   - Verify actions execute correctly

## Troubleshooting

### Service Not Visible
- Check BLE_OPB_CONFIG_SERVER is defined and set to 1
- Verify service registration in BLE app init
- Check GATT database size limits

### Config Not Persisting
- Verify NV storage API calls
- Check flash write success
- Ensure proper NV record structure size

### Actions Not Working
- Add TRACE statements in execute_button_action()
- Verify config is loaded on boot
- Check app_opb_config_get_action() returns correct values

## Next Steps

After firmware implementation:

1. Test thoroughly with nRF Connect app
2. Document any changes or deviations from spec
3. Create Android companion app
4. Integrate and test end-to-end

## References

- `docs/GATT_SPEC.md` - GATT service specification
- `services/ble_profiles/datapath/` - Example BLE service implementation
- `services/nvrecord/` - NV storage implementation
- `apps/main/key_handler.cpp` - Current key handling implementation

