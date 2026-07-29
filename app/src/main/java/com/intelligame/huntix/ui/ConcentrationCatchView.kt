package com.intelligame.huntix.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.sin

class ConcentrationCatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), CaptureMiniGame {

    companion object {
        const val MAX_ATTEMPTS = 3
    }

    private var miniGameListener: CaptureMiniGame.Listener? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1030")
    }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#444466")
    }
    private val targetRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#00FF88")
    }
    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888899")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val attemptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private var eggColor = Color.parseColor("#FFCC00")
    private var currentAttempt = 0
    private var ringRadius = 0f
    private var minRadius = 0f
    private var maxRadius = 0f
    private var pulseProgress = 0f
    private var isPulsing = false
    private var isResult = false
    private var resultLabel = ""
    private var resultColor = Color.WHITE
    private var quality = 0f
    private var canTap = false
    private var cx = 0f
    private var cy = 0f
    private var pulseAnimator: android.animation.ValueAnimator? = null

    override fun getView(): View = this

    override fun setEggColor(color: Int) {
        eggColor = color
        innerRingPaint.color = color
        invalidate()
    }

    override fun reset() {
        currentAttempt = 0
        isPulsing = false
        isResult = false
        resultLabel = ""
        canTap = false
        pulseAnimator?.cancel()
        invalidate()
    }

    override fun setListener(listener: CaptureMiniGame.Listener) {
        miniGameListener = listener
    }

    override fun release() {
        miniGameListener = null
        pulseAnimator?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        val maxDim = minOf(w, h).toFloat()
        maxRadius = maxDim * 0.4f
        minRadius = maxDim * 0.08f
        ringRadius = maxRadius
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (!isPulsing && !isResult && currentAttempt == 0) {
            drawIdle(canvas, w, h)
            return
        }
        if (isResult) {
            drawResult(canvas, w, h)
            return
        }
        if (currentAttempt > MAX_ATTEMPTS) {
            drawFailed(canvas, w, h)
            return
        }

        val attemptsLeft = MAX_ATTEMPTS - currentAttempt + 1
        val attemptsText = "Tentativi: ${"●".repeat(attemptsLeft)}${"○".repeat(currentAttempt - 1)}"
        canvas.drawText(attemptsText, w / 2f, h - 20f, attemptPaint)

        val r = minRadius + (maxRadius - minRadius) * (1f - pulseProgress)
        val alpha = (80 + (1f - pulseProgress) * 175).toInt().coerceIn(0, 255)

        glowPaint.color = eggColor
        glowPaint.alpha = (alpha * 0.3f).toInt()
        canvas.drawCircle(cx, cy, maxRadius * 1.2f, glowPaint)

        outerRingPaint.alpha = alpha
        canvas.drawCircle(cx, cy, maxRadius, outerRingPaint)

        innerRingPaint.alpha = alpha
        innerRingPaint.strokeWidth = 4f + pulseProgress * 6f
        canvas.drawCircle(cx, cy, r, innerRingPaint)

        val rimAlpha = (255 * (1f - pulseProgress)).toInt().coerceIn(40, 255)
        targetRingPaint.alpha = rimAlpha
        targetRingPaint.strokeWidth = 2f + (1f - pulseProgress) * 4f
        canvas.drawCircle(cx, cy, minRadius + 10f, targetRingPaint)

        val hint = when {
            r < minRadius * 1.3f -> "ORA!"
            r < maxRadius * 0.4f -> "Aspetta..."
            else -> "Avvicinati al centro..."
        }
        hintPaint.color = if (r < minRadius * 1.3f) Color.parseColor("#00FF88") else Color.parseColor("#888899")
        canvas.drawText(hint, w / 2f, h / 2f - maxRadius - 40f, hintPaint)
    }

    private fun drawIdle(canvas: Canvas, w: Float, h: Float) {
        canvas.drawText("🎯 Concentrazione", w / 2f, h * 0.35f, textPaint)
        hintPaint.color = Color.parseColor("#888899")
        canvas.drawText("Tocca per iniziare", w / 2f, h * 0.55f, hintPaint)
        canvas.drawCircle(cx, cy, maxRadius * 0.5f, outerRingPaint)
    }

    private fun drawResult(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = resultColor
        canvas.drawText(resultLabel, w / 2f, h * 0.45f, textPaint)
        if (currentAttempt <= MAX_ATTEMPTS && resultLabel != "CATTURATO!") {
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Tocca per riprovare", w / 2f, h * 0.58f, hintPaint)
        }
    }

    private fun drawFailed(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#FF3366")
        canvas.drawText("L'uovo è fuggito...", w / 2f, h * 0.45f, textPaint)
        hintPaint.color = Color.parseColor("#888899")
        canvas.drawText("Torna alla mappa per un'altra uova", w / 2f, h * 0.58f, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isResult) {
                if (currentAttempt > MAX_ATTEMPTS) return true
                if (resultLabel == "CATTURATO!") return true
                isResult = false
                startPulse()
                return true
            }
            if (!isPulsing && currentAttempt == 0) {
                startPulse()
                return true
            }
            if (isPulsing && canTap) {
                val r = minRadius + (maxRadius - minRadius) * (1f - pulseProgress)
                val idealR = minRadius * 1.1f
                val deviation = abs(r - idealR) / (maxRadius - minRadius)
                quality = (1f - deviation * 2f).coerceIn(0f, 1f)
                evaluateCatch()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startPulse() {
        currentAttempt++
        isPulsing = true
        canTap = false
        pulseProgress = 0f
        ringRadius = maxRadius
        pulseAnimator?.cancel()
        invalidate()

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                pulseProgress = it.animatedFraction
                val r = minRadius + (maxRadius - minRadius) * (1f - pulseProgress)
                canTap = r < minRadius * 1.5f
                invalidate()
            }
            start()
        }
    }

    private fun evaluateCatch() {
        isPulsing = false
        isResult = true
        pulseAnimator?.cancel()
        miniGameListener?.onThrowAttempt(currentAttempt, quality)

        val catchChance = when {
            quality >= 0.8f -> 0.85f
            quality >= 0.5f -> 0.60f
            quality >= 0.3f -> 0.35f
            else -> 0.15f
        }
        val caught = Math.random() < catchChance

        if (caught) {
            resultLabel = "CATTURATO!"
            resultColor = Color.parseColor("#00FF88")
            miniGameListener?.onCaptured(currentAttempt)
        } else if (currentAttempt >= MAX_ATTEMPTS) {
            resultLabel = "L'uovo è fuggito..."
            resultColor = Color.parseColor("#FF3366")
            miniGameListener?.onEscaped(currentAttempt)
        } else {
            resultLabel = "Mancato! Tenta ancora"
            resultColor = Color.parseColor("#FF8800")
        }
        invalidate()
    }
}
