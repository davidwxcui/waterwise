package com.davidwxcui.waterwise.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class ProgressRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = 0xFFE0E0E0.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = 0xFF2196F3.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val overPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = 0xFFD32F2F.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val rect = RectF()
    private var progress = 0f
    private var goal = 1f
    private var overLimit = false

    fun set(progress: Float, goal: Float, overLimit: Boolean) {
        this.progress = progress
        this.goal = if (goal <= 0f) 1f else goal
        this.overLimit = overLimit
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val pad = 10f
        rect.set(pad, pad, size - pad, size - pad)

        canvas.drawArc(rect, 0f, 360f, false, bgPaint)

        val sweep = (progress / goal).coerceAtMost(1f) * 360f
        canvas.drawArc(rect, -90f, sweep, false, fgPaint)

        if (overLimit) {
            val thinPad = 4f
            rect.set(pad + thinPad, pad + thinPad, size - pad - thinPad, size - pad - thinPad)
            canvas.drawArc(rect, 0f, 360f, false, overPaint)
        }
    }
}
