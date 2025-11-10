package com.erikspinebuds.companion.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.erikspinebuds.companion.databinding.ItemSavedEqBinding
import com.erikspinebuds.companion.data.SavedEqManager

class SavedEqAdapter(
    private var savedEqs: List<SavedEqManager.SavedEq>,
    private val onItemClick: (SavedEqManager.SavedEq) -> Unit,
    private val onItemLongClick: (SavedEqManager.SavedEq) -> Unit,
    private val onDeleteClick: (SavedEqManager.SavedEq) -> Unit
) : RecyclerView.Adapter<SavedEqAdapter.SavedEqViewHolder>() {

    inner class SavedEqViewHolder(private val binding: ItemSavedEqBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(savedEq: SavedEqManager.SavedEq) {
            binding.tvEqName.text = savedEq.name

            // Build details string showing band count and gain values
            val bandCount = savedEq.config.numBands
            val preGain = String.format("%.1f", savedEq.config.gain0)
            val postGain = String.format("%.1f", savedEq.config.gain1)
            binding.tvEqDetails.text = "$bandCount bands • Pre: ${preGain}dB • Post: ${postGain}dB"

            // Click to apply
            binding.root.setOnClickListener {
                onItemClick(savedEq)
            }

            // Long press to edit
            binding.root.setOnLongClickListener {
                onItemLongClick(savedEq)
                true
            }

            // Delete button
            binding.btnDelete.setOnClickListener {
                onDeleteClick(savedEq)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedEqViewHolder {
        val binding = ItemSavedEqBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SavedEqViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SavedEqViewHolder, position: Int) {
        holder.bind(savedEqs[position])
    }

    override fun getItemCount(): Int = savedEqs.size

    fun updateData(newSavedEqs: List<SavedEqManager.SavedEq>) {
        savedEqs = newSavedEqs
        notifyDataSetChanged()
    }
}
