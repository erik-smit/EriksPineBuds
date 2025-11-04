@echo off
REM OpenPineBuds Release Script for Windows
REM Usage: release.bat <major|minor|patch> [--dry-run]

setlocal enabledelayedexpansion

if "%~1"=="" (
    echo ERROR: Usage: release.bat ^<major^|minor^|patch^> [--dry-run]
    exit /b 1
)

set VERSION_TYPE=%1
set DRY_RUN=false

if "%~2"=="--dry-run" (
    set DRY_RUN=true
    echo WARNING: DRY RUN MODE - No changes will be made
    echo.
)

REM Validate version type
if not "%VERSION_TYPE%"=="major" if not "%VERSION_TYPE%"=="minor" if not "%VERSION_TYPE%"=="patch" (
    echo ERROR: Version type must be 'major', 'minor', or 'patch'
    exit /b 1
)

REM Check for uncommitted changes
if "%DRY_RUN%"=="false" (
    git diff-index --quiet HEAD -- 2>nul
    if errorlevel 1 (
        echo ERROR: You have uncommitted changes. Please commit or stash them first.
        exit /b 1
    )
)

REM Get current firmware version
set FIRMWARE_VERSION_FILE=services\ble_profiles\opb_config\opb_config_common.h

for /f "tokens=3" %%a in ('findstr /c:"#define OPB_CONFIG_VERSION_MAJOR" %FIRMWARE_VERSION_FILE%') do set CURRENT_MAJOR=%%a
for /f "tokens=3" %%a in ('findstr /c:"#define OPB_CONFIG_VERSION_MINOR" %FIRMWARE_VERSION_FILE%') do set CURRENT_MINOR=%%a
for /f "tokens=3" %%a in ('findstr /c:"#define OPB_CONFIG_VERSION_PATCH" %FIRMWARE_VERSION_FILE%') do set CURRENT_PATCH=%%a

echo Current firmware version: %CURRENT_MAJOR%.%CURRENT_MINOR%.%CURRENT_PATCH%

REM Calculate new version
set NEW_MAJOR=%CURRENT_MAJOR%
set NEW_MINOR=%CURRENT_MINOR%
set NEW_PATCH=%CURRENT_PATCH%

if "%VERSION_TYPE%"=="major" (
    set /a NEW_MAJOR=%CURRENT_MAJOR%+1
    set NEW_MINOR=0
    set NEW_PATCH=0
)
if "%VERSION_TYPE%"=="minor" (
    set /a NEW_MINOR=%CURRENT_MINOR%+1
    set NEW_PATCH=0
)
if "%VERSION_TYPE%"=="patch" (
    set /a NEW_PATCH=%CURRENT_PATCH%+1
)

set NEW_VERSION=%NEW_MAJOR%.%NEW_MINOR%.%NEW_PATCH%
echo New version will be: %NEW_VERSION%
echo.

if "%DRY_RUN%"=="true" (
    echo Dry run complete. No changes made.
    exit /b 0
)

REM Confirm with user
set /p CONFIRM="Proceed with release v%NEW_VERSION%? (y/N) "
if /i not "%CONFIRM%"=="y" (
    echo Release cancelled.
    exit /b 0
)

echo.
echo Updating version numbers...

REM Update firmware version using PowerShell
powershell -Command "(Get-Content '%FIRMWARE_VERSION_FILE%') -replace '#define OPB_CONFIG_VERSION_MAJOR %CURRENT_MAJOR%', '#define OPB_CONFIG_VERSION_MAJOR %NEW_MAJOR%' | Set-Content '%FIRMWARE_VERSION_FILE%'"
powershell -Command "(Get-Content '%FIRMWARE_VERSION_FILE%') -replace '#define OPB_CONFIG_VERSION_MINOR %CURRENT_MINOR%', '#define OPB_CONFIG_VERSION_MINOR %NEW_MINOR%' | Set-Content '%FIRMWARE_VERSION_FILE%'"
powershell -Command "(Get-Content '%FIRMWARE_VERSION_FILE%') -replace '#define OPB_CONFIG_VERSION_PATCH %CURRENT_PATCH%', '#define OPB_CONFIG_VERSION_PATCH %NEW_PATCH%' | Set-Content '%FIRMWARE_VERSION_FILE%'"

echo [OK] Updated firmware version in %FIRMWARE_VERSION_FILE%

REM Get current Android version
set ANDROID_BUILD_FILE=android\app\build.gradle
for /f "tokens=2 delims=^" %%a in ('findstr /c:"versionCode" %ANDROID_BUILD_FILE%') do (
    for /f "tokens=1" %%b in ("%%a") do set CURRENT_VERSION_CODE=%%b
)
for /f "tokens=2 delims=^" %%a in ('findstr /c:"versionName" %ANDROID_BUILD_FILE%') do (
    set TEMP_VERSION=%%a
    set CURRENT_ANDROID_VERSION=!TEMP_VERSION:"=!
)

set /a NEW_VERSION_CODE=%CURRENT_VERSION_CODE%+1

echo Current Android version: %CURRENT_ANDROID_VERSION% (code: %CURRENT_VERSION_CODE%)
echo New Android version: %NEW_VERSION% (code: %NEW_VERSION_CODE%)

REM Update Android version using PowerShell
powershell -Command "(Get-Content '%ANDROID_BUILD_FILE%') -replace 'versionCode %CURRENT_VERSION_CODE%', 'versionCode %NEW_VERSION_CODE%' | Set-Content '%ANDROID_BUILD_FILE%'"
powershell -Command "(Get-Content '%ANDROID_BUILD_FILE%') -replace 'versionName \"%CURRENT_ANDROID_VERSION%\"', 'versionName \"%NEW_VERSION%\"' | Set-Content '%ANDROID_BUILD_FILE%'"

echo [OK] Updated Android version in %ANDROID_BUILD_FILE%

REM Commit version changes
echo.
echo Committing version changes...
git add "%FIRMWARE_VERSION_FILE%" "%ANDROID_BUILD_FILE%"
git commit -m "Release v%NEW_VERSION%

Bump firmware version to %NEW_VERSION%
Bump Android app version to %NEW_VERSION% (code: %NEW_VERSION_CODE%)
"

echo [OK] Version changes committed

REM Create git tag
set TAG_NAME=v%NEW_VERSION%
echo Creating git tag: %TAG_NAME%
git tag -a "%TAG_NAME%" -m "Release v%NEW_VERSION%"

echo [OK] Tag %TAG_NAME% created

REM Show summary
echo.
echo ==========================================
echo Release v%NEW_VERSION% completed!
echo ==========================================
echo.
echo Summary:
echo   - Firmware version: %CURRENT_MAJOR%.%CURRENT_MINOR%.%CURRENT_PATCH% -^> %NEW_VERSION%
echo   - Android version: %CURRENT_ANDROID_VERSION% -^> %NEW_VERSION%
echo   - Git tag: %TAG_NAME% created
echo.
echo Next steps:
echo   1. Build and test the release:
echo      cd OpenPineBuds ^&^& docker compose run --rm builder ./build.sh
echo      cd android ^&^& gradlew assembleRelease
echo.
echo   2. Push to remote:
echo      git push origin main
echo      git push origin %TAG_NAME%
echo.
echo   3. Create GitHub release with binaries
