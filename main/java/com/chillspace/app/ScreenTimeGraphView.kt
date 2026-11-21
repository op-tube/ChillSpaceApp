package com.chillspace.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class ScreenTimeGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<Float> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1877F2.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x401877F2
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1877F2.toInt()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEF0F2.toInt()
        strokeWidth = 1f
    }

    fun setData(values: List<Float>) {
        data = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val padL = 12f; val padR = 12f; val padT = 12f; val padB = 12f
        val gw = w - padL - padR
        val gh = h - padT - padB
        val max = data.maxOrNull()?.coerceAtLeast(0.1f) ?: 1f

        // grid lines
        for (i in 0..4) {
            val y = padT + gh * (i / 4f)
            canvas.drawLine(padL, y, w - padR, y, gridPaint)
        }

        // build path
        val stepX = gw / (data.size - 1).coerceAtLeast(1)
        val points = data.mapIndexed { i, v ->
            val x = padL + i * stepX
            val y = padT + gh - (v / max) * gh
            x to y
        }

        // fill
        val fillPath = Path().apply {
            moveTo(points.first().first, padT + gh)
            points.forEach { lineTo(it.first, it.second) }
            lineTo(points.last().first, padT + gh)
            close()
        }
        canvas.drawPath(fillPath, fillPaint)

        // line
        val linePath = Path().apply {
            moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { lineTo(it.first, it.second) }
        }
        canvas.drawPath(linePath, linePaint)

        // dots
        points.forEachIndexed { i, (x, y) ->
            canvas.drawCircle(x, y, if (i == points.lastIndex) 6f else 4f, dotPaint)
        }
    }
}