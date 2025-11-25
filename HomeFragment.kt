package com.davidwxcui.waterwise.ui.home

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.DrinkLog
import com.davidwxcui.waterwise.data.DrinkType
import com.davidwxcui.waterwise.databinding.FragmentHomeBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import android.content.Intent
import com.davidwxcui.waterwise.minigame.GameActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by viewModels()
    private lateinit var timelineAdapter: TimelineAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun nowHour(): Int {
        return if (Build.VERSION.SDK_INT >= 26) {
            LocalDateTime.now().hour
        } else {
            java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dateText = if (Build.VERSION.SDK_INT >= 26) {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        } else {
            java.text.SimpleDateFormat(
                "EEE, MMM d",
                java.util.Locale.US
            ).format(java.util.Date())
        }
        binding.topTitle.text = getString(R.string.today_title, dateText)

        vm.uiState.observe(viewLifecycleOwner) { st ->
            // 进度环
            binding.progressRing.set(
                st.intakeMl.toFloat(),
                st.goalMl.toFloat(),
                st.overLimit
            )

            // 百分比文字
            val progressPercent =
                ((st.intakeMl.toDouble() / st.goalMl) * 100).roundToInt()
            binding.circularProgressPercent.text =
                String.format(Locale.US, "%d%%", progressPercent)

            // 文本信息
            binding.progressMain.text =
                getString(R.string.progress_main, st.intakeMl, st.goalMl)
            binding.progressSub.text =
                getString(R.string.progress_effective, st.effectiveMl)
            binding.remainingText.text = when {
                st.intakeMl > st.goalMl ->
                    getString(R.string.over_by, (st.intakeMl - st.goalMl))
                st.intakeMl == 0 ->
                    getString(R.string.no_drinks_yet)
                else ->
                    getString(R.string.remaining_ml, (st.goalMl - st.intakeMl))
            }

            // 线性进度条
            binding.ProgressBarValue.progress = progressPercent
            binding.ProgressBarValue.setProgress(progressPercent, true)
        }

        fun bindQuick(v: View, type: DrinkType) {
            v.setOnClickListener { showQuantityDialog(type) }
            v.contentDescription = type.displayName
        }

        bindQuick(binding.btnWater, DrinkType.Water)
        bindQuick(binding.btnTea, DrinkType.Tea)
        bindQuick(binding.btnCoffee, DrinkType.Coffee)
        bindQuick(binding.btnJuice, DrinkType.Juice)
        bindQuick(binding.btnSoda, DrinkType.Soda)
        bindQuick(binding.btnMilk, DrinkType.Milk)
        bindQuick(binding.btnYogurt, DrinkType.Yogurt)
        bindQuick(binding.btnAlcohol, DrinkType.Alcohol)
        bindQuick(binding.btnSparkling, DrinkType.Sparkling)

        // 智能建议卡片
        vm.uiState.observe(viewLifecycleOwner) { st ->
            val hour = nowHour()
            binding.insightCard.isVisible = true
            binding.insightWhy.setOnClickListener { showWhyDialog() }
            binding.insightText.text = when {
                hour >= 21 ->
                    getString(R.string.insight_late_small_sips)
                st.caffeineRatio > 0.4 ->
                    getString(R.string.insight_high_caffeine)
                else ->
                    getString(R.string.insight_suggest_now, vm.recommendNextMl())
            }
        }

        // 重要日子卡片
        vm.uiState.observe(viewLifecycleOwner) { st ->
            if (st.importantEvent != null &&
                st.importantEvent.daysToEvent in 0..7
            ) {
                binding.eventCard.isVisible = true
                binding.eventTitle.text = getString(
                    R.string.event_title_fmt,
                    st.importantEvent.name,
                    st.importantEvent.daysToEvent
                )
                binding.eventTip.text = st.importantEvent.todayTip
            } else {
                binding.eventCard.isVisible = false
            }
        }

        // 今日时间线（这里换成弹窗编辑）
        timelineAdapter = TimelineAdapter(
            onEdit = { log: DrinkLog -> showEditDialog(log) },
            onDelete = { log: DrinkLog -> vm.deleteDrink(log.id) }
        )
        binding.timelineList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = timelineAdapter
        }
        vm.timeline.observe(viewLifecycleOwner) { list ->
            val top5: List<DrinkLog> =
                if (list.size > 5) list.subList(0, 5) else list
            timelineAdapter.submitList(top5)
            binding.timelineEmpty.isVisible = list.isEmpty()
        }

        // 轻统计
        vm.summary.observe(viewLifecycleOwner) { s ->
            binding.donut.setData(
                s.waterRatio,
                s.caffeineRatio,
                s.sugaryRatio
            )
            binding.summaryText.text = getString(
                R.string.summary_text_fmt,
                (s.waterRatio * 100).toInt(),
                (s.caffeineRatio * 100).toInt(),
                (s.sugaryRatio * 100).toInt()
            )
        }

        binding.btnGame.setOnClickListener {
            startActivity(Intent(requireContext(), GameActivity::class.java))
        }
    }

    // FAB 调用：选择饮品类型
    fun showFabQuickAdd() {
        val types = DrinkType.values()
        val labels = types.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add))
            .setItems(labels) { _, which ->
                showQuantityDialog(types[which])
            }
            .show()
    }

    // 选择容量（新增）
    private fun showQuantityDialog(type: DrinkType) {
        val options = vm.defaultPortionsFor(type)
        val labels = (options.map { "${it} ml" } +
                listOf(
                    getString(R.string.same_as_last_time),
                    getString(R.string.custom_ellipsis)
                ))
            .toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_drink_title, type.displayName))
            .setItems(labels) { _, which ->
                when {
                    which < options.size ->
                        vm.addDrink(type, options[which])
                    labels[which] == getString(R.string.same_as_last_time) ->
                        vm.addSameAsLast(type)
                    else ->
                        showCustomInput(type)
                }
            }
            .show()
    }

    // 自定义容量输入（新增）
    private fun showCustomInput(type: DrinkType) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "e.g., 250"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.custom_amount_title, type.displayName))
            .setView(input)
            .setPositiveButton(R.string.add) { _, _ ->
                val v = input.text.toString().toIntOrNull()
                if (v != null && v > 0) {
                    val rounded = (v / 50) * 50
                    val finalValue = if (rounded == 0) 50 else rounded
                    vm.addDrink(type, finalValue)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(log: DrinkLog) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(log.volumeMl.toString())
            setSelection(text.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.custom_amount_title, log.type.displayName))
            .setView(input)
            .setPositiveButton(R.string.add) { _, _ ->
                val v = input.text.toString().toIntOrNull()
                if (v != null && v > 0) {
                    vm.updateDrinkVolume(log.id, v)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWhyDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.why_title)
            .setMessage(getString(R.string.why_message))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
