package com.ayn.magni.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Cap
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.ayn.magni.data.BottomScreenFeedbackEffect
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.roundToInt

class OledBlackoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class PointerTrack(
        var x: Float,
        var y: Float,
        var lastEmitAtMs: Long
    )

    private data class Pulse(
        val x: Float,
        val y: Float,
        val startedAtMs: Long,
        val dirX: Float,
        val dirY: Float,
        val seed: Int
    )

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val pulseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#44EF8B2F")
    }
    private val pulseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Cap.ROUND
        color = Color.parseColor("#EF8B2F")
    }
    private val trailSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Cap.ROUND
        color = Color.parseColor("#44EF8B2F")
    }
    private val trailAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Cap.ROUND
        color = Color.parseColor("#EF8B2F")
    }

    private val pulses = ArrayDeque<Pulse>()
    private var pulseSoftColor = Color.parseColor("#44EF8B2F")
    private var pulseAccentColor = Color.parseColor("#EF8B2F")
    private var feedbackEffect = BottomScreenFeedbackEffect.AURORA
    private var targetFrameIntervalMs = DEFAULT_FRAME_INTERVAL_MS
    private var lastFrameDrawAtMs: Long = 0L
    private val frameInvalidateRunnable = Runnable { invalidate() }
    private val pointerTracks = mutableMapOf<Int, PointerTrack>()
    private var seedCounter = 0

    var onTouchForward: ((MotionEvent) -> Boolean)? = null

    init {
        isClickable = true
        isFocusable = true
    }

    fun applyThemeColors(accentColor: Int, accentSoftColor: Int) {
        pulseAccentColor = accentColor
        pulseSoftColor = accentSoftColor
        invalidate()
    }

    fun setFeedbackEffect(effect: BottomScreenFeedbackEffect) {
        if (feedbackEffect == effect) {
            return
        }
        feedbackEffect = effect
        pulses.clear()
        pointerTracks.clear()
        removeCallbacks(frameInvalidateRunnable)
        invalidate()
    }

    fun setTargetFrameRate(fps: Int) {
        val boundedFps = fps.coerceIn(MIN_FRAME_RATE_FPS, MAX_FRAME_RATE_FPS)
        targetFrameIntervalMs = (1000f / boundedFps.toFloat()).toLong().coerceAtLeast(1L)
        if (pulses.isNotEmpty()) {
            scheduleNextFrame()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        lastFrameDrawAtMs = SystemClock.elapsedRealtime()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        pruneExpiredPulses(SystemClock.elapsedRealtime())
        if (pulses.isEmpty()) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        drawTrails(canvas, now)

        val iterator = pulses.iterator()
        while (iterator.hasNext()) {
            val pulse = iterator.next()
            val ageMs = now - pulse.startedAtMs

            val progress = ageMs / PULSE_DURATION_MS.toFloat()
            val radius = dp(PULSE_MIN_RADIUS_DP + (PULSE_MAX_RADIUS_DP - PULSE_MIN_RADIUS_DP) * progress)
            when (feedbackEffect) {
                BottomScreenFeedbackEffect.AURORA -> drawAuroraPulse(canvas, pulse, progress, radius)
                BottomScreenFeedbackEffect.RING -> drawRingPulse(canvas, pulse, progress, radius)
                BottomScreenFeedbackEffect.COMET -> drawCometPulse(canvas, pulse, progress, radius)
                BottomScreenFeedbackEffect.PIXEL -> drawPixelPulse(canvas, pulse, progress, radius)
            }
        }

        if (pulses.isNotEmpty()) {
            scheduleNextFrame()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val forwarded = onTouchForward?.invoke(event) == true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                val pointerId = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                val now = SystemClock.elapsedRealtime()
                addPulse(x, y, isMove = false, fromX = null, fromY = null)
                pointerTracks[pointerId] = PointerTrack(x = x, y = y, lastEmitAtMs = now)
            }

            MotionEvent.ACTION_MOVE -> {
                val now = SystemClock.elapsedRealtime()
                val activePointerIds = HashSet<Int>(event.pointerCount)
                for (index in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(index)
                    activePointerIds.add(pointerId)
                    val x = event.getX(index)
                    val y = event.getY(index)
                    val previous = pointerTracks[pointerId]
                    if (previous == null) {
                        pointerTracks[pointerId] = PointerTrack(x = x, y = y, lastEmitAtMs = now)
                        continue
                    }

                    val distance = hypot((x - previous.x).toDouble(), (y - previous.y).toDouble()).toFloat()
                    val shouldEmit =
                        distance >= dp(MIN_INTERPOLATION_DISTANCE_DP) &&
                            now - previous.lastEmitAtMs >= MOVE_PULSE_INTERVAL_MS
                    if (shouldEmit) {
                        addPulse(
                            x = x,
                            y = y,
                            isMove = true,
                            fromX = previous.x,
                            fromY = previous.y
                        )
                        previous.lastEmitAtMs = now
                    }
                    previous.x = x
                    previous.y = y
                }

                val iterator = pointerTracks.keys.iterator()
                while (iterator.hasNext()) {
                    if (!activePointerIds.contains(iterator.next())) {
                        iterator.remove()
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                val pointerId = event.getPointerId(index)
                pointerTracks.remove(pointerId)
            }

            MotionEvent.ACTION_UP -> {
                pointerTracks.clear()
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                pointerTracks.clear()
                // Keep last rendered pulse; no extra handling needed.
            }
        }

        return forwarded || true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun addPulse(
        x: Float,
        y: Float,
        isMove: Boolean,
        fromX: Float?,
        fromY: Float?
    ) {
        val safeX = x.coerceIn(0f, width.toFloat().coerceAtLeast(1f))
        val safeY = y.coerceIn(0f, height.toFloat().coerceAtLeast(1f))
        val now = SystemClock.elapsedRealtime()
        val direction = computeDirection(fromX, fromY, safeX, safeY)

        if (isMove && fromX != null && fromY != null) {
            enqueueInterpolatedPulses(
                fromX = fromX,
                fromY = fromY,
                toX = safeX,
                toY = safeY,
                now = now,
                dirX = direction.first,
                dirY = direction.second
            )
            scheduleNextFrame()
            return
        }

        when (feedbackEffect) {
            BottomScreenFeedbackEffect.AURORA -> {
                enqueuePulse(safeX, safeY, now, direction.first, direction.second)
                if (!isMove) {
                    val orbitDistance = dp(11f)
                    val baseAngle = (nextSeed() % 628) / 100f
                    enqueuePulse(
                        safeX + cos(baseAngle) * orbitDistance,
                        safeY + sin(baseAngle) * orbitDistance,
                        now,
                        0f,
                        0f
                    )
                    enqueuePulse(
                        safeX - cos(baseAngle) * orbitDistance,
                        safeY - sin(baseAngle) * orbitDistance,
                        now,
                        0f,
                        0f
                    )
                }
            }

            BottomScreenFeedbackEffect.RING -> {
                enqueuePulse(safeX, safeY, now, direction.first, direction.second)
            }

            BottomScreenFeedbackEffect.COMET -> {
                enqueuePulse(safeX, safeY, now, direction.first, direction.second)
                if (isMove) {
                    val trailDistance = dp(7f)
                    enqueuePulse(
                        safeX - direction.first * trailDistance,
                        safeY - direction.second * trailDistance,
                        now - 70L,
                        direction.first,
                        direction.second
                    )
                    enqueuePulse(
                        safeX - direction.first * trailDistance * 1.85f,
                        safeY - direction.second * trailDistance * 1.85f,
                        now - 120L,
                        direction.first,
                        direction.second
                    )
                }
            }

            BottomScreenFeedbackEffect.PIXEL -> {
                val snapped = snapToGrid(safeX, safeY)
                enqueuePulse(snapped.first, snapped.second, now, direction.first, direction.second)
            }
        }
        scheduleNextFrame()
    }

    private fun scheduleNextFrame() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastFrameDrawAtMs
        val delayMs = (targetFrameIntervalMs - elapsed).coerceAtLeast(0L)
        removeCallbacks(frameInvalidateRunnable)
        postDelayed(frameInvalidateRunnable, delayMs)
    }

    private fun enqueueInterpolatedPulses(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        now: Long,
        dirX: Float,
        dirY: Float
    ) {
        val dx = toX - fromX
        val dy = toY - fromY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < dp(MIN_INTERPOLATION_DISTANCE_DP)) {
            return
        }

        val stepPx = dp(INTERPOLATION_STEP_DP)
        val steps = (distance / stepPx).roundToInt().coerceIn(1, MAX_INTERPOLATION_STEPS)
        for (step in 1..steps) {
            val t = step.toFloat() / steps.toFloat()
            val x = fromX + (dx * t)
            val y = fromY + (dy * t)
            val ageOffset = (steps - step) * INTERPOLATION_TIME_STEP_MS
            when (feedbackEffect) {
                BottomScreenFeedbackEffect.PIXEL -> {
                    val snapped = snapToGrid(x, y)
                    enqueuePulse(snapped.first, snapped.second, now - ageOffset, dirX, dirY)
                }

                else -> {
                    enqueuePulse(x, y, now - ageOffset, dirX, dirY)
                }
            }
        }
    }

    private fun enqueuePulse(x: Float, y: Float, startedAtMs: Long, dirX: Float, dirY: Float) {
        val safeX = x.coerceIn(0f, width.toFloat().coerceAtLeast(1f))
        val safeY = y.coerceIn(0f, height.toFloat().coerceAtLeast(1f))
        pulses.addLast(Pulse(safeX, safeY, startedAtMs, dirX, dirY, nextSeed()))
        while (pulses.size > MAX_PULSES) {
            pulses.removeFirst()
        }
    }

    private fun pruneExpiredPulses(now: Long) {
        while (pulses.isNotEmpty()) {
            val oldest = pulses.first()
            if (now - oldest.startedAtMs <= PULSE_DURATION_MS) {
                break
            }
            pulses.removeFirst()
        }
    }

    private fun drawTrails(canvas: Canvas, now: Long) {
        if (feedbackEffect == BottomScreenFeedbackEffect.PIXEL || pulses.size < 2) {
            return
        }

        val iterator = pulses.iterator()
        var previous = if (iterator.hasNext()) iterator.next() else return
        while (iterator.hasNext()) {
            val current = iterator.next()
            val dx = current.x - previous.x
            val dy = current.y - previous.y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (distance <= dp(MAX_TRAIL_SEGMENT_DISTANCE_DP)) {
                val ageMs = now - current.startedAtMs
                val trailProgress = (ageMs / TRAIL_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                val intensity = (1f - trailProgress)

                val softAlpha = (intensity * intensity * TRAIL_SOFT_ALPHA_MAX).roundToInt()
                val accentAlpha = (intensity * TRAIL_ACCENT_ALPHA_MAX).roundToInt()
                val softWidth = dp(TRAIL_SOFT_MIN_WIDTH_DP + (intensity * TRAIL_SOFT_EXTRA_WIDTH_DP))
                val accentWidth = dp(TRAIL_ACCENT_MIN_WIDTH_DP + (intensity * TRAIL_ACCENT_EXTRA_WIDTH_DP))

                trailSoftPaint.color = colorWithAlpha(pulseSoftColor, softAlpha)
                trailSoftPaint.strokeWidth = softWidth
                canvas.drawLine(previous.x, previous.y, current.x, current.y, trailSoftPaint)

                trailAccentPaint.color = colorWithAlpha(pulseAccentColor, accentAlpha)
                trailAccentPaint.strokeWidth = accentWidth
                canvas.drawLine(previous.x, previous.y, current.x, current.y, trailAccentPaint)
            }
            previous = current
        }
    }

    private fun drawAuroraPulse(canvas: Canvas, pulse: Pulse, progress: Float, radius: Float) {
        val softAlpha = ((1f - progress) * AURORA_SOFT_MAX_ALPHA).roundToInt()
        val strokeAlpha = ((1f - progress) * AURORA_STROKE_MAX_ALPHA).roundToInt()
        pulseFillPaint.color = colorWithAlpha(pulseSoftColor, softAlpha)
        pulseStrokePaint.color = colorWithAlpha(pulseAccentColor, strokeAlpha)
        pulseStrokePaint.strokeWidth = dp(2.2f)

        canvas.drawCircle(pulse.x, pulse.y, radius * 1.08f, pulseFillPaint)
        canvas.drawCircle(pulse.x, pulse.y, radius, pulseStrokePaint)

        val orbitAngle = (pulse.seed % 360) * 0.01745f + progress * 4.9f
        val orbitRadius = radius * 0.45f
        val orbitX = pulse.x + cos(orbitAngle) * orbitRadius
        val orbitY = pulse.y + sin(orbitAngle) * orbitRadius
        val orbAlpha = ((1f - progress) * 200f).roundToInt()
        pulseFillPaint.color = colorWithAlpha(pulseAccentColor, orbAlpha)
        canvas.drawCircle(orbitX, orbitY, radius * 0.16f, pulseFillPaint)
    }

    private fun drawRingPulse(canvas: Canvas, pulse: Pulse, progress: Float, radius: Float) {
        val softAlpha = ((1f - progress) * SOFT_MAX_ALPHA).roundToInt()
        val strokeAlpha = ((1f - progress) * STROKE_MAX_ALPHA).roundToInt()
        pulseFillPaint.color = colorWithAlpha(pulseSoftColor, softAlpha)
        pulseStrokePaint.color = colorWithAlpha(pulseAccentColor, strokeAlpha)
        pulseStrokePaint.strokeWidth = dp(2f)

        canvas.drawCircle(pulse.x, pulse.y, radius, pulseFillPaint)
        canvas.drawCircle(pulse.x, pulse.y, radius, pulseStrokePaint)
    }

    private fun drawCometPulse(canvas: Canvas, pulse: Pulse, progress: Float, radius: Float) {
        val softAlpha = ((1f - progress) * 170f).roundToInt()
        val strokeAlpha = ((1f - progress) * 255f).roundToInt()
        pulseFillPaint.color = colorWithAlpha(pulseSoftColor, softAlpha)
        pulseStrokePaint.color = colorWithAlpha(pulseAccentColor, strokeAlpha)
        pulseStrokePaint.strokeWidth = dp(2.4f)

        val tailLength = max(dp(12f), radius * 1.5f)
        if (hypot(pulse.dirX.toDouble(), pulse.dirY.toDouble()).toFloat() > 0.1f) {
            canvas.drawLine(
                pulse.x - pulse.dirX * tailLength,
                pulse.y - pulse.dirY * tailLength,
                pulse.x,
                pulse.y,
                pulseStrokePaint
            )
        }
        canvas.drawCircle(pulse.x, pulse.y, radius * 0.44f, pulseFillPaint)
        canvas.drawCircle(pulse.x, pulse.y, radius * 0.32f, pulseStrokePaint)
    }

    private fun drawPixelPulse(canvas: Canvas, pulse: Pulse, progress: Float, radius: Float) {
        val softAlpha = ((1f - progress) * 150f).roundToInt()
        val strokeAlpha = ((1f - progress) * 240f).roundToInt()
        pulseFillPaint.color = colorWithAlpha(pulseSoftColor, softAlpha)
        pulseStrokePaint.color = colorWithAlpha(pulseAccentColor, strokeAlpha)
        pulseStrokePaint.strokeWidth = dp(2f)

        val size = radius * 1.08f
        canvas.drawRect(pulse.x - size, pulse.y - size, pulse.x + size, pulse.y + size, pulseFillPaint)
        canvas.drawRect(pulse.x - size, pulse.y - size, pulse.x + size, pulse.y + size, pulseStrokePaint)
        val crossArm = size * 1.2f
        canvas.drawLine(pulse.x - crossArm, pulse.y, pulse.x + crossArm, pulse.y, pulseStrokePaint)
        canvas.drawLine(pulse.x, pulse.y - crossArm, pulse.x, pulse.y + crossArm, pulseStrokePaint)
    }

    private fun computeDirection(fromX: Float?, fromY: Float?, x: Float, y: Float): Pair<Float, Float> {
        if (fromX == null || fromY == null) {
            return 0f to 0f
        }
        val dx = x - fromX
        val dy = y - fromY
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length < 0.001f) {
            return 0f to 0f
        }
        return dx / length to dy / length
    }

    private fun snapToGrid(x: Float, y: Float): Pair<Float, Float> {
        val grid = dp(8f)
        val snappedX = (x / grid).roundToInt() * grid
        val snappedY = (y / grid).roundToInt() * grid
        return snappedX to snappedY
    }

    private fun nextSeed(): Int {
        seedCounter = (seedCounter + 1) and 0x7FFFFFFF
        return seedCounter
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        val boundedAlpha = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (boundedAlpha shl 24)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        private const val DEFAULT_FRAME_INTERVAL_MS = 33L
        private const val MIN_FRAME_RATE_FPS = 30
        private const val MAX_FRAME_RATE_FPS = 120
        private const val MAX_PULSES = 64
        private const val PULSE_DURATION_MS = 320L
        private const val MOVE_PULSE_INTERVAL_MS = 14L
        private const val PULSE_MIN_RADIUS_DP = 7f
        private const val PULSE_MAX_RADIUS_DP = 30f
        private const val SOFT_MAX_ALPHA = 132f
        private const val STROKE_MAX_ALPHA = 240f
        private const val AURORA_SOFT_MAX_ALPHA = 168f
        private const val AURORA_STROKE_MAX_ALPHA = 248f
        private const val TRAIL_DURATION_MS = 170L
        private const val TRAIL_SOFT_ALPHA_MAX = 96f
        private const val TRAIL_ACCENT_ALPHA_MAX = 168f
        private const val TRAIL_SOFT_MIN_WIDTH_DP = 2.2f
        private const val TRAIL_SOFT_EXTRA_WIDTH_DP = 3.4f
        private const val TRAIL_ACCENT_MIN_WIDTH_DP = 1.0f
        private const val TRAIL_ACCENT_EXTRA_WIDTH_DP = 1.8f
        private const val MAX_TRAIL_SEGMENT_DISTANCE_DP = 18f
        private const val MIN_INTERPOLATION_DISTANCE_DP = 1.6f
        private const val INTERPOLATION_STEP_DP = 3.2f
        private const val INTERPOLATION_TIME_STEP_MS = 6L
        private const val MAX_INTERPOLATION_STEPS = 8
    }
}
