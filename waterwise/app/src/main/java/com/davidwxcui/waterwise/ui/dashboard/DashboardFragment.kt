package com.davidwxcui.waterwise.ui.dashboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.view.updatePadding
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentDashboardBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import lecho.lib.hellocharts.model.*
import lecho.lib.hellocharts.view.LineChartView
import lecho.lib.hellocharts.view.ColumnChartView
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Charts
    private lateinit var lineChartView: LineChartView
    private var lineChartData: LineChartData = LineChartData()

    private lateinit var columnChartView: ColumnChartView
    private var columnChartData: ColumnChartData = ColumnChartData()

    // Data / Filters
    private var allRows: List<DrinkRow> = emptyList()
    private val selectedTypes: MutableSet<String> = linkedSetOf()
    private var rangeStart: Long? = null
    private var rangeEnd: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Views
        columnChartView = binding.columnChart
        lineChartView = binding.lineChart

        setupBarChart()
        setupLineChart()

        refreshData()

        // actions
        binding.btnTypeFilter.setOnClickListener { showTypePickerDialog() }
        binding.btnDateRange.setOnClickListener { showDateRangePicker() }

        showHistory(true)
        binding.btnHistory.setOnClickListener { showHistory(true) }
        binding.btnAnalyze.setOnClickListener { showHistory(false) }

        binding.rootScroll.clipToPadding = false
        binding.rootScroll.updatePadding(
            bottom = resources.getDimensionPixelSize(
                com.google.android.material.R.dimen.design_bottom_navigation_height
            )
        )
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- 固定颜色：图表 & 历史共用 ----------
    private fun colorForType(type: String): Int {
        val preset = mapOf(
            "Water" to 0xFF1E88E5.toInt(),
            "Tea" to 0xFF43A047.toInt(),
            "Coffee" to 0xFF8E24AA.toInt(),
            "Juice" to 0xFFFB8C00.toInt(),
            "Soda" to 0xFFE53935.toInt(),
            "Milk" to 0xFF00897B.toInt(),
            "Beer" to 0xFF6D4C41.toInt(),
            "Wine" to 0xFFC2185B.toInt(),
            "Other" to 0xFF546E7A.toInt()
        )
        preset[type]?.let { return it }

        val h = (type.lowercase(Locale.getDefault()).hashCode() and 0x7fffffff) % 360
        return hslToColor(h.toFloat(), 0.65f, 0.55f)
    }

    private fun hslToColor(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = l - c / 2
        val (r1, g1, b1) = when {
            h < 60 -> floatArrayOf(c, x, 0f)
            h < 120 -> floatArrayOf(x, c, 0f)
            h < 180 -> floatArrayOf(0f, c, x)
            h < 240 -> floatArrayOf(0f, x, c)
            h < 300 -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        val r = ((r1 + m) * 255).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun isColorDark(color: Int): Boolean {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        val srgb = { c: Double -> if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
        val lum = 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)
        return lum < 0.5
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    // ---------- Charts setup ----------
    private fun setupLineChart() {
        lineChartView.apply {
            isInteractive = true
            isZoomEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            setViewportCalculationEnabled(false)
        }
    }

    private fun setupBarChart() {
        columnChartData = ColumnChartData().apply {
            axisXBottom = Axis().apply {
                textColor = Color.DKGRAY
            }
            axisYLeft = Axis().apply {
                name = "ml"
                textColor = Color.DKGRAY
                setHasLines(true)
            }
        }
        columnChartView.columnChartData = columnChartData
        columnChartView.isZoomEnabled = false
        columnChartView.isInteractive = true
        columnChartView.setBackgroundColor(Color.TRANSPARENT)
        columnChartView.setViewportCalculationEnabled(false)
    }

    /** 折线图 */
    private fun updateHelloChart(rows: List<DrinkRow>) {
        if (rows.isEmpty()) {
            binding.lineChart.visibility = View.GONE
            binding.tvEmptyChart.visibility = View.VISIBLE
            lineChartData.lines = emptyList()
            lineChartView.lineChartData = lineChartData
            return
        } else {
            binding.lineChart.visibility = View.VISIBLE
            binding.tvEmptyChart.visibility = View.GONE
        }

        val fmt = SimpleDateFormat("MM-dd HH", Locale.getDefault())
        val times = rows.map { if (it.timestamp < 1_000_000_000_000L) it.timestamp * 1000 else it.timestamp }
            .distinct().sorted()
        if (times.isEmpty()) {
            binding.lineChart.visibility = View.GONE
            binding.tvEmptyChart.visibility = View.VISIBLE
            return
        }

        val indexByTs = times.mapIndexed { i, ts -> ts to i }.toMap()
        val axisValues = times.mapIndexed { i, ts -> AxisValue(i.toFloat()).setLabel(fmt.format(Date(ts))) }

        val chartLines = mutableListOf<Line>()
        rows.groupBy { it.type }.toSortedMap().forEach { (type, list) ->
            val pts = list.sortedBy { it.timestamp }.map { r ->
                val ts = if (r.timestamp < 1_000_000_000_000L) r.timestamp * 1000 else r.timestamp
                PointValue(indexByTs[ts]!!.toFloat(), r.volumeMl.toFloat())
            }
            chartLines += Line(pts).apply {
                color = colorForType(type)
                strokeWidth = 2
                setCubic(false)
                setHasPoints(true)
                setHasLines(true)
            }
        }

        lineChartData = LineChartData().apply {
            lines = chartLines
            axisXBottom = Axis(axisValues).apply {
                setHasTiltedLabels(true)
                textColor = Color.DKGRAY
                maxLabelChars = 8
            }
            axisYLeft = Axis().apply {
                name = "ml"
                textColor = Color.DKGRAY
                setHasLines(true)
            }
        }
        lineChartView.lineChartData = lineChartData

        val maxX = (times.size - 1).coerceAtLeast(1)
        val maxY = (rows.maxOfOrNull { it.volumeMl } ?: 0).coerceAtLeast(1)
        val vp = Viewport().apply {
            left = -0.5f
            right = maxX + 0.5f
            bottom = 0f
            top = (maxY * 1.2f)
        }
        lineChartView.maximumViewport = vp
        lineChartView.currentViewport = vp
        lineChartView.invalidate()
    }

    /** 柱状图 */
    private fun updateBarChartDaily(rows: List<DrinkRow>) {
        // 统计一周 7 天总量（受 type 和日期过滤影响）
        val sums = FloatArray(7) { 0f }
        val cal = Calendar.getInstance()
        rows.forEach { r ->
            val ts = if (r.timestamp < 1_000_000_000_000L) r.timestamp * 1000 else r.timestamp
            cal.timeInMillis = ts
            val dow = cal.get(Calendar.DAY_OF_WEEK) // SUNDAY=1..SATURDAY=7
            val idx = dow - 1
            sums[idx] += r.volumeMl.toFloat()
        }

        val labels = arrayOf("SUN","MON","TUE","WED","THU","FRI","SAT")
        val columns = ArrayList<Column>(7)
        val axisX = ArrayList<AxisValue>(7)
        var maxY = 0f

        for (i in 0 until 7) {
            val v = sums[i]
            maxY = maxOf(maxY, v)
            val sub = SubcolumnValue(v, 0xFF03A9F4.toInt())
            columns += Column(listOf(sub)).apply {
                setHasLabels(false)
            }
            axisX += AxisValue(i.toFloat()).setLabel(labels[i])
        }

        columnChartData = ColumnChartData(columns).apply {
            axisXBottom = Axis(axisX).apply {
                textColor = Color.DKGRAY
            }
            axisYLeft = Axis().apply {
                name = "ml"
                textColor = Color.DKGRAY
                setHasLines(true)
            }
        }
        columnChartView.columnChartData = columnChartData

        val top = if (maxY <= 0f) 100f else maxY * 1.2f
        val vp = Viewport().apply {
            left = -0.5f
            right = 6.5f
            bottom = 0f
            this.top = top
        }
        columnChartView.maximumViewport = vp
        columnChartView.currentViewport = vp
        columnChartView.invalidate()
    }

    // ---------- Filtering / Data glue ----------
    private fun refreshData() {
        allRows = readDrinkLog(File(requireContext().filesDir, "drinkLog.csv"))

        val allTypes = allRows.map { it.type }.distinct().sorted()
        if (selectedTypes.isEmpty()) selectedTypes += allTypes
        else {
            selectedTypes.retainAll(allTypes.toSet())
            if (selectedTypes.isEmpty()) selectedTypes += allTypes
        }
        applyFiltersAndRender()
    }

    private fun applyFiltersAndRender() {
        val filtered = allRows.filter { r ->
            val okType = selectedTypes.isEmpty() || r.type in selectedTypes
            val okStart = rangeStart?.let { r.timestamp >= it } ?: true
            val okEnd = rangeEnd?.let { r.timestamp <= it } ?: true
            okType && okStart && okEnd
        }

        updateBarChartDaily(filtered)
        updateHelloChart(filtered)
        updateHistoryTable(filtered)
        updateDateRangeButtonText()
    }

    private fun showTypePickerDialog() {
        val types = allRows.map { it.type }.distinct().sorted()
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
            .setMultiChoiceItems(types.toTypedArray(), checked) { _, i, isChecked ->
                val t = types[i]
                if (isChecked) selectedTypes.add(t) else selectedTypes.remove(t)
            }
            .setPositiveButton("Apply") { _, _ -> applyFiltersAndRender() }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("All") { _, _ ->
                selectedTypes.clear(); selectedTypes += types; applyFiltersAndRender()
            }
            .show()
    }

    private fun showDateRangePicker() {
        val defaultStart = MaterialDatePicker.thisMonthInUtcMilliseconds()
        val defaultEnd = MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select date range")
            .setSelection(androidx.core.util.Pair(rangeStart ?: defaultStart, rangeEnd ?: defaultEnd))
            .build()
        picker.addOnPositiveButtonClickListener { pair ->
            rangeStart = pair.first
            val day = 24L * 60 * 60 * 1000
            rangeEnd = pair.second?.let { end -> (end / day) * day + day - 1 }
            applyFiltersAndRender()
        }
        picker.show(parentFragmentManager, "date_range_picker")
    }

    private fun updateDateRangeButtonText() {
        val btn = binding.btnDateRange
        if (rangeStart == null && rangeEnd == null) { btn.text = "Date Range"; return }
        val fmt = SimpleDateFormat("MM-dd", Locale.getDefault())
        val s = rangeStart?.let { fmt.format(Date(it)) } ?: "…"
        val e = rangeEnd?.let { fmt.format(Date(it)) } ?: "…"
        btn.text = "$s ~ $e"
    }

    // ---------- CSV ----------
    data class DrinkRow(
        val timestamp: Long,
        val type: String,
        val volumeMl: Int,
        val effectiveMl: Int,
        val note: String
    )

    private fun readDrinkLog(file: File): List<DrinkRow> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<DrinkRow>()
        BufferedReader(FileReader(file)).use { br ->
            var line = br.readLine()
            if (line != null && line.startsWith("timestamp")) line = br.readLine()
            while (line != null) {
                parseCsv(line)?.let { out += it }
                line = br.readLine()
            }
        }
        return out
    }

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
            var ts = cells.getOrNull(0)?.toLong() ?: return null
            if (ts < 1_000_000_000_000L) ts *= 1000
            val type = cells.getOrNull(1)?.trim()?.trim('"') ?: "Unknown"
            val vol  = cells.getOrNull(2)?.toIntOrNull() ?: 0
            val eff  = cells.getOrNull(3)?.toIntOrNull() ?: 0
            val note = cells.getOrNull(4)?.trim()?.trim('"') ?: ""
            DrinkRow(ts, type, vol, eff, note)
        } catch (_: Exception) { null }
    }

    // ---------- History Table----------
    private fun updateHistoryTable(rows: List<DrinkRow>) {
        val table: TableLayout = binding.tableHistory
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

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val padH = dp(12); val padV = dp(14)

        rows.sortedByDescending { it.timestamp }.forEach { r ->
            val tr = TableRow(requireContext()).apply {
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(R.drawable.rounded_grey_bg)
            }
            tr.addView(cell(fmt.format(Date(r.timestamp)), 1f, TextView.TEXT_ALIGNMENT_VIEW_START))
            val typeColor = colorForType(r.type)
            val typeBadge = TextView(requireContext()).apply {
                text = r.type
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(Color.BLACK) // 你之前要的“方案3：黑色文字”
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(withAlpha(typeColor, 0.18f))
                    setStroke(dp(1), typeColor)
                }
            }
            tr.addView(typeBadge)
            tr.addView(cell("${r.volumeMl} ml", 1f, TextView.TEXT_ALIGNMENT_VIEW_END))
            table.addView(tr)

            val spacer = View(requireContext())
            spacer.layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT, dp(8)
            )
            table.addView(spacer)
        }
    }

    // ---------- small utils ----------
    private fun cell(
        text: String,
        weight: Float,
        gravity: Int,
        @androidx.annotation.ColorInt textColor: Int = Color.BLACK
    ): TextView = TextView(requireContext()).apply {
        this.text = text
        textAlignment = gravity
        layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, weight)
        setTextColor(textColor) // 默认黑色
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).roundToInt()

    private fun showHistory(show: Boolean) {
        binding.containerHistory.visibility = if (show) View.VISIBLE else View.GONE
        binding.containerAnalyze.visibility = if (show) View.GONE else View.VISIBLE
        binding.btnHistory.alpha = if (show) 1f else 0.6f
        binding.btnAnalyze.alpha = if (show) 0.6f else 1f
    }
}
