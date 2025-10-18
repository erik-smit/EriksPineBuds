# OpenPineBuds Companion App

Android companion application for configuring OpenPineBuds wireless earbuds.

## Features

- Connect to OpenPineBuds via Bluetooth Low Energy
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
├── java/com/openpinebuds/companion/
│   ├── data/              # Data models and enums
│   │   ├── ButtonAction.kt
│   │   ├── GestureType.kt
│   │   ├── EarbudConfig.kt
│   │   └── BudsConfiguration.kt
│   ├── ble/               # BLE communication layer
│   │   ├── GattUuids.kt
│   │   └── BleManager.kt  (TODO)
│   ├── ui/                # UI components
│   │   └── MainActivity.kt
│   └── util/              # Utility classes
│       └── CrcUtils.kt
├── res/
│   ├── layout/            # XML layouts
│   ├── values/            # Strings, colors, themes
│   └── mipmap/            # App icons (TODO)
└── AndroidManifest.xml
```

## Implementation Status

### Completed
- ✅ Project structure and build configuration
- ✅ Data models (ButtonAction, EarbudConfig, BudsConfiguration)
- ✅ GATT UUIDs and constants
- ✅ CRC-16 checksum utility
- ✅ Basic UI layout and resources
- ✅ Manifest with proper permissions

### TODO
- ⬜ BLE Manager implementation
- ⬜ Device scanning and connection
- ⬜ GATT service communication
- ⬜ Configuration UI screens
- ⬜ ViewModel and LiveData
- ⬜ Permission handling
- ⬜ Error handling and user feedback
- ⬜ App icon and branding

## Testing

Test with firmware implementing the OpenPineBuds configuration GATT service:
- Service UUID: `0000FFC0-0000-1000-8000-00805F9B34FB`

See `../docs/GATT_SPEC.md` for complete specification.

## License

TBD - Follows OpenPineBuds firmware licensing

## Contributing

See main project README for contribution guidelines.
