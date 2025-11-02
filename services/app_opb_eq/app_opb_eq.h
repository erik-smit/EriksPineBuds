#ifndef _APP_OPB_EQ_H_
#define _APP_OPB_EQ_H_

#include "opb_eq_common.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Forward declarations for audio processing types
// (actual types defined in iir_process.h and hal_aud.h)
struct _IIR_CFG_T;
int app_opb_eq_get_iir_cfg(struct _IIR_CFG_T *iir_cfg, int sample_rate);

/**
 * Initialize EQ configuration from NV storage
 * @return 0 on success, -1 on error
 */
int app_opb_eq_init(void);

/**
 * Get current EQ configuration
 * @param config pointer to receive configuration
 * @return 0 on success, -1 on error
 */
int app_opb_eq_get_config(opb_eq_config_t *config);

/**
 * Set EQ configuration
 * @param config new EQ configuration
 * @param save_to_nv whether to persist to NV storage
 * @return 0 on success, negative on error
 */
int app_opb_eq_set_config(const opb_eq_config_t *config, bool save_to_nv);

/**
 * Get current EQ preset
 * @return current preset ID
 */
opb_eq_preset_t app_opb_eq_get_preset(void);

/**
 * Set EQ preset (loads predefined configuration)
 * @param preset preset ID to load
 * @param save_to_nv whether to persist to NV storage
 * @return 0 on success, negative on error
 */
int app_opb_eq_set_preset(opb_eq_preset_t preset, bool save_to_nv);

/**
 * Get EQ enabled/disabled state
 * @return true if enabled, false if bypassed
 */
bool app_opb_eq_is_enabled(void);

/**
 * Enable or disable EQ
 * @param enabled true to enable, false to bypass
 * @param save_to_nv whether to persist to NV storage
 * @return 0 on success, -1 on error
 */
int app_opb_eq_set_enabled(bool enabled, bool save_to_nv);

/**
 * Get EQ capabilities
 * @param caps pointer to receive capabilities
 * @return 0 on success, -1 on error
 */
int app_opb_eq_get_capabilities(opb_eq_capabilities_t *caps);

/**
 * Validate EQ configuration
 * @param config configuration to validate
 * @return 0 if valid, negative error code otherwise
 */
int app_opb_eq_validate_config(const opb_eq_config_t *config);

/**
 * Reset to flat EQ (bypass)
 * @param save_to_nv whether to persist to NV storage
 * @return 0 on success, -1 on error
 */
int app_opb_eq_reset(bool save_to_nv);

/**
 * Apply current EQ configuration to hardware
 * Called internally when config changes, and on audio stream start
 * @param sample_rate current audio sample rate
 * @return 0 on success, -1 on error
 */
int app_opb_eq_apply_to_hardware(uint32_t sample_rate);

/**
 * Initialize TWS sync for EQ sharing between earbuds
 */
void app_opb_eq_tws_sync_init(void);

/**
 * Apply current EQ immediately (without waiting for audio stream restart)
 * Used for live EQ updates via BLE
 */
void app_opb_eq_apply_immediately(void);

#ifdef __cplusplus
}
#endif

#endif // _APP_OPB_EQ_H_
