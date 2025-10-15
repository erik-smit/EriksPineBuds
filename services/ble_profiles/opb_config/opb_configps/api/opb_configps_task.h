#ifndef _OPB_CONFIGPS_TASK_H_
#define _OPB_CONFIGPS_TASK_H_

/**
 ****************************************************************************************
 * @addtogroup OPB_CONFIGPSTASK Task
 * @ingroup OPB_CONFIGPS
 * @brief OpenPineBuds Configuration Profile Server Task
 * @{
 ****************************************************************************************
 */

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "prf_types.h"
#include "rwip_task.h"

/*
 * DEFINES
 ****************************************************************************************
 */
#define OPB_CONFIG_MAX_LEN  (16)

/*
 * TYPE DEFINITIONS
 ****************************************************************************************
 */

/// Messages for OpenPineBuds Config Server
enum opb_configps_msg_id
{
    /// Start the profile - at connection
    OPB_CONFIGPS_ENABLE_REQ = TASK_FIRST_MSG(TASK_ID_OPB_CONFIGPS),
    /// Enable confirm
    OPB_CONFIGPS_ENABLE_RSP,
    /// Value change indication to APP
    OPB_CONFIGPS_CFG_WRITE_IND,
};

/// Database Configuration
struct opb_configps_db_cfg
{
    /// Dummy field
    uint8_t dummy;
};

/// Enable Request
struct opb_configps_enable_req
{
    /// Connection index
    uint8_t conidx;
};

/// Enable Response
struct opb_configps_enable_rsp
{
    /// Connection index
    uint8_t conidx;
    /// Status
    uint8_t status;
};

/// Configuration write indication
struct opb_configps_cfg_write_ind
{
    /// Connection index
    uint8_t conidx;
    /// Which config was written (0=left, 1=right)
    uint8_t cfg_type;
    /// Configuration data (16 bytes)
    uint8_t data[OPB_CONFIG_MAX_LEN];
};

/// @} OPB_CONFIGPSTASK

#endif /* _OPB_CONFIGPS_TASK_H_ */
