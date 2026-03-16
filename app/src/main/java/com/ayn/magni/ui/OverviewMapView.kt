package com.ayn.magni.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.ayn.magni.R
import com.ayn.magni.sync.BrowserViewport
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Custom map view showing page overview with pinch-to-zoom support.
 * NASA standard: includes accessibility support for screen readers.
 */
class OverviewMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val mapBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_map_empty)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_map_grid)
        strokeWidth = dp(1f)
    }
    private val viewportFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_orange_soft)
        style = Paint.Style.FILL
    }
    private val viewportStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.retro_orange)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val snapshotPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }

    private val mapRect = RectF()
    private val visibleWindowNorm = RectF(0f, 0f, 1f, 1f)
    private val bitmapSrcRect = Rect()
    private val drawRect = RectF()
    private val viewportRect = RectF()
    private val mapInsetPx = dp(6f)
    private val mapCornerRadiusPx = dp(8f)
    private val minViewportSizePx = dp(10f)

    private var snapshot: Bitmap? = null
    private var snapshotPageWidth: Int = 1
    private var snapshotPageHeight: Int = 1
    private var viewport: BrowserViewport? = null
    private var overviewZoom: Float = MIN_OVERVIEW_ZOOM
    private var overviewCenterXNorm: Float = 0.5f
    private var overviewCenterYNorm: Float = 0.5f
    private var multiTouchActive = false
    private var hasLastScaleFocus = false
    private var lastScaleFocusX = 0f
    private var lastScaleFocusY = 0f
    private var overlayMode = false

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                val canScale = mapRect.contains(detector.focusX, detector.focusY)
                if (canScale) {
                    multiTouchActive = true
                    hasLastScaleFocus = true
                    lastScaleFocusX = detector.focusX
                    lastScaleFocusY = detector.focusY
                }
                return canScale
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor.takeIf { it.isFinite() } ?: return false
                val oldZoom = overviewZoom
                val newZoom = (oldZoom * scaleFactor).coerceIn(MIN_OVERVIEW_ZOOM, MAX_OVERVIEW_ZOOM)
                if (!hasLastScaleFocus) {
                    hasLastScaleFocus = true
                    lastScaleFocusX = detector.focusX
                    lastScaleFocusY = detector.focusY
                }

                val anchorBefore = screenToPageNormalized(lastScaleFocusX, lastScaleFocusY)
                overviewZoom = newZoom
                updateVisibleWindow()

                if (anchorBefore != null) {
                    val anchorAfter = screenToPageNormalized(detector.focusX, detector.focusY)
                    if (anchorAfter != null) {
                        overviewCenterXNorm += anchorBefore.x - anchorAfter.x
                        overviewCenterYNorm += anchorBefore.y - anchorAfter.y
                        overviewCenterXNorm = overviewCenterXNorm.coerceIn(0f, 1f)
                        overviewCenterYNorm = overviewCenterYNorm.coerceIn(0f, 1f)
                    }
                }

                updateVisibleWindow()
                lastScaleFocusX = detector.focusX
                lastScaleFocusY = detector.focusY
                if (newZoom != oldZoom) {
                    onOverviewZoomChanged?.invoke(newZoom)
                }
                invalidate()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                multiTouchActive = false
                hasLastScaleFocus = false
            }
        }
    )

    var onPositionRequested: ((Int, Int) -> Unit)? = null
    var onOverviewZoomChanged: ((Float) -> Unit)? = null

    fun setOverlayMode(enabled: Boolean) {
        if (overlayMode == enabled) {
            return
        }
        overlayMode = enabled
        invalidate()
    }

    fun currentOverviewZoom(): Float = overviewZoom

    fun resetOverviewZoom() {
        overviewZoom = MIN_OVERVIEW_ZOOM
        val currentViewport = viewport
        if (currentViewport == null) {
            overviewCenterXNorm = 0.5f
            overviewCenterYNorm = 0.5f
        } else {
            val pageWidth = max(snapshotPageWidth, currentViewport.pageWidth).toFloat().coerceAtLeast(1f)
            val pageHeight = max(snapshotPageHeight, currentViewport.pageHeight).toFloat().coerceAtLeast(1f)
            overviewCenterXNorm = ((currentViewport.scrollX + currentViewport.viewportWidth / 2f) / pageWidth)
                .coerceIn(0f, 1f)
            overviewCenterYNorm = ((currentViewport.scrollY + currentViewport.viewportHeight / 2f) / pageHeight)
                .coerceIn(0f, 1f)
        }
        updateVisibleWindow()
        onOverviewZoomChanged?.invoke(overviewZoom)
        invalidate()
    }

    fun applyThemeColors(
        mapBackgroundColor: Int,
        mapGridColor: Int,
        accentColor: Int,
        accentSoftColor: Int
    ) {
        mapBackgroundPaint.color = mapBackgroundColor
        gridPaint.color = mapGridColor
        viewportStrokePaint.color = accentColor
        viewportFillPaint.color = accentSoftColor
        invalidate()
    }

    fun renderState(
        snapshot: Bitmap?,
        snapshotPageWidth: Int,
        snapshotPageHeight: Int,
        viewport: BrowserViewport?
    ) {
        this.snapshot = if (snapshot?.isRecycled == true) null else snapshot
        this.snapshotPageWidth = snapshotPageWidth.coerceAtLeast(1)
        this.snapshotPageHeight = snapshotPageHeight.coerceAtLeast(1)
        this.viewport = viewport
        if (!isScalingActive()) {
            keepViewportVisible(viewport)
        } else {
            updateVisibleWindow()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawRect.set(
            paddingLeft + mapInsetPx,
            paddingTop + mapInsetPx,
            width - paddingRight - mapInsetPx,
            height - paddingBottom - mapInsetPx
        )
        if (drawRect.width() <= 0f || drawRect.height() <= 0f) {
            return
        }

        if (!overlayMode) {
            canvas.drawRoundRect(drawRect, mapCornerRadiusPx, mapCornerRadiusPx, mapBackgroundPaint)
        }

        val bitmap = snapshot
        if (!overlayMode &&
            bitmap != null &&
            !bitmap.isRecycled &&
            bitmap.width > 0 &&
            bitmap.height > 0
        ) {
            mapRect.set(drawRect)
            updateVisibleWindow()
            drawSnapshot(canvas, bitmap)
        } else {
            mapRect.set(drawRect)
            updateVisibleWindow()
        }

        drawGrid(canvas)
        drawViewport(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)
        val currentViewport = viewport

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouchActive = false
                hasLastScaleFocus = false
                if (currentViewport != null) {
                    requestViewportPosition(event.x, event.y, currentViewport)
                }
                return mapRect.contains(event.x, event.y) || scaleHandled
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouchActive = true
                hasLastScaleFocus = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isScalingActive() && currentViewport != null) {
                    requestViewportPosition(event.x, event.y, currentViewport)
                }
                return scaleHandled || currentViewport != null
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    multiTouchActive = false
                    hasLastScaleFocus = false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                multiTouchActive = false
                hasLastScaleFocus = false
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                multiTouchActive = false
                hasLastScaleFocus = false
                return true
            }
        }

        return scaleHandled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun drawSnapshot(canvas: Canvas, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val maxX = bitmap.width.coerceAtLeast(1)
        val maxY = bitmap.height.coerceAtLeast(1)

        val srcLeft = (visibleWindowNorm.left * maxX).roundToInt().coerceIn(0, maxX - 1)
        val srcTop = (visibleWindowNorm.top * maxY).roundToInt().coerceIn(0, maxY - 1)
        val srcRight = (visibleWindowNorm.right * maxX).roundToInt().coerceIn(srcLeft + 1, maxX)
        val srcBottom = (visibleWindowNorm.bottom * maxY).roundToInt().coerceIn(srcTop + 1, maxY)

        bitmapSrcRect.set(srcLeft, srcTop, srcRight, srcBottom)
        runCatching { canvas.drawBitmap(bitmap, bitmapSrcRect, mapRect, snapshotPaint) }
    }

    private fun drawGrid(canvas: Canvas) {
        for (i in 1..3) {
            val x = mapRect.left + mapRect.width() * (i / 4f)
            val y = mapRect.top + mapRect.height() * (i / 4f)
            canvas.drawLine(x, mapRect.top, x, mapRect.bottom, gridPaint)
            canvas.drawLine(mapRect.left, y, mapRect.right, y, gridPaint)
        }
    }

    private fun drawViewport(canvas: Canvas) {
        val currentViewport = viewport ?: return
        val pageWidth = max(snapshotPageWidth, currentViewport.pageWidth).toFloat()
        val pageHeight = max(snapshotPageHeight, currentViewport.pageHeight).toFloat()

        val leftNorm = (currentViewport.scrollX / pageWidth).coerceIn(0f, 1f)
        val topNorm = (currentViewport.scrollY / pageHeight).coerceIn(0f, 1f)
        val widthNorm = (currentViewport.viewportWidth / pageWidth)
            .coerceIn(MIN_VIEWPORT_FRACTION, 1f)
        val heightNorm = (currentViewport.viewportHeight / pageHeight)
            .coerceIn(MIN_VIEWPORT_FRACTION, 1f)
        val rightNorm = (leftNorm + widthNorm).coerceIn(0f, 1f)
        val bottomNorm = (topNorm + heightNorm).coerceIn(0f, 1f)

        val visibleLeft = max(leftNorm, visibleWindowNorm.left)
        val visibleTop = max(topNorm, visibleWindowNorm.top)
        val visibleRight = min(rightNorm, visibleWindowNorm.right)
        val visibleBottom = min(bottomNorm, visibleWindowNorm.bottom)
        if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) {
            return
        }

        val localLeft = ((visibleLeft - visibleWindowNorm.left) / visibleWindowNorm.width())
            .coerceIn(0f, 1f)
        val localTop = ((visibleTop - visibleWindowNorm.top) / visibleWindowNorm.height())
            .coerceIn(0f, 1f)
        val localRight = ((visibleRight - visibleWindowNorm.left) / visibleWindowNorm.width())
            .coerceIn(0f, 1f)
        val localBottom = ((visibleBottom - visibleWindowNorm.top) / visibleWindowNorm.height())
            .coerceIn(0f, 1f)

        viewportRect.set(
            mapRect.left + mapRect.width() * localLeft,
            mapRect.top + mapRect.height() * localTop,
            mapRect.left + mapRect.width() * localRight,
            mapRect.top + mapRect.height() * localBottom
        )
        enforceMinViewportRectSize()
        if (viewportRect.width() <= 0f || viewportRect.height() <= 0f) {
            return
        }

        canvas.drawRect(viewportRect, viewportFillPaint)
        canvas.drawRect(viewportRect, viewportStrokePaint)
    }

    private fun requestViewportPosition(x: Float, y: Float, currentViewport: BrowserViewport) {
        if (!mapRect.contains(x, y)) {
            return
        }

        val pageWidth = max(snapshotPageWidth, currentViewport.pageWidth)
        val pageHeight = max(snapshotPageHeight, currentViewport.pageHeight)

        val normalizedTouch = screenToPageNormalized(x, y) ?: return
        val normalizedX = normalizedTouch.x
        val normalizedY = normalizedTouch.y

        val maxScrollX = (pageWidth - currentViewport.viewportWidth).coerceAtLeast(0)
        val maxScrollY = (pageHeight - currentViewport.viewportHeight).coerceAtLeast(0)

        val targetX = (normalizedX * pageWidth - currentViewport.viewportWidth / 2f)
            .roundToInt()
            .coerceIn(0, maxScrollX)
        val targetY = (normalizedY * pageHeight - currentViewport.viewportHeight / 2f)
            .roundToInt()
            .coerceIn(0, maxScrollY)

        onPositionRequested?.invoke(targetX, targetY)
    }

    private fun screenToPageNormalized(x: Float, y: Float): NormalizedPoint? {
        if (!mapRect.contains(x, y) || mapRect.width() <= 0f || mapRect.height() <= 0f) {
            return null
        }

        updateVisibleWindow()

        val localX = ((x - mapRect.left) / mapRect.width()).coerceIn(0f, 1f)
        val localY = ((y - mapRect.top) / mapRect.height()).coerceIn(0f, 1f)
        val pageX = visibleWindowNorm.left + visibleWindowNorm.width() * localX
        val pageY = visibleWindowNorm.top + visibleWindowNorm.height() * localY

        return NormalizedPoint(
            x = pageX.coerceIn(0f, 1f),
            y = pageY.coerceIn(0f, 1f)
        )
    }

    private fun updateVisibleWindow() {
        overviewZoom = overviewZoom.coerceIn(MIN_OVERVIEW_ZOOM, MAX_OVERVIEW_ZOOM)
        val mapAspect = resolveMapAspect()
        val pageAspect = resolvePageAspect()
        val windowAspect = (mapAspect / pageAspect).coerceAtLeast(MIN_ASPECT)
        val (baseWidthNorm, baseHeightNorm) = if (windowAspect >= 1f) {
            1f to (1f / windowAspect)
        } else {
            windowAspect to 1f
        }

        val halfWidth = (baseWidthNorm / (2f * overviewZoom))
            .coerceIn(MIN_WINDOW_FRACTION * 0.5f, 0.5f)
        val halfHeight = (baseHeightNorm / (2f * overviewZoom))
            .coerceIn(MIN_WINDOW_FRACTION * 0.5f, 0.5f)
        val minCenterX = halfWidth
        val maxCenterX = 1f - halfWidth
        val minCenterY = halfHeight
        val maxCenterY = 1f - halfHeight

        overviewCenterXNorm = if (minCenterX <= maxCenterX) {
            overviewCenterXNorm.coerceIn(minCenterX, maxCenterX)
        } else {
            0.5f
        }
        overviewCenterYNorm = if (minCenterY <= maxCenterY) {
            overviewCenterYNorm.coerceIn(minCenterY, maxCenterY)
        } else {
            0.5f
        }

        visibleWindowNorm.set(
            overviewCenterXNorm - halfWidth,
            overviewCenterYNorm - halfHeight,
            overviewCenterXNorm + halfWidth,
            overviewCenterYNorm + halfHeight
        )
    }

    private fun resolvePageAspect(): Float {
        val currentViewport = viewport
        val pageWidth = max(snapshotPageWidth, currentViewport?.pageWidth ?: snapshotPageWidth)
            .toFloat()
            .coerceAtLeast(1f)
        val pageHeight = max(snapshotPageHeight, currentViewport?.pageHeight ?: snapshotPageHeight)
            .toFloat()
            .coerceAtLeast(1f)
        return (pageWidth / pageHeight).coerceIn(MIN_ASPECT, MAX_ASPECT)
    }

    private fun resolveMapAspect(): Float {
        val resolvedWidth = when {
            mapRect.width() > 0f -> mapRect.width()
            drawRect.width() > 0f -> drawRect.width()
            else -> (width - paddingLeft - paddingRight).toFloat() - mapInsetPx * 2f
        }.coerceAtLeast(1f)
        val resolvedHeight = when {
            mapRect.height() > 0f -> mapRect.height()
            drawRect.height() > 0f -> drawRect.height()
            else -> (height - paddingTop - paddingBottom).toFloat() - mapInsetPx * 2f
        }.coerceAtLeast(1f)
        return (resolvedWidth / resolvedHeight).coerceIn(MIN_ASPECT, MAX_ASPECT)
    }

    private fun enforceMinViewportRectSize() {
        if (viewportRect.width() >= minViewportSizePx && viewportRect.height() >= minViewportSizePx) {
            return
        }

        val halfWidth = max(viewportRect.width() * 0.5f, minViewportSizePx * 0.5f)
        val halfHeight = max(viewportRect.height() * 0.5f, minViewportSizePx * 0.5f)
        val centerX = viewportRect.centerX()
        val centerY = viewportRect.centerY()

        viewportRect.set(
            centerX - halfWidth,
            centerY - halfHeight,
            centerX + halfWidth,
            centerY + halfHeight
        )

        if (viewportRect.left < mapRect.left) {
            viewportRect.offset(mapRect.left - viewportRect.left, 0f)
        }
        if (viewportRect.right > mapRect.right) {
            viewportRect.offset(mapRect.right - viewportRect.right, 0f)
        }
        if (viewportRect.top < mapRect.top) {
            viewportRect.offset(0f, mapRect.top - viewportRect.top)
        }
        if (viewportRect.bottom > mapRect.bottom) {
            viewportRect.offset(0f, mapRect.bottom - viewportRect.bottom)
        }

        viewportRect.set(
            max(mapRect.left, viewportRect.left),
            max(mapRect.top, viewportRect.top),
            min(mapRect.right, viewportRect.right),
            min(mapRect.bottom, viewportRect.bottom)
        )
    }

    private fun keepViewportVisible(currentViewport: BrowserViewport?) {
        if (currentViewport == null) {
            updateVisibleWindow()
            return
        }

        val pageWidth = max(snapshotPageWidth, currentViewport.pageWidth).toFloat().coerceAtLeast(1f)
        val pageHeight = max(snapshotPageHeight, currentViewport.pageHeight).toFloat().coerceAtLeast(1f)
        val centerNormX = ((currentViewport.scrollX + currentViewport.viewportWidth / 2f) / pageWidth)
            .coerceIn(0f, 1f)
        val centerNormY = ((currentViewport.scrollY + currentViewport.viewportHeight / 2f) / pageHeight)
            .coerceIn(0f, 1f)

        if (overviewZoom <= MIN_OVERVIEW_ZOOM) {
            overviewCenterXNorm = centerNormX
            overviewCenterYNorm = centerNormY
            updateVisibleWindow()
            return
        }

        updateVisibleWindow()

        if (!visibleWindowNorm.contains(centerNormX, centerNormY)) {
            overviewCenterXNorm = centerNormX
            overviewCenterYNorm = centerNormY
            updateVisibleWindow()
        }
    }

    private fun isScalingActive(): Boolean {
        return multiTouchActive || scaleGestureDetector.isInProgress
    }
    
    // Accessibility: Override to provide custom accessibility information
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.view.View"
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.isScrollable = true
        
        // Provide zoom level information for screen readers
        val zoomPercent = (overviewZoom * 100f).roundToInt()
        info.contentDescription = context.getString(
            R.string.accessibility_overview_map
        ) + ". " + context.getString(
            R.string.overview_zoom_template,
            zoomPercent
        )
    }
    
    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (!isAttachedToWindow) {
            return false
        }
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
                // Zoom in via accessibility action
                val oldZoom = overviewZoom
                overviewZoom = (overviewZoom * 1.5f).coerceIn(MIN_OVERVIEW_ZOOM, MAX_OVERVIEW_ZOOM)
                if (overviewZoom != oldZoom) {
                    updateVisibleWindow()
                    onOverviewZoomChanged?.invoke(overviewZoom)
                    invalidate()
                    announceForAccessibility(
                        context.getString(R.string.overview_zoom_template, (overviewZoom * 100f).roundToInt())
                    )
                    return true
                }
            }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                // Zoom out via accessibility action
                val oldZoom = overviewZoom
                overviewZoom = (overviewZoom / 1.5f).coerceIn(MIN_OVERVIEW_ZOOM, MAX_OVERVIEW_ZOOM)
                if (overviewZoom != oldZoom) {
                    updateVisibleWindow()
                    onOverviewZoomChanged?.invoke(overviewZoom)
                    invalidate()
                    announceForAccessibility(
                        context.getString(R.string.overview_zoom_template, (overviewZoom * 100f).roundToInt())
                    )
                    return true
                }
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class NormalizedPoint(
        val x: Float,
        val y: Float
    )

    private companion object {
        const val MIN_OVERVIEW_ZOOM = 1f
        const val MAX_OVERVIEW_ZOOM = 6f
        const val MIN_VIEWPORT_FRACTION = 0.01f
        const val MIN_WINDOW_FRACTION = 0.02f
        const val MIN_ASPECT = 0.1f
        const val MAX_ASPECT = 10f
    }
}
