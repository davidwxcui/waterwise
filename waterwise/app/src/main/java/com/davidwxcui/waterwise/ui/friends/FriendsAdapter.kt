package com.davidwxcui.waterwise.ui.friends

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.ui.home.HomeViewModel

class FriendsAdapter(
    private val onRemoveClick: (HomeViewModel.FriendUI) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    private val items = mutableListOf<HomeViewModel.FriendUI>()

    fun submitList(list: List<HomeViewModel.FriendUI>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(v)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onRemoveClick)
    }

    override fun getItemCount(): Int = items.size

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvAvatarInitial: TextView = itemView.findViewById(R.id.tvAvatarInitial)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)

        fun bind(
            item: HomeViewModel.FriendUI,
            onRemoveClick: (HomeViewModel.FriendUI) -> Unit
        ) {
            val displayName = item.name.ifBlank { item.uid }
            tvName.text = displayName

            val initialChar = displayName
                .trim()
                .firstOrNull()
                ?.uppercaseChar() ?: 'U'
            tvAvatarInitial.text = initialChar.toString()

            // Avatar
            if (!item.avatarUri.isNullOrBlank()) {
                tvAvatarInitial.visibility = View.GONE
                imgAvatar.visibility = View.VISIBLE

                Glide.with(itemView)
                    .load(item.avatarUri)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(imgAvatar)
            } else {
                imgAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
                tvAvatarInitial.visibility = View.VISIBLE
                imgAvatar.visibility = View.VISIBLE
            }

            // Daily Goal
            val today = item.todayIntakeMl
            val goal = item.todayGoalMl
            tvProgress.text = if (goal > 0) {
                "Today: $today / $goal ml"
            } else {
                "Today: $today ml"
            }

            // Delete
            btnMore.setOnClickListener {
                onRemoveClick(item)
            }
        }
    }
}
