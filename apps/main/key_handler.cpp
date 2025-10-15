#include "a2dp_api.h"
#include "app_audio.h"
#include "app_battery.h"
#include "app_ble_include.h"
#include "app_bt.h"
#include "app_bt_func.h"
#include "app_bt_media_manager.h"
#include "app_key.h"
#include "app_overlay.h"
#include "app_pwl.h"
#include "app_status_ind.h"
#include "app_thread.h"
#include "app_tws_ibrt_cmd_handler.h"
#include "app_utils.h"
#include "apps.h"
#include "audio_process.h"
#include "audioflinger.h"
#include "besbt.h"
#include "bt_drv_interface.h"
#include "bt_if.h"
#include "btapp.h"
#include "cmsis_os.h"
#include "crash_dump_section.h"
#include "factory_section.h"
#include "gapm_task.h"
#include "hal_bootmode.h"
#include "hal_i2c.h"
#include "hal_sleep.h"
#include "hal_timer.h"
#include "hal_trace.h"
#include "list.h"
#include "log_section.h"
#include "me_api.h"
#include "norflash_api.h"
#include "nvrecord.h"
#include "nvrecord_dev.h"
#include "nvrecord_env.h"
#include "os_api.h"
#include "pmu.h"
#include "stdio.h"
#include "string.h"
#include "tgt_hardware.h"

#ifdef __INTERCONNECTION__
#include "app_ble_mode_switch.h"
#include "app_interconnection.h"
#include "app_interconnection_ble.h"
#include "app_interconnection_logic_protocol.h"
#endif

#include "app_ibrt_customif_cmd.h"
#include "app_ibrt_customif_ui.h"
#include "app_ibrt_if.h"
#include "app_ibrt_ui_test.h"
#include "app_ibrt_voice_report.h"
#include "app_tws_if.h"

#include "app_anc.h"
#include "app_opb_config.h"

extern struct BT_DEVICE_T app_bt_device;

/*
 * handling of touch events when the devices are turned on

 * Both pods active:

 * Right Ear:
 * Single tap : Play/Pause
 * Double tap : Next track
 * Hold       : ANC on/off
 * Triple tap : Volume Up
 *
 * Left Ear:
 * Single tap : Play/Pause
 * Double tap : Previous track
 * Hold       : ANC on/off
 * Triple tap : Volume Down

 * Single pod active:

 * Single tap : Play/Pause
 * Double tap : Next track
 * Hold       : Previous track
 * Triple tap : Volume Up
 * Quad   tap : Volume Down



 * We use app_ibrt_if_start_user_action for handling actions, as this will apply
 locally if we are link master
 * OR send it over the link to the other bud if we are not
*/

void send_vol_up(void) {
  uint8_t action[] = {IBRT_ACTION_LOCAL_VOLUP};
  app_ibrt_if_start_user_action(action, sizeof(action));
}
void send_play_pause(void) {
  if (app_bt_device.a2dp_play_pause_flag != 0) {
    uint8_t action[] = {IBRT_ACTION_PAUSE};
    app_ibrt_if_start_user_action(action, sizeof(action));
  } else {
    uint8_t action[] = {IBRT_ACTION_PLAY};
    app_ibrt_if_start_user_action(action, sizeof(action));
  }
}
void send_vol_down(void) {
  uint8_t action[] = {IBRT_ACTION_LOCAL_VOLDN};
  app_ibrt_if_start_user_action(action, sizeof(action));
}

void send_next_track(void) {
  uint8_t action[] = {IBRT_ACTION_FORWARD};
  app_ibrt_if_start_user_action(action, sizeof(action));
}

void send_prev_track(void) {
  uint8_t action[] = {IBRT_ACTION_BACKWARD};
  app_ibrt_if_start_user_action(action, sizeof(action));
}

void send_enable_disable_anc(void) {
  uint8_t action[] = {IBRT_ACTION_ANC_NOTIRY_MASTER_EXCHANGE_COEF};
  app_ibrt_if_start_user_action(action, sizeof(action));
}

// Execute configured action based on action code
void execute_button_action(opb_button_action_t action) {
  TRACE(1, "[KEY] Executing action: %d", action);

  switch (action) {
    case OPB_ACTION_NONE:
      break;
    case OPB_ACTION_PLAY_PAUSE:
      send_play_pause();
      break;
    case OPB_ACTION_NEXT_TRACK:
      send_next_track();
      break;
    case OPB_ACTION_PREVIOUS_TRACK:
      send_prev_track();
      break;
    case OPB_ACTION_VOLUME_UP:
      send_vol_up();
      break;
    case OPB_ACTION_VOLUME_DOWN:
      send_vol_down();
      break;
    case OPB_ACTION_TOGGLE_ANC:
      send_enable_disable_anc();
      break;
    // TODO: Implement additional actions (voice assistant, call control, etc.)
    case OPB_ACTION_VOICE_ASSISTANT:
    case OPB_ACTION_ANSWER_CALL:
    case OPB_ACTION_REJECT_CALL:
    case OPB_ACTION_END_CALL:
    case OPB_ACTION_MUTE_MIC:
    case OPB_ACTION_TRANSPARENCY:
    case OPB_ACTION_ANC_OFF:
      TRACE(1, "[KEY] Action not yet implemented: %d", action);
      break;
    default:
      TRACE(1, "[KEY] Unknown action: %d", action);
      break;
  }
}

void app_key_single_tap(APP_KEY_STATUS *status, void *param) {
  TRACE(2, "%s event %d", __func__, status->event);

  // Always play/pause with single tap - simple and consistent
  send_play_pause();
}
void app_key_double_tap(APP_KEY_STATUS *status, void *param) {
  TRACE(2, "%s event %d", __func__, status->event);

  if (!app_tws_ibrt_tws_link_connected()) {
    // No other bud paired - use default (next track)
    TRACE(0, "Handling %s in single bud mode", __func__);
    send_next_track();
  } else {
    // Bud's are working as a pair - use configured action
    bool is_left = app_tws_is_left_side();
    TRACE(1, "Handling %s as %s bud", __func__, is_left ? "left" : "right");
    opb_button_action_t action = app_opb_config_get_action(is_left, OPB_GESTURE_DOUBLE_TAP);
    execute_button_action(action);
  }
}

void app_key_triple_tap(APP_KEY_STATUS *status, void *param) {
  TRACE(2, "%s event %d", __func__, status->event);

  if (!app_tws_ibrt_tws_link_connected()) {
    // No other bud paired - use default (volume up)
    TRACE(0, "Handling %s in single bud mode", __func__);
    send_vol_up();
  } else {
    // Bud's are working as a pair - use configured action
    bool is_left = app_tws_is_left_side();
    TRACE(1, "Handling %s as %s bud", __func__, is_left ? "left" : "right");
    opb_button_action_t action = app_opb_config_get_action(is_left, OPB_GESTURE_TRIPLE_TAP);
    execute_button_action(action);
  }
}
void app_key_quad_tap(APP_KEY_STATUS *status, void *param) {
  TRACE(2, "%s event %d", __func__, status->event);

  if (!app_tws_ibrt_tws_link_connected()) {
    // No other bud paired
    TRACE(0, "Handling %s in single bud mode", __func__);
    send_vol_down();
  }
}

void app_key_long_press_down(APP_KEY_STATUS *status, void *param) {
  TRACE(2, "%s event %d", __func__, status->event);

  if (!app_tws_ibrt_tws_link_connected()) {
    // No other bud paired - use default (previous track)
    TRACE(0, "Handling %s in single bud mode", __func__);
    send_prev_track();
  } else {
    // Bud's are working as a pair - use configured action
    bool is_left = app_tws_is_left_side();
    TRACE(1, "Handling %s as %s bud", __func__, is_left ? "left" : "right");
    opb_button_action_t action = app_opb_config_get_action(is_left, OPB_GESTURE_LONG_PRESS);
    execute_button_action(action);
  }
}

void app_key_reboot(APP_KEY_STATUS *status, void *param) {
  TRACE(1, "%s ", __func__);
  hal_cmu_sys_reboot();
}

void app_key_init(void) {
  uint8_t i = 0;
  TRACE(1, "%s", __func__);

  // Initialize button configuration
  app_opb_config_init();

  const APP_KEY_HANDLE key_cfg[] = {

      {{APP_KEY_CODE_PWR, APP_KEY_EVENT_CLICK}, "", app_key_single_tap, NULL},
      {{APP_KEY_CODE_PWR, APP_KEY_EVENT_DOUBLECLICK},
       "",
       app_key_double_tap,
       NULL},
      {{APP_KEY_CODE_PWR, APP_KEY_EVENT_TRIPLECLICK},
       "",
       app_key_triple_tap,
       NULL},
      {{APP_KEY_CODE_PWR, APP_KEY_EVENT_ULTRACLICK},
       "",
       app_key_quad_tap,
       NULL},
      {{APP_KEY_CODE_PWR, APP_KEY_EVENT_LONGPRESS},
       "",
       app_key_long_press_down,
       NULL},
  };

  app_key_handle_clear();
  for (i = 0; i < (sizeof(key_cfg) / sizeof(APP_KEY_HANDLE)); i++) {
    app_key_handle_registration(&key_cfg[i]);
  }
}

void app_key_init_on_charging(void) {
  uint8_t i = 0;
  const APP_KEY_HANDLE key_cfg[] = {
      {{APP_KEY_CODE_PWR, APP_KEY_EVENT_LONGLONGPRESS},
       "long press reboot",
       app_key_reboot,
       NULL},
  // {{APP_KEY_CODE_PWR,APP_KEY_EVENT_CLICK},"bt function
  // key",app_dfu_key_handler, NULL},
#ifdef __USB_COMM__
      {{APP_KEY_CODE_PWR, APP_KEY_EVENT_LONGPRESS},
       "usb cdc key",
       app_usb_cdc_comm_key_handler,
       NULL},
#endif
  };

  TRACE(1, "%s", __func__);
  for (i = 0; i < (sizeof(key_cfg) / sizeof(APP_KEY_HANDLE)); i++) {
    app_key_handle_registration(&key_cfg[i]);
  }
}
