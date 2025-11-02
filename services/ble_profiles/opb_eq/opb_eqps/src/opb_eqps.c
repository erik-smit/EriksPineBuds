/**
 ****************************************************************************************
 * @addtogroup OPB_EQPS
 * @{
 ****************************************************************************************
 */

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "rwip_config.h"

#if (BLE_OPB_EQ_SERVER)

// Compile-time verification that BLE_OPB_EQ_SERVER is defined
#if !defined(BLE_OPB_EQ_SERVER) || (BLE_OPB_EQ_SERVER == 0)
#error "BLE_OPB_EQ_SERVER is not properly defined!"
#endif

#include "attm.h"
#include "opb_eqps.h"
#include "opb_eqps_task.h"
#include "gap.h"
#include "gapc_task.h"
#include "gattc_task.h"
#include "prf_utils.h"
#include "co_utils.h"
#include "ke_mem.h"

/*
 * OPB EQ SERVICE ATTRIBUTES
 ****************************************************************************************
 */

// Service UUID: 0000FFD0-0000-1000-8000-00805F9B34FB
#define opb_eq_service_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xD0, 0xFF, 0x00, 0x00 }

// EQ Configuration UUID: 0000FFD1-0000-1000-8000-00805F9B34FB
#define opb_eq_cfg_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xD1, 0xFF, 0x00, 0x00 }

// EQ Preset UUID: 0000FFD2-0000-1000-8000-00805F9B34FB
#define opb_eq_preset_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xD2, 0xFF, 0x00, 0x00 }

// EQ Enable UUID: 0000FFD3-0000-1000-8000-00805F9B34FB
#define opb_eq_enable_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xD3, 0xFF, 0x00, 0x00 }

// EQ Capabilities UUID: 0000FFD4-0000-1000-8000-00805F9B34FB
#define opb_eq_caps_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xD4, 0xFF, 0x00, 0x00 }

// Apply Command UUID: 0000FFD5-0000-1000-8000-00805F9B34FB
#define opb_eq_apply_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xD5, 0xFF, 0x00, 0x00 }

// Status Notification UUID: 0000FFD6-0000-1000-8000-00805F9B34FB
#define opb_eq_status_char_uuid_128_content  \
  { 0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80, \
    0x00, 0x10, 0x00, 0x00, 0xD6, 0xFF, 0x00, 0x00 }

#define ATT_DECL_PRIMARY_SERVICE_UUID  { 0x00, 0x28 }
#define ATT_DECL_CHARACTERISTIC_UUID   { 0x03, 0x28 }
#define ATT_DESC_CLIENT_CHAR_CFG_UUID  { 0x02, 0x29 }

static const uint8_t OPB_EQ_SERVICE_UUID_128[ATT_UUID_128_LEN] =
    opb_eq_service_uuid_128_content;

/// Full OPB EQ SERVER Database Description
const struct attm_desc_128 opb_eqps_att_db[OPB_EQPS_IDX_NB] = {
    // Service Declaration
    [OPB_EQPS_IDX_SVC] = {ATT_DECL_PRIMARY_SERVICE_UUID, PERM(RD, ENABLE), 0, 0},

    // EQ Configuration Characteristic (Read/Write, 144 bytes)
    [OPB_EQPS_IDX_EQ_CFG_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_EQPS_IDX_EQ_CFG_VAL] = {
        opb_eq_cfg_char_uuid_128_content,
        PERM(RD, ENABLE) | PERM(WRITE_REQ, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        OPB_EQPS_MAX_LEN
    },

    // EQ Preset Characteristic (Read/Write, 4 bytes)
    [OPB_EQPS_IDX_PRESET_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_EQPS_IDX_PRESET_VAL] = {
        opb_eq_preset_char_uuid_128_content,
        PERM(RD, ENABLE) | PERM(WRITE_REQ, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        4
    },

    // EQ Enable Characteristic (Read/Write, 4 bytes)
    [OPB_EQPS_IDX_ENABLE_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_EQPS_IDX_ENABLE_VAL] = {
        opb_eq_enable_char_uuid_128_content,
        PERM(RD, ENABLE) | PERM(WRITE_REQ, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        4
    },

    // EQ Capabilities Characteristic (Read Only, 16 bytes)
    [OPB_EQPS_IDX_CAPS_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_EQPS_IDX_CAPS_VAL] = {
        opb_eq_caps_char_uuid_128_content,
        PERM(RD, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        16
    },

    // Apply Command Characteristic (Write Only, 4 bytes)
    [OPB_EQPS_IDX_APPLY_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_EQPS_IDX_APPLY_VAL] = {
        opb_eq_apply_char_uuid_128_content,
        PERM(WRITE_REQ, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        4
    },

    // Status Notification Characteristic (Notify, 4 bytes)
    [OPB_EQPS_IDX_STATUS_CHAR] = {ATT_DECL_CHARACTERISTIC_UUID, PERM(RD, ENABLE), 0, 0},
    [OPB_EQPS_IDX_STATUS_VAL] = {
        opb_eq_status_char_uuid_128_content,
        PERM(NTF, ENABLE),
        PERM(RI, ENABLE) | PERM_VAL(UUID_LEN, PERM_UUID_128),
        4
    },
    [OPB_EQPS_IDX_STATUS_NTF_CFG] = {
        ATT_DESC_CLIENT_CHAR_CFG_UUID,
        PERM(RD, ENABLE) | PERM(WRITE_REQ, ENABLE),
        0,
        0
    },
};

/**
 ****************************************************************************************
 * @brief Initialization of the OPB_EQPS module.
 ****************************************************************************************
 */
static uint8_t opb_eqps_init(struct prf_task_env *env, uint16_t *start_hdl,
                             uint16_t app_task, uint8_t sec_lvl, void *params) {
    uint8_t status;

    TRACE(2, "[OPB_EQ_PRF] Init called, start_hdl=0x%04x, task=0x%04x", *start_hdl, env->task);

    // Add Service Into Database
    status = attm_svc_create_db_128(
        start_hdl, OPB_EQ_SERVICE_UUID_128, NULL, OPB_EQPS_IDX_NB, NULL,
        env->task, &opb_eqps_att_db[0],
        (sec_lvl & (PERM_MASK_SVC_DIS | PERM_MASK_SVC_AUTH | PERM_MASK_SVC_EKS)) |
            PERM(SVC_MI, DISABLE) | PERM_VAL(SVC_UUID_LEN, PERM_UUID_128));

    TRACE(2, "[OPB_EQ_PRF] attm_svc_create_db_128 returned status=0x%02x, start_hdl=0x%04x", status, *start_hdl);

    // Allocate OPB_EQPS required environment variable
    if (status == ATT_ERR_NO_ERROR) {
        TRACE(0, "[OPB_EQ_PRF] Service created successfully!");
        struct opb_eqps_env_tag *opb_eqps_env =
            (struct opb_eqps_env_tag *)ke_malloc(
                sizeof(struct opb_eqps_env_tag), KE_MEM_ATT_DB);

        memset((uint8_t *)opb_eqps_env, 0, sizeof(struct opb_eqps_env_tag));

        // Initialize environment
        env->env = (prf_env_t *)opb_eqps_env;
        opb_eqps_env->shdl = *start_hdl;
        opb_eqps_env->prf_env.app_task = app_task |
            (PERM_GET(sec_lvl, SVC_MI) ? PERM(PRF_MI, ENABLE) : PERM(PRF_MI, DISABLE));
        opb_eqps_env->prf_env.prf_task = env->task | PERM(PRF_MI, DISABLE);

        // Initialize environment variable
        env->id = TASK_ID_OPB_EQPS;
        opb_eqps_task_init(&(env->desc), opb_eqps_env);

        // Initialize notification config to disabled
        for (uint8_t i = 0; i < BLE_CONNECTION_MAX; i++) {
            opb_eqps_env->ntf_cfg[i] = 0;
        }

        ke_state_set(env->task, OPB_EQPS_IDLE);
    } else {
        TRACE(1, "[OPB_EQ_PRF] ERROR: Service creation failed with status=0x%02x", status);
    }

    return status;
}

/**
 ****************************************************************************************
 * @brief Destruction of the OPB_EQPS module
 ****************************************************************************************
 */
static void opb_eqps_destroy(struct prf_task_env *env) {
    struct opb_eqps_env_tag *opb_eqps_env =
        (struct opb_eqps_env_tag *)env->env;

    // Free profile environment variables
    env->env = NULL;
    ke_free(opb_eqps_env);
}

/**
 ****************************************************************************************
 * @brief Handles Connection creation
 ****************************************************************************************
 */
static void opb_eqps_create(struct prf_task_env *env, uint8_t conidx) {
    // Nothing to do
}

/**
 ****************************************************************************************
 * @brief Handles Disconnection
 ****************************************************************************************
 */
static void opb_eqps_cleanup(struct prf_task_env *env, uint8_t conidx, uint8_t reason) {
    struct opb_eqps_env_tag *opb_eqps_env = (struct opb_eqps_env_tag *)env->env;

    // Reset notification configuration for this connection
    if (conidx < BLE_CONNECTION_MAX) {
        opb_eqps_env->ntf_cfg[conidx] = 0;
    }
}

/// OPB EQ Server Task interface required by profile manager
const struct prf_task_cbs opb_eqps_itf = {
    opb_eqps_init,
    opb_eqps_destroy,
    opb_eqps_create,
    opb_eqps_cleanup,
};

/**
 ****************************************************************************************
 * @brief Retrieve profile interface
 ****************************************************************************************
 */
const struct prf_task_cbs* opb_eqps_prf_itf_get(void) {
    return &opb_eqps_itf;
}

#endif /* BLE_OPB_EQ_SERVER */

/// @} OPB_EQPS
