# Release Process

## Quick Release

The release scripts automate version bumping and tagging:

### Linux/Mac:
```bash
./release.sh <major|minor|patch>
```

### Windows:
```bash
release.bat <major|minor|patch>
```

### Examples:
- `./release.sh patch` - Bump from 1.0.0 → 1.0.1
- `./release.sh minor` - Bump from 1.0.1 → 1.1.0
- `./release.sh major` - Bump from 1.1.0 → 2.0.0

### Dry Run:
Preview changes without making them:
```bash
./release.sh patch --dry-run
```

## What the Script Does

1. **Validates environment**
   - Checks for uncommitted changes
   - Verifies version type is valid

2. **Updates version numbers**
   - Firmware: `services/ble_profiles/opb_config/opb_config_common.h`
     - `OPB_CONFIG_VERSION_MAJOR`
     - `OPB_CONFIG_VERSION_MINOR`
     - `OPB_CONFIG_VERSION_PATCH`
   - Android: `android/app/build.gradle`
     - `versionCode` (auto-incremented)
     - `versionName`

3. **Creates git commit**
   - Commits version changes with standardized message

4. **Creates git tag**
   - Tags commit as `v<VERSION>` (e.g., `v1.2.3`)

## After Running the Script

### 1. Build and Test

Build firmware:
```bash
cd OpenPineBuds
docker compose run --rm builder ./build.sh
```

Build Android app (debug):
```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Build Android app (release):
```bash
cd android
./gradlew assembleRelease
# Signed APK will be in app/build/outputs/apk/release/
```

**Test thoroughly:**
- Flash firmware to both earbuds
- Test all touch controls
- Test EQ presets
- Test connection to both left and right buds
- Verify version number shown in app

### 2. Push to Remote

```bash
# Push commits
git push origin main

# Push tag
git push origin v1.2.3  # Replace with your version
```

### 3. Create GitHub Release

1. Go to GitHub → Releases → "Draft a new release"
2. Select the tag you just pushed
3. Title: `v1.2.3 - <Brief Description>`
4. Write release notes (see template below)
5. Attach binaries:
   - `OpenPineBuds/out/open_source/open_source.bin` (firmware)
   - `android/app/build/outputs/apk/release/app-release.apk` (Android app)
6. Publish release

## Release Notes Template

```markdown
# Release v1.2.3

## What's New

### Features
- Feature 1 description
- Feature 2 description

### Improvements
- Improvement 1
- Improvement 2

### Bug Fixes
- Fixed issue with...
- Fixed crash when...

## Installation

### Firmware (Both Earbuds)
1. Download `open_source.bin`
2. Flash to both earbuds using bestool:
   ```bash
   bestool write-image open_source.bin --port /dev/ttyACM0  # Right bud
   bestool write-image open_source.bin --port /dev/ttyACM1  # Left bud
   ```

### Android App
1. Download `app-release.apk`
2. Install on your Android device
3. If upgrading, uninstall old version first to avoid conflicts

## Compatibility

- Firmware: OpenPineBuds hardware (BES2300 SoC)
- Android: Android 5.0+ (API 21+)
- Tested on: [List devices you tested on]

## Known Issues

- Issue 1 description
- Issue 2 description

## Full Changelog

[Link to commit comparison on GitHub]
```

## Version Numbering

We use semantic versioning (MAJOR.MINOR.PATCH):

- **MAJOR**: Breaking changes (incompatible firmware/app versions)
- **MINOR**: New features (backwards compatible)
- **PATCH**: Bug fixes and minor improvements

### Examples:

- `1.0.0 → 1.0.1` - Fixed EQ crash (patch)
- `1.0.1 → 1.1.0` - Added new EQ presets (minor)
- `1.1.0 → 2.0.0` - Changed BLE protocol format (major)

## Troubleshooting

### Script fails with "uncommitted changes"
```bash
git status                  # Check what's uncommitted
git add -A && git commit   # Commit changes first
# OR
git stash                  # Stash changes temporarily
```

### Need to rollback a release
```bash
# Delete local tag
git tag -d v1.2.3

# Delete remote tag
git push --delete origin v1.2.3

# Reset to previous commit
git reset --hard HEAD~1
```

### Forgot to test before pushing
```bash
# If you haven't pushed yet
git reset --soft HEAD~1     # Undo commit, keep changes
# Make fixes
git add -A && git commit    # Re-commit

# If you already pushed
# Don't force push! Make a new patch release instead
./release.sh patch
# Fix the issue, test, then push
```
