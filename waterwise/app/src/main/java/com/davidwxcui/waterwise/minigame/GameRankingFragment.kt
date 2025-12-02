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
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentGameRankingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldPath
class GameRankingFragment : Fragment() {

    private var _binding: FragmentGameRankingBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: GameRankingAdapter
    private lateinit var roomId: String   // 当前房间 id

    /** 排行作用域：房间(Room) / 全局(Global) */
    private enum class Scope { ROOM, GLOBAL }
    private var currentScope: Scope = Scope.ROOM

    // UI 用的数据模型
    data class GameRankingUI(
        val uid: String,
        val name: String,
        val coins: Long,
        val rank: Int,
        val isMe: Boolean,
        val avatarUrl: String?
    )

    companion object {
        private const val ARG_ROOM_ID = "roomId"
        private const val ROOMS_COLLECTION = "rooms"
        private const val MEMBERS_SUBCOLLECTION = "members"

        // 可选：如果你以后想用 arguments 的写法，可以用这个
        fun newInstance(roomId: String): GameRankingFragment {
            return GameRankingFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ROOM_ID, roomId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ① 优先从 arguments 取（如果你用 newInstance 传过来）
        // ② 如果没有，就从宿主 Activity 的 Intent 里取 GameActivity.EXTRA_ROOM_ID
        roomId = arguments?.getString(ARG_ROOM_ID)
            ?: requireActivity().intent.getStringExtra(GameActivity.EXTRA_ROOM_ID).orEmpty()
    }

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

        if (currentScope == Scope.ROOM && roomId.isEmpty()) {
            adapter.submitList(emptyList())
            binding.gameRankingEmpty.isVisible = true
            binding.gameRankingEmpty.text = "Room ID not provided"
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
     * 直接从 users 集合里读取最高金币（highestCoins / highestcoins），
     * 在内存里排序，取前 10 个。
     */
    private suspend fun loadGlobalRanking(myUid: String): List<GameRankingUI> {
        // 1. 取一批用户，这里先限制 50 个，够你排行榜用
        val usersSnap = firestore.collection("users")
            .limit(50)
            .get()
            .await()

        if (usersSnap.isEmpty) return emptyList()

        val tmp = mutableListOf<GameRankingUI>()

        for (userDoc in usersSnap.documents) {
            val uid = userDoc.id

            // 注意字段名：同时兼容 highestCoins 和 highestcoins
            val coins = userDoc.getLong("highestCoins")
                ?: userDoc.getLong("highestcoins")
                ?: 0L

            // 如果不想显示没玩过的人，可以直接跳过 0：
//         if (coins <= 0L) continue

            val name = userDoc.getString("name")
                ?: userDoc.getString("displayName")
                ?: uid

            val avatarUrl = userDoc.getString("avatarUri")

            tmp += GameRankingUI(
                uid = uid,
                name = name,
                coins = coins,
                rank = 0,             // 先占位，后面统一写 rank
                isMe = (uid == myUid),
                avatarUrl = avatarUrl
            )
        }

        // 2. 在内存里按 coins 降序，取前 10，并写入 rank
        return tmp
            .sortedByDescending { it.coins }
            .take(10)
            .mapIndexed { index, item ->
                item.copy(rank = index + 1)
            }
    }




    /**
     * Room 排行榜：当前房间 rooms/<roomId>/members 下的所有玩家，按 coins 排序
     */
    private suspend fun loadRoomRanking(myUid: String): List<GameRankingUI> {
        if (roomId.isEmpty()) return emptyList()

        // 1. 读取 rooms/<roomId>/members 下所有玩家
        val membersSnap = firestore.collection(ROOMS_COLLECTION)
            .document(roomId)
            .collection(MEMBERS_SUBCOLLECTION)
            .get()
            .await()

        if (membersSnap.isEmpty) return emptyList()

        val tmp = mutableListOf<GameRankingUI>()

        // 2. 对每个 member 读 coins + 用户信息
        for (memberDoc in membersSnap.documents) {
            val uid = memberDoc.id
            val coins = memberDoc.getLong("coins") ?: 0L

            // 用户信息从 users/<uid> 里拿 name / avatarUri
            val userSnap = try {
                firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()
            } catch (_: Exception) {
                null
            }

            val name = when {
                userSnap != null && userSnap.exists() ->
                    userSnap.getString("name")
                        ?: userSnap.getString("displayName")
                        ?: uid
                else -> uid
            }

            val avatarUrl = userSnap?.getString("avatarUri")

            tmp += GameRankingUI(
                uid = uid,
                name = name,
                coins = coins,
                rank = 0,                  // 先占位，后面统一写 rank
                isMe = (uid == myUid),
                avatarUrl = avatarUrl
            )
        }

        // 3. 按 coins DESC 排序并写入 rank
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

                // 第 1 名用奖杯图标，其它名次隐藏图标
                if (item.rank == 1) {
                    imgAvatar.visibility = View.VISIBLE
                    imgAvatar.setImageResource(R.drawable.ic_gameranking)
                } else {
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
