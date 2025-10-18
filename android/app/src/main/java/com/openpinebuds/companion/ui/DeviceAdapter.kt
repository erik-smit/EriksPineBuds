package com.openpinebuds.companion.ui

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.openpinebuds.companion.databinding.ItemDeviceBinding

/**
 * RecyclerView adapter for displaying discovered BLE devices
 */
class DeviceAdapter(
    private val onDeviceClick: (ScanResult) -> Unit
) : ListAdapter<ScanResult, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding, onDeviceClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding,
        private val onDeviceClick: (ScanResult) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("MissingPermission")
        fun bind(scanResult: ScanResult) {
            val device = scanResult.device

            // Device name (or "Unknown Device" if null)
            binding.tvDeviceName.text = device.name ?: "Unknown Device"

            // Device address (MAC address)
            binding.tvDeviceAddress.text = device.address

            // Signal strength (RSSI)
            val rssi = scanResult.rssi
            binding.tvRssi.text = "$rssi dBm"

            // Signal strength indicator
            binding.tvSignalStrength.text = when {
                rssi >= -50 -> "Excellent"
                rssi >= -70 -> "Good"
                rssi >= -80 -> "Fair"
                else -> "Weak"
            }

            // Click handler
            binding.root.setOnClickListener {
                onDeviceClick(scanResult)
            }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<ScanResult>() {
        override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
            return oldItem.device.address == newItem.device.address
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
            // Compare RSSI to update signal strength if it changes
            return oldItem.device.address == newItem.device.address &&
                    oldItem.rssi == newItem.rssi
        }
    }
}
