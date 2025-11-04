package com.erikspinebuds.companion.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * Manager for BLE operations with OpenPineBuds
 * Handles device scanning, connection, and GATT communication
 */
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var bluetoothGatt: BluetoothGatt? = null

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Configuration characteristics
    private var leftConfigChar: BluetoothGattCharacteristic? = null
    private var rightConfigChar: BluetoothGattCharacteristic? = null
    private var versionChar: BluetoothGattCharacteristic? = null
    private var deviceNameChar: BluetoothGattCharacteristic? = null

    // EQ characteristics
    private var eqConfigChar: BluetoothGattCharacteristic? = null
    private var eqPresetChar: BluetoothGattCharacteristic? = null
    private var eqEnableChar: BluetoothGattCharacteristic? = null
    private var eqCapabilitiesChar: BluetoothGattCharacteristic? = null
    private var eqApplyChar: BluetoothGattCharacteristic? = null
    private var eqStatusChar: BluetoothGattCharacteristic? = null

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Scan for OpenPineBuds devices
     * Returns a Flow of discovered devices
     */
    @SuppressLint("MissingPermission")
    fun scanForDevices(): Flow<ScanResult> = callbackFlow {
        // Ensure scanner is initialized and BLE adapter is ready
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "Bluetooth LE scanner not available")
            close(IllegalStateException("Bluetooth LE scanner not available"))
            return@callbackFlow
        }

        // Ensure adapter is enabled
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth adapter is not enabled")
            close(IllegalStateException("Bluetooth adapter is not enabled"))
            return@callbackFlow
        }

        Log.d(TAG, "BLE scanner initialized, preparing scan...")

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.d(TAG, "onScanResult: ${result.device.address}, RSSI: ${result.rssi}")
                trySend(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                Log.d(TAG, "onBatchScanResults: ${results?.size} results")
                results?.forEach { result ->
                    trySend(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error: $errorCode")
                close(Exception("Scan failed: $errorCode"))
            }
        }

        // Scan for devices advertising the config service 0xFFC0
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(GattUuids.CONFIG_SERVICE))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setReportDelay(0)  // Report immediately
            .build()

        Log.d(TAG, "Starting BLE scan for devices with service ${GattUuids.CONFIG_SERVICE}...")

        try {
            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            Log.d(TAG, "BLE scan started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            close(e)
        }

        awaitClose {
            Log.d(TAG, "Stopping BLE scan")
            try {
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
        }
    }

    /**
     * Connect to a device
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Flow<BleEvent> = callbackFlow {
        Log.d(TAG, "Connecting to device: ${device.address}")

        // Ensure any previous connection is fully cleaned up before connecting
        if (bluetoothGatt != null) {
            Log.w(TAG, "Previous GATT connection still exists, cleaning up first")
            cleanup()
            // Give Android time to fully release resources
            kotlinx.coroutines.delay(500)
        }

        _connectionState.value = ConnectionState.CONNECTING

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d(TAG, "onConnectionStateChange: status=$status (0x${status.toString(16)}), newState=$newState")

                // Check if connection operation succeeded
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Connection failed with status: $status (0x${status.toString(16)})")
                    val errorMsg = when (status) {
                        0x85 -> "Connection timeout (0x85)"
                        0x3e -> "Connection failed to establish (0x3E)"
                        0x22 -> "Connection terminated by local host (0x22)"
                        0x08 -> "Connection timeout (0x08)"
                        0x13 -> "Connection terminated by peer (0x13)"
                        0x16 -> "Connection terminated by local host (0x16)"
                        133 -> "GATT error 133 (device unreachable or internal error)"
                        else -> "GATT error $status (0x${status.toString(16)})"
                    }
                    _connectionState.value = ConnectionState.DISCONNECTED
                    trySend(BleEvent.Error(errorMsg))
                    cleanup()
                    return
                }

                // Status OK, check connection state
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to GATT server")
                        _connectionState.value = ConnectionState.CONNECTED
                        trySend(BleEvent.Connected)

                        // Discover services
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from GATT server (normal disconnect)")
                        _connectionState.value = ConnectionState.DISCONNECTED
                        trySend(BleEvent.Disconnected)
                        cleanup()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered successfully!")

                    // List ALL services found
                    Log.d(TAG, "Found ${gatt.services.size} services:")
                    gatt.services.forEach { service ->
                        Log.d(TAG, "  Service: ${service.uuid}")
                        service.characteristics.forEach { char ->
                            Log.d(TAG, "    Characteristic: ${char.uuid}")
                        }
                    }

                    // Find config service and characteristics
                    Log.d(TAG, "Looking for config service: ${GattUuids.CONFIG_SERVICE}")
                    val configService = gatt.getService(GattUuids.CONFIG_SERVICE)
                    if (configService != null) {
                        Log.d(TAG, "Config service FOUND!")
                        leftConfigChar = configService.getCharacteristic(GattUuids.LEFT_EARBUD_CONFIG)
                        rightConfigChar = configService.getCharacteristic(GattUuids.RIGHT_EARBUD_CONFIG)
                        versionChar = configService.getCharacteristic(GattUuids.CONFIG_VERSION)
                        deviceNameChar = configService.getCharacteristic(GattUuids.DEVICE_NAME)

                        Log.d(TAG, "Characteristics - Left: ${leftConfigChar != null}, Right: ${rightConfigChar != null}, Version: ${versionChar != null}, DeviceName: ${deviceNameChar != null}")
                    } else {
                        Log.e(TAG, "Config service NOT FOUND!")
                        Log.e(TAG, "Expected UUID: ${GattUuids.CONFIG_SERVICE}")
                        trySend(BleEvent.Error("Config service 0xFFC0 not found. Is Phase 1 firmware flashed?"))
                    }

                    // Find EQ service and characteristics
                    Log.d(TAG, "Looking for EQ service: ${GattUuids.EQ_SERVICE}")
                    val eqService = gatt.getService(GattUuids.EQ_SERVICE)
                    if (eqService != null) {
                        Log.d(TAG, "EQ service FOUND!")
                        eqConfigChar = eqService.getCharacteristic(GattUuids.EQ_CONFIG)
                        eqPresetChar = eqService.getCharacteristic(GattUuids.EQ_PRESET)
                        eqEnableChar = eqService.getCharacteristic(GattUuids.EQ_ENABLE)
                        eqCapabilitiesChar = eqService.getCharacteristic(GattUuids.EQ_CAPABILITIES)
                        eqApplyChar = eqService.getCharacteristic(GattUuids.EQ_APPLY)
                        eqStatusChar = eqService.getCharacteristic(GattUuids.EQ_STATUS)

                        Log.d(TAG, "EQ Characteristics - Config: ${eqConfigChar != null}, Preset: ${eqPresetChar != null}, Enable: ${eqEnableChar != null}, Caps: ${eqCapabilitiesChar != null}, Apply: ${eqApplyChar != null}, Status: ${eqStatusChar != null}")
                    } else {
                        Log.w(TAG, "EQ service NOT FOUND (0xFFD0) - EQ features will be unavailable")
                    }

                    // Mark as ready if at least config service was found
                    if (configService != null) {
                        _connectionState.value = ConnectionState.READY
                        trySend(BleEvent.ServicesDiscovered)
                    }
                } else {
                    Log.e(TAG, "Service discovery failed: $status")
                    trySend(BleEvent.Error("Service discovery failed"))
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Characteristic read: ${characteristic.uuid}")
                    trySend(BleEvent.CharacteristicRead(characteristic.uuid, characteristic.value))
                } else {
                    Log.e(TAG, "Characteristic read failed: $status")
                    trySend(BleEvent.Error("Read failed"))
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Characteristic write success: ${characteristic.uuid}")
                    trySend(BleEvent.CharacteristicWritten(characteristic.uuid))
                } else {
                    Log.e(TAG, "Characteristic write failed: $status")
                    trySend(BleEvent.Error("Write failed"))
                }
            }
        }

        // Use TRANSPORT_LE to explicitly request BLE connection (not Classic)
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        awaitClose {
            disconnect()
        }
    }

    /**
     * Read left earbud configuration
     */
    @SuppressLint("MissingPermission")
    fun readLeftConfig(): Boolean {
        val char = leftConfigChar ?: return false
        return bluetoothGatt?.readCharacteristic(char) == true
    }

    /**
     * Read right earbud configuration
     */
    @SuppressLint("MissingPermission")
    fun readRightConfig(): Boolean {
        val char = rightConfigChar ?: return false
        return bluetoothGatt?.readCharacteristic(char) == true
    }

    /**
     * Read firmware version
     */
    @SuppressLint("MissingPermission")
    fun readVersion(): Boolean {
        val char = versionChar ?: return false
        return bluetoothGatt?.readCharacteristic(char) == true
    }

    /**
     * Read device name
     */
    @SuppressLint("MissingPermission")
    fun readDeviceName(): Boolean {
        val char = deviceNameChar ?: return false
        return bluetoothGatt?.readCharacteristic(char) == true
    }

    /**
     * Write left earbud configuration
     */
    @SuppressLint("MissingPermission")
    fun writeLeftConfig(data: ByteArray): Boolean {
        val char = leftConfigChar ?: return false
        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return bluetoothGatt?.writeCharacteristic(char) == true
    }

    /**
     * Write right earbud configuration
     */
    @SuppressLint("MissingPermission")
    fun writeRightConfig(data: ByteArray): Boolean {
        val char = rightConfigChar ?: return false
        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return bluetoothGatt?.writeCharacteristic(char) == true
    }

    /**
     * Write device name
     */
    @SuppressLint("MissingPermission")
    fun writeDeviceName(name: String): Boolean {
        val char = deviceNameChar ?: return false
        char.value = name.toByteArray(Charsets.UTF_8)
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return bluetoothGatt?.writeCharacteristic(char) == true
    }

    /**
     * Read current EQ preset
     */
    @SuppressLint("MissingPermission")
    fun readEqPreset(): Boolean {
        val char = eqPresetChar ?: return false
        return bluetoothGatt?.readCharacteristic(char) == true
    }

    /**
     * Write EQ preset
     * @param preset EQ preset enum value (0-9)
     */
    @SuppressLint("MissingPermission")
    fun writeEqPreset(preset: EqPreset): Boolean {
        val char = eqPresetChar ?: return false
        // Convert preset to 4-byte little-endian uint32
        val data = ByteArray(4)
        data[0] = (preset.value and 0xFF).toByte()
        data[1] = ((preset.value shr 8) and 0xFF).toByte()
        data[2] = ((preset.value shr 16) and 0xFF).toByte()
        data[3] = ((preset.value shr 24) and 0xFF).toByte()

        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return bluetoothGatt?.writeCharacteristic(char) == true
    }

    /**
     * Read EQ enabled state
     */
    @SuppressLint("MissingPermission")
    fun readEqEnabled(): Boolean {
        val char = eqEnableChar ?: return false
        return bluetoothGatt?.readCharacteristic(char) == true
    }

    /**
     * Write EQ enabled state
     * @param enabled true to enable EQ, false to disable
     */
    @SuppressLint("MissingPermission")
    fun writeEqEnabled(enabled: Boolean): Boolean {
        val char = eqEnableChar ?: return false
        // Write 4 bytes: [enabled, 0, 0, 0]
        val data = ByteArray(4)
        data[0] = if (enabled) 1 else 0
        data[1] = 0
        data[2] = 0
        data[3] = 0

        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return bluetoothGatt?.writeCharacteristic(char) == true
    }

    /**
     * Apply EQ configuration
     * @param command Apply command (APPLY_AND_SAVE, APPLY_TEMPORARY, RESET_TO_FLAT)
     */
    @SuppressLint("MissingPermission")
    fun applyEqConfig(command: Byte): Boolean {
        val char = eqApplyChar ?: return false
        char.value = byteArrayOf(command)
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return bluetoothGatt?.writeCharacteristic(char) == true
    }

    /**
     * Check if EQ service is available
     */
    fun isEqAvailable(): Boolean {
        return eqPresetChar != null
    }

    /**
     * Disconnect from device
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        bluetoothGatt?.disconnect()
    }

    /**
     * Clean up resources
     */
    @SuppressLint("MissingPermission")
    private fun cleanup() {
        // Clear GATT cache before closing to prevent stale cache on next connection
        bluetoothGatt?.let { gatt ->
            clearGattCache(gatt)
            gatt.close()
        }
        bluetoothGatt = null
        leftConfigChar = null
        rightConfigChar = null
        versionChar = null
        deviceNameChar = null
        eqConfigChar = null
        eqPresetChar = null
        eqEnableChar = null
        eqCapabilitiesChar = null
        eqApplyChar = null
        eqStatusChar = null
    }

    /**
     * Clear Android's GATT cache
     * This is necessary when switching between devices to prevent stale cache issues
     */
    @SuppressLint("MissingPermission")
    private fun clearGattCache(gatt: BluetoothGatt): Boolean {
        try {
            val refresh = gatt.javaClass.getMethod("refresh")
            val success = refresh.invoke(gatt) as Boolean
            Log.d(TAG, "GATT cache cleared: $success")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear GATT cache", e)
            return false
        }
    }
}

/**
 * Connection states
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    READY
}

/**
 * BLE events
 */
sealed class BleEvent {
    object Connected : BleEvent()
    object Disconnected : BleEvent()
    object ServicesDiscovered : BleEvent()
    data class CharacteristicRead(val uuid: UUID, val data: ByteArray) : BleEvent()
    data class CharacteristicWritten(val uuid: UUID) : BleEvent()
    data class Error(val message: String) : BleEvent()
}
