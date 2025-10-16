# Debug Mode: Force Box Open

## Problem
When earbuds are in the charging case, UART is accessible but BLE is disabled.
When taken out, BLE works but UART is lost.
**Result**: Can't see full BLE initialization logs!

## Solution
Added `DEBUG_FORCE_BOX_OPEN` flag to keep BLE active while physically in the case.

## How to Enable

Edit `config/open_source/target.mk` line 340:

```makefile
export DEBUG_FORCE_BOX_OPEN ?= 1  # Change 0 to 1
```

Then rebuild:
```bash
cd OpenPineBuds
docker compose run --rm builder ./build.sh
```

## What It Does

When enabled, this code ignores box open/close events:

**File**: `services/ble_app/app_main/app_ble_core.c:161-165`
```c
#ifdef DEBUG_FORCE_BOX_OPEN
  // Debug mode: Ignore box events to keep BLE active while in case
  LOG_I("%s IGNORING evt_type %d - DEBUG_FORCE_BOX_OPEN enabled", __func__, ibrt_evt_type);
  return;
#endif
```

## Effect
✅ BLE stays active even when physically in the charging case
✅ Can see full boot sequence and BLE initialization in UART
✅ nRF Connect detects earbuds while they're in the case
✅ Can debug crashes that happen during "box open" sequence

## To Revert
Change back to `export DEBUG_FORCE_BOX_OPEN ?= 0` and rebuild.

## Example Use Case
```
1. Place earbud in charging case
2. Connect UART to view logs
3. Flash firmware with DEBUG_FORCE_BOX_OPEN=1
4. Watch UART - see full BLE initialization
5. Open nRF Connect - see "BES_ble" device
6. Debug without physically removing from case
```
