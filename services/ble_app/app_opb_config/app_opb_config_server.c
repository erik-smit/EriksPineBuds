/**
 ****************************************************************************************
 * @addtogroup APP
 * @{
 ****************************************************************************************
 */

#include "rwip_config.h"

#if (BLE_APP_OPB_CONFIG)

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "app.h"
#include "app_opb_config_server.h"
#include "app_task.h"
#include "arch.h"
#include "co_bt.h"
#include "opb_configps_task.h"
#include "prf.h"
#include "prf_types.h"
#include "prf_utils.h"
#include "string.h"
#include "hal_trace.h"
#include "app_ble_mode_switch.h"

/*
 * GLOBAL VARIABLE DEFINITIONS
 ****************************************************************************************
 */

/// OpenPineBuds Config Server application environment structure
struct app_opb_config_server_env_tag app_opb_config_server_env = {
    .connectionIndex = 0xFF
};

/*
 * GLOBAL FUNCTION DEFINITIONS
 ****************************************************************************************
 */

void app_opb_config_server_connected_evt_handler(uint8_t conidx) {
    TRACE(0, "[OPB_CFG_APP] Connected, conidx=%d", conidx);
    app_opb_config_server_env.connectionIndex = conidx;
}

void app_opb_config_server_disconnected_evt_handler(uint8_t conidx) {
    if (conidx == app_opb_config_server_env.connectionIndex) {
        TRACE(0, "[OPB_CFG_APP] Disconnected, conidx=%d", conidx);
        app_opb_config_server_env.connectionIndex = 0xFF;
    }
}

static void app_opb_config_ble_data_fill_handler(void *param) {
    TRACE(0, "[OPB_CFG_APP] Advertising data fill handler called");

    // Simply enable advertising for our config service
    // We don't need to add custom advertising data - the service UUID
    // will be discoverable through GATT service discovery
    app_ble_data_fill_enable(USER_OPB_CONFIG, true);
}

void app_opb_config_server_init(void) {
    // Reset the environment
    TRACE(0, "[OPB_CFG_APP] Initializing");
    app_opb_config_server_env.connectionIndex = 0xFF;

    // Register advertising data fill handler
    TRACE(0, "[OPB_CFG_APP] Registering advertising handler");
    app_ble_register_data_fill_handle(USER_OPB_CONFIG,
                                     (BLE_DATA_FILL_FUNC_T)app_opb_config_ble_data_fill_handler,
                                     false);
}

void app_opb_config_add_server(void) {
    TRACE(0, "[OPB_CFG_APP] Adding OPB Config Server to GATT database");

    struct gapm_profile_task_add_cmd *req =
        KE_MSG_ALLOC_DYN(GAPM_PROFILE_TASK_ADD_CMD, TASK_GAPM, TASK_APP,
                         gapm_profile_task_add_cmd, 0);

    // Fill message
    req->operation = GAPM_PROFILE_TASK_ADD;
    req->sec_lvl = PERM(SVC_AUTH, DISABLE); // No authentication required
    req->prf_task_id = TASK_ID_OPB_CONFIGPS;
    req->app_task = TASK_APP;
    req->start_hdl = 0; // Dynamically allocated

    // Send the message
    ke_msg_send(req);
}

#endif //(BLE_APP_OPB_CONFIG)

/// @} APP
