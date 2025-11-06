package com.erikspinebuds.companion.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.erikspinebuds.companion.data.EqBand
import com.erikspinebuds.companion.data.EqFilterType
import com.erikspinebuds.companion.databinding.ItemEqBandBinding

/**
 * RecyclerView adapter for displaying and managing EQ bands
 */
class EqBandAdapter(
    private val bands: MutableList<EqBand>,
    private val onEdit: (Int, EqBand) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<EqBandAdapter.BandViewHolder>() {

    inner class BandViewHolder(private val binding: ItemEqBandBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(band: EqBand, position: Int) {
            binding.tvBandType.text = filterTypeToString(band.type)
            binding.tvBandParams.text = String.format(
                "%s, %+.1f dB, Q=%.1f",
                formatFrequency(band.frequency),
                band.gain,
                band.q
            )

            binding.btnEdit.setOnClickListener {
                onEdit(position, band)
            }

            binding.btnDelete.setOnClickListener {
                onDelete(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BandViewHolder {
        val binding = ItemEqBandBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BandViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BandViewHolder, position: Int) {
        holder.bind(bands[position], position)
    }

    override fun getItemCount(): Int = bands.size

    /**
     * Update the adapter with new bands list
     */
    fun updateBands(newBands: List<EqBand>) {
        bands.clear()
        bands.addAll(newBands)
        notifyDataSetChanged()
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
