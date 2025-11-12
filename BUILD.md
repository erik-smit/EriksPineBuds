# Building PineBuds Firmware

This guide covers building and flashing the OpenPineBuds firmware.

## Prerequisites

You will need a system with Docker installed. Docker is used to:
1. Make the build process reproducible and easier to debug
2. Avoid modifying your host system

**Note:** Privileged mode is required to program the buds from inside the Docker container, so be more careful than usual.

## Building with Docker

```bash
./start_dev.sh # Start the Docker dev environment (takes 1-3 minutes depending on network speed)

# Now you will be inside of the container, and your prompt will look like "root@ec5410d0a265:/usr/src#"

./build.sh # Build the firmware. If you have errors, try running clean.sh or rm -rf the out folder first
```

## Flashing to Hardware

After the firmware builds successfully, you can flash it to your earbuds.

### Backup (Recommended)

```bash
./backup.sh # Back up the current firmware before flashing
```

### Programming the Buds

You may need to take the buds out of the case, wait three seconds, then place them back. This wakes them up and the programmer needs to catch this reboot.

**Using the helper script:**
```bash
./download.sh
```

**Or manually:**
```bash
# Assuming your serial ports are 0 and 1, run the following commands to program each bud:
bestool write-image out/open_source/open_source.bin --port /dev/ttyACM0
bestool write-image out/open_source/open_source.bin --port /dev/ttyACM1
```

## Build Customization

### Changing Audio Alerts

The default audio alerts are stored in: `config/_default_cfg_src_/res/en/`

To change an alert to a custom sound, replace the sound file you'd like to change (e.g., `SOUND_POWER_ON.opus`) with your own audio file with the same base name (e.g., `SOUND_POWER_ON.mp3`) and recompile with `./build.sh`.

### Language Support

The `AUDIO` environment variable can be set when running `build.sh` to load sound files for languages other than the default English.

Example:
```bash
AUDIO=cn ./build.sh # Load Chinese sound files from config/_default_cfg_src_/res/cn/
```

Supported languages:
- English (`en`) - default
- Chinese (`cn`)

To add other languages (or custom sound sets):
1. Create a `config/_default_cfg_src_/res/<custom_sounds>/` directory
2. Add all required sound files
3. Build with `AUDIO=<custom_sounds> ./build.sh`

### Blue Light When Connected

Configure whether the buds have a blinking blue light when connected:

```bash
CONNECTED_BLUE_LIGHT=1 ./build.sh # Enable blinking when connected
CONNECTED_BLUE_LIGHT=0 ./build.sh # Keep LEDs off when connected (default)
```

## Troubleshooting

- If you encounter build errors, try running `clean.sh` or `rm -rf out/` before rebuilding
- If the programmer can't find the buds, try removing them from the case, waiting 3 seconds, and placing them back
- The programmer needs to catch the buds during their reboot cycle

## License

**NOTE:** Currently, the SDK is not licensed under an 'open source' license. We are working to resolve this issue, and will be reaching out to contributors and other parties soon. For now, consider this SDK as 'All Rights Reserved'/'shared source'.
