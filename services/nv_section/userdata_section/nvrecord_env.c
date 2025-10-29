/***************************************************************************
 *
 * Copyright 2015-2019 BES.
 * All rights reserved. All unpublished rights reserved.
 *
 * No part of this work may be used or reproduced in any form or by any
 * means, or stored in a database or retrieval system, without prior written
 * permission of BES.
 *
 * Use of this work is governed by a license granted by BES.
 * This work contains confidential and proprietary information of
 * BES. which is protected by copyright, trade secret,
 * trademark and other intellectual property rights.
 *
 ****************************************************************************/
#if defined(NEW_NV_RECORD_ENABLED)
#include "nvrecord_env.h"
#include "hal_trace.h"
#include "nvrecord_extension.h"
#include <assert.h>
#include <stdbool.h>
#include <string.h>

static struct nvrecord_env_t localSystemInfo;

void nvrecord_rebuild_system_env(struct nvrecord_env_t *pSystemEnv) {
  memset((uint8_t *)pSystemEnv, 0, sizeof(struct nvrecord_env_t));

  pSystemEnv->media_language.language = NVRAM_ENV_MEDIA_LANGUAGE_DEFAULT;
  pSystemEnv->ibrt_mode.mode = NVRAM_ENV_TWS_MODE_DEFAULT;
  pSystemEnv->ibrt_mode.tws_connect_success = 0;
  pSystemEnv->factory_tester_status.status =
      NVRAM_ENV_FACTORY_TESTER_STATUS_DEFAULT;

  pSystemEnv->aiManagerInfo.voice_key_enable = false;
  pSystemEnv->aiManagerInfo.setedCurrentAi = 0;
  pSystemEnv->aiManagerInfo.aiStatusDisableFlag = 0;
  pSystemEnv->aiManagerInfo.amaAssistantEnableStatus = 1;

  // Initialize button configuration with defaults
  opb_earbud_config_t default_left = OPB_CONFIG_DEFAULT_LEFT_INIT;
  opb_earbud_config_t default_right = OPB_CONFIG_DEFAULT_RIGHT_INIT;
  pSystemEnv->button_config.left = default_left;
  pSystemEnv->button_config.right = default_right;
  pSystemEnv->button_config.version_major = OPB_CONFIG_VERSION_MAJOR;
  pSystemEnv->button_config.version_minor = OPB_CONFIG_VERSION_MINOR;
  pSystemEnv->button_config.version_patch = OPB_CONFIG_VERSION_PATCH;
  pSystemEnv->button_config.reserved = 0;

  // Initialize device name to empty (will use factory default)
  memset(pSystemEnv->device_name, 0, sizeof(pSystemEnv->device_name));

  localSystemInfo = *pSystemEnv;
}

int nv_record_env_get(struct nvrecord_env_t **nvrecord_env) {
  if (NULL == nvrecord_env) {
    return -1;
  }

  if (NULL == nvrecord_extension_p) {
    return -1;
  }

  localSystemInfo = nvrecord_extension_p->system_info;
  *nvrecord_env = &localSystemInfo;

  return 0;
}

int nv_record_env_set(struct nvrecord_env_t *nvrecord_env) {
  if (NULL == nvrecord_extension_p) {
    return -1;
  }

  uint32_t lock = nv_record_pre_write_operation();
  nvrecord_extension_p->system_info = *nvrecord_env;

  nv_record_update_runtime_userdata();
  nv_record_post_write_operation(lock);
  return 0;
}

void nv_record_update_ibrt_info(uint32_t newMode, bt_bdaddr_t *ibrtPeerAddr) {
  if (NULL == nvrecord_extension_p) {
    return;
  }

  uint32_t lock = nv_record_pre_write_operation();

  TRACE(2, "##%s,%d", __func__, newMode);
  nvrecord_extension_p->system_info.ibrt_mode.mode = newMode;
  memcpy(nvrecord_extension_p->system_info.ibrt_mode.record.bdAddr.address,
         ibrtPeerAddr->address, BTIF_BD_ADDR_SIZE);

  nv_record_update_runtime_userdata();
  nv_record_post_write_operation(lock);
}

void nv_record_update_factory_tester_status(uint32_t status) {
  if (NULL == nvrecord_extension_p) {
    return;
  }

  uint32_t lock = nv_record_pre_write_operation();

  nvrecord_extension_p->system_info.factory_tester_status.status = status;

  nv_record_update_runtime_userdata();
  nv_record_post_write_operation(lock);
}

int nv_record_env_init(void) {
  nv_record_open(section_usrdata_ddbrecord);
  return 0;
}

// Get button configuration from NV storage
int nv_record_get_button_config(opb_config_t **config) {
  struct nvrecord_env_t *env = NULL;

  if (!config)
    return -1;

  if (nv_record_env_get(&env) != 0)
    return -1;

  *config = &env->button_config;
  return 0;
}

// Set button configuration and mark for save
int nv_record_set_button_config(const opb_config_t *config) {
  struct nvrecord_env_t *env = NULL;

  if (!config)
    return -1;

  if (nv_record_env_get(&env) != 0)
    return -1;

  env->button_config = *config;
  return nv_record_env_set(env);
}

// Get device name from NV storage
int nv_record_get_device_name(char **name) {
  struct nvrecord_env_t *env = NULL;

  if (!name)
    return -1;

  if (nv_record_env_get(&env) != 0)
    return -1;

  *name = env->device_name;
  return 0;
}

// Set device name and mark for save
int nv_record_set_device_name(const char *name) {
  struct nvrecord_env_t *env = NULL;

  if (!name)
    return -1;

  if (nv_record_env_get(&env) != 0)
    return -1;

  // Copy name with truncation if too long
  strncpy(env->device_name, name, sizeof(env->device_name) - 1);
  env->device_name[sizeof(env->device_name) - 1] = '\0';

  return nv_record_env_set(env);
}

#endif // #if defined(NEW_NV_RECORD_ENABLED)
