package com.davidwxcui.waterwise.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.anychart.AnyChart
import com.anychart.AnyChartView
import com.anychart.chart.common.dataentry.DataEntry
import com.anychart.charts.Cartesian
import com.anychart.enums.HoverMode
import com.anychart.enums.MarkerType
import com.anychart.enums.TooltipPositionMode
import com.anychart.scales.DateTime
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentDashboardBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // --- AnyChart ---
    private lateinit var chartView: AnyChartView
    private var cartesian: Cartesian? = null
    private val seriesByType = mutableMapOf<String, com.anychart.core.cartesian.series.Line>()

    // --- Data / Filters ---
    private var allRows: List<DrinkRow> = emptyList()
    private val selectedTypes: MutableSet<String> = linkedSetOf()

    // 时间区间（null 表示不限）
    private var rangeStart: Long? = null
    private var rangeEnd: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chartView = binding.anyChartView
        setupAnyChart()

        // 初次加载 CSV 并渲染
        refreshData()

        // 类型筛选
        binding.btnTypeFilter.setOnClickListener { showTypePickerDialog() }

        // 日期区间
        binding.btnDateRange.setOnClickListener { showDateRangePicker() }

        // Tabs
        showHistory(true)
        binding.btnHistory.setOnClickListener { showHistory(true) }
        binding.btnAnalyze.setOnClickListener { showHistory(false) }
        binding.btnGeneral.setOnClickListener { /* TODO */ }
        binding.btnAI.setOnClickListener { /* TODO */ }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    // ========================== Rendering / Filters ===========================

    /** 读取 CSV -> 初始化/校正筛选项 -> 统一渲染 */
    private fun refreshData() {
        allRows = readDrinkLog(File(requireContext().filesDir, "drinkLog.csv"))

        val allTypes = allRows.map { it.type }.distinct().sorted()
        if (selectedTypes.isEmpty()) {
            selectedTypes += allTypes
        } else {
            selectedTypes.retainAll(allTypes.toSet())
            if (selectedTypes.isEmpty()) selectedTypes += allTypes
        }
        applyFiltersAndRender()
    }

    /** 应用类型 + 时间区间过滤，并刷新图表与历史表 */
    private fun applyFiltersAndRender() {
        val filtered = allRows.filter { r ->
            val okType = selectedTypes.isEmpty() || r.type in selectedTypes
            val okStart = rangeStart?.let { r.timestamp >= it } ?: true
            val okEnd = rangeEnd?.let { r.timestamp <= it } ?: true
            okType && okStart && okEnd
        }

        // 切换显示：无数据 -> 只显示文字；有数据 -> 显示图表
        if (filtered.isEmpty()) {
            binding.anyChartView.visibility = View.GONE
            binding.tvEmptyChart.visibility = View.VISIBLE
        } else {
            binding.anyChartView.visibility = View.VISIBLE
            binding.tvEmptyChart.visibility = View.GONE
        }

        updateAnyChart(filtered)      // 仍然更新（有数据才会画线）
        updateHistoryTable(filtered)  // 同步 History 列表
        updateDateRangeButtonText()
    }


    // ============================== Dialogs ==================================

    /** 多选饮品类型 */
    private fun showTypePickerDialog() {
        val types = (if (allRows.isEmpty()) emptyList() else allRows.map { it.type }.distinct()).sorted()
        if (types.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select drink types")
                .setMessage("No records yet.\nPlease add some drinks first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        if (selectedTypes.isEmpty()) selectedTypes += types

        val checked = types.map { it in selectedTypes }.toBooleanArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select drink types")
            .setMultiChoiceItems(types.toTypedArray(), checked) { _, which, isChecked ->
                val t = types[which]
                if (isChecked) selectedTypes.add(t) else selectedTypes.remove(t)
            }
            .setPositiveButton("Apply") { _, _ -> applyFiltersAndRender() }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("All") { _, _ ->
                selectedTypes.clear(); selectedTypes += types
                applyFiltersAndRender()
            }
            .show()
    }

    /** 日期区间选择器（闭区间，end 包含到当天 23:59:59.999） */
    private fun showDateRangePicker() {
        val defaultStart = MaterialDatePicker.thisMonthInUtcMilliseconds()
        val defaultEnd = MaterialDatePicker.todayInUtcMilliseconds()

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select date range")
            .setSelection(
                androidx.core.util.Pair(
                    rangeStart ?: defaultStart,
                    rangeEnd ?: defaultEnd
                )
            )
            .build()

        picker.addOnPositiveButtonClickListener { pair ->
            rangeStart = pair.first
            rangeEnd = pair.second?.let { end ->
                val day = 24L * 60 * 60 * 1000
                (end / day) * day + day - 1
            }
            applyFiltersAndRender()
        }
        picker.show(parentFragmentManager, "date_range_picker")
    }

    private fun updateDateRangeButtonText() {
        val btn = binding.btnDateRange
        if (rangeStart == null && rangeEnd == null) {
            btn.text = "Date Range"
            return
        }
        val fmt = SimpleDateFormat("MM-dd", Locale.getDefault())
        val s = rangeStart?.let { fmt.format(Date(it)) } ?: "…"
        val e = rangeEnd?.let { fmt.format(Date(it)) } ?: "…"
        btn.text = "$s ~ $e"
    }

    // ============================== AnyChart =================================

    private fun setupAnyChart() {
        cartesian = AnyChart.line().apply {
            animation(true)
            padding(10.0, 20.0, 10.0, 20.0)

            yAxis(0).title("Volume(ml)")

            // X 轴：DateTime，刻度最小到小时
            val dt = DateTime.instantiate()
            dt.ticks().interval("1 hour")
            dt.minorTicks().interval("1 hour")
            xScale(dt)

            xScroller(false)
            xZoom().setToPointsCount(24, true, null)

            xAxis(0).labels().format("{%value}{dateTimeFormat:MM-dd HH}:00")
            tooltip().positionMode(TooltipPositionMode.POINT)
            tooltip().format("{%x}{dateTimeFormat:MM-dd HH}:00\n{%seriesName}: {%value} ml")

            interactivity().hoverMode(HoverMode.BY_X)
            legend().enabled(true)
            legend().fontSize(12.0)
        }
        chartView.setChart(cartesian)
    }

    /** 把过滤后的 rows 画成多折线（Y = volume_ml） */
    private fun updateAnyChart(rows: List<DrinkRow>) {
        val chart = cartesian ?: return

        // 先隐藏全部 series
        seriesByType.values.forEach { it.enabled(false) }

        // 按类型分组并按时间排序
        val grouped = rows.groupBy { it.type }.toSortedMap()

        grouped.forEach { (type, list) ->
            val data = list.sortedBy { it.timestamp }.map { r ->
                object : DataEntry() {
                    init {
                        setValue("x", r.timestamp)       // 毫秒时间戳
                        setValue("value", r.volumeMl)    // 用 volume_ml
                    }
                }
            }

            val series = seriesByType[type] ?: run {
                val s = chart.line(data).name(type)
                s.hovered().markers().enabled(true)
                s.hovered().markers().type(MarkerType.CIRCLE).size(3.0)
                seriesByType[type] = s
                s
            }
            series.data(data)
            series.enabled(true)
        }

        chartView.invalidate()
    }

    // =============================== CSV =====================================

    data class DrinkRow(
        val timestamp: Long,
        val type: String,
        val volumeMl: Int,
        val effectiveMl: Int,
        val note: String
    )

    /** 读取 filesDir/drinkLog.csv；无文件返回空列表 */
    private fun readDrinkLog(file: File): List<DrinkRow> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<DrinkRow>()
        BufferedReader(FileReader(file)).use { br ->
            var line = br.readLine()
            if (line != null && line.startsWith("timestamp")) line = br.readLine() // 跳过表头
            while (line != null) {
                parseCsv(line)?.let { out += it }
                line = br.readLine()
            }
        }
        return out
    }

    /** 解析一行 CSV，兼容 note 含逗号与引号 */
    private fun parseCsv(line: String): DrinkRow? {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        for (c in line) {
            when {
                c == '"' -> inQuote = !inQuote
                c == ',' && !inQuote -> { cells += sb.toString().trim(); sb.clear() }
                else -> sb.append(c)
            }
        }
        cells += sb.toString().trim()

        return try {
            val ts = cells.getOrNull(0)?.toLong() ?: return null
            val type = cells.getOrNull(1)?.trim()?.trim('"') ?: "Unknown"
            val vol = cells.getOrNull(2)?.toIntOrNull() ?: 0
            val eff = cells.getOrNull(3)?.toIntOrNull() ?: 0
            val note = cells.getOrNull(4)?.trim()?.trim('"') ?: ""
            DrinkRow(ts, type, vol, eff, note)
        } catch (_: Exception) {
            null
        }
    }

    // =========================== History rendering ===========================

    /** 用传入 rows 更新 History 表（按时间倒序；Intake 显示 volume_ml） */
    private fun updateHistoryTable(rows: List<DrinkRow>) {
        val table: TableLayout = binding.tableHistory
        // 清空旧数据（保留表头第 0 行）
        while (table.childCount > 1) table.removeViewAt(1)

        if (rows.isEmpty()) {
            val row = TableRow(requireContext())
            val tv = TextView(requireContext()).apply {
                text = "No records yet"
                setPadding(dp(12), dp(14), dp(12), dp(14))
            }
            row.addView(tv)
            table.addView(row)
            return
        }

        val padH = dp(12)
        val padV = dp(14)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        rows.sortedByDescending { it.timestamp }.forEach { r ->
            val row = TableRow(requireContext()).apply {
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(R.drawable.rounded_grey_bg)
            }
            row.addView(cell(fmt.format(Date(r.timestamp)), 1f, TextView.TEXT_ALIGNMENT_VIEW_START))
            row.addView(cell(r.type, 1f, TextView.TEXT_ALIGNMENT_CENTER))
            row.addView(cell("${r.volumeMl} ml", 1f, TextView.TEXT_ALIGNMENT_VIEW_END))
            table.addView(row)

            val spacer = View(requireContext())
            spacer.layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT, dp(8)
            )
            table.addView(spacer)
        }
    }

    // =============================== Utils ==================================

    private fun cell(text: String, weight: Float, gravity: Int): TextView =
        TextView(requireContext()).apply {
            this.text = text
            textAlignment = gravity
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weight)
        }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).roundToInt()

    private fun showHistory(show: Boolean) {
        binding.containerHistory.visibility = if (show) View.VISIBLE else View.GONE
        binding.containerAnalyze.visibility = if (show) View.GONE else View.VISIBLE
        binding.btnHistory.alpha = if (show) 1f else 0.6f
        binding.btnAnalyze.alpha = if (show) 0.6f else 1f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
