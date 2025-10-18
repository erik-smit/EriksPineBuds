# Erik's PineBuds Configuration GATT Service Specification

**Version**: 1.0
**Date**: 2025-10-18
**Status**: Implemented

## Overview

This document defines the Bluetooth Low Energy (BLE) GATT service for configuring touch controls on Erik's PineBuds wireless earbuds. The service allows a companion app to read and write button configuration mappings for various touch gestures.

## Service Definition

### Primary Service
- **Service Name**: Erik's PineBuds Configuration Service
- **Service UUID**: `0000FFC0-0000-1000-8000-00805F9B34FB`
- **Service Type**: Primary
- **BLE Device Name**: `BES_ble`

## Characteristics

### 1. Left Earbud Configuration
- **UUID**: `0000FFC1-0000-1000-8000-00805F9B34FB`
- **Properties**: Read, Write
- **Permissions**: Read (No authentication), Write (No authentication)
- **Size**: 16 bytes
- **Description**: Configuration mappings for left earbud touch gestures

**Data Format**:
```
Byte 0-3:   Single Tap Action (uint32_t, little-endian)
Byte 4-7:   Double Tap Action (uint32_t, little-endian)
Byte 8-11:  Triple Tap Action (uint32_t, little-endian)
Byte 12-15: Long Press Action (uint32_t, little-endian)
```

**Behavior**: Writing to this characteristic immediately applies and saves the configuration to NV flash. TWS sync automatically transfers the configuration to the paired earbud.

### 2. Right Earbud Configuration
- **UUID**: `0000FFC2-0000-1000-8000-00805F9B34FB`
- **Properties**: Read, Write
- **Permissions**: Read (No authentication), Write (No authentication)
- **Size**: 16 bytes
- **Description**: Configuration mappings for right earbud touch gestures

**Data Format**:
```
Byte 0-3:   Single Tap Action (uint32_t, little-endian)
Byte 4-7:   Double Tap Action (uint32_t, little-endian)
Byte 8-11:  Triple Tap Action (uint32_t, little-endian)
Byte 12-15: Long Press Action (uint32_t, little-endian)
```

**Behavior**: Writing to this characteristic immediately applies and saves the configuration to NV flash. TWS sync automatically transfers the configuration to the paired earbud.

### 3. Configuration Version
- **UUID**: `0000FFC3-0000-1000-8000-00805F9B34FB`
- **Properties**: Read
- **Permissions**: Read (No authentication)
- **Size**: 3 bytes
- **Description**: Protocol version for compatibility checking

**Data Format**:
```
Byte 0:   Major Version (uint8_t) = 1
Byte 1:   Minor Version (uint8_t) = 0
Byte 2:   Patch Version (uint8_t) = 0
```

**Current Version**: 1.0.0

## Action Codes

The following action codes can be assigned to gestures:

| Code | Action | Description |
|------|--------|-------------|
| 0x0000 | None | No action |
| 0x0001 | Play/Pause | Toggle audio playback |
| 0x0002 | Next Track | Skip to next track |
| 0x0003 | Previous Track | Return to previous track |
| 0x0004 | Volume Up | Increase volume by one step |
| 0x0005 | Volume Down | Decrease volume by one step |
| 0x0006 | Toggle ANC | Toggle Active Noise Cancellation |
| 0x0007 | Voice Assistant | Trigger voice assistant (Siri/Google) |
| 0x0008 | Answer Call | Answer incoming call |
| 0x0009 | Reject Call | Reject incoming call |
| 0x000A | End Call | End current call |
| 0x000B | Mute Microphone | Toggle microphone mute |
| 0x000C | Transparency Mode | Enable transparency/ambient mode |
| 0x000D | ANC Off | Disable ANC |
| 0x000E | Toggle EQ | Toggle EQ preset |
| 0x000F | Enter Pairing | Enter Bluetooth pairing mode |

## Data Structures (C/C++)

### Action Enum
```c
typedef enum {
    OPB_ACTION_NONE = 0x0000,
    OPB_ACTION_PLAY_PAUSE = 0x0001,
    OPB_ACTION_NEXT_TRACK = 0x0002,
    OPB_ACTION_PREVIOUS_TRACK = 0x0003,
    OPB_ACTION_VOLUME_UP = 0x0004,
    OPB_ACTION_VOLUME_DOWN = 0x0005,
    OPB_ACTION_TOGGLE_ANC = 0x0006,
    OPB_ACTION_VOICE_ASSISTANT = 0x0007,
    OPB_ACTION_ANSWER_CALL = 0x0008,
    OPB_ACTION_REJECT_CALL = 0x0009,
    OPB_ACTION_END_CALL = 0x000A,
    OPB_ACTION_MUTE_MIC = 0x000B,
    OPB_ACTION_TRANSPARENCY = 0x000C,
    OPB_ACTION_ANC_OFF = 0x000D,
    OPB_ACTION_CUSTOM_1 = 0x000E,
    OPB_ACTION_CUSTOM_2 = 0x000F,
} opb_button_action_t;
```

### Earbud Configuration
```c
typedef struct __attribute__((packed)) {
    uint32_t single_tap;
    uint32_t double_tap;
    uint32_t triple_tap;
    uint32_t long_press;
} opb_earbud_config_t;
```

### Full Configuration
```c
typedef struct {
    opb_earbud_config_t left;
    opb_earbud_config_t right;
    uint8_t version_major;
    uint8_t version_minor;
    uint8_t version_patch;
    uint8_t reserved;
} opb_config_t;
```

## Communication Flow

### 1. Initial Connection
```
App → Device: Connect via BLE to "BES_ble"
App → Device: Discover services
App → Device: Discover characteristics
App → Device: Read Configuration Version (0xFFC3)
App ← Device: Returns version (1.0.0)
```

### 2. Read Current Configuration
```
App → Device: Read Left Earbud Config (0xFFC1)
App ← Device: Returns 16 bytes of left config
App → Device: Read Right Earbud Config (0xFFC2)
App ← Device: Returns 16 bytes of right config
```

### 3. Write New Configuration
```
App → Device: Write Left Earbud Config (0xFFC1) = [16 bytes]
App ← Device: Write successful
→ Firmware immediately applies and saves to NV flash
→ TWS sync sends config to paired earbud

App → Device: Write Right Earbud Config (0xFFC2) = [16 bytes]
App ← Device: Write successful
→ Firmware immediately applies and saves to NV flash
→ TWS sync sends config to paired earbud
```

**Important**: No separate "apply" command is needed. Each write is applied and persisted immediately.

## Default Configuration

### Left Earbud Defaults
- **Single Tap**: Play/Pause (0x0001)
- **Double Tap**: Previous Track (0x0003)
- **Triple Tap**: Volume Down (0x0005)
- **Long Press**: Toggle ANC (0x0006)

### Right Earbud Defaults
- **Single Tap**: Play/Pause (0x0001)
- **Double Tap**: Next Track (0x0002)
- **Triple Tap**: Volume Up (0x0004)
- **Long Press**: Toggle ANC (0x0006)

### Single Bud Mode Defaults
When only one earbud is active:
- **Single Tap**: Play/Pause (0x0001)
- **Double Tap**: Next Track (0x0002)
- **Triple Tap**: Volume Up (0x0004)
- **Long Press**: Previous Track (0x0003)

## Implementation Details

### TWS Configuration Synchronization
When configuration is written to one earbud (e.g., left), the firmware automatically:
1. Saves the configuration to NV flash on the receiving earbud
2. Sends the configuration to the paired earbud via TWS
3. The paired earbud saves it to its NV flash
4. Both earbuds reload configuration from NV on next boot

### Storage
- Configuration is stored in NV flash using the `nvrecord` service
- Total storage: 40 bytes (16 bytes left + 16 bytes right + 8 bytes metadata)
- Configuration persists across reboots and factory resets (unless explicitly cleared)
- Maximum write cycles: Limited by flash (typically ~10,000 cycles)

### Error Handling
- Invalid action codes are validated and rejected
- Write operations that fail return GATT error code
- No explicit status/error characteristics (relies on GATT protocol errors)

## Security Considerations

### Current Implementation (v1.0)
- ⚠️ No authentication required (open access)
- ⚠️ No encryption
- ⚠️ Anyone within BLE range can connect and modify settings
- ⚠️ Separate BLE device ("BES_ble") from main audio device ("PineBuds Pro")
- ℹ️ Configuration service only available on "BES_ble" device

### Future Enhancements
- Add BLE bonding for paired devices
- Optional PIN/passkey for write operations
- Integrate BLE service into main Bluetooth Classic device
- Encrypted characteristics for sensitive features

## Power Considerations

- GATT service active only when earbuds are out of case
- BLE advertising disabled when in charging case (release build)
- Configuration writes should be batched to minimize power
- Recommend disconnecting BLE after configuration complete

## Compatibility

### Firmware Requirements
- BLE 4.0 or higher
- GATT server support
- TWS (True Wireless Stereo) support for sync
- 40 bytes NV storage for configuration
- BES2300 SoC with BLE stack

### App Requirements
- Android 5.0+ (API 21) for BLE Central
- iOS 8.0+ for CoreBluetooth
- BLE scanning and GATT client capabilities
- Location permission (Android 6-11 for BLE scanning)
- Bluetooth permissions (Android 12+)

## Testing Checklist

- [x] Service discoverable by generic BLE scanner (nRF Connect)
- [x] Service appears as "BES_ble" device
- [x] All three characteristics have correct properties
- [x] Read operations return valid data
- [x] Write operations accept valid data
- [x] Configuration applies immediately on write
- [x] Configuration saves to NV flash
- [x] Configuration survives reboot
- [x] TWS sync transfers config to paired earbud
- [x] Both earbuds can be configured independently
- [x] Default configuration loads on first boot
- [x] BLE and Bluetooth Classic coexist without issues
- [x] Touch controls work with configured actions

## Known Limitations

1. **Separate BLE Device**: Configuration service appears as "BES_ble", separate from "PineBuds Pro" audio device
2. **No Security**: No pairing/bonding required - anyone can connect and modify settings
3. **No Status Feedback**: No explicit success/error notifications (relies on GATT protocol)
4. **Debug Mode Only**: BLE active in case only when `DEBUG_FORCE_BOX_OPEN=1` (debug builds)

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-10-18 | Updated to match actual implementation, simplified to 3 characteristics |

## References

- Bluetooth Core Specification 5.0+
- GATT Specification Profile
- Erik's PineBuds Firmware Documentation
- BES2300 BLE Stack Documentation
- Android Companion App: [android/README.md](../android/README.md)

