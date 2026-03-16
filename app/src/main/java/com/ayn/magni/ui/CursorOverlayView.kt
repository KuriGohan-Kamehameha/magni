package com.ayn.magni.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ayn.magni.R
import com.ayn.magni.data.CursorShape

class CursorOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_orange)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_orange_soft)
        style = Paint.Style.FILL
    }

    private var normalizedX = 0.5f
    private var normalizedY = 0.5f
    private var visible = false
    private var cursorShape = CursorShape.CROSSHAIR

    fun setCursorShape(shape: CursorShape) {
        cursorShape = shape
        invalidate()
    }

    fun applyThemeColors(accentColor: Int, accentSoftColor: Int) {
        cursorPaint.color = accentColor
        fillPaint.color = accentSoftColor
        invalidate()
    }

    fun setCursorPosition(x: Float, y: Float) {
        normalizedX = x.coerceIn(0f, 1f)
        normalizedY = y.coerceIn(0f, 1f)
        visible = true
        invalidate()
    }

    fun hideCursor() {
        visible = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visible) return

        val width = width.toFloat().coerceAtLeast(1f)
        val height = height.toFloat().coerceAtLeast(1f)
        val x = normalizedX * width
        val y = normalizedY * height
        val radius = dp(8f)

        when (cursorShape) {
            CursorShape.CROSSHAIR -> {
                canvas.drawCircle(x, y, radius * 0.52f, fillPaint)
                canvas.drawCircle(x, y, radius, cursorPaint)
                canvas.drawLine(x - radius * 1.6f, y, x + radius * 1.6f, y, cursorPaint)
                canvas.drawLine(x, y - radius * 1.6f, x, y + radius * 1.6f, cursorPaint)
            }

            CursorShape.DOT -> {
                canvas.drawCircle(x, y, radius * 0.92f, cursorPaint)
                canvas.drawCircle(x, y, radius * 0.52f, fillPaint)
            }

            CursorShape.RING -> {
                canvas.drawCircle(x, y, radius, cursorPaint)
                canvas.drawCircle(x, y, radius * 0.64f, cursorPaint)
                canvas.drawCircle(x, y, radius * 0.2f, fillPaint)
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
