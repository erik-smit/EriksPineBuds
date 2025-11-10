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
import com.erikspinebuds.companion.data.EqPresets
import com.erikspinebuds.companion.data.GestureType
import com.erikspinebuds.companion.databinding.ActivityMainBinding
import com.erikspinebuds.companion.util.PermissionHelper
import com.erikspinebuds.companion.data.SavedEqManager
import com.erikspinebuds.companion.ble.BleManager
import kotlinx.coroutines.launch
import android.widget.Button

/**
 * Main activity for the OpenPineBuds Companion app
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var savedEqManager: SavedEqManager
    private lateinit var savedEqAdapter: SavedEqAdapter

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
        // Only auto-start scanning on first launch (not on rotation)
        if (savedInstanceState == null) {
            checkPermissionsAndStart()
        }
    }

    private fun setupUI() {
        // Initialize SavedEqManager
        savedEqManager = SavedEqManager(this)

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
            // Save device name first if changed
            saveDeviceNameIfChanged()
            // Then save earbud configurations
            viewModel.saveConfiguration()
        }

        binding.btnReload.setOnClickListener {
            viewModel.loadConfiguration()
        }

        // Save device name when "Done" is pressed on keyboard
        binding.etDeviceName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                saveDeviceNameIfChanged()
                // Hide keyboard
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.etDeviceName.windowToken, 0)
                // Clear focus
                binding.etDeviceName.clearFocus()
                true
            } else {
                false
            }
        }

        // Auto-save device name when focus is lost
        binding.etDeviceName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveDeviceNameIfChanged()
            }
        }

        // Make config cards clickable to edit
        setupConfigCardClickListeners()

        // Setup EQ UI
        setupEqUI()
    }

    private fun saveDeviceNameIfChanged() {
        val currentName = binding.etDeviceName.text.toString().trim()
        val storedName = viewModel.deviceName.value
        if (currentName != storedName && currentName.isNotEmpty() && currentName.length <= 32) {
            viewModel.saveDeviceName(currentName)
            Toast.makeText(this, "Saving device name...", Toast.LENGTH_SHORT).show()
        }
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

    private fun setupEqUI() {
        // EQ Enable Switch
        binding.switchEqEnable.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEqEnabled(isChecked)
            Toast.makeText(this, "EQ ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // EQ Preset Buttons
        // Short tap = apply preset, long press = customize from preset
        setupPresetButton(binding.btnEqFlat, com.erikspinebuds.companion.ble.EqPreset.FLAT)
        setupPresetButton(binding.btnEqBassBoost, com.erikspinebuds.companion.ble.EqPreset.BASS_BOOST)
        setupPresetButton(binding.btnEqTrebleBoost, com.erikspinebuds.companion.ble.EqPreset.TREBLE_BOOST)
        setupPresetButton(binding.btnEqVShape, com.erikspinebuds.companion.ble.EqPreset.V_SHAPE)
        setupPresetButton(binding.btnEqVocal, com.erikspinebuds.companion.ble.EqPreset.VOCAL)
        setupPresetButton(binding.btnEqClassical, com.erikspinebuds.companion.ble.EqPreset.CLASSICAL)
        setupPresetButton(binding.btnEqRock, com.erikspinebuds.companion.ble.EqPreset.ROCK)
        setupPresetButton(binding.btnEqJazz, com.erikspinebuds.companion.ble.EqPreset.JAZZ)
        setupPresetButton(binding.btnEqElectronic, com.erikspinebuds.companion.ble.EqPreset.ELECTRONIC)
        setupPresetButton(binding.btnEqPodcast, com.erikspinebuds.companion.ble.EqPreset.PODCAST)

        // Setup saved EQs list
        setupSavedEqsList()
    }

    private fun setupSavedEqsList() {
        savedEqAdapter = SavedEqAdapter(
            savedEqs = emptyList(),
            onItemClick = { savedEq ->
                // Apply saved EQ to device
                MaterialAlertDialogBuilder(this)
                    .setTitle("Apply ${savedEq.name}?")
                    .setMessage("This will apply this custom EQ configuration to your device.")
                    .setPositiveButton("Apply") { _, _ ->
                        applySavedEqToDevice(savedEq)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemLongClick = { savedEq ->
                // Edit saved EQ
                val intent = Intent(this, CustomEqActivity::class.java)
                intent.putExtra("SAVED_EQ_NAME", savedEq.name)
                startActivity(intent)
            },
            onDeleteClick = { savedEq ->
                // Confirm deletion
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete ${savedEq.name}?")
                    .setMessage("This will permanently delete this saved EQ configuration.")
                    .setPositiveButton("Delete") { _, _ ->
                        savedEqManager.deleteEq(savedEq.name)
                        refreshSavedEqsList()
                        Toast.makeText(this, "${savedEq.name} deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.recyclerSavedEqs.adapter = savedEqAdapter
        binding.recyclerSavedEqs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        // Load saved EQs
        refreshSavedEqsList()
    }

    private fun refreshSavedEqsList() {
        val savedEqs = savedEqManager.getSavedEqs()
        savedEqAdapter.updateData(savedEqs)

        // Show/hide the saved EQs section based on whether there are any
        if (savedEqs.isEmpty()) {
            binding.tvSavedEqsLabel.visibility = android.view.View.GONE
            binding.tvSavedEqsHint.visibility = android.view.View.GONE
            binding.recyclerSavedEqs.visibility = android.view.View.GONE
        } else {
            binding.tvSavedEqsLabel.visibility = android.view.View.VISIBLE
            binding.tvSavedEqsHint.visibility = android.view.View.VISIBLE
            binding.recyclerSavedEqs.visibility = android.view.View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh saved EQs list when returning to this activity
        // (in case user saved a new EQ from CustomEqActivity)
        refreshSavedEqsList()
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

        lifecycleScope.launch {
            viewModel.deviceName.collect { name ->
                // Update EditText only if different to avoid cursor jumping
                if (binding.etDeviceName.text.toString() != name) {
                    binding.etDeviceName.setText(name)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.eqAvailable.collect { available ->
                // Show/hide EQ card based on availability
                binding.cardEq.visibility = if (available) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.eqEnabled.collect { enabled ->
                // Update switch without triggering listener
                if (binding.switchEqEnable.isChecked != enabled) {
                    binding.switchEqEnable.setOnCheckedChangeListener(null)
                    binding.switchEqEnable.isChecked = enabled
                    binding.switchEqEnable.setOnCheckedChangeListener { _, isChecked ->
                        viewModel.setEqEnabled(isChecked)
                        Toast.makeText(this@MainActivity, "EQ ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.eqPreset.collect { preset ->
                binding.tvEqCurrent.text = "Current: ${preset.displayName}"
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
                binding.cardDeviceName.visibility = android.view.View.GONE
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
                binding.cardDeviceName.visibility = android.view.View.GONE
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
                // Hide device list and config while connecting
                binding.tvDeviceListTitle.visibility = android.view.View.GONE
                binding.recyclerDevices.visibility = android.view.View.GONE
                binding.cardDeviceName.visibility = android.view.View.GONE
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
                binding.cardDeviceName.visibility = android.view.View.VISIBLE
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
                val deviceCount = viewModel.devices.value.size
                binding.tvStatus.text = if (deviceCount == 0) {
                    "Disconnected - Tap 'Scan for Devices' to reconnect"
                } else {
                    "Disconnected - Select a device to reconnect"
                }
                Toast.makeText(this, "Disconnected from device", Toast.LENGTH_SHORT).show()
                // Enable scan button, disable disconnect button
                binding.btnScan.isEnabled = true
                binding.btnScan.text = "Scan for Devices"
                binding.btnDisconnect.isEnabled = false
                binding.btnSave.isEnabled = false
                binding.btnReload.isEnabled = false
                // Show device list again, hide config UI
                binding.tvDeviceListTitle.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.recyclerDevices.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.cardDeviceName.visibility = android.view.View.GONE
                binding.cardEq.visibility = android.view.View.GONE
                binding.tvConfigTitle.visibility = android.view.View.GONE
                binding.cardLeft.visibility = android.view.View.GONE
                binding.cardRight.visibility = android.view.View.GONE
                binding.btnReload.visibility = android.view.View.GONE
                binding.btnSave.visibility = android.view.View.GONE
            }
            is UiState.Error -> {
                val deviceCount = viewModel.devices.value.size
                binding.tvStatus.text = "Error: ${state.message}"
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()

                // Enable scan button so user can try again, disable disconnect
                binding.progressScanning.visibility = android.view.View.GONE
                binding.btnScan.isEnabled = true
                binding.btnScan.text = "Scan for Devices"
                binding.btnDisconnect.isEnabled = false
                binding.btnSave.isEnabled = false
                binding.btnReload.isEnabled = false

                // Show device list again if we have devices
                binding.tvDeviceListTitle.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.recyclerDevices.visibility = if (deviceCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                binding.cardDeviceName.visibility = android.view.View.GONE
                binding.cardEq.visibility = android.view.View.GONE
                binding.tvConfigTitle.visibility = android.view.View.GONE
                binding.cardLeft.visibility = android.view.View.GONE
                binding.cardRight.visibility = android.view.View.GONE
                binding.btnReload.visibility = android.view.View.GONE
                binding.btnSave.visibility = android.view.View.GONE
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

    private fun applySavedEqToDevice(savedEq: SavedEqManager.SavedEq) {
        lifecycleScope.launch {
            try {
                val bleManager = BleManager.getInstance(applicationContext)

                // Wait for any ongoing BLE operations
                kotlinx.coroutines.delay(500)

                // Write custom EQ configuration to device
                val configResult = bleManager.writeEqConfigSuspend(savedEq.config)
                if (configResult.isFailure) {
                    Toast.makeText(this@MainActivity, "Failed to write EQ: ${configResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Write preset as CUSTOM
                val presetResult = bleManager.writeEqPresetSuspend(com.erikspinebuds.companion.ble.EqPreset.CUSTOM)
                if (presetResult.isFailure) {
                    Toast.makeText(this@MainActivity, "Failed to set preset: ${presetResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Apply the configuration with save
                val applyResult = bleManager.applyEqConfigSuspend(com.erikspinebuds.companion.ble.EqApplyCommand.APPLY_AND_SAVE)
                if (applyResult.isFailure) {
                    Toast.makeText(this@MainActivity, "Failed to apply: ${applyResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Store as active custom EQ
                savedEqManager.setActiveCustomEq(savedEq.config)
                Toast.makeText(this@MainActivity, "${savedEq.name} applied", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Setup preset button with short-tap to apply and long-press to customize
     */
    private fun setupPresetButton(button: Button, preset: com.erikspinebuds.companion.ble.EqPreset) {
        val presetName = EqPresets.getPresetName(preset)
        val presetDescription = EqPresets.getPresetDescription(preset)

        // Short tap: Apply preset directly
        button.setOnClickListener {
            viewModel.setEqPreset(preset)
            Toast.makeText(this, "$presetName applied", Toast.LENGTH_SHORT).show()
        }

        // Long press: Customize from preset
        button.setOnLongClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Customize $presetName")
                .setMessage("$presetDescription\n\nOpen custom EQ editor with this preset as a starting point?")
                .setPositiveButton("Customize") { _, _ ->
                    val intent = Intent(this, CustomEqActivity::class.java)
                    intent.putExtra("PRESET_NAME", presetName)
                    intent.putExtra("PRESET_ORDINAL", preset.ordinal)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            true  // Consume the long click event
        }
    }
}
