#ifndef _OPB_EQPS_TASK_H_
#define _OPB_EQPS_TASK_H_

/**
 ****************************************************************************************
 * @addtogroup OPB_EQPSTASK Task
 * @ingroup OPB_EQPS
 * @brief OpenPineBuds EQ Profile Server Task
 * @{
 ****************************************************************************************
 */

/*
 * INCLUDE FILES
 ****************************************************************************************
 */

#include "rwip_config.h"

#if (BLE_OPB_EQ_SERVER)

#include "ke_task.h"

/*
 * DEFINES
 ****************************************************************************************
 */

/// Maximum length of EQ config characteristic value
#define OPB_EQPS_EQ_CFG_MAX_LEN        (144)

/// Messages for OpenPineBuds EQ Profile Server
enum opb_eqps_msg_id
{
    OPB_EQPS_ENABLE_REQ = TASK_FIRST_MSG(TASK_ID_OPB_EQPS),
    OPB_EQPS_ENABLE_RSP,
};

/*
 * API MESSAGES STRUCTURES
 ****************************************************************************************
 */

/// Parameters of the initialization function
struct opb_eqps_db_cfg
{
    uint8_t dummy;
};

/*
 * FORWARD DECLARATIONS
 ****************************************************************************************
 */
struct opb_eqps_env_tag;

/*
 * FUNCTION DECLARATIONS
 ****************************************************************************************
 */

void opb_eqps_task_init(struct ke_task_desc *task_desc, struct opb_eqps_env_tag *opb_eqps_env);

#endif //BLE_OPB_EQ_SERVER

/// @} OPB_EQPSTASK

#endif // _OPB_EQPS_TASK_H_
