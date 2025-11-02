/**
 ****************************************************************************************
 * @addtogroup APP
 * @{
 ****************************************************************************************
 */

#include "rwip_config.h"

#if (BLE_APP_OPB_EQ)

// Compile-time verification that BLE_APP_OPB_EQ is defined
#if !defined(BLE_APP_OPB_EQ) || (BLE_APP_OPB_EQ == 0)
#error "BLE_APP_OPB_EQ is not properly defined!"
#endif

/*
 * INCLUDE FILES
 ****************************************************************************************
 */
#include "app.h"
#include "app_opb_eq_server.h"
#include "app_task.h"
#include "arch.h"
#include "co_bt.h"
#include "opb_eqps_task.h"
#include "prf.h"
#include "prf_types.h"
#include "prf_utils.h"
#include "string.h"
#include "hal_trace.h"
#include "app_ble_mode_switch.h"
#include "app_opb_eq.h"  // EQ manager functions

/*
 * GLOBAL VARIABLE DEFINITIONS
 ****************************************************************************************
 */

/// OpenPineBuds EQ Server application environment structure
struct app_opb_eq_server_env_tag app_opb_eq_server_env = {
    .connectionIndex = 0xFF
};

/*
 * GLOBAL FUNCTION DEFINITIONS
 ****************************************************************************************
 */

void app_opb_eq_server_connected_evt_handler(uint8_t conidx) {
    TRACE(1, "[OPB_EQ_APP] Connected, conidx=%d", conidx);
    app_opb_eq_server_env.connectionIndex = conidx;
}

void app_opb_eq_server_disconnected_evt_handler(uint8_t conidx) {
    if (conidx == app_opb_eq_server_env.connectionIndex) {
        TRACE(1, "[OPB_EQ_APP] Disconnected, conidx=%d", conidx);
        app_opb_eq_server_env.connectionIndex = 0xFF;
    }
}

static void app_opb_eq_ble_data_fill_handler(void *param) {
    TRACE(0, "[OPB_EQ_APP] Advertising data fill handler called");

    // NOTE: We don't add the EQ service UUID to advertising data to save space
    // The advertising data is limited to 31 bytes, and we already have the
    // OPB_CONFIG service UUID (18 bytes). Adding another 128-bit UUID would
    // exceed the limit and cause a crash.
    //
    // The EQ service will still be fully discoverable after BLE connection.
    // Most BLE apps (like nRF Connect) scan all services after connecting.

    TRACE(0, "[OPB_EQ_APP] EQ service will be discoverable after connection");

    app_ble_data_fill_enable(USER_OPB_EQ, true);
}

void app_opb_eq_server_init(void) {
    // Reset the environment
    TRACE(0, "[OPB_EQ_APP] Initializing");
    app_opb_eq_server_env.connectionIndex = 0xFF;

    // Initialize EQ manager early (loads config from NV and updates global audio config)
    // This must happen BEFORE audio streams initialize, so that audio_eq_sw_iir_cfg
    // is set correctly from NV storage instead of using the flat default
    TRACE(0, "[OPB_EQ_APP] Calling app_opb_eq_init() to load config from NV");
    app_opb_eq_init();
    TRACE(0, "[OPB_EQ_APP] EQ config loaded from NV storage");

    // Register advertising data fill handler
    TRACE(0, "[OPB_EQ_APP] Registering advertising handler");
    app_ble_register_data_fill_handle(USER_OPB_EQ,
                                     (BLE_DATA_FILL_FUNC_T)app_opb_eq_ble_data_fill_handler,
                                     false);
}

void app_opb_eq_add_server(void) {
    TRACE(0, "[OPB_EQ_APP] *** ADD_SERVER CALLED! Adding OPB EQ Server to GATT database");

    struct gapm_profile_task_add_cmd *req =
        KE_MSG_ALLOC_DYN(GAPM_PROFILE_TASK_ADD_CMD, TASK_GAPM, TASK_APP,
                         gapm_profile_task_add_cmd, 0);

    TRACE(0, "[OPB_EQ_APP] Allocated message for GAPM_PROFILE_TASK_ADD");

    // Fill message
    req->operation = GAPM_PROFILE_TASK_ADD;
    req->sec_lvl = PERM(SVC_AUTH, DISABLE); // No authentication required
    req->prf_task_id = TASK_ID_OPB_EQPS;
    req->app_task = TASK_APP;
    req->start_hdl = 0; // Dynamically allocated

    TRACE(3, "[OPB_EQ_APP] Sending GAPM_PROFILE_TASK_ADD: prf_task_id=0x%04x, app_task=0x%04x, start_hdl=0x%04x",
          req->prf_task_id, req->app_task, req->start_hdl);

    // Send the message
    ke_msg_send(req);

    TRACE(0, "[OPB_EQ_APP] Message sent successfully");
}

#endif //(BLE_APP_OPB_EQ)

/// @} APP
