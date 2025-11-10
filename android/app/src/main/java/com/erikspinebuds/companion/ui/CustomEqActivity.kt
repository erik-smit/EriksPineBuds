package com.erikspinebuds.companion.ui

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import java.io.OutputStream
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.erikspinebuds.companion.data.EqBand
import com.erikspinebuds.companion.data.EqConfiguration
import com.erikspinebuds.companion.data.EqFilterType
import com.erikspinebuds.companion.data.EqPresets
import com.erikspinebuds.companion.data.SavedEqManager
import com.erikspinebuds.companion.databinding.ActivityCustomEqBinding
import com.erikspinebuds.companion.util.EqQrCodeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/**
 * Activity for creating and editing custom EQ configurations
 */
class CustomEqActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomEqBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var bandAdapter: EqBandAdapter
    private lateinit var savedEqManager: SavedEqManager
    private var currentConfig: EqConfiguration = EqConfiguration.flat()

    // Debounce timer for live updates
    private var applyJob: kotlinx.coroutines.Job? = null
    private val APPLY_DELAY_MS = 500L // Wait 500ms after last change before applying

    // QR code scanner launcher
    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleQrCodeScanned(result.contents)
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomEqBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use the singleton BleManager directly (shared across all activities)
        // Creating a new ViewModel would create a separate instance without the active connection
        viewModel = MainViewModel(application)
        savedEqManager = SavedEqManager(this)

        setupToolbar()
        setupRecyclerView()
        setupSliders()
        setupButtons()
        observeViewModel()

        // Check if we're customizing from a preset
        val presetOrdinal = intent.getIntExtra("PRESET_ORDINAL", -1)
        val presetName = intent.getStringExtra("PRESET_NAME")
        val savedEqName = intent.getStringExtra("SAVED_EQ_NAME")

        when {
            presetOrdinal >= 0 && presetName != null -> {
                // Load preset as starting point
                try {
                    val preset = com.erikspinebuds.companion.ble.EqPreset.values()[presetOrdinal]
                    currentConfig = EqPresets.getPresetConfig(preset).copy()
                    // Set default name: "Custom $preset_name"
                    binding.etEqName.setText("Custom $presetName")
                    updateUI()
                    Toast.makeText(this, "Customizing $presetName preset", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error loading preset", Toast.LENGTH_SHORT).show()
                    loadActiveCustomEq()
                }
            }
            savedEqName != null -> {
                // Load saved custom EQ
                val savedEq = savedEqManager.getEq(savedEqName)
                if (savedEq != null) {
                    currentConfig = savedEq.config.copy()
                    // Set name from saved EQ
                    binding.etEqName.setText(savedEq.name)
                    updateUI()
                    Toast.makeText(this, "Editing $savedEqName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Saved EQ not found", Toast.LENGTH_SHORT).show()
                    loadActiveCustomEq()
                }
            }
            else -> {
                // Load active custom EQ from device (if CUSTOM preset is active)
                // Set default name: "My Custom EQ"
                binding.etEqName.setText("My Custom EQ")
                loadActiveCustomEq()
            }
        }
    }

    /**
     * Load the active custom EQ configuration
     */
    private fun loadActiveCustomEq() {
        val activeEq = savedEqManager.getActiveCustomEq()
        if (activeEq != null) {
            currentConfig = activeEq.copy()
            updateUI()
            Toast.makeText(this, "Loaded active custom EQ", Toast.LENGTH_SHORT).show()
        } else {
            // No active custom EQ, start with flat
            currentConfig = EqConfiguration.flat()
            updateUI()
        }
    }

    /**
     * Load custom EQ config directly from BleManager singleton
     * (avoiding ViewModel to ensure we use the connected instance)
     */
    private fun loadCustomEqConfigDirectly() {
        lifecycleScope.launch {
            val bleManager = com.erikspinebuds.companion.ble.BleManager.getInstance(applicationContext)
            if (bleManager.readEqConfig()) {
                // Config will be loaded via BLE events, for now just log
                android.util.Log.d("CustomEqActivity", "Initiated EQ config read from BleManager")
            } else {
                android.util.Log.e("CustomEqActivity", "Failed to initiate EQ config read")
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        // Create adapter with its own mutable list (not sharing currentConfig.bands reference)
        bandAdapter = EqBandAdapter(
            bands = mutableListOf(),
            onEdit = { position, band ->
                showBandEditor(position, band)
            },
            onDelete = { position ->
                confirmDeleteBand(position)
            }
        )

        binding.recyclerEqBands.apply {
            layoutManager = LinearLayoutManager(this@CustomEqActivity)
            adapter = bandAdapter
        }

        updateBandList()
    }

    private fun setupSliders() {
        // Pre-EQ gain slider
        binding.sliderPreGain.addOnChangeListener { _, value, _ ->
            currentConfig.gain0 = value
            binding.tvPreGainValue.text = String.format("%.1f dB", value)
            scheduleApplyToDevice()
        }

        // Post-EQ gain slider
        binding.sliderPostGain.addOnChangeListener { _, value, _ ->
            currentConfig.gain1 = value
            binding.tvPostGainValue.text = String.format("%.1f dB", value)
            scheduleApplyToDevice()
        }
    }

    /**
     * Schedule applying EQ to device with debounce
     */
    private fun scheduleApplyToDevice() {
        // Cancel any pending apply
        applyJob?.cancel()

        // Schedule new apply after delay
        applyJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(APPLY_DELAY_MS)
            applyConfigToDeviceLive()
        }
    }

    /**
     * Apply a temporary config (for previewing new bands before saving)
     */
    private fun applyTempConfigLive(tempConfig: EqConfiguration) {
        // Cancel any pending apply
        applyJob?.cancel()

        // Schedule new apply after delay
        applyJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(APPLY_DELAY_MS)
            applyConfigToDeviceLive(tempConfig)
        }
    }

    /**
     * Apply current config to device (live preview, no save)
     */
    private suspend fun applyConfigToDeviceLive(config: EqConfiguration = currentConfig) {
        android.util.Log.d("CustomEqActivity", "applyConfigToDeviceLive: numBands=${config.numBands}, isValid=${config.isValid()}")

        if (!config.isValid()) {
            android.util.Log.w("CustomEqActivity", "Config not valid, skipping apply")
            return
        }

        try {
            val bleManager = com.erikspinebuds.companion.ble.BleManager.getInstance(applicationContext)

            android.util.Log.d("CustomEqActivity", "Writing EQ config...")
            // Write config to device
            val configResult = bleManager.writeEqConfigSuspend(config)
            if (configResult.isFailure) {
                android.util.Log.e("CustomEqActivity", "Failed to write EQ config: ${configResult.exceptionOrNull()?.message}")
                return
            }

            android.util.Log.d("CustomEqActivity", "Writing preset CUSTOM...")
            // Write preset as CUSTOM
            val presetResult = bleManager.writeEqPresetSuspend(com.erikspinebuds.companion.ble.EqPreset.CUSTOM)
            if (presetResult.isFailure) {
                android.util.Log.e("CustomEqActivity", "Failed to set preset: ${presetResult.exceptionOrNull()?.message}")
                return
            }

            android.util.Log.d("CustomEqActivity", "Applying TEMPORARY...")
            // Apply without save (live preview)
            val applyResult = bleManager.applyEqConfigSuspend(com.erikspinebuds.companion.ble.EqApplyCommand.APPLY_TEMPORARY)
            if (applyResult.isFailure) {
                android.util.Log.e("CustomEqActivity", "Failed to apply: ${applyResult.exceptionOrNull()?.message}")
                return
            }

            android.util.Log.d("CustomEqActivity", "EQ applied to device (live preview) - ${config.numBands} bands")
        } catch (e: Exception) {
            android.util.Log.e("CustomEqActivity", "Error applying EQ: ${e.message}", e)
        }
    }

    private fun setupButtons() {
        binding.btnAddBand.setOnClickListener {
            if (currentConfig.bands.size >= EqConfiguration.MAX_BANDS) {
                Toast.makeText(this, "Maximum ${EqConfiguration.MAX_BANDS} bands allowed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create a new band with default values
            val newBand = EqBand(
                type = EqFilterType.PEAK,
                gain = 0.0f,
                frequency = 1000.0f,
                q = 1.0f
            )

            showBandEditor(-1, newBand)
        }

        binding.btnSaveEq.setOnClickListener {
            saveEq()
        }

        binding.btnShareQr.setOnClickListener {
            showQrCodeDialog()
        }

        binding.btnScanQr.setOnClickListener {
            startQrCodeScanner()
        }
    }

    /**
     * Save QR code image to gallery and return the URI
     */
    private fun saveQrCodeImageToGallery(bitmap: Bitmap): Uri? {
        try {
            val filename = "OpenPineBuds_EQ_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (Scoped Storage)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OpenPineBuds")
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    return uri
                }
            } else {
                // Android 9 and below
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val openPinebudsDir = java.io.File(imagesDir, "OpenPineBuds")
                if (!openPinebudsDir.exists()) {
                    openPinebudsDir.mkdirs()
                }

                val imageFile = java.io.File(openPinebudsDir, filename)
                java.io.FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                // Notify gallery (use MediaScannerConnection for modern API)
                val uri = Uri.fromFile(imageFile)
                android.media.MediaScannerConnection.scanFile(
                    this,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                return uri
            }
        } catch (e: Exception) {
            android.util.Log.e("CustomEqActivity", "Failed to save QR code", e)
        }
        return null
    }

    /**
     * Share QR code image
     */
    private fun shareQrCodeImage(bitmap: Bitmap) {
        val uri = saveQrCodeImageToGallery(bitmap)
        if (uri == null) {
            Toast.makeText(this, "Failed to save QR code", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Scan this QR code with OpenPineBuds app to import my custom EQ configuration")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share EQ QR Code"))
        Toast.makeText(this, "QR code saved to gallery", Toast.LENGTH_SHORT).show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.eqCustomConfig.collect { config ->
                config?.let {
                    currentConfig = it.copy()
                    updateUI()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is UiState.ConfigurationSaved -> {
                        Toast.makeText(this@CustomEqActivity, "Custom EQ saved successfully!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is UiState.Error -> {
                        Toast.makeText(this@CustomEqActivity, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showBandEditor(position: Int, band: EqBand) {
        val editedBand = band.copy()  // Work on a copy
        val dialog = EqBandEditorDialog(
            context = this,
            band = editedBand,
            onSave = { savedBand ->
                if (position == -1) {
                    // New band
                    currentConfig.addBand(savedBand)
                } else {
                    // Edit existing band
                    currentConfig.bands[position] = savedBand
                }
                updateUI()
            },
            onLiveUpdate = { updatedBand ->
                // Apply live preview while editing
                if (position == -1) {
                    // For new band, temporarily add it to preview
                    // (will be added permanently on Close)
                    val tempConfig = currentConfig.copy()
                    tempConfig.addBand(updatedBand)
                    if (tempConfig.isValid()) {
                        applyTempConfigLive(tempConfig)
                    }
                } else {
                    // For existing band, update in place
                    currentConfig.bands[position] = updatedBand
                    scheduleApplyToDevice()
                }
            }
        )
        dialog.show()
    }

    private fun confirmDeleteBand(position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Band")
            .setMessage("Are you sure you want to delete this EQ band?")
            .setPositiveButton("Delete") { _, _ ->
                currentConfig.removeBand(position)
                updateUI()
                scheduleApplyToDevice()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset EQ")
            .setMessage("Reset to flat EQ configuration? This will remove all bands.")
            .setPositiveButton("Reset") { _, _ ->
                currentConfig = EqConfiguration.flat()
                updateUI()
                Toast.makeText(this, "EQ reset to flat", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveToDevice() {
        // Log current config for debugging
        android.util.Log.d("CustomEqActivity", "Attempting to save: numBands=${currentConfig.numBands}, bands.size=${currentConfig.bands.size}")
        currentConfig.bands.forEachIndexed { index, band ->
            android.util.Log.d("CustomEqActivity", "  Band $index: type=${band.type}, freq=${band.frequency}, gain=${band.gain}, q=${band.q}, valid=${band.isValid()}")
        }

        if (!currentConfig.isValid()) {
            Toast.makeText(this, "Invalid EQ: numBands=${currentConfig.numBands}, bands=${currentConfig.bands.size}", Toast.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Save Custom EQ")
            .setMessage("Save this custom EQ configuration to the device? This will replace any existing custom EQ.")
            .setPositiveButton("Save") { _, _ ->
                saveCustomEqDirectly(currentConfig)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Save custom EQ directly using BleManager singleton
     */
    private fun saveCustomEqDirectly(config: EqConfiguration) {
        lifecycleScope.launch {
            try {
                val bleManager = com.erikspinebuds.companion.ble.BleManager.getInstance(applicationContext)

                // Wait a moment to ensure any ongoing BLE reads from MainActivity have completed
                // BLE GATT only allows one operation at a time
                android.util.Log.d("CustomEqActivity", "Waiting for any ongoing BLE operations to complete...")
                kotlinx.coroutines.delay(500)

                // Write config to device (wait for completion)
                android.util.Log.d("CustomEqActivity", "Writing EQ config (144 bytes)...")
                val configResult = bleManager.writeEqConfigSuspend(config)
                if (configResult.isFailure) {
                    android.util.Log.e("CustomEqActivity", "Failed to write EQ config: ${configResult.exceptionOrNull()?.message}")
                    Toast.makeText(this@CustomEqActivity, "Failed to write EQ config: ${configResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                android.util.Log.d("CustomEqActivity", "EQ config written successfully")

                // Write preset=CUSTOM (wait for completion)
                android.util.Log.d("CustomEqActivity", "Writing preset=CUSTOM...")
                val presetResult = bleManager.writeEqPresetSuspend(com.erikspinebuds.companion.ble.EqPreset.CUSTOM)
                if (presetResult.isFailure) {
                    android.util.Log.e("CustomEqActivity", "Failed to write CUSTOM preset: ${presetResult.exceptionOrNull()?.message}")
                    Toast.makeText(this@CustomEqActivity, "Failed to set CUSTOM preset: ${presetResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                android.util.Log.d("CustomEqActivity", "Preset written successfully")

                // Apply the configuration (with save) and wait for completion
                android.util.Log.d("CustomEqActivity", "Applying EQ config...")
                val applyResult = bleManager.applyEqConfigSuspend(com.erikspinebuds.companion.ble.EqApplyCommand.APPLY_AND_SAVE)
                if (applyResult.isFailure) {
                    android.util.Log.e("CustomEqActivity", "Failed to apply EQ config: ${applyResult.exceptionOrNull()?.message}")
                    Toast.makeText(this@CustomEqActivity, "Failed to apply EQ config: ${applyResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                android.util.Log.d("CustomEqActivity", "Custom EQ config applied and saved successfully")

                // Save as active custom EQ in SharedPreferences
                savedEqManager.setActiveCustomEq(config)

                Toast.makeText(this@CustomEqActivity, "Custom EQ saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("CustomEqActivity", "Error saving EQ", e)
                Toast.makeText(this@CustomEqActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUI() {
        // Update band count
        binding.tvBandCount.text = "${currentConfig.numBands} / ${EqConfiguration.MAX_BANDS} bands"

        // Update pre/post gain sliders
        binding.sliderPreGain.value = currentConfig.gain0
        binding.tvPreGainValue.text = String.format("%.1f dB", currentConfig.gain0)

        binding.sliderPostGain.value = currentConfig.gain1
        binding.tvPostGainValue.text = String.format("%.1f dB", currentConfig.gain1)

        // Update band list
        updateBandList()
    }

    private fun updateBandList() {
        android.util.Log.d("CustomEqActivity", "updateBandList: bands.size=${currentConfig.bands.size}, isEmpty=${currentConfig.bands.isEmpty()}")
        currentConfig.bands.forEachIndexed { index, band ->
            android.util.Log.d("CustomEqActivity", "  Band $index: ${band.type}, ${band.frequency}Hz, ${band.gain}dB")
        }

        // Always update the adapter with the current bands list
        bandAdapter.updateBands(currentConfig.bands)

        // Show/hide empty state
        if (currentConfig.bands.isEmpty()) {
            android.util.Log.d("CustomEqActivity", "Showing empty state")
            binding.recyclerEqBands.visibility = View.GONE
            binding.tvEmptyBands.visibility = View.VISIBLE
        } else {
            android.util.Log.d("CustomEqActivity", "Showing RecyclerView with ${currentConfig.bands.size} bands")
            binding.recyclerEqBands.visibility = View.VISIBLE
            binding.tvEmptyBands.visibility = View.GONE
        }

        // Disable add button if at max bands
        binding.btnAddBand.isEnabled = currentConfig.bands.size < EqConfiguration.MAX_BANDS
    }

    /**
     * Show QR code dialog to share current EQ configuration
     */
    private fun showQrCodeDialog() {
        if (!currentConfig.isValid()) {
            Toast.makeText(this, "Please create a valid EQ configuration first", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate QR code
        val qrBitmap = EqQrCodeUtils.generateQrCode(currentConfig, 512)
        if (qrBitmap == null) {
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
            return
        }

        // Create dialog with QR code image
        val imageView = ImageView(this).apply {
            setImageBitmap(qrBitmap)
            setPadding(32, 32, 32, 32)
        }

        val description = EqQrCodeUtils.getConfigDescription(currentConfig)

        MaterialAlertDialogBuilder(this)
            .setTitle("Share EQ Configuration")
            .setMessage(description)
            .setView(imageView)
            .setPositiveButton("Share Image") { _, _ ->
                shareQrCodeImage(qrBitmap)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Start QR code scanner
     */
    private fun startQrCodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan an OpenPineBuds EQ QR code")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true)
        }
        qrScannerLauncher.launch(options)
    }

    /**
     * Handle scanned QR code
     */
    private fun handleQrCodeScanned(qrContent: String) {
        android.util.Log.d("CustomEqActivity", "Scanned QR code: $qrContent")

        // Try to decode the EQ configuration
        val decodedConfig = EqQrCodeUtils.decodeQrCode(qrContent)
        if (decodedConfig == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Invalid QR Code")
                .setMessage("This QR code does not contain a valid OpenPineBuds EQ configuration.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Show preview and confirmation dialog
        val description = EqQrCodeUtils.getConfigDescription(decodedConfig)
        MaterialAlertDialogBuilder(this)
            .setTitle("Import EQ Configuration")
            .setMessage("Found valid EQ configuration:\n\n$description\n\nDo you want to load this configuration?")
            .setPositiveButton("Load") { _, _ ->
                currentConfig = decodedConfig.copy()
                updateUI()
                Toast.makeText(this, "EQ configuration loaded!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show dialog to save custom EQ with a name
     */
    /**
     * Save EQ with name from text field, applying live to device
     */
    private fun saveEq() {
        val name = binding.etEqName.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name for this EQ", Toast.LENGTH_SHORT).show()
            return
        }

        if (!currentConfig.isValid()) {
            Toast.makeText(this, "Please create a valid EQ configuration first", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if name already exists
        if (savedEqManager.nameExists(name)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Overwrite?")
                .setMessage("An EQ named \"$name\" already exists. Overwrite it?")
                .setPositiveButton("Overwrite") { _, _ ->
                    saveAndApplyEq(name)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            saveAndApplyEq(name)
        }
    }

    /**
     * Save EQ with given name and apply to device
     */
    private fun saveAndApplyEq(name: String) {
        lifecycleScope.launch {
            try {
                // Save to SharedPreferences
                if (savedEqManager.saveEq(name, currentConfig)) {
                    Toast.makeText(this@CustomEqActivity, "Saving \"$name\"...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CustomEqActivity, "Failed to save EQ", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Apply to device
                val bleManager = com.erikspinebuds.companion.ble.BleManager.getInstance(applicationContext)

                // Wait for any ongoing BLE operations
                kotlinx.coroutines.delay(500)

                // Write config to device
                val configResult = bleManager.writeEqConfigSuspend(currentConfig)
                if (configResult.isFailure) {
                    Toast.makeText(this@CustomEqActivity, "Failed to write EQ: ${configResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Write preset as CUSTOM
                val presetResult = bleManager.writeEqPresetSuspend(com.erikspinebuds.companion.ble.EqPreset.CUSTOM)
                if (presetResult.isFailure) {
                    Toast.makeText(this@CustomEqActivity, "Failed to set preset: ${presetResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Apply and save
                val applyResult = bleManager.applyEqConfigSuspend(com.erikspinebuds.companion.ble.EqApplyCommand.APPLY_AND_SAVE)
                if (applyResult.isFailure) {
                    Toast.makeText(this@CustomEqActivity, "Failed to apply: ${applyResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Store as active custom EQ
                savedEqManager.setActiveCustomEq(currentConfig)

                Toast.makeText(this@CustomEqActivity, "\"$name\" saved and applied!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@CustomEqActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSaveAsDialog() {
        if (!currentConfig.isValid()) {
            Toast.makeText(this, "Please create a valid EQ configuration first", Toast.LENGTH_SHORT).show()
            return
        }

        // Create input dialog
        val input = TextInputEditText(this).apply {
            hint = "Enter a name for this EQ"
            setText("")
        }

        val container = android.widget.FrameLayout(this).apply {
            val params = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(64, 32, 64, 0)
            addView(input, params)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Save Custom EQ")
            .setMessage("Give your custom EQ a memorable name:")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString()?.trim() ?: ""
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Check if name already exists
                if (savedEqManager.nameExists(name)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Overwrite?")
                        .setMessage("An EQ named \"$name\" already exists. Overwrite it?")
                        .setPositiveButton("Overwrite") { _, _ ->
                            if (savedEqManager.saveEq(name, currentConfig)) {
                                Toast.makeText(this, "Saved as \"$name\"", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    if (savedEqManager.saveEq(name, currentConfig)) {
                        Toast.makeText(this, "Saved as \"$name\"", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        // Focus the input
        input.requestFocus()
    }
}
