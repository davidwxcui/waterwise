package com.davidwxcui.waterwise.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.davidwxcui.waterwise.database.event.Event
import com.davidwxcui.waterwise.databinding.ItemEventBinding
import com.daimajia.swipe.SwipeLayout
import com.google.android.material.shape.ShapeAppearanceModel

class EventAdapter(private val onDeleteClick: (Event) -> Unit = {}) :
    ListAdapter<Event, EventAdapter.EventViewHolder>(EventDiffCallback()) {

    private var openedPosition: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding, onDeleteClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position), position, openedPosition)
    }

    class EventViewHolder(
        private val binding: ItemEventBinding,
        private val onDeleteClick: (Event) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event, position: Int, openedPosition: Int?) {
            binding.eventTitle.text = event.title
            binding.eventDate.text = event.date
            binding.daysUntil.text = "${event.daysUntil} days"
            binding.eventDescription.text = event.description
            binding.eventRecommendation.text = event.recommendation

            // Set chip background color based on event color
            val chipColor = when (event.color) {
                "purple" -> android.graphics.Color.parseColor("#F3E8FF")
                "orange" -> android.graphics.Color.parseColor("#FFE8D0")
                "teal" -> android.graphics.Color.parseColor("#D0F5F1")
                else -> android.graphics.Color.parseColor("#F3E8FF")
            }
            binding.daysUntil.chipBackgroundColor = android.content.res.ColorStateList.valueOf(chipColor)

            // Get SwipeLayout and MaterialCardView reference
            val swipeLayout = binding.root as SwipeLayout
            val cardView = binding.root.findViewById<com.google.android.material.card.MaterialCardView>(
                com.davidwxcui.waterwise.R.id.cardView
            )

            val shapeAppearanceModelWithCorners = ShapeAppearanceModel.builder()
                .setAllCornerSizes(12f)
                .build()

            val shapeAppearanceModelWithoutCorners = ShapeAppearanceModel.builder()
                .setAllCornerSizes(0f)
                .build()
            // Add swipe listener to change corner radius
            swipeLayout.addSwipeListener(object : com.daimajia.swipe.SwipeLayout.SwipeListener {
                override fun onStartOpen(view: SwipeLayout?) {}

                override fun onOpen(view: SwipeLayout?) {
                    cardView?.shapeAppearanceModel = shapeAppearanceModelWithoutCorners
                }

                override fun onStartClose(view: SwipeLayout?) {}

                override fun onClose(view: SwipeLayout?) {
                    cardView?.shapeAppearanceModel = shapeAppearanceModelWithCorners
                }

                override fun onUpdate(view: SwipeLayout?, leftOffset: Int, topOffset: Int) {}

                override fun onHandRelease(view: SwipeLayout?, xvel: Float, yvel: Float) {}
            })

            // Delete button click handler
            binding.deleteButton.setOnClickListener {
                onDeleteClick(event)
                swipeLayout.close(true)
            }

            // Close previously opened item and open current if needed
            if (openedPosition == position) {
                swipeLayout.open(true)
            } else {
                swipeLayout.close(false)
            }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Event, newItem: Event) = oldItem == newItem
    }
}