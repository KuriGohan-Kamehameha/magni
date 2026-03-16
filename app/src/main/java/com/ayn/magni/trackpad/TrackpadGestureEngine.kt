package com.ayn.magni.trackpad

import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal class TrackpadGestureEngine(
    private val touchSlop: Float,
    private val longPressTimeoutMs: Long,
    private val isWithinTrackpadBounds: (Float, Float) -> Boolean,
    private val trackpadWidthPx: () -> Float,
    private val trackpadHeightPx: () -> Float,
    private val cursorScaleDistancePx: () -> Float,
    private val onTapRequested: (Float, Float) -> Unit,
    private val onLongPressRequested: (Float, Float) -> Unit,
    private val onScrollRequested: (Float, Float) -> Unit,
    private val onZoomRequested: (Int) -> Unit,
    private val onCursorMoved: (Float, Float) -> Unit,
    private val onRequestDisallowIntercept: () -> Unit,
    private val onScheduleLongPress: (Long) -> Unit,
    private val onCancelLongPress: () -> Unit,
    private val onTapHaptic: () -> Unit,
    private val onLongPressHaptic: () -> Unit,
    private val onMultiTouchHaptic: () -> Unit,
    private val onZoomStepHaptic: () -> Unit,
    private val onInvalidate: () -> Unit,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private var cursorXNorm = 0.5f
    private var cursorYNorm = 0.5f
    private var primaryPointerId = MotionEvent.INVALID_POINTER_ID
    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var lastScrollFocusX = 0f
    private var lastScrollFocusY = 0f
    private var gestureTravel = 0f
    private var gestureStartAtMs = 0L
    private var scrollMode = false
    private var longPressTriggered = false
    private var twoFingerTapCandidate = false
    private var twoFingerTapStartAtMs = 0L
    private var twoFingerTravel = 0f
    private var pinchBaselineDistance = 0f
    private var lastPinchStepAtMs = 0L
    private var showViewportPreview = false

    fun showViewportPreview(): Boolean = showViewportPreview

    fun resetCursor() {
        cursorXNorm = 0.5f
        cursorYNorm = 0.5f
        onCursorMoved(cursorXNorm, cursorYNorm)
        onInvalidate()
    }

    fun triggerLongPressIfEligible() {
        if (scrollMode || gestureTravel > touchSlop || longPressTriggered) {
            return
        }
        longPressTriggered = true
        onLongPressHaptic()
        onLongPressRequested(cursorXNorm, cursorYNorm)
        onInvalidate()
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isWithinTrackpadBounds(event.x, event.y)) {
                    return false
                }
                primaryPointerId = event.getPointerId(0)
                lastPointerX = event.x
                lastPointerY = event.y
                gestureTravel = 0f
                scrollMode = false
                longPressTriggered = false
                twoFingerTapCandidate = false
                twoFingerTravel = 0f
                gestureStartAtMs = nowMs()
                scheduleLongPress()
                onRequestDisallowIntercept()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    val wasScrollMode = scrollMode
                    scrollMode = true
                    longPressTriggered = false
                    showViewportPreview = true
                    cancelPendingLongPress()
                    if (!wasScrollMode) {
                        onMultiTouchHaptic()
                    }
                    val focus = pointerFocus(event)
                    lastScrollFocusX = focus.first
                    lastScrollFocusY = focus.second
                    twoFingerTapCandidate = event.pointerCount == 2
                    twoFingerTapStartAtMs = nowMs()
                    twoFingerTravel = 0f
                    pinchBaselineDistance = pointerDistance(event)
                    lastPinchStepAtMs = 0L
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (scrollMode && event.pointerCount >= 2) {
                    val focus = pointerFocus(event)
                    val deltaX = focus.first - lastScrollFocusX
                    val deltaY = focus.second - lastScrollFocusY
                    twoFingerTravel += hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()
                    if (twoFingerTravel > TWO_FINGER_TAP_MAX_TRAVEL_PX) {
                        twoFingerTapCandidate = false
                    }
                    lastScrollFocusX = focus.first
                    lastScrollFocusY = focus.second
                    if (abs(deltaX) > 0.5f || abs(deltaY) > 0.5f) {
                        onScrollRequested(
                            deltaX * TWO_FINGER_SCROLL_MULTIPLIER,
                            deltaY * TWO_FINGER_SCROLL_MULTIPLIER
                        )
                    }
                    maybeEmitPinchZoom(event)
                    return true
                }

                val pointerIndex = event.findPointerIndex(primaryPointerId)
                if (pointerIndex < 0) {
                    return true
                }
                val nextX = event.getX(pointerIndex)
                val nextY = event.getY(pointerIndex)
                val deltaX = nextX - lastPointerX
                val deltaY = nextY - lastPointerY
                gestureTravel += hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()
                if (gestureTravel > touchSlop) {
                    cancelPendingLongPress()
                }
                moveCursor(deltaX, deltaY)
                lastPointerX = nextX
                lastPointerY = nextY
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (scrollMode && event.pointerCount - 1 >= 2) {
                    val focus = pointerFocus(event)
                    lastScrollFocusX = focus.first
                    lastScrollFocusY = focus.second
                    pinchBaselineDistance = pointerDistance(event)
                    twoFingerTapCandidate = false
                    return true
                }
                if (scrollMode && event.pointerCount - 1 < 2) {
                    val triggerSecondaryClick =
                        twoFingerTapCandidate &&
                            event.pointerCount == 2 &&
                            (nowMs() - twoFingerTapStartAtMs) <= TWO_FINGER_TAP_TIMEOUT_MS &&
                            twoFingerTravel <= TWO_FINGER_TAP_MAX_TRAVEL_PX
                    if (triggerSecondaryClick) {
                        longPressTriggered = true
                        onLongPressHaptic()
                        onLongPressRequested(cursorXNorm, cursorYNorm)
                    }
                    scrollMode = false
                    showViewportPreview = false
                    twoFingerTapCandidate = false
                    twoFingerTravel = 0f
                    pinchBaselineDistance = 0f
                    lastPinchStepAtMs = 0L
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    if (remainingIndex in 0 until event.pointerCount) {
                        primaryPointerId = event.getPointerId(remainingIndex)
                        lastPointerX = event.getX(remainingIndex)
                        lastPointerY = event.getY(remainingIndex)
                    }
                    gestureTravel = touchSlop * 2f
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasTap = !scrollMode &&
                    !longPressTriggered &&
                    gestureTravel <= touchSlop * TAP_TRAVEL_MULTIPLIER &&
                    nowMs() - gestureStartAtMs <= TAP_TIMEOUT_MS
                cancelPendingLongPress()
                if (wasTap) {
                    onTapHaptic()
                    onTapRequested(cursorXNorm, cursorYNorm)
                }
                primaryPointerId = MotionEvent.INVALID_POINTER_ID
                scrollMode = false
                showViewportPreview = false
                twoFingerTapCandidate = false
                twoFingerTravel = 0f
                pinchBaselineDistance = 0f
                lastPinchStepAtMs = 0L
                onInvalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                primaryPointerId = MotionEvent.INVALID_POINTER_ID
                scrollMode = false
                longPressTriggered = false
                showViewportPreview = false
                twoFingerTapCandidate = false
                twoFingerTravel = 0f
                pinchBaselineDistance = 0f
                lastPinchStepAtMs = 0L
                onInvalidate()
                return true
            }
        }

        return false
    }

    private fun moveCursor(deltaX: Float, deltaY: Float) {
        val scaleDistance = max(cursorScaleDistancePx(), 1f)
        val scaleBoost = 1f + min(1.6f, hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat() / scaleDistance)
        val widthScale = max(trackpadWidthPx(), 1f)
        val heightScale = max(trackpadHeightPx(), 1f)
        cursorXNorm = (cursorXNorm + (deltaX * CURSOR_SPEED_MULTIPLIER * scaleBoost / widthScale)).coerceIn(0f, 1f)
        cursorYNorm = (cursorYNorm + (deltaY * CURSOR_SPEED_MULTIPLIER * scaleBoost / heightScale)).coerceIn(0f, 1f)
        onCursorMoved(cursorXNorm, cursorYNorm)
        onInvalidate()
    }

    private fun pointerFocus(event: MotionEvent): Pair<Float, Float> {
        var focusX = 0f
        var focusY = 0f
        for (index in 0 until event.pointerCount) {
            focusX += event.getX(index)
            focusY += event.getY(index)
        }
        val count = event.pointerCount.coerceAtLeast(1)
        return focusX / count to focusY / count
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) {
            return 0f
        }
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    private fun maybeEmitPinchZoom(event: MotionEvent) {
        if (event.pointerCount != 2) {
            return
        }
        val currentDistance = pointerDistance(event)
        if (currentDistance <= 0f) {
            return
        }
        if (pinchBaselineDistance <= 0f) {
            pinchBaselineDistance = currentDistance
            return
        }

        val scaleRatio = currentDistance / pinchBaselineDistance
        if (abs(scaleRatio - 1f) < PINCH_STEP_SCALE_THRESHOLD) {
            return
        }

        val now = nowMs()
        if (lastPinchStepAtMs != 0L && now - lastPinchStepAtMs < PINCH_STEP_MIN_INTERVAL_MS) {
            return
        }

        val zoomDelta = if (scaleRatio > 1f) 1 else -1
        onZoomRequested(zoomDelta)
        onZoomStepHaptic()
        twoFingerTapCandidate = false
        pinchBaselineDistance = currentDistance
        lastPinchStepAtMs = now
    }

    private fun scheduleLongPress() {
        cancelPendingLongPress()
        onScheduleLongPress(longPressTimeoutMs)
    }

    private fun cancelPendingLongPress() {
        onCancelLongPress()
    }

    private companion object {
        private const val TAP_TIMEOUT_MS = 260L
        private const val TAP_TRAVEL_MULTIPLIER = 1.35f
        private const val CURSOR_SPEED_MULTIPLIER = 1.2f
        private const val TWO_FINGER_SCROLL_MULTIPLIER = 1.5f
        private const val TWO_FINGER_TAP_TIMEOUT_MS = 210L
        private const val TWO_FINGER_TAP_MAX_TRAVEL_PX = 10f
        private const val PINCH_STEP_SCALE_THRESHOLD = 0.09f
        private const val PINCH_STEP_MIN_INTERVAL_MS = 85L
    }
}
