#!/bin/bash
set -e

# OpenPineBuds Release Script
# Usage: ./release.sh <major|minor|patch> [--dry-run]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

error() {
    echo -e "${RED}ERROR: $1${NC}" >&2
    exit 1
}

info() {
    echo -e "${GREEN}$1${NC}"
}

warn() {
    echo -e "${YELLOW}$1${NC}"
}

# Check arguments
if [ $# -lt 1 ]; then
    error "Usage: $0 <major|minor|patch> [--dry-run]"
fi

VERSION_TYPE=$1
DRY_RUN=false

if [ "$2" == "--dry-run" ]; then
    DRY_RUN=true
    warn "DRY RUN MODE - No changes will be made"
fi

# Validate version type
if [[ ! "$VERSION_TYPE" =~ ^(major|minor|patch)$ ]]; then
    error "Version type must be 'major', 'minor', or 'patch'"
fi

# Check for uncommitted changes
if [ "$DRY_RUN" == "false" ]; then
    if ! git diff-index --quiet HEAD --; then
        error "You have uncommitted changes. Please commit or stash them first."
    fi
fi

# Get current versions from firmware
FIRMWARE_VERSION_FILE="services/ble_profiles/opb_config/opb_config_common.h"
CURRENT_MAJOR=$(grep "#define OPB_CONFIG_VERSION_MAJOR" "$FIRMWARE_VERSION_FILE" | awk '{print $3}')
CURRENT_MINOR=$(grep "#define OPB_CONFIG_VERSION_MINOR" "$FIRMWARE_VERSION_FILE" | awk '{print $3}')
CURRENT_PATCH=$(grep "#define OPB_CONFIG_VERSION_PATCH" "$FIRMWARE_VERSION_FILE" | awk '{print $3}')

info "Current firmware version: $CURRENT_MAJOR.$CURRENT_MINOR.$CURRENT_PATCH"

# Calculate new version
NEW_MAJOR=$CURRENT_MAJOR
NEW_MINOR=$CURRENT_MINOR
NEW_PATCH=$CURRENT_PATCH

case "$VERSION_TYPE" in
    major)
        NEW_MAJOR=$((CURRENT_MAJOR + 1))
        NEW_MINOR=0
        NEW_PATCH=0
        ;;
    minor)
        NEW_MINOR=$((CURRENT_MINOR + 1))
        NEW_PATCH=0
        ;;
    patch)
        NEW_PATCH=$((CURRENT_PATCH + 1))
        ;;
esac

NEW_VERSION="$NEW_MAJOR.$NEW_MINOR.$NEW_PATCH"
info "New version will be: $NEW_VERSION"

if [ "$DRY_RUN" == "true" ]; then
    info "Dry run complete. No changes made."
    exit 0
fi

# Confirm with user
read -p "Proceed with release v$NEW_VERSION? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    warn "Release cancelled."
    exit 0
fi

info "Updating version numbers..."

# Update firmware version
sed -i "s/#define OPB_CONFIG_VERSION_MAJOR $CURRENT_MAJOR/#define OPB_CONFIG_VERSION_MAJOR $NEW_MAJOR/" "$FIRMWARE_VERSION_FILE"
sed -i "s/#define OPB_CONFIG_VERSION_MINOR $CURRENT_MINOR/#define OPB_CONFIG_VERSION_MINOR $NEW_MINOR/" "$FIRMWARE_VERSION_FILE"
sed -i "s/#define OPB_CONFIG_VERSION_PATCH $CURRENT_PATCH/#define OPB_CONFIG_VERSION_PATCH $NEW_PATCH/" "$FIRMWARE_VERSION_FILE"

info "✓ Updated firmware version in $FIRMWARE_VERSION_FILE"

# Get current Android version
ANDROID_BUILD_FILE="android/app/build.gradle"
CURRENT_ANDROID_VERSION=$(grep "versionName" "$ANDROID_BUILD_FILE" | sed 's/.*"\(.*\)".*/\1/')
CURRENT_VERSION_CODE=$(grep "versionCode" "$ANDROID_BUILD_FILE" | awk '{print $2}')
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

info "Current Android version: $CURRENT_ANDROID_VERSION (code: $CURRENT_VERSION_CODE)"
info "New Android version: $NEW_VERSION (code: $NEW_VERSION_CODE)"

# Update Android version
sed -i "s/versionCode $CURRENT_VERSION_CODE/versionCode $NEW_VERSION_CODE/" "$ANDROID_BUILD_FILE"
sed -i "s/versionName \"$CURRENT_ANDROID_VERSION\"/versionName \"$NEW_VERSION\"/" "$ANDROID_BUILD_FILE"

info "✓ Updated Android version in $ANDROID_BUILD_FILE"

# Commit version changes
info "Committing version changes..."
git add "$FIRMWARE_VERSION_FILE" "$ANDROID_BUILD_FILE"
git commit -m "Release v$NEW_VERSION

Bump firmware version to $NEW_VERSION
Bump Android app version to $NEW_VERSION (code: $NEW_VERSION_CODE)
"

info "✓ Version changes committed"

# Create git tag
TAG_NAME="v$NEW_VERSION"
info "Creating git tag: $TAG_NAME"
git tag -a "$TAG_NAME" -m "Release v$NEW_VERSION"

info "✓ Tag $TAG_NAME created"

# Show summary
echo ""
info "=========================================="
info "Release v$NEW_VERSION completed!"
info "=========================================="
echo ""
info "Summary:"
echo "  - Firmware version: $CURRENT_MAJOR.$CURRENT_MINOR.$CURRENT_PATCH → $NEW_VERSION"
echo "  - Android version: $CURRENT_ANDROID_VERSION → $NEW_VERSION"
echo "  - Git tag: $TAG_NAME created"
echo ""
warn "Next steps:"
echo "  1. Build and test the release:"
echo "     cd OpenPineBuds && docker compose run --rm builder ./build.sh"
echo "     cd android && ./gradlew assembleRelease"
echo ""
echo "  2. Push to remote:"
echo "     git push origin main"
echo "     git push origin $TAG_NAME"
echo ""
echo "  3. Create GitHub release with binaries"
