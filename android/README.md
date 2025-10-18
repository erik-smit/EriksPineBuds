# Erik's PineBuds Companion App

Android companion application for configuring Erik's PineBuds wireless earbuds.

## Features

- Connect to Erik's PineBuds via Bluetooth Low Energy
- Configure touch controls (single tap, double tap, triple tap, long press)
- Customize actions for left and right earbuds independently
- Persist configuration to device NV storage
- Reset to factory defaults

## Requirements

- Android 5.0 (API 21) or higher
- Bluetooth Low Energy support
- Location permission (for BLE scanning on Android 6-11)
- Bluetooth permissions (for Android 12+)

## Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on device or emulator with BLE support

```bash
./gradlew assembleDebug
```

## Project Structure

```
app/src/main/
├── java/com/erikspinebuds/companion/
│   ├── data/              # Data models and enums
│   │   ├── ButtonAction.kt
│   │   ├── GestureType.kt
│   │   ├── EarbudConfig.kt
│   │   └── BudsConfiguration.kt
│   ├── ble/               # BLE communication layer
│   │   ├── GattUuids.kt
│   │   └── BleManager.kt
│   ├── ui/                # UI components
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt
│   │   ├── ConfigEditorDialog.kt
│   │   └── DeviceAdapter.kt
│   └── util/              # Utility classes
│       ├── CrcUtils.kt
│       └── PermissionHelper.kt
├── res/
│   ├── layout/            # XML layouts
│   ├── values/            # Strings, colors, themes
│   └── mipmap/            # App icons
└── AndroidManifest.xml
```

## Implementation Status

### Completed
- ✅ Project structure and build configuration
- ✅ Data models (ButtonAction, EarbudConfig, BudsConfiguration, GestureType)
- ✅ GATT UUIDs and constants
- ✅ CRC-16 checksum utility
- ✅ BLE Manager implementation
- ✅ Device scanning and connection
- ✅ GATT service communication
- ✅ Configuration UI screens (MainActivity, ConfigEditorDialog)
- ✅ ViewModel with StateFlow
- ✅ Permission handling (PermissionHelper)
- ✅ Device adapter for BLE scan results
- ✅ Auto-save functionality
- ✅ Error handling and user feedback
- ✅ UI layout and resources
- ✅ Manifest with proper permissions
- ✅ App icon and branding

### Future Enhancements
- ⬜ Firmware OTA updates
- ⬜ Battery level indicator
- ⬜ Advanced settings (EQ, etc.)
- ⬜ Multi-language support

## Testing

Test with firmware implementing the Erik's PineBuds configuration GATT service:
- Service UUID: `0000FFC0-0000-1000-8000-00805F9B34FB`

See `../docs/GATT_SPEC.md` for complete specification.

## License

TBD - Follows Erik's PineBuds firmware licensing

## Contributing

See main project README for contribution guidelines.
