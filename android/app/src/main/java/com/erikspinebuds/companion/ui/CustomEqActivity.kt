package com.erikspinebuds.companion.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.erikspinebuds.companion.data.EqBand
import com.erikspinebuds.companion.data.EqConfiguration
import com.erikspinebuds.companion.data.EqFilterType
import com.erikspinebuds.companion.databinding.ActivityCustomEqBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Activity for creating and editing custom EQ configurations
 */
class CustomEqActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomEqBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var bandAdapter: EqBandAdapter
    private var currentConfig: EqConfiguration = EqConfiguration.flat()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomEqBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use the singleton BleManager directly (shared across all activities)
        // Creating a new ViewModel would create a separate instance without the active connection
        viewModel = MainViewModel(application)

        setupToolbar()
        setupRecyclerView()
        setupSliders()
        setupButtons()
        observeViewModel()

        // Load current config from device using the singleton BleManager
        loadCustomEqConfigDirectly()
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
        }

        // Post-EQ gain slider
        binding.sliderPostGain.addOnChangeListener { _, value, _ ->
            currentConfig.gain1 = value
            binding.tvPostGainValue.text = String.format("%.1f dB", value)
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

        binding.btnReset.setOnClickListener {
            confirmReset()
        }

        binding.btnSave.setOnClickListener {
            saveToDevice()
        }
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
}
