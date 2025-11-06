# Release v1.2.1

**Release Date:** November 4, 2025

This release brings a configurable N-band equalizer with companion app, improved BLE connection reliability, and enhanced development tools.

## 🎵 Major Features

### Configurable N-band Equalizer
- **10 Built-in Presets**: Flat, Bass Boost, Treble Boost, V-Shape, Vocal, Classical, Rock, Jazz, Electronic, Podcast
- **Real-time Updates**: Change EQ settings without reconnecting
- **TWS Sync**: EQ changes sync instantly to both earbuds
- **Persistent Storage**: Settings survive reboots
- **Android Companion App**: Easy-to-use UI for managing EQ

**Memory Footprint:**
- Flash: ~3.5KB
- RAM: ~80 bytes
- NV Storage: 40 bytes

### Android Companion App Improvements
- **EQ Control**: Switch between presets with one tap
- **Enable/Disable Toggle**: Quickly turn EQ on/off
- **Improved Connection**: Fixed left bud connection issues
- **Better Error Messages**: Clear feedback when connections fail
- **Cleaner UI**: Proper state management and button styling

## ✨ Improvements

### BLE Connection Reliability
- **Fixed left bud connection**: Can now connect to left or right bud independently
- **GATT Cache Clearing**: Prevents stale cached data when switching between earbuds
- **Explicit BLE Transport**: Forces BLE-only connection to avoid Classic fallback
- **Connection Cleanup**: 500ms delay ensures Android fully releases resources
- **Better Error Handling**: Shows actual BLE error codes (0x85, 0x3E, etc.) instead of generic failures

### Developer Experience
- **Automated Release Scripts**: `release.bat` / `release.sh` for version management
- **Version in REVISION_INFO**: Firmware now shows `v1.2.1:hash:target` format
- **Release Documentation**: Complete guide in `RELEASE.md`
- **Build Automation**: One command creates release, tags, and updates all version numbers

## 🐛 Bug Fixes

### Android App
- **Fixed UI State After Errors**: Scan button re-enables after connection failures
- **Fixed Status Messages**: Shows connection state and errors correctly

## 📋 Technical Details

### New BLE GATT Service
**Service UUID:** 0xFFD0

**Characteristics:**
- 0xFFD1 - EQ Config (Read/Write): Full configuration with up to 8 bands
- 0xFFD2 - EQ Preset (Read/Write): Select from 10 built-in presets
- 0xFFD3 - EQ Enable (Read/Write): Enable/disable EQ processing
- 0xFFD4 - EQ Capabilities (Read): Max bands, supported filter types
- 0xFFD5 - EQ Apply (Write): Apply settings (immediate, save, or both)
- 0xFFD6 - EQ Status (Read/Notify): Current state and active preset

### NV Storage Changes
- **NV_EXTENSION_MAJOR_VERSION**: Bumped from 4 to 5
- **New Fields**: EQ config (144 bytes), enabled flag (1 byte), preset (4 bytes)
- **Automatic Migration**: Old NV storage rebuilt on first boot with new firmware

## 📦 Installation

### Firmware (Both Earbuds)
1. Download `open_source.bin` from the release assets
2. Flash to both earbuds:
   ```bash
   # Windows
   .\bestool\bestool\target\release\bestool.exe write-image open_source.bin --port COM4
   .\bestool\bestool\target\release\bestool.exe write-image open_source.bin --port COM5

   # Linux/Mac
   bestool write-image open_source.bin --port /dev/ttyACM0  # Right bud
   bestool write-image open_source.bin --port /dev/ttyACM1  # Left bud
   ```

### Android App
1. Download `app-release.apk` from the release assets
2. Install the new APK
3. Grant Bluetooth and Location permissions when prompted

## ⚠️ Important Notes

### Breaking Changes
- **NV Storage Format Changed**: First boot will reset to factory defaults
- **Must flash both earbuds**: Left and right must both run v1.2.1 for TWS sync to work

### Upgrade Steps
1. Flash new firmware to **both** earbuds
2. Put earbuds in case to pair (TWS pairing)
3. Take earbuds out and open Android app
4. Touch controls and EQ will be reset to defaults - reconfigure as needed

## 🧪 Testing

This release has been tested with:
- **Firmware**: OpenPineBuds hardware (BES2300 SoC)
- **Android**: Android 5.0+ (API 21+)
- **Features Verified**:
  - ✅ All 10 EQ presets working
  - ✅ EQ persists after reboot
  - ✅ TWS sync working (both earbuds get EQ updates)
  - ✅ Connection to left bud working
  - ✅ Connection to right bud working
  - ✅ Touch controls working
  - ✅ ANC compatibility maintained

## 📊 What's Changed Since v1.1.0

**Commits:** 10 commits
**Files Changed:** 25+ files
**Lines Added:** ~2000+ lines
**Lines Removed:** ~100+ lines

**Key Components:**
- New: `services/app_opb_eq/` - EQ manager (700+ lines)
- New: `services/ble_profiles/opb_eq/` - BLE GATT profile for EQ
- New: `android/.../EQ*.kt` - Android EQ UI and logic
- Modified: `services/nv_section/` - NV storage for EQ persistence
- Modified: `services/app_tws/` - TWS sync for EQ
- New: `release.sh`, `release.bat`, `RELEASE.md` - Release automation

## 🙏 Credits

Developed with assistance from Claude Code (Anthropic).

## 📝 Full Changelog

See the full diff: [v1.1.0...v1.2.1](../../compare/v1.1.0...v1.2.1)

---

**Note:** This release includes changes from unreleased v1.2.0, which was created during development but not published.
