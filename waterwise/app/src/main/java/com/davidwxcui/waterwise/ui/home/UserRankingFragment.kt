package com.davidwxcui.waterwise.ui.home

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.FirestoreDrinkStorage
import com.davidwxcui.waterwise.data.FirestoreFriendStorage
import com.davidwxcui.waterwise.databinding.FragmentUserRankingBinding
import com.davidwxcui.waterwise.ui.profile.LocalAuthRepository
import com.davidwxcui.waterwise.ui.profile.ProfilePrefs
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

class UserRankingFragment : Fragment() {

    private var _binding: FragmentUserRankingBinding? = null
    private val binding get() = _binding!!

    private enum class Range { TODAY, WEEK, MONTH }
    private var currentRange: Range = Range.TODAY

    private val drinkStorage = FirestoreDrinkStorage()
    private val friendStorage = FirestoreFriendStorage()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var rankingAdapter: RankingAdapter

    private val rankingCache = mutableMapOf<Range, List<RankItem>>()

    private data class RankItem(
        val uid: String,
        val name: String,
        val isMe: Boolean,
        val totalMl: Int,
        val percent: Int,
        val rank: Int,
        val avatarUrl: String?
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

        binding.btnBackRanking.setOnClickListener {
            findNavController().navigateUp()
        }

        val profile = ProfilePrefs.load(requireContext())
        val displayName = profile.name.ifBlank { "Guest" }
        binding.myUserId.text = displayName

        rankingAdapter = RankingAdapter()
        binding.rankingList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rankingAdapter
        }

        setupRangeTabs()
        updateRangeUi()
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

    private fun currentDays(): Int = when (currentRange) {
        Range.TODAY -> 1
        Range.WEEK -> 7
        Range.MONTH -> 30
    }

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

                val goal = (snap.getLong("goalMl")
                    ?: snap.getLong("dailyGoalMl")
                    ?: 0L).toInt()

                if (goal > 0) {
                    result[uid] = goal
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun loadRankingForRange() {
        val ctx = requireContext()
        val myUid = LocalAuthRepository.getUid(ctx) ?: run {
            rankingAdapter.submitList(emptyList())
            binding.rankingEmpty.visibility = View.VISIBLE
            binding.myRankNumber.text = "#-"
            binding.myAmount.text = "--"
            binding.myPercent.text = "--"
            return
        }

        val myProfile = ProfilePrefs.load(ctx)

        rankingCache[currentRange]?.let { cached ->
            applyRankingToUi(cached, myUid)
        }

        val days = currentDays()

        viewLifecycleOwner.lifecycleScope.launch {
            val friends = friendStorage.fetchFriendsOnce(myUid)

            val allUids = buildList {
                add(myUid)
                addAll(friends.map { it.uid })
            }.distinct()

            if (allUids.isEmpty()) {
                rankingCache[currentRange] = emptyList()
                applyRankingToUi(emptyList(), myUid)
                return@launch
            }

            val totalsMap = drinkStorage.fetchTotalIntakeForUids(allUids, days)
            val goalsMap = fetchDailyGoalsForUids(allUids)

            val itemsNoRank = allUids.map { uid ->
                val isMe = uid == myUid
                val friendInfo = friends.firstOrNull { it.uid == uid }

                val rawName = if (isMe) {
                    binding.myUserId.text?.toString().orEmpty()
                } else {
                    friendInfo?.name ?: uid
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

                val avatarUrl = if (isMe) {
                    myProfile.avatarUri
                } else {
                    friendInfo?.avatarUri
                }

                RankItem(
                    uid = uid,
                    name = name,
                    isMe = isMe,
                    totalMl = totalMl,
                    percent = percent,
                    rank = 0,
                    avatarUrl = avatarUrl
                )
            }

            val comparator = when (currentRange) {
                Range.TODAY ->
                    compareByDescending<RankItem> { it.percent }
                        .thenByDescending { it.totalMl }
                Range.WEEK, Range.MONTH ->
                    compareByDescending<RankItem> { it.totalMl }
            }

            val sorted = itemsNoRank
                .sortedWith(comparator)
                .mapIndexed { index, item ->
                    item.copy(rank = index + 1)
                }

            rankingCache[currentRange] = sorted
            applyRankingToUi(sorted, myUid)
        }
    }

    private fun applyRankingToUi(sorted: List<RankItem>, myUid: String) {
        val myItem = sorted.firstOrNull { it.uid == myUid }
        if (myItem != null) {
            binding.myRankNumber.text = "#${myItem.rank}"

            val rangeLabel = when (currentRange) {
                Range.TODAY -> "today"
                Range.WEEK -> "last 7 days"
                Range.MONTH -> "last 30 days"
            }
            binding.myAmount.text = "${myItem.totalMl} ml $rangeLabel"

            binding.myPercent.text = when (currentRange) {
                Range.TODAY -> "${myItem.percent}%"
                Range.WEEK, Range.MONTH -> ""
            }
        } else {
            binding.myRankNumber.text = "#-"
            binding.myAmount.text = "--"
            binding.myPercent.text = "--"
        }

        rankingAdapter.submitList(sorted)
        binding.rankingEmpty.visibility =
            if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

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
            private val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)

            fun bind(item: RankItem) {
                tvRank.text = "#${item.rank}"
                tvName.text = if (item.isMe) "${item.name} (You)" else item.name

                val prefix = when (currentRange) {
                    Range.TODAY -> "Today"
                    Range.WEEK -> "Last 7 days"
                    Range.MONTH -> "Last 30 days"
                }

                tvProgress.text = when (currentRange) {
                    Range.TODAY ->
                        "$prefix: ${item.totalMl} ml · ${item.percent}%"
                    Range.WEEK, Range.MONTH ->
                        "$prefix: ${item.totalMl} ml"
                }

                if (item.avatarUrl.isNullOrBlank()) {
                    imgAvatar.setImageDrawable(null)
                } else {
                    Glide.with(imgAvatar.context)
                        .load(item.avatarUrl)
                        .circleCrop()
                        .into(imgAvatar)
                }
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
