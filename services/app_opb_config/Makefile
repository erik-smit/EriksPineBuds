cur_dir := $(dir $(lastword $(MAKEFILE_LIST)))

obj_c := $(patsubst $(cur_dir)%,%,$(wildcard $(cur_dir)*.c))
obj-y := $(obj_c:.c=.o)

subdir-ccflags-y += -Iservices/nv_section/userdata_section \
                    -Iservices/nv_section/include \
                    -Iservices/nvrecord \
                    -Iservices/ble_stack/common/api \
                    -Iservices/ble_stack/ble_ip \
                    -Iservices/ble_profiles/opb_config \
                    -Iservices/app_opb_config \
                    -Iplatform/hal \
                    $(BT_IF_INCLUDES)
