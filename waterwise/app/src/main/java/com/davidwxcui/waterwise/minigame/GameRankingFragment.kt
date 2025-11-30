package com.davidwxcui.waterwise.minigame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentGameRankingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class GameRankingFragment : Fragment() {

    private var _binding: FragmentGameRankingBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: GameRankingAdapter

    // UI 用的数据模型
    data class GameRankingUI(
        val uid: String,
        val name: String,
        val coins: Long,
        val rank: Int,
        val isMe: Boolean
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameRankingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 返回按钮
        binding.btnBackGameRanking.setOnClickListener {
            findNavController().navigateUp()
        }

        // RecyclerView
        adapter = GameRankingAdapter()
        binding.gameRankingList.layoutManager = LinearLayoutManager(requireContext())
        binding.gameRankingList.adapter = adapter

        // 读取排行榜数据
        loadRanking()
    }

    private fun loadRanking() {
        val myUid = auth.currentUser?.uid

        firestore.collection("games")
            .orderBy("coins", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapIndexed { index, doc ->
                    val uid = doc.getString("uid") ?: doc.id
                    val name = doc.getString("displayName") ?: uid
                    val coins = doc.getLong("coins") ?: 0L

                    GameRankingUI(
                        uid = uid,
                        name = name,
                        coins = coins,
                        rank = index + 1,
                        isMe = (uid == myUid)
                    )
                }

                adapter.submitList(list)
                binding.gameRankingEmpty.isVisible = list.isEmpty()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Failed to load ranking: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------------- Adapter ----------------

    private class GameRankingAdapter :
        RecyclerView.Adapter<GameRankingAdapter.GameRankViewHolder>() {

        private val items = mutableListOf<GameRankingUI>()

        fun submitList(list: List<GameRankingUI>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameRankViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_game_ranking, parent, false)
            return GameRankViewHolder(v)
        }

        override fun onBindViewHolder(holder: GameRankViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class GameRankViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvRank: TextView = itemView.findViewById(R.id.tvRank)
            private val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
            private val tvName: TextView = itemView.findViewById(R.id.tvName)
            private val tvCoins: TextView = itemView.findViewById(R.id.tvCoins)
            private val tvMeTag: TextView = itemView.findViewById(R.id.tvMeTag)

            fun bind(item: GameRankingUI) {
                tvRank.text = "#${item.rank}"
                tvName.text = if (item.isMe) "${item.name} (You)" else item.name
                tvCoins.text = "Coins: ${item.coins}"

                // 现在统一用 ic_gameranking 图标
                imgAvatar.setImageResource(R.drawable.ic_gameranking)

                tvMeTag.isVisible = item.isMe
            }
        }
    }
}
