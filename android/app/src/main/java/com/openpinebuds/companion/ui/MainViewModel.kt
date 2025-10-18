package com.openpinebuds.companion.ui

import android.app.Application
import android.bluetooth.le.ScanResult
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openpinebuds.companion.ble.BleEvent
import com.openpinebuds.companion.ble.BleManager
import com.openpinebuds.companion.ble.ConnectionState
import com.openpinebuds.companion.ble.GattUuids
import com.openpinebuds.companion.data.ButtonAction
import com.openpinebuds.companion.data.EarbudConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the main activity
 * Manages BLE operations and configuration state
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val bleManager = BleManager(application)

    // Scan job to allow cancellation
    private var scanJob: kotlinx.coroutines.Job? = null

    // UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Scanning)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Discovered devices
    private val _devices = MutableStateFlow<List<ScanResult>>(emptyList())
    val devices: StateFlow<List<ScanResult>> = _devices.asStateFlow()

    // Connection state
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState

    // Configuration
    private val _leftConfig = MutableStateFlow(EarbudConfig.defaultLeft())
    val leftConfig: StateFlow<EarbudConfig> = _leftConfig.asStateFlow()

    private val _rightConfig = MutableStateFlow(EarbudConfig.defaultRight())
    val rightConfig: StateFlow<EarbudConfig> = _rightConfig.asStateFlow()

    // Firmware version
    private val _firmwareVersion = MutableStateFlow<String?>(null)
    val firmwareVersion: StateFlow<String?> = _firmwareVersion.asStateFlow()

    /**
     * Start scanning for devices
     */
    fun startScanning() {
        Log.d(TAG, "startScanning() called")

        if (!bleManager.isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled!")
            _uiState.value = UiState.Error("Bluetooth is not enabled")
            return
        }

        Log.d(TAG, "Starting device scan...")
        _uiState.value = UiState.Scanning
        _devices.value = emptyList()

        // Store the scan job so we can cancel it later
        scanJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Launching scan coroutine...")
                bleManager.scanForDevices().collect { scanResult ->
                    Log.d(TAG, "Device found: ${scanResult.device.address}, RSSI: ${scanResult.rssi}")

                    // Add device if not already in list
                    val currentDevices = _devices.value.toMutableList()
                    if (currentDevices.none { it.device.address == scanResult.device.address }) {
                        Log.d(TAG, "Adding new device to list")
                        currentDevices.add(scanResult)
                        _devices.value = currentDevices
                    } else {
                        Log.d(TAG, "Device already in list, updating...")
                        // Update existing device with new RSSI
                        val index = currentDevices.indexOfFirst { it.device.address == scanResult.device.address }
                        if (index >= 0) {
                            currentDevices[index] = scanResult
                            _devices.value = currentDevices
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                _uiState.value = UiState.Error("Scan failed: ${e.message}")
            }
        }
    }

    /**
     * Connect to a device
     */
    fun connectToDevice(scanResult: ScanResult) {
        Log.d(TAG, "Connecting to: ${scanResult.device.address}")

        // Stop scanning immediately when connecting
        stopScanning()

        _uiState.value = UiState.Connecting

        viewModelScope.launch {
            try {
                bleManager.connect(scanResult.device).collect { event ->
                    handleBleEvent(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _uiState.value = UiState.Error("Connection failed: ${e.message}")
            }
        }
    }

    /**
     * Stop scanning
     */
    fun stopScanning() {
        Log.d(TAG, "Stopping scan...")
        scanJob?.cancel()
        scanJob = null

        // Keep the devices list but change state to show scan is stopped
        if (_uiState.value is UiState.Scanning) {
            // Keep devices visible but show scan is stopped
            _uiState.value = UiState.ScanStopped
        }
    }

    /**
     * Handle BLE events
     */
    private fun handleBleEvent(event: BleEvent) {
        when (event) {
            is BleEvent.Connected -> {
                Log.d(TAG, "BLE connected")
            }
            is BleEvent.ServicesDiscovered -> {
                Log.d(TAG, "Services discovered, loading configuration...")
                Log.d(TAG, "Setting UI state to Connected")
                _uiState.value = UiState.Connected
                Log.d(TAG, "UI state is now: ${_uiState.value}")
                loadConfiguration()
            }
            is BleEvent.CharacteristicRead -> {
                handleCharacteristicRead(event)
            }
            is BleEvent.CharacteristicWritten -> {
                Log.d(TAG, "Configuration written: ${event.uuid}")
                onWriteComplete()
            }
            is BleEvent.Disconnected -> {
                Log.d(TAG, "BLE disconnected")
                _uiState.value = UiState.Disconnected
            }
            is BleEvent.Error -> {
                Log.e(TAG, "BLE error: ${event.message}")
                // If we're waiting for writes, mark it as a write error
                if (pendingWrites > 0) {
                    onWriteError()
                } else {
                    _uiState.value = UiState.Error(event.message)
                }
            }
        }
    }

    /**
     * Handle characteristic read results
     */
    private fun handleCharacteristicRead(event: BleEvent.CharacteristicRead) {
        when (event.uuid) {
            GattUuids.LEFT_EARBUD_CONFIG -> {
                try {
                    _leftConfig.value = EarbudConfig.fromBytes(event.data)
                    Log.d(TAG, "Left config loaded: ${_leftConfig.value}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse left config", e)
                }
            }
            GattUuids.RIGHT_EARBUD_CONFIG -> {
                try {
                    _rightConfig.value = EarbudConfig.fromBytes(event.data)
                    Log.d(TAG, "Right config loaded: ${_rightConfig.value}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse right config", e)
                }
            }
            GattUuids.CONFIG_VERSION -> {
                if (event.data.size >= 3) {
                    val major = event.data[0].toInt() and 0xFF
                    val minor = event.data[1].toInt() and 0xFF
                    val patch = event.data[2].toInt() and 0xFF
                    _firmwareVersion.value = "$major.$minor.$patch"
                    Log.d(TAG, "Firmware version: ${_firmwareVersion.value}")
                }
            }
        }
    }

    /**
     * Load configuration from device
     */
    fun loadConfiguration() {
        viewModelScope.launch {
            Log.d(TAG, "Loading configuration...")
            // BLE reads must be sequential - wait between each read
            bleManager.readLeftConfig()
            kotlinx.coroutines.delay(100)
            bleManager.readRightConfig()
            kotlinx.coroutines.delay(100)
            bleManager.readVersion()
        }
    }

    /**
     * Update left earbud action
     */
    fun updateLeftAction(gesture: com.openpinebuds.companion.data.GestureType, action: ButtonAction) {
        val newConfig = _leftConfig.value.copy()
        newConfig.setAction(gesture, action)
        _leftConfig.value = newConfig
    }

    /**
     * Update right earbud action
     */
    fun updateRightAction(gesture: com.openpinebuds.companion.data.GestureType, action: ButtonAction) {
        val newConfig = _rightConfig.value.copy()
        newConfig.setAction(gesture, action)
        _rightConfig.value = newConfig
    }

    // Track pending writes
    private var pendingWrites = 0
    private var writeErrors = 0

    /**
     * Save configuration to device
     */
    fun saveConfiguration() {
        viewModelScope.launch {
            Log.d(TAG, "Saving configuration...")
            pendingWrites = 2
            writeErrors = 0

            // Write left config
            val leftInitiated = bleManager.writeLeftConfig(_leftConfig.value.toBytes())
            if (!leftInitiated) {
                Log.e(TAG, "Failed to initiate left config write")
                pendingWrites--
                writeErrors++
            }

            // Wait a bit for first write to start, then write right config
            kotlinx.coroutines.delay(100)
            val rightInitiated = bleManager.writeRightConfig(_rightConfig.value.toBytes())
            if (!rightInitiated) {
                Log.e(TAG, "Failed to initiate right config write")
                pendingWrites--
                writeErrors++
            }

            // If neither write was initiated, show error immediately
            if (writeErrors == 2) {
                _uiState.value = UiState.Error("Failed to save configuration")
            }
            // Otherwise, we'll wait for the write callbacks (handled in handleBleEvent)
        }
    }

    /**
     * Called when a write completes successfully
     */
    private fun onWriteComplete() {
        pendingWrites--
        Log.d(TAG, "Write completed, pending: $pendingWrites, errors: $writeErrors")

        if (pendingWrites <= 0) {
            if (writeErrors > 0) {
                _uiState.value = UiState.Error("Failed to save some configuration")
            } else {
                Log.d(TAG, "All configuration saved successfully")
                _uiState.value = UiState.ConfigurationSaved
            }
        }
    }

    /**
     * Called when a write fails
     */
    private fun onWriteError() {
        pendingWrites--
        writeErrors++
        Log.d(TAG, "Write failed, pending: $pendingWrites, errors: $writeErrors")

        if (pendingWrites <= 0) {
            _uiState.value = UiState.Error("Failed to save configuration")
        }
    }

    /**
     * Disconnect from device
     */
    fun disconnect() {
        bleManager.disconnect()
        _uiState.value = UiState.Scanning
    }

    /**
     * Reset to scanning state
     */
    fun resetToScanning() {
        _uiState.value = UiState.Scanning
        _devices.value = emptyList()
    }
}

/**
 * UI state
 */
sealed class UiState {
    object Scanning : UiState()
    object ScanStopped : UiState()
    object Connecting : UiState()
    object Connected : UiState()
    object ConfigurationSaved : UiState()
    object Disconnected : UiState()
    data class Error(val message: String) : UiState()
}
