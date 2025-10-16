# Debug Flags for OpenPineBuds Development

## Overview
Two debug flags enable firmware development and debugging while earbuds are in the charging case with UART connected.

---

## DEBUG_FORCE_BOX_OPEN

### Problem
- When earbuds are in the charging case, UART is accessible but BLE is **disabled**
- When taken out of case, BLE works but UART connection is **lost**
- **Result**: Can't see full BLE initialization logs or test BLE while monitoring UART!

### Solution
Keeps BLE active even when physically in the charging case by ignoring box open/close events.

### How to Enable

Edit `config/open_source/target.mk` line 340:
```makefile
export DEBUG_FORCE_BOX_OPEN ?= 1  # Change 0 to 1
```

Then rebuild:
```bash
cd OpenPineBuds
docker compose run --rm builder ./build.sh
```

### What It Does

**File**: `services/ble_app/app_main/app_ble_core.c:161-165`
```c
#ifdef DEBUG_FORCE_BOX_OPEN
  // Debug mode: Ignore box events to keep BLE active while in case
  LOG_I("%s IGNORING evt_type %d - DEBUG_FORCE_BOX_OPEN enabled", __func__, ibrt_evt_type);
  return;
#endif
```

### Effect
- ✅ BLE stays active even when physically in the charging case
- ✅ Can see full boot sequence and BLE initialization in UART logs
- ✅ nRF Connect detects earbuds while they're in the case
- ✅ Can debug BLE issues during "box open" sequence

---

## DEBUG_DISABLE_CHARGER_RESET

### Problem
- When earbuds detect charging (in case), firmware automatically **resets**
- This happens on both charger **plugin** and **plugout**
- **Result**: Device won't stay booted long enough to see logs or test features!

### Solution
Disables the automatic reset behavior when charging is detected, allowing the device to stay running.

### How to Enable

Edit `config/open_source/target.mk` line 346:
```makefile
export DEBUG_DISABLE_CHARGER_RESET ?= 1  # Change 0 to 1 if needed (default is 1)
```

Then rebuild:
```bash
cd OpenPineBuds
docker compose run --rm builder ./build.sh
```

### What It Does

**File**: `apps/battery/app_battery.cpp:334-342, 373-382`
```c
#ifdef DEBUG_DISABLE_CHARGER_RESET
  // Debug mode: Stay running while charging for debugging
  TRACE(0, "CHARGER PLUGIN - DEBUG MODE, NOT RESETTING");
  app_battery_measure.status = APP_BATTERY_STATUS_CHARGING;
#elif CHARGER_PLUGINOUT_RESET
  app_reset();
#else
  app_battery_measure.status = APP_BATTERY_STATUS_CHARGING;
#endif
```

### Effect
- ✅ Device boots and stays running even while charging
- ✅ Can monitor full UART logs from boot through charging state
- ✅ Device doesn't reset when placed in/removed from charging case
- ✅ Enables long-running debug sessions

---

## Using Both Flags Together

### Best Practice for Development
Enable **both** flags for maximum debugging capability:

```makefile
export DEBUG_FORCE_BOX_OPEN ?= 1
export DEBUG_DISABLE_CHARGER_RESET ?= 1
```

This combination allows:
1. Device boots and stays running (no charger reset)
2. BLE remains active for testing (no box close disable)
3. UART stays connected (case-mounted connection)
4. Full visibility into all firmware behavior

### Typical Development Workflow
```
1. Place earbud in charging case (UART connected)
2. Flash firmware with both debug flags enabled
3. Device boots and stays running (DEBUG_DISABLE_CHARGER_RESET)
4. BLE advertises even in case (DEBUG_FORCE_BOX_OPEN)
5. Monitor UART logs while testing with nRF Connect
6. Debug without removing earbud from case
```

---

## Reverting to Production

### Disable for Release Builds
Change both flags back to `0` in `config/open_source/target.mk`:

```makefile
export DEBUG_FORCE_BOX_OPEN ?= 0
export DEBUG_DISABLE_CHARGER_RESET ?= 0
```

### Why Disable for Production
- **Power consumption**: BLE active in case drains battery faster
- **User experience**: Normal behavior expects BLE off when in case
- **Charging logic**: Production firmware should reset on charger events

---

## Troubleshooting

### Still seeing resets?
- Verify `DEBUG_DISABLE_CHARGER_RESET ?= 1` in target.mk
- Check UART for "DEBUG MODE, NOT RESETTING" messages
- Ensure you flashed the **new** firmware (check build timestamp)

### BLE not appearing in nRF Connect?
- Verify `DEBUG_FORCE_BOX_OPEN ?= 1` in target.mk
- Check UART for "IGNORING evt_type" messages
- Make sure earbud is **in** the charging case during test

### Can't see UART logs?
- Ensure UART is connected through charging case contacts
- Verify DEBUG_PORT is set correctly (usually `DEBUG_PORT ?= 1`)
- Check baud rate matches (default: 2000000)

---

## See Also
- `config/open_source/target.mk` - Build configuration
- `services/ble_app/app_main/app_ble_core.c` - Box event handling
- `apps/battery/app_battery.cpp` - Charger detection and reset logic
