package com.erikspinebuds.companion.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import com.erikspinebuds.companion.R
import com.erikspinebuds.companion.data.EqBand
import com.erikspinebuds.companion.data.EqFilterType
import com.erikspinebuds.companion.databinding.DialogEqBandEditorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.pow

/**
 * Dialog for editing a single EQ band
 */
class EqBandEditorDialog(
    context: Context,
    private val band: EqBand,
    private val onSave: (EqBand) -> Unit,
    private val onLiveUpdate: ((EqBand) -> Unit)? = null
) : Dialog(context) {

    private lateinit var binding: DialogEqBandEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogEqBandEditorBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        setupFilterTypeSpinner()
        setupSliders()
        setupButtons()

        // Load initial values
        loadBandValues()
    }

    private fun setupFilterTypeSpinner() {
        val filterTypes = EqFilterType.values().map { filterTypeToString(it) }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, filterTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilterType.adapter = adapter
    }

    private fun setupSliders() {
        // Frequency slider (logarithmic scale: 20 Hz to 20 kHz)
        binding.sliderFrequency.addOnChangeListener { _, value, _ ->
            val frequency = frequencyFromSlider(value)
            band.frequency = frequency
            binding.tvFrequencyValue.text = formatFrequency(frequency)
            onLiveUpdate?.invoke(band)
        }

        // Gain slider (-12 to +12 dB)
        binding.sliderGain.addOnChangeListener { _, value, _ ->
            band.gain = value
            binding.tvGainValue.text = String.format("%.1f dB", value)
            onLiveUpdate?.invoke(band)
        }

        // Q slider (0.3 to 10.0)
        binding.sliderQ.addOnChangeListener { _, value, _ ->
            band.q = value
            binding.tvQValue.text = String.format("%.1f", value)
            onLiveUpdate?.invoke(band)
        }

        // Filter type spinner
        binding.spinnerFilterType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                band.type = EqFilterType.values()[position]
                updateGainVisibility()
                onLiveUpdate?.invoke(band)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnClose.setOnClickListener {
            // Clamp values to valid ranges
            band.gain = band.gain.coerceIn(-12.0f, 12.0f)
            band.frequency = band.frequency.coerceIn(20.0f, 20000.0f)
            band.q = band.q.coerceIn(0.3f, 10.0f)

            android.util.Log.d("EqBandEditor", "Closing - band applied live: type=${band.type}, freq=${band.frequency}, gain=${band.gain}, q=${band.q}")
            onSave(band)
            dismiss()
        }
    }

    private fun loadBandValues() {
        // Load filter type
        binding.spinnerFilterType.setSelection(band.type.ordinal)

        // Load frequency (convert to slider value, rounded to match stepSize)
        val frequencySliderValue = sliderFromFrequency(band.frequency).coerceIn(0f, 100f)
        binding.sliderFrequency.value = kotlin.math.round(frequencySliderValue)
        binding.tvFrequencyValue.text = formatFrequency(band.frequency)

        // Load gain
        binding.sliderGain.value = band.gain
        binding.tvGainValue.text = String.format("%.1f dB", band.gain)

        // Load Q
        binding.sliderQ.value = band.q
        binding.tvQValue.text = String.format("%.1f", band.q)

        updateGainVisibility()
    }

    /**
     * Update gain slider visibility based on filter type
     * Low-pass and high-pass filters don't use gain (only fc and Q)
     */
    private fun updateGainVisibility() {
        val gainRelevant = band.type != EqFilterType.LOW_PASS && band.type != EqFilterType.HIGH_PASS

        binding.sliderGain.isEnabled = gainRelevant
        binding.sliderGain.alpha = if (gainRelevant) 1.0f else 0.4f

        // Set gain to 0 for filters that don't use it
        if (!gainRelevant && band.gain != 0.0f) {
            band.gain = 0.0f
            binding.tvGainValue.text = "0.0 dB (N/A)"
        }
    }

    /**
     * Convert slider value (0-100) to frequency (20-20000 Hz) using logarithmic scale
     */
    private fun frequencyFromSlider(sliderValue: Float): Float {
        // Logarithmic mapping: 20 Hz to 20 kHz
        val minLog = kotlin.math.log10(20.0)
        val maxLog = kotlin.math.log10(20000.0)
        val logValue = minLog + (sliderValue / 100.0) * (maxLog - minLog)
        return 10.0.pow(logValue).toFloat()
    }

    /**
     * Convert frequency (20-20000 Hz) to slider value (0-100) using logarithmic scale
     */
    private fun sliderFromFrequency(frequency: Float): Float {
        val minLog = kotlin.math.log10(20.0)
        val maxLog = kotlin.math.log10(20000.0)
        val freqLog = kotlin.math.log10(frequency.toDouble())
        return ((freqLog - minLog) / (maxLog - minLog) * 100.0).toFloat().coerceIn(0f, 100f)
    }

    /**
     * Format frequency for display
     */
    private fun formatFrequency(frequency: Float): String {
        return when {
            frequency < 1000 -> String.format("%.0f Hz", frequency)
            else -> String.format("%.1f kHz", frequency / 1000.0)
        }
    }

    /**
     * Convert filter type enum to display string
     */
    private fun filterTypeToString(type: EqFilterType): String {
        return when (type) {
            EqFilterType.LOW_SHELF -> "Low Shelf"
            EqFilterType.PEAK -> "Peak"
            EqFilterType.HIGH_SHELF -> "High Shelf"
            EqFilterType.LOW_PASS -> "Low Pass"
            EqFilterType.HIGH_PASS -> "High Pass"
        }
    }
}
