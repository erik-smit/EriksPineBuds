# OpenPineBuds EQ Configuration GATT Service Specification

**Version**: 1.0
**Date**: 2025-11-01
**Status**: Draft

## Overview

This document defines the Bluetooth Low Energy (BLE) GATT service for configuring the hardware equalizer on OpenPineBuds wireless earbuds. The service allows a companion app to configure up to 8 bands of parametric EQ using the BES2300's hardware codec IIR filters.

## Service Definition

### Primary Service
- **Service Name**: OpenPineBuds EQ Configuration Service
- **Service UUID**: `0000FFD0-0000-1000-8000-00805F9B34FB`
- **Service Type**: Primary

## Hardware Capabilities

- **Maximum Bands**: 8 (BES2300P hardware limit)
- **Processing**: Hardware codec DSP (zero CPU overhead)
- **Filter Types**: Low Shelf, High Shelf, Peak, Low Pass, High Pass
- **Sample Rates**: Auto-configured based on audio stream (8k-192kHz)

## Characteristics

### 1. EQ Configuration
- **UUID**: `0000FFD1-0000-1000-8000-00805F9B34FB`
- **Properties**: Read, Write
- **Permissions**: Read (No authentication), Write (No authentication)
- **Size**: 144 bytes (max)
- **Description**: Complete EQ configuration with up to 8 bands

**Data Format**:
```
Byte 0-3:   Global Gain 0 (float32, pre-EQ gain in dB)
Byte 4-7:   Global Gain 1 (float32, post-EQ gain in dB)
Byte 8-11:  Number of bands (uint32_t, 0-8)
Byte 12-15: Reserved (uint32_t, 0x00000000)

For each band (16 bytes × 8 = 128 bytes):
  Byte 0-3:   Filter Type (uint32_t, see Filter Types table)
  Byte 4-7:   Gain (float32, dB, range: -12.0 to +12.0)
  Byte 8-11:  Center Frequency (float32, Hz, range: 20.0 to 20000.0)
  Byte 12-15: Q Factor (float32, range: 0.3 to 10.0)
```

**Total Size**: 16 + (16 × 8) = 144 bytes

### 2. EQ Preset
- **UUID**: `0000FFD2-0000-1000-8000-00805F9B34FB`
- **Properties**: Read, Write
- **Permissions**: Read (No authentication), Write (No authentication)
- **Size**: 4 bytes
- **Description**: Apply a predefined EQ preset

**Data Format**:
```
Byte 0-3:   Preset ID (uint32_t)
```

**Preset IDs**:
| ID | Name | Description |
|----|------|-------------|
| 0x00 | Flat | No EQ (bypass) |
| 0x01 | Bass Boost | Enhanced low frequencies |
| 0x02 | Treble Boost | Enhanced high frequencies |
| 0x03 | V-Shape | Bass + treble boost |
| 0x04 | Vocal | Midrange emphasis |
| 0x05 | Classical | Balanced, wide soundstage |
| 0x06 | Rock | Punchy mids and bass |
| 0x07 | Jazz | Smooth, warm signature |
| 0x08 | Electronic | Deep bass, crisp highs |
| 0x09 | Podcast | Voice clarity |
| 0xFF | Custom | User-defined (from Characteristic 1) |

### 3. EQ Enable/Disable
- **UUID**: `0000FFD3-0000-1000-8000-00805F9B34FB`
- **Properties**: Read, Write
- **Permissions**: Read (No authentication), Write (No authentication)
- **Size**: 4 bytes
- **Description**: Enable or bypass the EQ

**Data Format**:
```
Byte 0:     Enable (uint8_t, 0 = bypass, 1 = enabled)
Byte 1-3:   Reserved (0x00)
```

### 4. EQ Version & Capabilities
- **UUID**: `0000FFD4-0000-1000-8000-00805F9B34FB`
- **Properties**: Read
- **Permissions**: Read (No authentication)
- **Size**: 16 bytes
- **Description**: EQ capabilities and version info

**Data Format**:
```
Byte 0:     Version Major (uint8_t)
Byte 1:     Version Minor (uint8_t)
Byte 2:     Version Patch (uint8_t)
Byte 3:     Max Bands (uint8_t, typically 8)
Byte 4-7:   Supported Filter Types Bitmask (uint32_t)
            Bit 0: Low Shelf
            Bit 1: Peak
            Bit 2: High Shelf
            Bit 3: Low Pass
            Bit 4: High Pass
            Bits 5-31: Reserved
Byte 8-11:  Min Frequency (float32, Hz)
Byte 12-15: Max Frequency (float32, Hz)
```

**Current Version**: 1.0.0

### 5. Apply EQ Command
- **UUID**: `0000FFD5-0000-1000-8000-00805F9B34FB`
- **Properties**: Write
- **Permissions**: Write (No authentication)
- **Size**: 4 bytes
- **Description**: Apply and persist EQ configuration

**Data Format**:
```
Byte 0:   Command (uint8_t)
          0x01 = Apply and Save to NV
          0x02 = Apply without Saving (temporary)
          0x03 = Reset to Flat (bypass)
          0xFF = Reboot device
Byte 1:   Target (uint8_t)
          0x00 = Both channels (stereo)
          0x01 = Left channel only
          0x02 = Right channel only
Byte 2-3: Checksum (uint16_t, CRC-16)
```

### 6. EQ Status Notification
- **UUID**: `0000FFD6-0000-1000-8000-00805F9B34FB`
- **Properties**: Notify
- **Permissions**: Read (No authentication)
- **Size**: 4 bytes
- **Description**: Status updates and error notifications

**Data Format**:
```
Byte 0:   Status Code (uint8_t)
          0x00 = Success
          0x01 = Invalid configuration
          0x02 = Checksum error
          0x03 = Storage error
          0x04 = Not supported
          0x05 = Out of range parameter
          0xFF = Unknown error
Byte 1:   Last command (uint8_t)
Byte 2-3: Reserved (0x00)
```

## Filter Types

| Type | Code | Description | Parameters |
|------|------|-------------|------------|
| Low Shelf | 0x00 | Bass adjustment | fc, gain, Q |
| Peak | 0x01 | Parametric peak/notch | fc, gain, Q |
| High Shelf | 0x02 | Treble adjustment | fc, gain, Q |
| Low Pass | 0x03 | Subsonic filter | fc, Q (gain ignored) |
| High Pass | 0x04 | Subsonic filter | fc, Q (gain ignored) |

## Parameter Ranges

| Parameter | Min | Max | Default | Unit |
|-----------|-----|-----|---------|------|
| Global Gain 0 | -12.0 | +12.0 | 0.0 | dB |
| Global Gain 1 | -12.0 | +12.0 | 0.0 | dB |
| Band Gain | -12.0 | +12.0 | 0.0 | dB |
| Frequency | 20.0 | 20000.0 | 1000.0 | Hz |
| Q Factor | 0.3 | 10.0 | 0.707 | - |

## Example Preset Configurations

### Flat (Bypass)
```
num_bands: 0
bypass: true
```

### Bass Boost (5-band)
```
Band 1: Low Shelf,  60 Hz,  +6 dB, Q=0.7
Band 2: Peak,       150 Hz, +3 dB, Q=1.0
Band 3: Peak,       400 Hz, +1 dB, Q=1.0
Band 4: Peak,       1k Hz,   0 dB, Q=1.0
Band 5: Peak,       4k Hz,  -1 dB, Q=1.0
```

### Treble Boost (4-band)
```
Band 1: Peak,       1k Hz,   0 dB, Q=1.0
Band 2: Peak,       2.5k Hz, +2 dB, Q=1.0
Band 3: Peak,       6k Hz,   +4 dB, Q=1.0
Band 4: High Shelf, 12k Hz,  +6 dB, Q=0.7
```

### V-Shape (8-band)
```
Band 1: Low Shelf,  60 Hz,   +5 dB, Q=0.7
Band 2: Peak,       200 Hz,  +2 dB, Q=1.0
Band 3: Peak,       500 Hz,  -2 dB, Q=1.0
Band 4: Peak,       1k Hz,   -3 dB, Q=1.0
Band 5: Peak,       2k Hz,   -2 dB, Q=1.0
Band 6: Peak,       4k Hz,   +2 dB, Q=1.0
Band 7: Peak,       8k Hz,   +4 dB, Q=1.0
Band 8: High Shelf, 16k Hz,  +5 dB, Q=0.7
```

## Communication Flow

### 1. Read Capabilities
```
App → Device: Read EQ Version & Capabilities (0xFFD4)
App ← Device: Returns version, max bands, frequency range
```

### 2. Apply Preset
```
App → Device: Write EQ Preset (0xFFD2) = [0x01] (Bass Boost)
App → Device: Write Apply Command (0xFFD5) = [0x01, 0x00, CRC]
App ← Device: Status notification (success)
```

### 3. Custom EQ Configuration
```
App → Device: Write EQ Configuration (0xFFD1) = [custom config]
App → Device: Write EQ Enable (0xFFD3) = [0x01]
App → Device: Write Apply Command (0xFFD5) = [0x01, 0x00, CRC]
App ← Device: Status notification (success)
```

### 4. Disable EQ
```
App → Device: Write EQ Enable (0xFFD3) = [0x00]
```

## Data Structures (C/C++)

### Filter Type Enum
```c
typedef enum {
    OPB_EQ_FILTER_LOW_SHELF = 0,
    OPB_EQ_FILTER_PEAK,
    OPB_EQ_FILTER_HIGH_SHELF,
    OPB_EQ_FILTER_LOW_PASS,
    OPB_EQ_FILTER_HIGH_PASS,
} opb_eq_filter_type_t;
```

### Band Configuration
```c
typedef struct __attribute__((packed)) {
    uint32_t type;      // opb_eq_filter_type_t
    float gain;         // dB
    float frequency;    // Hz
    float q;            // Q factor
} opb_eq_band_t;
```

### EQ Configuration
```c
typedef struct __attribute__((packed)) {
    float gain0;                    // Pre-EQ gain
    float gain1;                    // Post-EQ gain
    uint32_t num_bands;             // 0-8
    uint32_t reserved;
    opb_eq_band_t bands[8];         // Up to 8 bands
} opb_eq_config_t;
```

### Preset Enum
```c
typedef enum {
    OPB_EQ_PRESET_FLAT = 0,
    OPB_EQ_PRESET_BASS_BOOST,
    OPB_EQ_PRESET_TREBLE_BOOST,
    OPB_EQ_PRESET_V_SHAPE,
    OPB_EQ_PRESET_VOCAL,
    OPB_EQ_PRESET_CLASSICAL,
    OPB_EQ_PRESET_ROCK,
    OPB_EQ_PRESET_JAZZ,
    OPB_EQ_PRESET_ELECTRONIC,
    OPB_EQ_PRESET_PODCAST,
    OPB_EQ_PRESET_CUSTOM = 0xFF,
} opb_eq_preset_t;
```

## Error Handling

### Parameter Validation
- Frequency out of range (20-20000 Hz): Return 0x05 (Out of range)
- Gain out of range (-12 to +12 dB): Return 0x05 (Out of range)
- Q out of range (0.3 to 10.0): Return 0x05 (Out of range)
- Invalid filter type: Return 0x04 (Not supported)
- Too many bands (>8): Return 0x01 (Invalid configuration)

### Checksum Validation
CRC-16/XMODEM calculated over:
- Command byte
- Target byte
- Current EQ configuration (144 bytes)

Total: 146 bytes

### Storage Failure
If NV storage fails:
1. EQ remains active in RAM (temporary)
2. Return status 0x03 (Storage error)
3. Will revert to saved settings on reboot

## Integration with Existing System

### Mapping to Hardware IIR
The BLE configuration maps directly to the hardware codec IIR system:

```c
// Convert opb_eq_config_t to IIR_CFG_T
IIR_CFG_T iir_cfg;
iir_cfg.gain0 = eq_config.gain0;
iir_cfg.gain1 = eq_config.gain1;
iir_cfg.num = eq_config.num_bands;

for (int i = 0; i < eq_config.num_bands; i++) {
    iir_cfg.param[i].type = (IIR_TYPE_T)eq_config.bands[i].type;
    iir_cfg.param[i].gain = eq_config.bands[i].gain;
    iir_cfg.param[i].fc = eq_config.bands[i].frequency;
    iir_cfg.param[i].Q = eq_config.bands[i].q;
}

// Apply to hardware
HW_CODEC_IIR_CFG_T *hw_cfg = hw_codec_iir_get_cfg(sample_rate, &iir_cfg);
hw_codec_iir_set_cfg(hw_cfg, sample_rate, HW_CODEC_IIR_DAC);
```

## Power & Performance

- **CPU Overhead**: 0% (hardware processing)
- **Memory**: ~200 bytes RAM, ~150 bytes NV storage
- **Latency**: < 1ms (hardware filters)
- **Battery Impact**: Negligible (codec DSP always active)

## Testing Checklist

- [ ] Service discoverable by BLE scanner
- [ ] All characteristics readable/writable
- [ ] Preset selection works correctly
- [ ] Custom EQ configuration applies
- [ ] Parameter validation works
- [ ] Out-of-range values rejected
- [ ] Checksum validation works
- [ ] EQ persists across reboots
- [ ] Enable/disable toggles EQ
- [ ] Audio quality maintained
- [ ] No audible artifacts or clicks
- [ ] Works with all sample rates
- [ ] No interference with BT Classic audio

## Compatibility

### Firmware Requirements
- BES2300 with hardware codec IIR support
- Existing audio processing pipeline
- NV storage for EQ configuration
- BLE GATT server

### App Requirements
- Android 5.0+ (API 21) or iOS 8.0+
- BLE GATT client
- Floating-point number support
- EQ visualization UI (optional)

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-11-01 | Initial specification |

## References

- `eq_cfg.c` - EQ configuration implementation
- `iir_process.h` - IIR filter structures
- `hw_codec_iir_process.h` - Hardware codec IIR API
- BES2300 Codec Hardware Manual
- OpenPineBuds Touch Control GATT Spec
