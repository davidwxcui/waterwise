package com.davidwxcui.waterwise.ui.home

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentUserRankingBinding
import com.davidwxcui.waterwise.ui.profile.ProfilePrefs
import kotlin.math.roundToInt

class UserRankingFragment : Fragment() {

    private var _binding: FragmentUserRankingBinding? = null
    private val binding get() = _binding!!

    // 复用 Home 的饮水统计
    private val homeVm: HomeViewModel by viewModels()

    // 排行时间范围
    private enum class Range { TODAY, WEEK, MONTH }
    private var currentRange: Range = Range.TODAY

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

        // 1. 返回按钮：回到 Home
        binding.btnBackRanking.setOnClickListener {
            findNavController().navigateUp()
        }

        // 2. 显示当前用户名字（从 Profile 里读）
        val profile = ProfilePrefs.load(requireContext())
        val displayName = profile.name.ifBlank { "Guest" }
        binding.myUserId.text = displayName

        // 3. 设置时间范围 tab 点击
        setupRangeTabs()
        updateRangeUi()  // 初始化一次 UI

        // 4. 监听 HomeViewModel 的 uiState，更新“我的”卡片
        homeVm.uiState.observe(viewLifecycleOwner) { st ->
            // 今日喝水量（目前先用今天的，后面你有周/月统计再扩展）
            binding.myAmount.text = "${st.intakeMl} ml today"

            // 完成百分比
            val progressPercent = if (st.goalMl <= 0) {
                0
            } else {
                ((st.intakeMl.toDouble() / st.goalMl) * 100)
                    .roundToInt()
                    .coerceIn(0, 100)
            }
            binding.myPercent.text = "$progressPercent%"

            // 暂时写死自己是 #1（等有好友数据再按百分比排序）
            binding.myRankNumber.text = "#1"
        }

        // rankingList 先空着，后面接好友排行榜
        // TODO: 根据 currentRange 加载对应时间范围的好友排行
    }

    private fun setupRangeTabs() {
        binding.tabToday.setOnClickListener {
            if (currentRange != Range.TODAY) {
                currentRange = Range.TODAY
                updateRangeUi()
            }
        }
        binding.tab7Days.setOnClickListener {
            if (currentRange != Range.WEEK) {
                currentRange = Range.WEEK
                updateRangeUi()
            }
        }
        binding.tab30Days.setOnClickListener {
            if (currentRange != Range.MONTH) {
                currentRange = Range.MONTH
                updateRangeUi()
            }
        }
    }

    /** 更新 tab 样式 + 顶部文案
     *  目前只做 UI，实际数据之后在这里根据 currentRange 去查询即可
     */
    private fun updateRangeUi() {
        fun styleTab(tv: TextView, selected: Boolean) {
            tv.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            val colorRes = if (selected) android.R.color.black else android.R.color.darker_gray
            tv.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        }

        styleTab(binding.tabToday, currentRange == Range.TODAY)
        styleTab(binding.tab7Days, currentRange == Range.WEEK)
        styleTab(binding.tab30Days, currentRange == Range.MONTH)

        // 顶部副标题，先改文案占位，将来你可以换成 string 资源
        binding.rankingSubtitle.text = when (currentRange) {
            Range.TODAY -> "Today · Compare your water intake with friends"
            Range.WEEK -> "Last 7 days · Compare your water intake with friends"
            Range.MONTH -> "Last 30 days · Compare your water intake with friends"
        }

        // TODO 这里将来根据 currentRange 去刷新排行榜列表数据
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
