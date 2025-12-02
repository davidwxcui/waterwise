package com.davidwxcui.waterwise.ui.reminder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.davidwxcui.waterwise.databinding.ItemReminderTimeBinding
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Adapter for displaying custom reminder times in a RecyclerView
 */
class ReminderTimeAdapter(
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<ReminderTimeAdapter.ViewHolder>() {

    private var times = mutableListOf<String>()
    private val displayFormatter = DateTimeFormatter.ofPattern("h:mm a") // 12-hour format

    fun submitList(newTimes: List<String>) {
        times.clear()
        times.addAll(newTimes.sorted()) // Sort times chronologically
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderTimeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(times[position])
    }

    override fun getItemCount(): Int = times.size

    inner class ViewHolder(
        private val binding: ItemReminderTimeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(time: String) {
            // Parse and format time for display
            try {
                val localTime = LocalTime.parse(time) // "HH:mm" format
                binding.tvTime.text = localTime.format(displayFormatter) // "9:00 AM"
            } catch (e: Exception) {
                binding.tvTime.text = time // Fallback to raw string
            }

            // Handle delete button click
            binding.btnDelete.setOnClickListener {
                onDeleteClick(time)
            }
        }
    }
}

