package com.davidwxcui.waterwise.ui.home

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.FirestoreDrinkStorage
import com.davidwxcui.waterwise.data.FirestoreFriendStorage
import com.davidwxcui.waterwise.databinding.FragmentUserRankingBinding
import com.davidwxcui.waterwise.ui.profile.LocalAuthRepository
import com.davidwxcui.waterwise.ui.profile.ProfilePrefs
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.tasks.await

class UserRankingFragment : Fragment() {

    private var _binding: FragmentUserRankingBinding? = null
    private val binding get() = _binding!!

    // 排行时间范围
    private enum class Range { TODAY, WEEK, MONTH }
    private var currentRange: Range = Range.TODAY

    // Firestore 数据访问
    private val drinkStorage = FirestoreDrinkStorage()
    private val friendStorage = FirestoreFriendStorage()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // 排行列表 adapter
    private lateinit var rankingAdapter: RankingAdapter

    // 排行 item：已经包含 uid、是否自己、总 ml、百分比、名次
    private data class RankItem(
        val uid: String,
        val name: String,
        val isMe: Boolean,
        val totalMl: Int,
        val percent: Int,
        val rank: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserRankingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 返回按钮
        binding.btnBackRanking.setOnClickListener {
            findNavController().navigateUp()
        }

        // 2. 显示当前用户名字（从 Profile 里读）
        val profile = ProfilePrefs.load(requireContext())
        val displayName = profile.name.ifBlank { "Guest" }
        binding.myUserId.text = displayName

        // 3. RecyclerView 初始化
        rankingAdapter = RankingAdapter()
        binding.rankingList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rankingAdapter
        }

        // 4. tab 点击
        setupRangeTabs()
        updateRangeUi()

        // 5. 进入页面先加载一次 Today 排行榜
        loadRankingForRange()
    }

    private fun setupRangeTabs() {
        binding.tabToday.setOnClickListener {
            if (currentRange != Range.TODAY) {
                currentRange = Range.TODAY
                updateRangeUi()
                loadRankingForRange()
            }
        }
        binding.tab7Days.setOnClickListener {
            if (currentRange != Range.WEEK) {
                currentRange = Range.WEEK
                updateRangeUi()
                loadRankingForRange()
            }
        }
        binding.tab30Days.setOnClickListener {
            if (currentRange != Range.MONTH) {
                currentRange = Range.MONTH
                updateRangeUi()
                loadRankingForRange()
            }
        }
    }

    /** 更新 tab 样式 + 顶部说明文字 */
    private fun updateRangeUi() {
        fun styleTab(tv: TextView, selected: Boolean) {
            tv.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            val colorRes =
                if (selected) android.R.color.black else android.R.color.darker_gray
            tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        }

        styleTab(binding.tabToday, currentRange == Range.TODAY)
        styleTab(binding.tab7Days, currentRange == Range.WEEK)
        styleTab(binding.tab30Days, currentRange == Range.MONTH)

        binding.rankingSubtitle.text = when (currentRange) {
            Range.TODAY -> "Today · Compare your water intake with friends"
            Range.WEEK -> "Last 7 days · Compare your water intake with friends"
            Range.MONTH -> "Last 30 days · Compare your water intake with friends"
        }
    }

    /** 当前 tab 对应多少天 */
    private fun currentDays(): Int = when (currentRange) {
        Range.TODAY -> 1
        Range.WEEK -> 7
        Range.MONTH -> 30
    }

    /** 一次性从 Firestore users 文档里读每个 uid 的 daily goal（单位 ml） */
    private suspend fun fetchDailyGoalsForUids(uids: List<String>): Map<String, Int> {
        if (uids.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Int>()

        for (uid in uids) {
            try {
                val snap = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                if (!snap.exists()) continue

                // 如果你的字段名不叫 goalMl / dailyGoalMl，在这里改
                val goal = (snap.getLong("goalMl")
                    ?: snap.getLong("dailyGoalMl")
                    ?: 0L).toInt()

                if (goal > 0) {
                    result[uid] = goal
                }
            } catch (_: Exception) {
                // 某个用户取失败就忽略
            }
        }
        return result
    }

    /** 核心：按百分比加载并排序排行榜 */
    private fun loadRankingForRange() {
        val ctx = requireContext()
        val myUid = LocalAuthRepository.getUid(ctx) ?: run {
            // 未登录：清空列表
            rankingAdapter.submitList(emptyList())
            binding.rankingEmpty.visibility = View.VISIBLE
            binding.myRankNumber.text = "#-"
            binding.myAmount.text = "--"
            binding.myPercent.text = "--"
            return
        }

        val days = currentDays()

        viewLifecycleOwner.lifecycleScope.launch {
            binding.rankingEmpty.visibility = View.VISIBLE

            // 1. 读好友列表（一次性）
            val friends = friendStorage.fetchFriendsOnce(myUid)

            // 2. 自己 + 好友 一起算
            val allUids = buildList {
                add(myUid)
                addAll(friends.map { it.uid })
            }.distinct()

            if (allUids.isEmpty()) {
                rankingAdapter.submitList(emptyList())
                binding.rankingEmpty.visibility = View.VISIBLE
                return@launch
            }

            // 3. 取这段时间内每个人总共喝了多少 ml
            val totalsMap = drinkStorage.fetchTotalIntakeForUids(allUids, days)

            // 4. 取每个人的 daily goal
            val goalsMap = fetchDailyGoalsForUids(allUids)

            // 5. 组装 RankItem（先不排 rank）
            val itemsNoRank = allUids.map { uid ->
                val isMe = uid == myUid
                val rawName = if (isMe) {
                    binding.myUserId.text?.toString().orEmpty()
                } else {
                    friends.firstOrNull { it.uid == uid }?.name ?: uid
                }
                val name = rawName.ifBlank { uid }

                val totalMl = totalsMap[uid] ?: 0
                val goalPerDay = goalsMap[uid]?.takeIf { it > 0 } ?: 2500

                val percent = if (goalPerDay <= 0 || days <= 0) {
                    0
                } else {
                    ((totalMl.toDouble() / (goalPerDay * days)) * 100)
                        .roundToInt()
                        .coerceIn(0, 100)
                }

                RankItem(
                    uid = uid,
                    name = name,
                    isMe = isMe,
                    totalMl = totalMl,
                    percent = percent,
                    rank = 0
                )
            }

            // 6. 按百分比排序（再按 ml 排），并写入 rank
            val sorted = itemsNoRank
                .sortedWith(
                    compareByDescending<RankItem> { it.percent }
                        .thenByDescending { it.totalMl }
                )
                .mapIndexed { index, item ->
                    item.copy(rank = index + 1)
                }

            // 7. 更新顶部“我的”卡片
            val myItem = sorted.firstOrNull { it.uid == myUid }
            if (myItem != null) {
                binding.myRankNumber.text = "#${myItem.rank}"

                val rangeLabel = when (currentRange) {
                    Range.TODAY -> "today"
                    Range.WEEK -> "last 7 days"
                    Range.MONTH -> "last 30 days"
                }
                binding.myAmount.text = "${myItem.totalMl} ml $rangeLabel"
                binding.myPercent.text = "${myItem.percent}%"
            } else {
                binding.myRankNumber.text = "#-"
                binding.myAmount.text = "--"
                binding.myPercent.text = "--"
            }

            // 8. 更新列表
            rankingAdapter.submitList(sorted)
            binding.rankingEmpty.visibility =
                if (sorted.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 排行榜列表 Adapter，复用 item_friend.xml */
    private inner class RankingAdapter :
        RecyclerView.Adapter<RankingAdapter.VH>() {

        private val items = mutableListOf<RankItem>()

        fun submitList(list: List<RankItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val tvRank: TextView = view.findViewById(R.id.tvRank)
            private val tvName: TextView = view.findViewById(R.id.tvName)
            private val tvProgress: TextView = view.findViewById(R.id.tvProgress)

            fun bind(item: RankItem) {
                tvRank.text = "#${item.rank}"
                tvName.text = if (item.isMe) "${item.name} (You)" else item.name

                val prefix = when (currentRange) {
                    Range.TODAY -> "Today"
                    Range.WEEK -> "Last 7 days"
                    Range.MONTH -> "Last 30 days"
                }
                tvProgress.text = "$prefix: ${item.totalMl} ml · ${item.percent}%"
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_friend, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
