/**
 ****************************************************************************************
 * @addtogroup OPB_CONFIGPSTASK
 * @{
 ****************************************************************************************
 */

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "rwip_config.h"

#if (BLE_OPB_CONFIG_SERVER)
#include "attm.h"
#include "opb_configps.h"
#include "opb_configps_task.h"
#include "gap.h"
#include "gapc_task.h"
#include "gattc_task.h"
#include "prf_utils.h"
#include "co_utils.h"
#include "ke_mem.h"

// Include our config manager
#include "app_opb_config.h"
#include "opb_config_common.h"
#include "nvrecord_env.h"

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
    struct opb_configps_env_tag *opb_configps_env = PRF_ENV_GET(OPB_CONFIGPS, opb_configps);
    uint8_t conidx = KE_IDX_GET(src_id);
    uint8_t status = ATT_ERR_NO_ERROR;
    struct gattc_read_cfm *cfm;

    uint8_t value[OPB_CONFIGPS_MAX_LEN];
    uint8_t length = 0;

    if (param->handle == (opb_configps_env->shdl + OPB_CONFIGPS_IDX_LEFT_CFG_VAL)) {
        // Read left earbud configuration
        opb_config_t config;
        if (app_opb_config_get(&config) == 0) {
            memcpy(value, &config.left, sizeof(opb_earbud_config_t));
            length = sizeof(opb_earbud_config_t);
        } else {
            status = ATT_ERR_APP_ERROR;
        }
    }
    else if (param->handle == (opb_configps_env->shdl + OPB_CONFIGPS_IDX_RIGHT_CFG_VAL)) {
        // Read right earbud configuration
        opb_config_t config;
        if (app_opb_config_get(&config) == 0) {
            memcpy(value, &config.right, sizeof(opb_earbud_config_t));
            length = sizeof(opb_earbud_config_t);
        } else {
            status = ATT_ERR_APP_ERROR;
        }
    }
    else if (param->handle == (opb_configps_env->shdl + OPB_CONFIGPS_IDX_VERSION_VAL)) {
        // Read version
        opb_config_t config;
        if (app_opb_config_get(&config) == 0) {
            value[0] = config.version_major;
            value[1] = config.version_minor;
            value[2] = config.version_patch;
            value[3] = 0;  // reserved
            length = 4;
        } else {
            status = ATT_ERR_APP_ERROR;
        }
    }
    else if (param->handle == (opb_configps_env->shdl + OPB_CONFIGPS_IDX_DEVICE_NAME_VAL)) {
        // Read device name
        char *device_name = NULL;
        if (nv_record_get_device_name(&device_name) == 0 && device_name != NULL) {
            // Use strnlen to safely get length (max 32 bytes)
            length = strnlen(device_name, 32);
            if (length > 0) {
                memcpy(value, device_name, length);
            } else {
                // Empty name = use factory default, return empty string
                length = 0;
            }
        } else {
            // Error getting device name, return empty string
            length = 0;
        }
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
    struct opb_configps_env_tag *opb_configps_env = PRF_ENV_GET(OPB_CONFIGPS, opb_configps);
    uint8_t conidx = KE_IDX_GET(src_id);
    uint8_t status = ATT_ERR_NO_ERROR;

    if (opb_configps_env != NULL) {
        if (param->handle == (opb_configps_env->shdl + OPB_CONFIGPS_IDX_LEFT_CFG_VAL)) {
            // Write left earbud configuration
            TRACE(0, "[OPB_CFG_TASK] BLE write to left config characteristic");
            if (param->length == sizeof(opb_earbud_config_t)) {
                opb_config_t config;
                if (app_opb_config_get(&config) == 0) {
                    memcpy(&config.left, param->value, sizeof(opb_earbud_config_t));
                    // Save to NV storage
                    if (app_opb_config_set(&config, true) != 0) {
                        TRACE(0, "[OPB_CFG_TASK] Failed to save left config");
                        status = ATT_ERR_APP_ERROR;
                    } else {
                        TRACE(0, "[OPB_CFG_TASK] Left config saved successfully");
#ifdef TWS_SYSTEM_ENABLED
                        // Sync config to peer earbud if TWS link is active
                        TRACE(0, "[OPB_CFG_TASK] Triggering TWS sync for left config");
                        app_tws_if_sync_info(TWS_SYNC_USER_OPB_CONFIG);
#endif
                    }
                } else {
                    TRACE(0, "[OPB_CFG_TASK] Failed to get current config");
                    status = ATT_ERR_APP_ERROR;
                }
            } else {
                TRACE(2, "[OPB_CFG_TASK] Invalid length: %d, expected: %d", param->length, sizeof(opb_earbud_config_t));
                status = ATT_ERR_INVALID_ATTRIBUTE_VAL_LEN;
            }
        }
        else if (param->handle == (opb_configps_env->shdl + OPB_CONFIGPS_IDX_RIGHT_CFG_VAL)) {
            // Write right earbud configuration
            TRACE(0, "[OPB_CFG_TASK] BLE write to right config characteristic");
            if (param->length == sizeof(opb_earbud_config_t)) {
                opb_config_t config;
                if (app_opb_config_get(&config) == 0) {
                    memcpy(&config.right, param->value, sizeof(opb_earbud_config_t));
                    // Save to NV storage
                    if (app_opb_config_set(&config, true) != 0) {
                        TRACE(0, "[OPB_CFG_TASK] Failed to save right config");
                        status = ATT_ERR_APP_ERROR;
                    } else {
                        TRACE(0, "[OPB_CFG_TASK] Right config saved successfully");
#ifdef TWS_SYSTEM_ENABLED
                        // Sync config to peer earbud if TWS link is active
                        TRACE(0, "[OPB_CFG_TASK] Triggering TWS sync for right config");
                        app_tws_if_sync_info(TWS_SYNC_USER_OPB_CONFIG);
#endif
                    }
                } else {
                    TRACE(0, "[OPB_CFG_TASK] Failed to get current config");
                    status = ATT_ERR_APP_ERROR;
                }
            } else {
                TRACE(2, "[OPB_CFG_TASK] Invalid length: %d, expected: %d", param->length, sizeof(opb_earbud_config_t));
                status = ATT_ERR_INVALID_ATTRIBUTE_VAL_LEN;
            }
        }
        else if (param->handle == (opb_configps_env->shdl + OPB_CONFIGPS_IDX_DEVICE_NAME_VAL)) {
            // Write device name
            TRACE(0, "[OPB_CFG_TASK] BLE write to device name characteristic");
            if (param->length <= 32) {
                char device_name[33];
                memcpy(device_name, param->value, param->length);
                device_name[param->length] = '\0';

                // Save to NV storage
                if (nv_record_set_device_name(device_name) != 0) {
                    TRACE(0, "[OPB_CFG_TASK] Failed to save device name");
                    status = ATT_ERR_APP_ERROR;
                } else {
                    TRACE(1, "[OPB_CFG_TASK] Device name saved: %s", device_name);
#ifdef TWS_SYSTEM_ENABLED
                    // Sync device name to peer earbud if TWS link is active
                    TRACE(0, "[OPB_CFG_TASK] Triggering TWS sync for device name");
                    app_tws_if_sync_info(TWS_SYNC_USER_OPB_CONFIG);
#endif
                }
            } else {
                TRACE(1, "[OPB_CFG_TASK] Device name too long: %d", param->length);
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
    struct gattc_write_cfm *cfm = KE_MSG_ALLOC(GATTC_WRITE_CFM, src_id, dest_id, gattc_write_cfm);
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
KE_MSG_HANDLER_TAB(opb_configps){
    {GATTC_READ_REQ_IND, (ke_msg_func_t)gattc_read_req_ind_handler},
    {GATTC_WRITE_REQ_IND, (ke_msg_func_t)gattc_write_req_ind_handler},
};

/**
 ****************************************************************************************
 * @brief Initialize task handler
 ****************************************************************************************
 */
void opb_configps_task_init(struct ke_task_desc *task_desc, struct opb_configps_env_tag *opb_configps_env) {
    task_desc->msg_handler_tab = opb_configps_msg_handler_tab;
    task_desc->msg_cnt = ARRAY_LEN(opb_configps_msg_handler_tab);
    task_desc->state = &(opb_configps_env->state);
    task_desc->idx_max = BLE_CONNECTION_MAX;
}

#endif /* BLE_OPB_CONFIG_SERVER */

/// @} OPB_CONFIGPSTASK
