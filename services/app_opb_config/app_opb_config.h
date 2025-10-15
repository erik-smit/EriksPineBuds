#ifndef _APP_OPB_CONFIG_H_
#define _APP_OPB_CONFIG_H_

#include "opb_config_common.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    OPB_GESTURE_SINGLE_TAP,
    OPB_GESTURE_DOUBLE_TAP,
    OPB_GESTURE_TRIPLE_TAP,
    OPB_GESTURE_LONG_PRESS,
} opb_gesture_t;

/**
 * Initialize configuration from NV storage
 * @return 0 on success, -1 on error
 */
int app_opb_config_init(void);

/**
 * Get action for a specific gesture on a specific earbud
 * @param is_left true for left earbud, false for right
 * @param gesture the gesture type
 * @return the action code
 */
opb_button_action_t app_opb_config_get_action(bool is_left, opb_gesture_t gesture);

/**
 * Get current configuration
 * @param config pointer to receive configuration
 * @return 0 on success, -1 on error
 */
int app_opb_config_get(opb_config_t *config);

/**
 * Set configuration
 * @param config new configuration
 * @param save_to_nv whether to persist to NV storage
 * @return 0 on success, negative on error
 */
int app_opb_config_set(const opb_config_t *config, bool save_to_nv);

/**
 * Reset to factory defaults
 * @return 0 on success, -1 on error
 */
int app_opb_config_reset(void);

#ifdef __cplusplus
}
#endif

#endif // _APP_OPB_CONFIG_H_
