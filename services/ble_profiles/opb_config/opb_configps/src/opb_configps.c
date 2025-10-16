/**
 ****************************************************************************************
 * @addtogroup OPB_CONFIGPS
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

/*
 * OPB CONFIG SERVICE ATTRIBUTES
 ****************************************************************************************
 */

// Service UUID: 0000FFC0-0000-1000-8000-00805F9B34FB
#define opb_config_service_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xC0, 0xFF, 0x00, 0x00 }

// Left Config UUID: 0000FFC1-0000-1000-8000-00805F9B34FB
#define opb_config_left_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xC1, 0xFF, 0x00, 0x00 }

// Right Config UUID: 0000FFC2-0000-1000-8000-00805F9B34FB
#define opb_config_right_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xC2, 0xFF, 0x00, 0x00 }

// Version UUID: 0000FFC3-0000-1000-8000-00805F9B34FB
#define opb_config_version_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xC3, 0xFF, 0x00, 0x00 }

#define ATT_DECL_PRIMARY_SERVICE_UUID  { 0x00, 0x28 }
#define ATT_DECL_CHARACTERISTIC_UUID   { 0x03, 0x28 }

static const uint8_t OPB_CONFIG_SERVICE_UUID_128[ATT_UUID_128_LEN] =
    opb_config_service_uuid_128_content;

/// Full OPB CONFIG SERVER Database Description
const struct attm_desc_128 opb_configps_att_db[OPB_CONFIGPS_IDX_NB] = {
    // Service Declaration
    [OPB_CONFIGPS_IDX_SVC] = {ATT_DECL_PRIMARY_SERVICE_UUID, PERM(RD, ENABLE), 0, 0},

    // Left Earbud Configuration Characteristic
    [OPB_CONFIGPS_IDX_LEFT_CFG_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_CONFIGPS_IDX_LEFT_CFG_VAL] = {
        opb_config_left_char_uuid_128_content,
        PERM(RD, ENABLE) | PERM(WRITE_REQ, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        OPB_CONFIGPS_MAX_LEN
    },

    // Right Earbud Configuration Characteristic
    [OPB_CONFIGPS_IDX_RIGHT_CFG_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_CONFIGPS_IDX_RIGHT_CFG_VAL] = {
        opb_config_right_char_uuid_128_content,
        PERM(RD, ENABLE) | PERM(WRITE_REQ, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        OPB_CONFIGPS_MAX_LEN
    },

    // Version Characteristic (Read Only)
    [OPB_CONFIGPS_IDX_VERSION_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_CONFIGPS_IDX_VERSION_VAL] = {
        opb_config_version_char_uuid_128_content,
        PERM(RD, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        4  // Version is 4 bytes
    },
};

/**
 ****************************************************************************************
 * @brief Initialization of the OPB_CONFIGPS module.
 ****************************************************************************************
 */
static uint8_t opb_configps_init(struct prf_task_env *env, uint16_t *start_hdl,
                                 uint16_t app_task, uint8_t sec_lvl, void *params) {
    uint8_t status;

    // Add Service Into Database
    status = attm_svc_create_db_128(
        start_hdl, OPB_CONFIG_SERVICE_UUID_128, NULL, OPB_CONFIGPS_IDX_NB, NULL,
        env->task, &opb_configps_att_db[0],
        (sec_lvl & (PERM_MASK_SVC_DIS | PERM_MASK_SVC_AUTH | PERM_MASK_SVC_EKS)) |
            PERM(SVC_MI, DISABLE) | PERM_VAL(SVC_UUID_LEN, PERM_UUID_128));

    // Allocate OPB_CONFIGPS required environment variable
    if (status == ATT_ERR_NO_ERROR) {
        struct opb_configps_env_tag *opb_configps_env =
            (struct opb_configps_env_tag *)ke_malloc(
                sizeof(struct opb_configps_env_tag), KE_MEM_ATT_DB);

        memset((uint8_t *)opb_configps_env, 0, sizeof(struct opb_configps_env_tag));

        // Initialize environment
        env->env = (prf_env_t *)opb_configps_env;
        opb_configps_env->shdl = *start_hdl;
        opb_configps_env->prf_env.app_task = app_task |
            (PERM_GET(sec_lvl, SVC_MI) ? PERM(PRF_MI, ENABLE) : PERM(PRF_MI, DISABLE));
        opb_configps_env->prf_env.prf_task = env->task | PERM(PRF_MI, DISABLE);

        // Initialize environment variable
        env->id = TASK_ID_OPB_CONFIGPS;
        opb_configps_task_init(&(env->desc), opb_configps_env);

        ke_state_set(env->task, OPB_CONFIGPS_IDLE);
    }

    return status;
}

/**
 ****************************************************************************************
 * @brief Destruction of the OPB_CONFIGPS module
 ****************************************************************************************
 */
static void opb_configps_destroy(struct prf_task_env *env) {
    struct opb_configps_env_tag *opb_configps_env =
        (struct opb_configps_env_tag *)env->env;

    // Free profile environment variables
    env->env = NULL;
    ke_free(opb_configps_env);
}

/**
 ****************************************************************************************
 * @brief Handles Connection creation
 ****************************************************************************************
 */
static void opb_configps_create(struct prf_task_env *env, uint8_t conidx) {
    // Nothing to do
}

/**
 ****************************************************************************************
 * @brief Handles Disconnection
 ****************************************************************************************
 */
static void opb_configps_cleanup(struct prf_task_env *env, uint8_t conidx, uint8_t reason) {
    // Nothing to do
}

/// OPB CONFIG Server Task interface required by profile manager
const struct prf_task_cbs opb_configps_itf = {
    opb_configps_init,
    opb_configps_destroy,
    opb_configps_create,
    opb_configps_cleanup,
};

/**
 ****************************************************************************************
 * @brief Retrieve profile interface
 ****************************************************************************************
 */
const struct prf_task_cbs* opb_configps_prf_itf_get(void) {
    return &opb_configps_itf;
}

#endif /* BLE_OPB_CONFIG_SERVER */

/// @} OPB_CONFIGPS
