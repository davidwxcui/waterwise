package com.davidwxcui.waterwise.minigame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.FirestoreFriendStorage
import com.davidwxcui.waterwise.databinding.FragmentGameRankingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GameRankingFragment : Fragment() {

    private var _binding: FragmentGameRankingBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val friendStorage by lazy { FirestoreFriendStorage() }

    private lateinit var adapter: GameRankingAdapter

    /** 排行作用域：好友(Room) / 全局(Global) */
    private enum class Scope { ROOM, GLOBAL }
    private var currentScope: Scope = Scope.ROOM

    // UI 用的数据模型（加了 avatarUrl）
    data class GameRankingUI(
        val uid: String,
        val name: String,
        val coins: Long,
        val rank: Int,
        val isMe: Boolean,
        val avatarUrl: String?
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

        // 返回按钮：直接关掉这个 Activity
        binding.btnBackGameRanking.setOnClickListener {
            requireActivity().finish()
        }

        // RecyclerView
        adapter = GameRankingAdapter()
        binding.gameRankingList.layoutManager = LinearLayoutManager(requireContext())
        binding.gameRankingList.adapter = adapter

        // Room / Global tab
        setupScopeTabs()
        updateScopeUi()

        // 初次加载（默认 Room）
        loadRanking()
    }

    // ---------------- Scope Tab ----------------

    private fun setupScopeTabs() {
        binding.tabRoom.setOnClickListener {
            if (currentScope != Scope.ROOM) {
                currentScope = Scope.ROOM
                updateScopeUi()
                loadRanking()
            }
        }
        binding.tabGlobal.setOnClickListener {
            if (currentScope != Scope.GLOBAL) {
                currentScope = Scope.GLOBAL
                updateScopeUi()
                loadRanking()
            }
        }
    }

    private fun updateScopeUi() {
        fun styleTab(tv: TextView, selected: Boolean) {
            tv.setTypeface(
                null,
                if (selected) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL
            )
            val colorRes =
                if (selected) android.R.color.black else android.R.color.darker_gray
            tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        }

        styleTab(binding.tabRoom, currentScope == Scope.ROOM)
        styleTab(binding.tabGlobal, currentScope == Scope.GLOBAL)

        binding.tvGameRankingSubtitle.text = when (currentScope) {
            Scope.ROOM -> "Current room · top players by coins"
            Scope.GLOBAL -> "Global · top players by coins"
        }
    }

    // ---------------- 加载排行榜 ----------------

    private fun loadRanking() {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            adapter.submitList(emptyList())
            binding.gameRankingEmpty.isVisible = true
            binding.gameRankingEmpty.text = "Please login to see ranking"
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.gameRankingEmpty.isVisible = false

            try {
                val list: List<GameRankingUI> = when (currentScope) {
                    Scope.GLOBAL -> loadGlobalRanking(myUid)
                    Scope.ROOM -> loadRoomRanking(myUid)
                }

                adapter.submitList(list)
                binding.gameRankingEmpty.isVisible = list.isEmpty()
                binding.gameRankingEmpty.text = when (currentScope) {
                    Scope.ROOM -> "No players in this room yet"
                    Scope.GLOBAL -> "No players yet"
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to load ranking: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * 全局排行榜：
     * 直接查 games 集合，按 coins DESC，最多 10 条
     * 只会显示真正玩过小游戏、在 games 里有文档的用户
     */
    /** 全局排行榜：所有 users 里的账号，按 games 里的 coins 排序，取前 10 个 */
    private suspend fun loadGlobalRanking(myUid: String): List<GameRankingUI> {

        // 1. 先取所有用户（这里限制最多 50 个，根据你实际规模可以调）
        val usersSnap = firestore.collection("users")
            .limit(20)
            .get()
            .await()

        val tmp = mutableListOf<GameRankingUI>()

        // 2. 对每个用户去 games/{uid} 里读 coins（没有就 0）
        for (userDoc in usersSnap.documents) {
            val uid = userDoc.id

            val name =
                userDoc.getString("name")
                    ?: userDoc.getString("displayName")
                    ?: uid

            val avatarUrl = userDoc.getString("avatarUri")

            val gameDoc = firestore.collection("games")
                .document(uid)
                .get()
                .await()

            val coins = gameDoc.getLong("coins") ?: 0L   // 没有 coins 字段就当 0

            tmp += GameRankingUI(
                uid = uid,
                name = name,
                coins = coins,
                rank = 0,                 // 先占位，后面统一写 rank
                isMe = (uid == myUid),
                avatarUrl = avatarUrl
            )
        }

        // 3. 按 coins DESC 排序，只保留前 10 个并写入 rank
        return tmp
            .sortedByDescending { it.coins }
            .take(10)
            .mapIndexed { index, item ->
                item.copy(rank = index + 1)
            }
    }


    /**
     * Room 排行榜：
     * 不再看 FirestoreRoomStorage，
     * 而是：自己 + 所有好友，按 coins 排名
     */
    /** Room 排行榜：自己 + 所有好友，按 coins 排名（显示名字） */
    private suspend fun loadRoomRanking(myUid: String): List<GameRankingUI> {
        // 1. 取好友列表
        val friends = friendStorage.fetchFriendsOnce(myUid)

        // 2. 自己 + 好友们 的 uid 集合
        val allUids = buildList {
            add(myUid)
            addAll(friends.map { it.uid })
        }.distinct()

        if (allUids.isEmpty()) return emptyList()

        // 3. 先把“我自己”的 profile 从 users 取出来（name、avatarUri）
        val myUserSnap = firestore.collection("users").document(myUid).get().await()
        val myNameFromUsers = myUserSnap.getString("name")
        val myAvatarFromUsers = myUserSnap.getString("avatarUri")

        val tmp = mutableListOf<GameRankingUI>()

        // 4. 逐个 uid 读取 games 里的 coins，再决定用什么名字、头像
        for (uid in allUids) {
            val gameDoc = firestore.collection("games").document(uid).get().await()
            val coins = gameDoc.getLong("coins") ?: 0L

            val friend = friends.firstOrNull { it.uid == uid }

            // 名字优先级：
            //  - 如果是自己：users.name -> games.displayName -> uid
            //  - 如果是好友：friends.name -> games.displayName -> uid
            val name = when {
                uid == myUid ->
                    myNameFromUsers
                        ?: gameDoc.getString("displayName")
                        ?: uid

                friend != null ->
                    friend.name.ifBlank {
                        gameDoc.getString("displayName") ?: uid
                    }

                else ->
                    gameDoc.getString("displayName") ?: uid
            }

            // 头像优先级：
            //  - 自己：users.avatarUri -> games.avatarUrl
            //  - 好友：friends.avatarUri -> games.avatarUrl
            val avatarUrl = when {
                uid == myUid ->
                    myAvatarFromUsers ?: gameDoc.getString("avatarUrl")

                friend != null ->
                    friend.avatarUri ?: gameDoc.getString("avatarUrl")

                else ->
                    gameDoc.getString("avatarUrl")
            }

            tmp += GameRankingUI(
                uid = uid,
                name = name,
                coins = coins,
                rank = 0,                 // 先占位，后面统一写 rank
                isMe = (uid == myUid),
                avatarUrl = avatarUrl
            )
        }

        // 5. 按 coins DESC 排序，并写入 rank
        return tmp
            .sortedByDescending { it.coins }
            .mapIndexed { index, item ->
                item.copy(rank = index + 1)
            }
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

                // 只让第 1 名显示奖杯，其它名次不显示图标
                if (item.rank == 1) {
                    imgAvatar.visibility = View.VISIBLE
                    imgAvatar.setImageResource(R.drawable.ic_gameranking) // 你现在用的奖杯图标
                } else {
                    // 不想占位就用 GONE；想保留布局占位就用 INVISIBLE
                    imgAvatar.visibility = View.INVISIBLE
                }

                tvMeTag.isVisible = item.isMe
            }

        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
