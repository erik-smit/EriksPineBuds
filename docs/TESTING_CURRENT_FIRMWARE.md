# Testing Current OpenPineBuds Firmware

## What's Already Working

The current firmware has basic functionality you can test without any modifications:

### 1. Touch Controls (Hardcoded)

The earbuds respond to touch gestures with hardcoded actions:

**Both Earbuds Connected:**

| Gesture | Left Earbud | Right Earbud |
|---------|-------------|--------------|
| Single Tap | Play/Pause | Play/Pause |
| Double Tap | Previous Track | Next Track |
| Triple Tap | Volume Down | Volume Up |
| Long Press | Toggle ANC | Toggle ANC |

**Single Earbud Mode:**

| Gesture | Action |
|---------|--------|
| Single Tap | Play/Pause |
| Double Tap | Next Track |
| Triple Tap | Volume Up |
| Quad Tap | Volume Down |
| Long Press | Previous Track |

**Test this now:**
1. Flash the current firmware to your buds
2. Pair with your phone via Bluetooth Classic
3. Play music and test each gesture
4. Verify the actions match the table above

### 2. BLE Scanning

The firmware has BLE capability but no custom GATT service yet.

**Test with nRF Connect:**
1. Install nRF Connect app on your phone
2. Open nRF Connect and scan for devices
3. Look for your OpenPineBuds in the list
4. Connect and explore available services

**What you'll see:**
- Standard GATT services (Generic Access, Generic Attribute, etc.)
- Possibly some vendor-specific services
- **You will NOT see** the custom config service (`0000FFC0...`) yet

### 3. Basic Pairing and Audio

**Test Bluetooth Classic:**
1. Take buds out of case
2. Phone should discover "OpenPineBuds" or similar
3. Pair via Bluetooth settings
4. Play audio and verify stereo works
5. Test putting one bud in case (TWS handoff)

## What You CANNOT Test Yet

### ❌ Configuration via Companion App
- The BLE GATT service doesn't exist
- No way to read/write button mappings
- Configuration is hardcoded in firmware

### ❌ Custom Button Actions
- You can't change what gestures do
- All actions are fixed in the code
- No persistent configuration storage

### ❌ Android Companion App
- App structure exists but BLE Manager not implemented
- Cannot connect to config service (doesn't exist)
- UI is just a placeholder

## Implementation Required Before Testing Companion App

To test the companion app functionality, you need to implement firmware changes first:

### Phase 1: Minimal Testable Implementation (~2-4 hours)

Implement just enough to test with nRF Connect:

1. **Add data structures** (15 min)
   - Create `opb_config_common.h`
   - Define action enums and config structures

2. **Add NV storage** (30 min)
   - Modify `nvrecord_env.h/c`
   - Add button config to NV structure
   - Initialize with defaults

3. **Create GATT service skeleton** (1-2 hours)
   - Create basic service with UUIDs
   - Implement read characteristics only
   - Return dummy/default data

4. **Register service** (30 min)
   - Add to BLE app initialization
   - Enable in build configuration

**Test checkpoint:**
- Service visible in nRF Connect
- Can read config characteristics
- Sees default values

### Phase 2: Full Read/Write (~3-5 hours)

5. **Implement write handlers**
   - Accept config writes
   - Validate data
   - Store to RAM (not NV yet)

6. **Implement apply command**
   - Basic command handling
   - No checksum validation yet

**Test checkpoint:**
- Can write config via nRF Connect
- Changes stored in RAM
- Can read back written values

### Phase 3: Integration (~2-3 hours)

7. **Connect to key handler**
   - Modify key_handler.cpp
   - Read actions from config
   - Execute configured actions

8. **Add persistence**
   - Save to NV on apply
   - Load on boot

**Test checkpoint:**
- Write config via nRF Connect
- Test touch controls - actions change!
- Reboot - config persists

### Phase 4: Android App (~4-8 hours)

9. **Implement BLE Manager**
10. **Create configuration UI**
11. **Test end-to-end**

## Quick Start Testing Path

### Option A: Test Current Firmware (Now)
```bash
cd OpenPineBuds
./build.sh
./download.sh
# Test touch controls with music
```

### Option B: Implement Minimal GATT Service (Today)
```bash
# Follow docs/FIRMWARE_GUIDE.md Steps 1-5
# Focus on read-only implementation first
# Test with nRF Connect
```

### Option C: Full Implementation (This Week)
```bash
# Complete all firmware changes
# Test with nRF Connect thoroughly
# Then build Android app
```

## Testing Tools

### nRF Connect (Essential)
- **Download**: [Android](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp) | [iOS](https://apps.apple.com/app/nrf-connect/id1054362403)
- **Use for**:
  - Discovering BLE services
  - Reading characteristics
  - Writing test data
  - Debugging GATT issues

### LightBlue (Alternative)
- **Download**: [iOS](https://apps.apple.com/app/lightblue/id557428110) | [Android](https://play.google.com/store/apps/details?id=com.punchthrough.lightblueexplorer)
- Similar to nRF Connect
- Good for iOS testing

### Serial Debug Log
```bash
# Connect USB to OpenPineBuds debug port
./uart_log.sh
# Watch for TRACE output during testing
```

## Recommended Testing Order

1. ✅ **Test current firmware** (10 min)
   - Verify touch controls work
   - Confirm basic BLE advertising

2. ⚙️ **Implement minimal GATT service** (2-3 hours)
   - Add service skeleton
   - Test with nRF Connect

3. ⚙️ **Add write support** (2-3 hours)
   - Implement write handlers
   - Test writing configs

4. ⚙️ **Connect to key handler** (2 hours)
   - Make controls dynamic
   - Test changing actions

5. 📱 **Build Android app** (4-8 hours)
   - Complete BLE Manager
   - Create UI
   - Test end-to-end

## Troubleshooting Current Firmware

### Touch Controls Not Working
- Check if buds are awake (take out of case)
- Verify Bluetooth connection
- Try both TWS mode and single bud mode

### Can't See in nRF Connect
- Ensure BLE advertising is enabled in firmware
- Check buds are not in case
- Try turning Bluetooth off/on on phone
- Check location permission (Android)

### Audio Issues
- This is separate from BLE configuration
- Check Bluetooth Classic pairing
- Verify A2DP profile connected

## Summary

**Right now you can test:**
- ✅ Touch controls (hardcoded)
- ✅ Basic BLE advertising
- ✅ Audio playback

**You cannot test yet:**
- ❌ BLE configuration service
- ❌ Companion app functionality
- ❌ Custom button mappings

**Next step:** Implement Phase 1 (minimal GATT service) to start testing with nRF Connect, then build up from there.
