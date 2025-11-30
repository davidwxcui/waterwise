package com.davidwxcui.waterwise.ui.friends

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
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
        holder.bind(item, position + 1, onRemoveClick)
    }

    override fun getItemCount(): Int = items.size

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        private val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)

        fun bind(
            item: HomeViewModel.FriendUI,
            rank: Int,
            onRemoveClick: (HomeViewModel.FriendUI) -> Unit
        ) {
            tvRank.text = "#$rank"
            tvName.text = item.name.ifBlank { item.uid }
            tvProgress.text = "Today: -- / -- ml"
            btnMore.setOnClickListener {
                onRemoveClick(item)
            }
        }
    }
}
