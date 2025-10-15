#ifndef APP_OPB_CONFIG_SERVER_H_
#define APP_OPB_CONFIG_SERVER_H_

/**
 ****************************************************************************************
 * @addtogroup APP
 * @brief OpenPineBuds Config Server Application entry point
 * @{
 ****************************************************************************************
 */

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "rwip_config.h"

#if (BLE_APP_OPB_CONFIG)

#include <stdint.h>
#include "ke_task.h"

/*
 * TYPE DEFINITIONS
 ****************************************************************************************
 */

/// OpenPineBuds Config Server application environment structure
struct app_opb_config_server_env_tag
{
    uint8_t connectionIndex;
};

/*
 * GLOBAL VARIABLES DECLARATIONS
 ****************************************************************************************
 */

/// OpenPineBuds Config Server Application environment
extern struct app_opb_config_server_env_tag app_opb_config_server_env;

#ifdef __cplusplus
extern "C" {
#endif

/*
 * FUNCTIONS DECLARATION
 ****************************************************************************************
 */

/**
 ****************************************************************************************
 * @brief Initialize OpenPineBuds Config Server Application
 ****************************************************************************************
 */
void app_opb_config_server_init(void);

/**
 ****************************************************************************************
 * @brief Add an OpenPineBuds Config Server instance in the DB
 ****************************************************************************************
 */
void app_opb_config_add_server(void);

/**
 ****************************************************************************************
 * @brief Handle connection event
 ****************************************************************************************
 */
void app_opb_config_server_connected_evt_handler(uint8_t conidx);

/**
 ****************************************************************************************
 * @brief Handle disconnection event
 ****************************************************************************************
 */
void app_opb_config_server_disconnected_evt_handler(uint8_t conidx);

#ifdef __cplusplus
}
#endif

#endif //(BLE_APP_OPB_CONFIG)

/// @} APP

#endif // APP_OPB_CONFIG_SERVER_H_
