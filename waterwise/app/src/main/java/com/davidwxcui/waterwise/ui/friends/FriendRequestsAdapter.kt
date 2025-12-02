package com.davidwxcui.waterwise.ui.friends

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.ui.home.HomeViewModel

class FriendRequestsAdapter(
    private val onAccept: (HomeViewModel.FriendRequestUI) -> Unit,
    private val onDecline: (HomeViewModel.FriendRequestUI) -> Unit
) : RecyclerView.Adapter<FriendRequestsAdapter.RequestViewHolder>() {

    private val items = mutableListOf<HomeViewModel.FriendRequestUI>()

    fun submitList(list: List<HomeViewModel.FriendRequestUI>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_request, parent, false)
        return RequestViewHolder(v)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onAccept, onDecline)
    }

    override fun getItemCount(): Int = items.size

    class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvAvatarInitial: TextView = itemView.findViewById(R.id.tvAvatarInitial)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvMsg: TextView = itemView.findViewById(R.id.tvMsg)
        private val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        private val btnDecline: Button = itemView.findViewById(R.id.btnDecline)

        fun bind(
            item: HomeViewModel.FriendRequestUI,
            onAccept: (HomeViewModel.FriendRequestUI) -> Unit,
            onDecline: (HomeViewModel.FriendRequestUI) -> Unit
        ) {
            val displayName = item.name.ifBlank { item.uid }
            tvName.text = displayName
            tvMsg.text = "wants to add you as a friend"

            val initialChar = displayName
                .trim()
                .firstOrNull()
                ?.uppercaseChar() ?: 'U'
            tvAvatarInitial.text = initialChar.toString()

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

            btnAccept.setOnClickListener { onAccept(item) }
            btnDecline.setOnClickListener { onDecline(item) }
        }
    }
}
