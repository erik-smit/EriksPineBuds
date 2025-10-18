package com.erikspinebuds.companion.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.erikspinebuds.companion.R
import com.erikspinebuds.companion.data.ButtonAction
import com.erikspinebuds.companion.data.GestureType
import com.erikspinebuds.companion.databinding.ActivityMainBinding
import com.erikspinebuds.companion.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * Main activity for the OpenPineBuds Companion app
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkBluetoothAndStart()
        } else {
            showPermissionDeniedDialog()
        }
    }

    // Bluetooth enable launcher
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startScanning()
        } else {
            Toast.makeText(this, "Bluetooth is required for this app", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
        checkPermissionsAndStart()
    }

    private fun setupUI() {
        // Initialize device list adapter
        deviceAdapter = DeviceAdapter { scanResult ->
            // User tapped on a device - connect to it
            viewModel.connectToDevice(scanResult)
        }
        binding.recyclerDevices.adapter = deviceAdapter
        binding.recyclerDevices.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        // Disable item animations to prevent distracting flicker on RSSI updates
        binding.recyclerDevices.itemAnimator = null

        binding.btnScan.setOnClickListener {
            when (viewModel.uiState.value) {
                is UiState.Scanning -> {
                    // Stop scanning
                    viewModel.stopScanning()
                }
                else -> {
                    // Start scanning
                    viewModel.resetToScanning()
                    checkPermissionsAndStart()
                }
            }
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        binding.btnSave.setOnClickListener {
            viewModel.saveConfiguration()
        }

        binding.btnReload.setOnClickListener {
            viewModel.loadConfiguration()
        }

        // Make config cards clickable to edit
        setupConfigCardClickListeners()
    }

    private fun setupConfigCardClickListeners() {
        // Left earbud card - click to show gesture menu
        binding.cardLeft.setOnClickListener {
            showGesturePickerForEarbud(isLeft = true)
        }

        // Right earbud card - click to show gesture menu
        binding.cardRight.setOnClickListener {
            showGesturePickerForEarbud(isLeft = false)
        }
    }

    private fun showGesturePickerForEarbud(isLeft: Boolean) {
        val earbudName = if (isLeft) "Left" else "Right"
        val currentConfig = if (isLeft) viewModel.leftConfig.value else viewModel.rightConfig.value

        // Show a menu to select which gesture to edit
        val gestures = GestureType.values()
        val gestureNames = gestures.map { it.displayName }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("$earbudName Earbud - Select Gesture")
            .setItems(gestureNames) { _, which ->
                val selectedGesture = gestures[which]
                val currentAction = currentConfig.getAction(selectedGesture)
                showActionPicker(isLeft, selectedGesture, currentAction)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showActionPicker(isLeft: Boolean, gesture: GestureType, currentAction: ButtonAction) {
        ConfigEditorDialog.newInstance(
            isLeftEarbud = isLeft,
            gestureType = gesture,
            currentAction = currentAction
        ) { selectedGesture, selectedAction ->
            // Update the configuration in the ViewModel
            if (isLeft) {
                viewModel.updateLeftAction(selectedGesture, selectedAction)
            } else {
                viewModel.updateRightAction(selectedGesture, selectedAction)
            }

            // Auto-save the configuration to the device
            Toast.makeText(
                this,
                "Saving ${if (isLeft) "Left" else "Right"} ${selectedGesture.displayName} → ${selectedAction.displayName}...",
                Toast.LENGTH_SHORT
            ).show()

            // Save configuration immediately after update
            viewModel.saveConfiguration()
        }.show(supportFragmentManager, "config_editor")
    }

    private fun observeViewModel() {
        // Observe UI state
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUIForState(state)
            }
        }

        // Observe discovered devices
        lifecycleScope.launch {
            viewModel.devices.collect { devices ->
                // Update RecyclerView with devices
                deviceAdapter.submitList(devices)

                // Update visibility when scanning - fixes race condition where UI state
                // updates before devices are added
                if (viewModel.uiState.value is UiState.Scanning || viewModel.uiState.value is UiState.ScanStopped) {
                    val deviceCount = devices.size
                    binding.tvStatus.text = if (deviceCount == 0) {
                        if (viewModel.uiState.value is UiState.Scanning) {
                            "Scanning for devices..."
                        } else {
                            "No devices found - Tap 'Scan for Devices' to retry"
                        }
                    } else {
                        "Found $deviceCount device(s) - Tap to connect"
                    }
                    binding.tvDeviceListTitle.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                    binding.recyclerDevices.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        }

        // Observe connection state
        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                binding.tvConnectionState.text = "Connection: $state"
            }
        }

        // Observe configurations
        lifecycleScope.launch {
            viewModel.leftConfig.collect { config ->
                binding.tvLeftConfig.text = """
                    Left Earbud:
                    • Single Tap: ${config.singleTap.displayName}
                    • Double Tap: ${config.doubleTap.displayName}
                    • Triple Tap: ${config.tripleTap.displayName}
                    • Long Press: ${config.longPress.displayName}
                """.trimIndent()
            }
        }

        lifecycleScope.launch {
            viewModel.rightConfig.collect { config ->
                binding.tvRightConfig.text = """
                    Right Earbud:
                    • Single Tap: ${config.singleTap.displayName}
                    • Double Tap: ${config.doubleTap.displayName}
                    • Triple Tap: ${config.tripleTap.displayName}
                    • Long Press: ${config.longPress.displayName}
                """.trimIndent()
            }
        }

        lifecycleScope.launch {
            viewModel.firmwareVersion.collect { version ->
                binding.tvVersion.text = version?.let { "Firmware: $it" } ?: ""
            }
        }
    }

    private fun updateUIForState(state: UiState) {
        android.util.Log.d("MainActivity", "updateUIForState: $state")
        when (state) {
            is UiState.Scanning -> {
                val deviceCount = viewModel.devices.value.size
                binding.tvStatus.text = if (deviceCount == 0) {
                    "Scanning for OpenPineBuds..."
                } else {
                    "Found $deviceCount device(s) - Tap to connect"
                }
                binding.progressScanning.visibility = android.view.View.VISIBLE
                binding.btnScan.isEnabled = true
                binding.btnScan.text = "Stop Scan"
                binding.btnDisconnect.isEnabled = false
                binding.btnSave.isEnabled = false
                binding.btnReload.isEnabled = false
                // Show device list, hide config
                binding.tvDeviceListTitle.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.recyclerDevices.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.tvConfigTitle.visibility = android.view.View.GONE
                binding.cardLeft.visibility = android.view.View.GONE
                binding.cardRight.visibility = android.view.View.GONE
                binding.btnReload.visibility = android.view.View.GONE
                binding.btnSave.visibility = android.view.View.GONE
            }
            is UiState.ScanStopped -> {
                val deviceCount = viewModel.devices.value.size
                binding.tvStatus.text = if (deviceCount == 0) {
                    "No devices found - Tap 'Scan for Devices' to retry"
                } else {
                    "Scan stopped - Found $deviceCount device(s)"
                }
                binding.progressScanning.visibility = android.view.View.GONE
                binding.btnScan.isEnabled = true
                binding.btnScan.text = "Scan for Devices"
                binding.btnDisconnect.isEnabled = false
                binding.btnSave.isEnabled = false
                binding.btnReload.isEnabled = false
                // Keep device list visible if there are devices
                binding.tvDeviceListTitle.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.recyclerDevices.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.tvConfigTitle.visibility = android.view.View.GONE
                binding.cardLeft.visibility = android.view.View.GONE
                binding.cardRight.visibility = android.view.View.GONE
                binding.btnReload.visibility = android.view.View.GONE
                binding.btnSave.visibility = android.view.View.GONE
            }
            is UiState.Connecting -> {
                binding.tvStatus.text = "Connecting..."
                binding.progressScanning.visibility = android.view.View.GONE
                binding.btnScan.isEnabled = false
                binding.btnScan.text = "Scan for Devices"
                binding.btnDisconnect.isEnabled = false
                binding.btnSave.isEnabled = false
                binding.btnReload.isEnabled = false
                // Hide device list while connecting
                binding.tvDeviceListTitle.visibility = android.view.View.GONE
                binding.recyclerDevices.visibility = android.view.View.GONE
            }
            is UiState.Connected -> {
                binding.tvStatus.text = "Connected - Configuration loaded"
                binding.progressScanning.visibility = android.view.View.GONE
                binding.btnScan.isEnabled = false
                binding.btnScan.text = "Scan for Devices"
                binding.btnDisconnect.isEnabled = true
                binding.btnSave.isEnabled = true
                binding.btnReload.isEnabled = true
                // Hide device list, show config
                binding.tvDeviceListTitle.visibility = android.view.View.GONE
                binding.recyclerDevices.visibility = android.view.View.GONE
                binding.tvConfigTitle.visibility = android.view.View.VISIBLE
                binding.cardLeft.visibility = android.view.View.VISIBLE
                binding.cardRight.visibility = android.view.View.VISIBLE
                binding.btnReload.visibility = android.view.View.VISIBLE
                binding.btnSave.visibility = android.view.View.VISIBLE
            }
            is UiState.ConfigurationSaved -> {
                binding.tvStatus.text = "Configuration saved!"
                Toast.makeText(this, "Configuration saved successfully", Toast.LENGTH_SHORT).show()
            }
            is UiState.Disconnected -> {
                binding.tvStatus.text = "Disconnected"
                Toast.makeText(this, "Disconnected from device", Toast.LENGTH_SHORT).show()
            }
            is UiState.Error -> {
                binding.tvStatus.text = "Error: ${state.message}"
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        android.util.Log.d("MainActivity", "Checking permissions...")
        if (PermissionHelper.hasAllPermissions(this)) {
            android.util.Log.d("MainActivity", "Permissions OK, checking Bluetooth...")
            checkBluetoothAndStart()
        } else {
            android.util.Log.d("MainActivity", "Permissions missing, showing dialog...")
            showPermissionRationaleDialog()
        }
    }

    private fun checkBluetoothAndStart() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        android.util.Log.d("MainActivity", "Bluetooth enabled: ${bluetoothAdapter?.isEnabled}")
        if (bluetoothAdapter?.isEnabled != true) {
            android.util.Log.d("MainActivity", "Requesting Bluetooth enable...")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            android.util.Log.d("MainActivity", "Bluetooth is enabled, starting scan...")
            startScanning()
        }
    }

    private fun startScanning() {
        android.util.Log.d("MainActivity", "Starting scan...")
        // Add small delay to ensure BLE is fully ready
        binding.root.postDelayed({
            viewModel.startScanning()
        }, 300)
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(PermissionHelper.getPermissionExplanations())
            .setPositiveButton("Grant") { _, _ ->
                PermissionHelper.requestPermissions(permissionLauncher)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Permissions are required to use this app", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Denied")
            .setMessage("The app cannot function without the required permissions. Please grant them in the app settings.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
