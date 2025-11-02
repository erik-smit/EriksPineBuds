#ifndef _OPB_EQPS_H_
#define _OPB_EQPS_H_

/**
 ****************************************************************************************
 * @addtogroup OPB_EQPS OpenPineBuds EQ Profile Server
 * @ingroup OPB_EQ
 * @brief OpenPineBuds EQ Profile Server
 * @{
 ****************************************************************************************
 */

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "rwip_config.h"

#if (BLE_OPB_EQ_SERVER)

#include "prf_types.h"
#include "prf.h"
#include "opb_eqps_task.h"
#include "attm.h"
#include "prf_utils.h"

/*
 * DEFINES
 ****************************************************************************************
 */
#define OPB_EQPS_MAX_LEN  (144)  // Max length for EQ config characteristic
#define OPB_EQPS_IDX_MAX  (0x01)

/*
 * ENUMERATIONS
 ****************************************************************************************
 */

/// Possible states of the OPB_EQPS task
enum
{
    /// Idle state
    OPB_EQPS_IDLE,
    /// Busy state
    OPB_EQPS_BUSY,
    /// Number of defined states
    OPB_EQPS_STATE_MAX,
};

/// Attribute indices
enum
{
    OPB_EQPS_IDX_SVC,

    // EQ Configuration (144 bytes, Read/Write)
    OPB_EQPS_IDX_EQ_CFG_CHAR,
    OPB_EQPS_IDX_EQ_CFG_VAL,

    // EQ Preset (4 bytes, Read/Write)
    OPB_EQPS_IDX_PRESET_CHAR,
    OPB_EQPS_IDX_PRESET_VAL,

    // EQ Enable (4 bytes, Read/Write)
    OPB_EQPS_IDX_ENABLE_CHAR,
    OPB_EQPS_IDX_ENABLE_VAL,

    // EQ Capabilities (16 bytes, Read Only)
    OPB_EQPS_IDX_CAPS_CHAR,
    OPB_EQPS_IDX_CAPS_VAL,

    // Apply Command (4 bytes, Write Only)
    OPB_EQPS_IDX_APPLY_CHAR,
    OPB_EQPS_IDX_APPLY_VAL,

    // Status Notification (4 bytes, Notify)
    OPB_EQPS_IDX_STATUS_CHAR,
    OPB_EQPS_IDX_STATUS_VAL,
    OPB_EQPS_IDX_STATUS_NTF_CFG,

    OPB_EQPS_IDX_NB,
};

/*
 * TYPE DEFINITIONS
 ****************************************************************************************
 */

/// OpenPineBuds EQ Profile Server environment variable
struct opb_eqps_env_tag
{
    /// Profile environment
    prf_env_t prf_env;
    /// Service Start Handle
    uint16_t shdl;
    /// State of different task instances
    ke_state_t state;
    /// Notification configuration
    uint16_t ntf_cfg[BLE_CONNECTION_MAX];
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
const struct prf_task_cbs* opb_eqps_prf_itf_get(void);

#endif //BLE_OPB_EQ_SERVER

/// @} OPB_EQPS

#endif // _OPB_EQPS_H_
