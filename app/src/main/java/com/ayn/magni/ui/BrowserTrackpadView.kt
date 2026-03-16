package com.ayn.magni.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.ayn.magni.R
import com.ayn.magni.sync.BrowserViewport
import com.ayn.magni.trackpad.TrackpadGestureEngine
import kotlin.math.min

class BrowserTrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val uiHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

    private val padRect = RectF()
    private val miniMapRect = RectF()
    private val miniViewportRect = RectF()

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_map_empty)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_map_grid)
        strokeWidth = dp(1f)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_orange)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val accentSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_orange_soft)
        style = Paint.Style.FILL
    }
    private val cursorCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_orange)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_text)
        textSize = dp(11f)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_muted)
        textSize = dp(9f)
    }

    private var viewport: BrowserViewport? = null

    var onTapRequested: ((Float, Float) -> Unit)? = null
    var onLongPressRequested: ((Float, Float) -> Unit)? = null
    var onScrollRequested: ((Float, Float) -> Unit)? = null
    var onZoomRequested: ((Int) -> Unit)? = null
    var onCursorMoved: ((Float, Float) -> Unit)? = null

    private val longPressRunnable = Runnable {
        gestureEngine.triggerLongPressIfEligible()
    }

    private val gestureEngine: TrackpadGestureEngine by lazy {
        TrackpadGestureEngine(
            touchSlop = touchSlop,
            longPressTimeoutMs = longPressTimeoutMs,
            isWithinTrackpadBounds = { x, y -> padRect.contains(x, y) },
            trackpadWidthPx = { padRect.width() },
            trackpadHeightPx = { padRect.height() },
            cursorScaleDistancePx = { dp(42f) },
            onTapRequested = { x, y -> onTapRequested?.invoke(x, y) },
            onLongPressRequested = { x, y -> onLongPressRequested?.invoke(x, y) },
            onScrollRequested = { deltaX, deltaY -> onScrollRequested?.invoke(deltaX, deltaY) },
            onZoomRequested = { delta -> onZoomRequested?.invoke(delta) },
            onCursorMoved = { x, y -> onCursorMoved?.invoke(x, y) },
            onRequestDisallowIntercept = { parent?.requestDisallowInterceptTouchEvent(true) },
            onScheduleLongPress = { delayMs -> uiHandler.postDelayed(longPressRunnable, delayMs) },
            onCancelLongPress = { uiHandler.removeCallbacks(longPressRunnable) },
            onTapHaptic = { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
            onLongPressHaptic = { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
            onMultiTouchHaptic = { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) },
            onZoomStepHaptic = { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            onInvalidate = { invalidate() }
        )
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.accessibility_trackpad)
    }

    fun renderState(viewport: BrowserViewport?) {
        this.viewport = viewport
        invalidate()
    }

    fun applyThemeColors(
        surfaceColor: Int,
        gridColor: Int,
        accentColor: Int,
        accentSoftColor: Int,
        textColor: Int,
        mutedTextColor: Int
    ) {
        surfacePaint.color = surfaceColor
        gridPaint.color = gridColor
        accentPaint.color = accentColor
        cursorCenterPaint.color = accentColor
        accentSoftPaint.color = accentSoftColor
        labelPaint.color = textColor
        hintPaint.color = mutedTextColor
        invalidate()
    }

    fun resetCursor() {
        gestureEngine.resetCursor()
    }

    override fun onDetachedFromWindow() {
        uiHandler.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        padRect.set(
            paddingLeft + dp(6f),
            paddingTop + dp(6f),
            width - paddingRight - dp(6f),
            height - paddingBottom - dp(6f)
        )
        if (padRect.width() <= 0f || padRect.height() <= 0f) {
            return
        }

        canvas.drawRoundRect(padRect, dp(14f), dp(14f), surfacePaint)
        drawTrackpadGrid(canvas)
        if (gestureEngine.showViewportPreview()) {
            drawMiniMap(canvas)
        }
        drawLabels(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureEngine.onTouchEvent(event)
        if (!handled) {
            return super.onTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun drawTrackpadGrid(canvas: Canvas) {
        val columns = 4
        val rows = 3
        for (column in 1 until columns) {
            val x = padRect.left + (padRect.width() * column / columns)
            canvas.drawLine(x, padRect.top, x, padRect.bottom, gridPaint)
        }
        for (row in 1 until rows) {
            val y = padRect.top + (padRect.height() * row / rows)
            canvas.drawLine(padRect.left, y, padRect.right, y, gridPaint)
        }
        canvas.drawLine(padRect.left, padRect.top, padRect.right, padRect.bottom, gridPaint)
        canvas.drawLine(padRect.right, padRect.top, padRect.left, padRect.bottom, gridPaint)
    }

    private fun drawMiniMap(canvas: Canvas) {
        val currentViewport = viewport ?: return
        val miniMapWidth = min(padRect.width() * 0.24f, dp(112f))
        val miniMapHeight = min(padRect.height() * 0.18f, dp(72f))
        miniMapRect.set(
            padRect.right - miniMapWidth - dp(14f),
            padRect.top + dp(14f),
            padRect.right - dp(14f),
            padRect.top + dp(14f) + miniMapHeight
        )
        canvas.drawRoundRect(miniMapRect, dp(8f), dp(8f), accentSoftPaint)
        canvas.drawRoundRect(miniMapRect, dp(8f), dp(8f), accentPaint)

        val pageWidth = currentViewport.pageWidth.toFloat().coerceAtLeast(1f)
        val pageHeight = currentViewport.pageHeight.toFloat().coerceAtLeast(1f)
        val viewportLeft = (currentViewport.scrollX / pageWidth).coerceIn(0f, 1f)
        val viewportTop = (currentViewport.scrollY / pageHeight).coerceIn(0f, 1f)
        val viewportWidth = (currentViewport.viewportWidth / pageWidth).coerceIn(0.04f, 1f)
        val viewportHeight = (currentViewport.viewportHeight / pageHeight).coerceIn(0.04f, 1f)
        miniViewportRect.set(
            miniMapRect.left + miniMapRect.width() * viewportLeft,
            miniMapRect.top + miniMapRect.height() * viewportTop,
            miniMapRect.left + miniMapRect.width() * min(1f, viewportLeft + viewportWidth),
            miniMapRect.top + miniMapRect.height() * min(1f, viewportTop + viewportHeight)
        )
        canvas.drawRoundRect(miniViewportRect, dp(4f), dp(4f), cursorCenterPaint)
    }

    private fun drawLabels(canvas: Canvas) {
        val left = padRect.left + dp(14f)
        val bottom = padRect.bottom - dp(18f)
        canvas.drawText(context.getString(R.string.trackpad_primary_hint), left, bottom - dp(14f), labelPaint)
        canvas.drawText(context.getString(R.string.trackpad_secondary_hint), left, bottom, hintPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
