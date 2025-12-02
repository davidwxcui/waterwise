package com.davidwxcui.waterwise.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.davidwxcui.waterwise.data.DrinkLog
import com.davidwxcui.waterwise.databinding.ItemTimelineEntryBinding

class TimelineAdapter(
    private val onEdit: (DrinkLog) -> Unit,
    private val onDelete: (DrinkLog) -> Unit
) : ListAdapter<DrinkLog, TimelineAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<DrinkLog>() {
        override fun areItemsTheSame(oldItem: DrinkLog, newItem: DrinkLog) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DrinkLog, newItem: DrinkLog) =
            oldItem == newItem
    }

    inner class VH(val b: ItemTimelineEntryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTimelineEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.b.timeText.text =
            android.text.format.DateFormat.format("HH:mm", item.timeMillis)
        holder.b.titleText.text = item.type.displayName
        holder.b.tvIntakeValue.text = "${item.volumeMl} ml"
        holder.b.tvEffectiveValue.text = "${item.effectiveMl} ml"

        holder.b.btnEdit.setOnClickListener { onEdit(item) }
        holder.b.btnDelete.setOnClickListener { onDelete(item) }
    }
}
