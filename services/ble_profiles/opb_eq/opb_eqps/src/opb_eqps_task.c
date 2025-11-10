/**
 ****************************************************************************************
 * @addtogroup OPB_EQPSTASK
 * @{
 ****************************************************************************************
 */

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "rwip_config.h"

#if (BLE_OPB_EQ_SERVER)
#include "attm.h"
#include "opb_eqps.h"
#include "opb_eqps_task.h"
#include "gap.h"
#include "gapc_task.h"
#include "gattc_task.h"
#include "prf_utils.h"
#include "co_utils.h"
#include "ke_mem.h"

// Include our EQ manager
#include "app_opb_eq.h"
#include "opb_eq_common.h"

#ifdef TWS_SYSTEM_ENABLED
#include "app_tws_if.h"
#endif

/*
 * LOCAL FUNCTIONS DEFINITIONS
 ****************************************************************************************
 */

/**
 ****************************************************************************************
 * @brief Handles reception of the read request from peer device
 ****************************************************************************************
 */
static int gattc_read_req_ind_handler(ke_msg_id_t const msgid,
                                      struct gattc_read_req_ind const *param,
                                      ke_task_id_t const dest_id,
                                      ke_task_id_t const src_id) {
    struct opb_eqps_env_tag *opb_eqps_env = PRF_ENV_GET(OPB_EQPS, opb_eqps);
    uint8_t conidx = KE_IDX_GET(src_id);
    uint8_t status = ATT_ERR_NO_ERROR;
    struct gattc_read_cfm *cfm;

    uint8_t value[OPB_EQPS_MAX_LEN];
    uint8_t length = 0;

    if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_EQ_CFG_VAL)) {
        // Read EQ configuration
        opb_eq_config_t config;
        if (app_opb_eq_get_config(&config) == 0) {
            memcpy(value, &config, sizeof(opb_eq_config_t));
            length = sizeof(opb_eq_config_t);
        } else {
            status = ATT_ERR_APP_ERROR;
        }
    }
    else if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_PRESET_VAL)) {
        // Read current preset
        opb_eq_preset_t preset_enum = app_opb_eq_get_preset();
        uint32_t preset_value = (uint32_t)preset_enum;
        memcpy(value, &preset_value, sizeof(uint32_t));
        length = 4;
    }
    else if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_ENABLE_VAL)) {
        // Read EQ enabled state
        bool enabled = app_opb_eq_is_enabled();
        value[0] = enabled ? 1 : 0;
        value[1] = 0;
        value[2] = 0;
        value[3] = 0;
        length = 4;
    }
    else if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_CAPS_VAL)) {
        // Read EQ capabilities
        opb_eq_capabilities_t caps;
        if (app_opb_eq_get_capabilities(&caps) == 0) {
            memcpy(value, &caps, sizeof(opb_eq_capabilities_t));
            length = sizeof(opb_eq_capabilities_t);
        } else {
            status = ATT_ERR_APP_ERROR;
        }
    }
    else if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_STATUS_NTF_CFG)) {
        // Read notification configuration
        uint16_t ntf_cfg = opb_eqps_env->ntf_cfg[conidx];
        value[0] = ntf_cfg & 0xFF;
        value[1] = (ntf_cfg >> 8) & 0xFF;
        length = 2;
    }
    else {
        status = ATT_ERR_REQUEST_NOT_SUPPORTED;
    }

    // Send read response
    cfm = KE_MSG_ALLOC_DYN(GATTC_READ_CFM, src_id, dest_id, gattc_read_cfm, length);
    cfm->handle = param->handle;
    cfm->status = status;
    cfm->length = length;
    if (status == ATT_ERR_NO_ERROR && length > 0) {
        memcpy(cfm->value, value, length);
    }
    ke_msg_send(cfm);

    return (KE_MSG_CONSUMED);
}

/**
 ****************************************************************************************
 * @brief Handles reception of the write request from peer device
 ****************************************************************************************
 */
static int gattc_write_req_ind_handler(ke_msg_id_t const msgid,
                                       struct gattc_write_req_ind const *param,
                                       ke_task_id_t const dest_id,
                                       ke_task_id_t const src_id) {
    struct opb_eqps_env_tag *opb_eqps_env = PRF_ENV_GET(OPB_EQPS, opb_eqps);
    uint8_t conidx = KE_IDX_GET(src_id);
    uint8_t status = ATT_ERR_NO_ERROR;

    if (opb_eqps_env != NULL) {
        if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_EQ_CFG_VAL)) {
            // Write EQ configuration
            TRACE(1, "[OPB_EQ_TASK] BLE write to EQ config characteristic (%d bytes)", param->length);
            if (param->length == sizeof(opb_eq_config_t)) {
                opb_eq_config_t config;
                memcpy(&config, param->value, sizeof(opb_eq_config_t));

                // Validate configuration
                if (app_opb_eq_validate_config(&config) == 0) {
                    // Set config (don't save yet, wait for apply command)
                    if (app_opb_eq_set_config(&config, false) != 0) {
                        TRACE(0, "[OPB_EQ_TASK] Failed to set EQ config");
                        status = ATT_ERR_APP_ERROR;
                    } else {
                        TRACE(1, "[OPB_EQ_TASK] EQ config updated (bands=%d)", config.num_bands);
                    }
                } else {
                    TRACE(0, "[OPB_EQ_TASK] Invalid EQ configuration");
                    status = ATT_ERR_INVALID_ATTRIBUTE_VAL_LEN;
                }
            } else {
                TRACE(2, "[OPB_EQ_TASK] Invalid length: %d, expected: %d", param->length, sizeof(opb_eq_config_t));
                status = ATT_ERR_INVALID_ATTRIBUTE_VAL_LEN;
            }
        }
        else if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_PRESET_VAL)) {
            // Write EQ preset
            TRACE(0, "[OPB_EQ_TASK] BLE write to EQ preset characteristic");
            if (param->length == 4) {
                uint32_t preset_value;
                memcpy(&preset_value, param->value, sizeof(uint32_t));
                opb_eq_preset_t preset = (opb_eq_preset_t)preset_value;

                // Set preset (don't save yet, wait for apply command)
                if (app_opb_eq_set_preset(preset, false) != 0) {
                    TRACE(1, "[OPB_EQ_TASK] Failed to set preset %d", preset);
                    status = ATT_ERR_APP_ERROR;
                } else {
                    TRACE(1, "[OPB_EQ_TASK] Preset set to %d", preset);
                }
            } else {
                status = ATT_ERR_INVALID_ATTRIBUTE_VAL_LEN;
            }
        }
        else if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_ENABLE_VAL)) {
            // Write EQ enable/disable
            TRACE(0, "[OPB_EQ_TASK] BLE write to EQ enable characteristic");
            if (param->length >= 1) {
                bool enabled = (param->value[0] != 0);

                if (app_opb_eq_set_enabled(enabled, false) != 0) {
                    TRACE(0, "[OPB_EQ_TASK] Failed to set enabled state");
                    status = ATT_ERR_APP_ERROR;
                } else {
                    TRACE(1, "[OPB_EQ_TASK] EQ enabled=%d", enabled);
                }
            } else {
                status = ATT_ERR_INVALID_ATTRIBUTE_VAL_LEN;
            }
        }
        else if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_APPLY_VAL)) {
            // Apply command - save current config to NV and sync
            TRACE(0, "[OPB_EQ_TASK] BLE write to apply command");
            if (param->length >= 1) {
                uint8_t command = param->value[0];

                if (command == 0x01) {
                    // Apply and save to NV
                    TRACE(0, "[OPB_EQ_TASK] Applying and saving EQ config");

                    // Get current config and save it
                    opb_eq_config_t config;
                    bool enabled = app_opb_eq_is_enabled();
                    opb_eq_preset_t preset = app_opb_eq_get_preset();

                    if (app_opb_eq_get_config(&config) == 0) {
                        // Re-save with NV flag
                        TRACE(0, "[OPB_EQ_TASK] Saving config to NV");
                        app_opb_eq_set_config(&config, true);
                        app_opb_eq_set_enabled(enabled, true);
                        app_opb_eq_set_preset(preset, true);
                        TRACE(0, "[OPB_EQ_TASK] EQ config saved to NV");

#ifdef TWS_SYSTEM_ENABLED
                        // Sync to peer earbud (deferred to avoid blocking GATT response)
                        TRACE(0, "[OPB_EQ_TASK] Triggering TWS sync");
                        app_tws_if_sync_info(TWS_SYNC_USER_OPB_EQ);
                        TRACE(0, "[OPB_EQ_TASK] TWS sync completed");
#else
                        TRACE(0, "[OPB_EQ_TASK] TWS not enabled, skipping sync");
#endif
                        // Apply EQ immediately (no need to reconnect!)
                        TRACE(0, "[OPB_EQ_TASK] Applying EQ immediately");
                        app_opb_eq_apply_immediately();

                        TRACE(0, "[OPB_EQ_TASK] Apply command completed");
                    } else {
                        TRACE(0, "[OPB_EQ_TASK] ERROR: Failed to get config");
                        status = ATT_ERR_APP_ERROR;
                    }
                }
                else if (command == 0x02) {
                    // Apply without saving (temporary/live preview)
                    TRACE(0, "[OPB_EQ_TASK] Applying EQ config (temporary, no save)");
                    app_opb_eq_apply_immediately();

#ifdef TWS_SYSTEM_ENABLED
                    // Sync to peer earbud for live preview
                    TRACE(0, "[OPB_EQ_TASK] Syncing temporary EQ to peer");
                    app_tws_if_sync_info(TWS_SYNC_USER_OPB_EQ);
#endif
                    TRACE(0, "[OPB_EQ_TASK] EQ applied temporarily");
                }
                else if (command == 0x03) {
                    // Reset to flat
                    TRACE(0, "[OPB_EQ_TASK] Resetting EQ to flat");
                    app_opb_eq_reset(true);

#ifdef TWS_SYSTEM_ENABLED
                    app_tws_if_sync_info(TWS_SYNC_USER_OPB_EQ);
#endif
                }
                else {
                    TRACE(1, "[OPB_EQ_TASK] Unknown command: 0x%02x", command);
                    status = ATT_ERR_APP_ERROR;
                }
            } else {
                status = ATT_ERR_INVALID_ATTRIBUTE_VAL_LEN;
            }
        }
        else if (param->handle == (opb_eqps_env->shdl + OPB_EQPS_IDX_STATUS_NTF_CFG)) {
            // Write notification configuration
            if (param->length == 2) {
                uint16_t ntf_cfg = param->value[0] | (param->value[1] << 8);
                opb_eqps_env->ntf_cfg[conidx] = ntf_cfg;
                TRACE(2, "[OPB_EQ_TASK] Notification config set to 0x%04x for conidx %d", ntf_cfg, conidx);
            } else {
                status = ATT_ERR_INVALID_ATTRIBUTE_VAL_LEN;
            }
        }
        else {
            status = ATT_ERR_REQUEST_NOT_SUPPORTED;
        }
    } else {
        status = ATT_ERR_APP_ERROR;
    }

    // Send write response
    struct gattc_write_cfm *cfm = KE_MSG_ALLOC(
        GATTC_WRITE_CFM, src_id, dest_id, gattc_write_cfm);
    cfm->handle = param->handle;
    cfm->status = status;
    ke_msg_send(cfm);

    return (KE_MSG_CONSUMED);
}

/*
 * GLOBAL VARIABLE DEFINITIONS
 ****************************************************************************************
 */

/* Default State handlers definition. */
KE_MSG_HANDLER_TAB(opb_eqps){
    {GATTC_READ_REQ_IND, (ke_msg_func_t)gattc_read_req_ind_handler},
    {GATTC_WRITE_REQ_IND, (ke_msg_func_t)gattc_write_req_ind_handler},
};

/**
 ****************************************************************************************
 * @brief Initialize task handler
 ****************************************************************************************
 */
void opb_eqps_task_init(struct ke_task_desc *task_desc, struct opb_eqps_env_tag *opb_eqps_env) {
    task_desc->msg_handler_tab = opb_eqps_msg_handler_tab;
    task_desc->msg_cnt = ARRAY_LEN(opb_eqps_msg_handler_tab);
    task_desc->state = &(opb_eqps_env->state);
    task_desc->idx_max = BLE_CONNECTION_MAX;
}

#endif /* BLE_OPB_EQ_SERVER */

/// @} OPB_EQPSTASK
