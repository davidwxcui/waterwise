package com.davidwxcui.waterwise.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SummaryDonutView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.BUTT
    }
    private val rect = RectF()
    private var waterRatio = 0.0
    private var caffeineRatio = 0.0
    private var sugaryRatio = 0.0

    fun setData(water: Double, caffeine: Double, sugary: Double) {
        waterRatio = water
        caffeineRatio = caffeine
        sugaryRatio = sugary
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val pad = 10f
        rect.set(pad, pad, size - pad, size - pad)

        var start = -90f
        val total = 360f

        paint.color = 0xFF2196F3.toInt() // Water
        canvas.drawArc(rect, start, (total * waterRatio).toFloat(), false, paint)
        start += (total * waterRatio).toFloat()

        paint.color = 0xFF9C27B0.toInt() // Caffeine
        canvas.drawArc(rect, start, (total * caffeineRatio).toFloat(), false, paint)
        start += (total * caffeineRatio).toFloat()

        paint.color = 0xFFFF9800.toInt() // Sugary
        canvas.drawArc(rect, start, (total * sugaryRatio).toFloat(), false, paint)
    }
}
