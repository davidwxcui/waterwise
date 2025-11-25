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
import androidx.lifecycle.lifecycleScope
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentDashboardBinding
import com.davidwxcui.waterwise.ui.profile.FirebaseAuthRepository
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import lecho.lib.hellocharts.model.*
import lecho.lib.hellocharts.view.LineChartView
import lecho.lib.hellocharts.view.ColumnChartView
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

    private val db = FirebaseFirestore.getInstance()

    data class DrinkRow(
        val timestamp: Long,
        val type: String,
        val volumeMl: Int,
        val effectiveMl: Int,
        val note: String
    )

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
            "Yogurt" to 0xFF6D4C41.toInt(),
            "Alcohol" to 0xFFC2185B.toInt(),
            "Sparkling" to 0xFF546E7A.toInt()
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
                pointRadius = 5
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

        // Add touch listener for tooltips
        setupChartTooltip(rows, times, indexByTs)
    }

    private fun setupChartTooltip(rows: List<DrinkRow>, times: List<Long>, indexByTs: Map<Long, Int>) {
        lineChartView.setOnTouchListener { _, event ->
            val x = event.x
            val y = event.y

            val viewport = lineChartView.currentViewport
            val chart = lineChartView.chartComputator

            // Convert touch coordinates to chart coordinates
            val chartX = (x - chart.contentRectMinusAllMargins.left) / chart.contentRectMinusAllMargins.width() *
                    (viewport.right - viewport.left) + viewport.left
            val chartY = (chart.contentRectMinusAllMargins.bottom - y) / chart.contentRectMinusAllMargins.height() *
                    (viewport.top - viewport.bottom) + viewport.bottom

            // Find closest point within tolerance
            val tolerance = 1.0f
            var closestRow: DrinkRow? = null
            var minDistance = Float.MAX_VALUE

            rows.forEach { row ->
                val ts = if (row.timestamp < 1_000_000_000_000L) row.timestamp * 1000 else row.timestamp
                val pointX = indexByTs[ts]?.toFloat() ?: return@forEach
                val pointY = row.volumeMl.toFloat()

                val distance = kotlin.math.sqrt(((pointX - chartX) * (pointX - chartX) + (pointY - chartY) * (pointY - chartY)).toDouble()).toFloat()
                if (distance < tolerance && distance < minDistance) {
                    minDistance = distance
                    closestRow = row
                }
            }

            if (closestRow != null) {
                val tooltip = "${closestRow!!.type} - ${closestRow!!.volumeMl}ml"
                android.widget.Toast.makeText(requireContext(), tooltip, android.widget.Toast.LENGTH_SHORT).show()
            }

            false
        }
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
        val uid = FirebaseAuthRepository.currentUid()
        if (uid == null) {
            allRows = emptyList()
            binding.tvEmptyChart.visibility = View.VISIBLE
            binding.lineChart.visibility = View.GONE
            binding.columnChart.visibility = View.GONE
            binding.tableHistory.removeAllViews()
            return
        }

        // Fetch from Firestore
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snapshot = db.collection("users")
                    .document(uid)
                    .collection("drinkLogs")
                    .get()
                    .await()

                allRows = snapshot.documents.mapNotNull { doc ->
                    try {
                        val type = doc.getString("type") ?: "Unknown"
                        val volumeMl = (doc.getLong("volumeMl") ?: 0L).toInt()
                        val effectiveMl = (doc.getLong("effectiveMl") ?: 0L).toInt()
                        val timeMillis = doc.getLong("timeMillis") ?: 0L
                        val note = doc.getString("note") ?: ""

                        DrinkRow(
                            timestamp = timeMillis,
                            type = type,
                            volumeMl = volumeMl,
                            effectiveMl = effectiveMl,
                            note = note
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("DashboardFragment", "Parse error: ${e.message}")
                        null
                    }
                }

                val allTypes = allRows.map { it.type }.distinct().sorted()
                if (selectedTypes.isEmpty()) selectedTypes += allTypes
                else {
                    selectedTypes.retainAll(allTypes.toSet())
                    if (selectedTypes.isEmpty()) selectedTypes += allTypes
                }
                applyFiltersAndRender()
            } catch (e: Exception) {
                android.util.Log.e("DashboardFragment", "Failed to fetch drink logs", e)
                allRows = emptyList()
                binding.tvEmptyChart.visibility = View.VISIBLE
                binding.lineChart.visibility = View.GONE
            }
        }
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

    // ---------- History Table----------
    private fun updateHistoryTable(rows: List<DrinkRow>) {
        val table: TableLayout = binding.tableHistory
        table.removeAllViews()

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

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
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
                setTextColor(Color.BLACK)
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
        setTextColor(textColor)
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).roundToInt()
}