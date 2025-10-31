#ifndef _OPB_CONFIGPS_H_
#define _OPB_CONFIGPS_H_

/**
 ****************************************************************************************
 * @addtogroup OPB_CONFIGPS OpenPineBuds Configuration Profile Server
 * @ingroup OPB_CONFIG
 * @brief OpenPineBuds Configuration Profile Server
 * @{
 ****************************************************************************************
 */

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "rwip_config.h"

#if (BLE_OPB_CONFIG_SERVER)

#include "prf_types.h"
#include "prf.h"
#include "opb_configps_task.h"
#include "attm.h"
#include "prf_utils.h"

/*
 * DEFINES
 ****************************************************************************************
 */
#define OPB_CONFIGPS_MAX_LEN  (32)  // Increased to accommodate device name (32 bytes max)
#define OPB_CONFIGPS_IDX_MAX  (0x01)

/*
 * ENUMERATIONS
 ****************************************************************************************
 */

/// Possible states of the OPB_CONFIGPS task
enum
{
    /// Idle state
    OPB_CONFIGPS_IDLE,
    /// Busy state
    OPB_CONFIGPS_BUSY,
    /// Number of defined states
    OPB_CONFIGPS_STATE_MAX,
};

/// Attribute indices
enum
{
    OPB_CONFIGPS_IDX_SVC,

    OPB_CONFIGPS_IDX_LEFT_CFG_CHAR,
    OPB_CONFIGPS_IDX_LEFT_CFG_VAL,

    OPB_CONFIGPS_IDX_RIGHT_CFG_CHAR,
    OPB_CONFIGPS_IDX_RIGHT_CFG_VAL,

    OPB_CONFIGPS_IDX_VERSION_CHAR,
    OPB_CONFIGPS_IDX_VERSION_VAL,

    OPB_CONFIGPS_IDX_DEVICE_NAME_CHAR,
    OPB_CONFIGPS_IDX_DEVICE_NAME_VAL,

    OPB_CONFIGPS_IDX_NB,
};

/*
 * TYPE DEFINITIONS
 ****************************************************************************************
 */

/// OpenPineBuds Config Profile Server environment variable
struct opb_configps_env_tag
{
    /// Profile environment
    prf_env_t prf_env;
    /// Service Start Handle
    uint16_t shdl;
    /// State of different task instances
    ke_state_t state;
};

/*
 * FUNCTION DECLARATIONS
 ****************************************************************************************
 */

/**
 ****************************************************************************************
 * @brief Retrieve profile interface
 * @return Profile interface
 ****************************************************************************************
 */
const struct prf_task_cbs* opb_configps_prf_itf_get(void);

/**
 ****************************************************************************************
 * @brief Initialize task handler
 * @param task_desc Task descriptor to fill
 * @param env Environment pointer
 ****************************************************************************************
 */
void opb_configps_task_init(struct ke_task_desc *task_desc, struct opb_configps_env_tag *env);

#endif /* BLE_OPB_CONFIG_SERVER */

/// @} OPB_CONFIGPS

#endif /* _OPB_CONFIGPS_H_ */
