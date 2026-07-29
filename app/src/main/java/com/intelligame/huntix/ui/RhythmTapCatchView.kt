package com.intelligame.huntix.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs

class RhythmTapCatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), CaptureMiniGame {

    companion object {
        const val MAX_ATTEMPTS = 3
        const val BEATS_PER_ATTEMPT = 4
    }

    private var miniGameListener: CaptureMiniGame.Listener? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1030")
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pulseRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val beatGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#444466")
    }
    private val beatHitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00FF88")
        alpha = 80
    }
    private val beatMissPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF3366")
        alpha = 80
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888899")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val attemptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private var eggColor = Color.parseColor("#FFCC00")
    private var currentAttempt = 0
    private var isPlaying = false
    private var isResult = false
    private var resultLabel = ""
    private var resultColor = Color.WHITE
    private var quality = 0f

    private var pulseScale = 1f
    private var beatIndex = 0
    private var beatsHit = 0
    private var beatsTotal = 0
    private var lastBeatTime = 0L
    private var beatInterval = 600L
    private var cx = 0f
    private var cy = 0f
    private var maxR = 0f
    private var animator: ValueAnimator? = null
    private var h = 0f
    private var w = 0f
    private val beatResults = mutableListOf<Boolean>()

    override fun getView(): View = this

    override fun setEggColor(color: Int) {
        eggColor = color
        pulsePaint.color = color
        pulseRingPaint.color = color
        invalidate()
    }

    override fun reset() {
        currentAttempt = 0
        isPlaying = false
        isResult = false
        resultLabel = ""
        beatResults.clear()
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun setListener(listener: CaptureMiniGame.Listener) {
        miniGameListener = listener
    }

    override fun release() {
        miniGameListener = null
        animator?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.w = w.toFloat()
        this.h = h.toFloat()
        cx = w / 2f
        cy = h * 0.5f
        maxR = minOf(w, h) * 0.3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isPlaying && !isResult && currentAttempt == 0) {
            canvas.drawText("🎵 Cattura a Ritmo", w / 2f, h * 0.3f, textPaint)
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Tocca a tempo con il battito dell'uovo", w / 2f, h * 0.42f, hintPaint)
            canvas.drawText("Tocca per iniziare", w / 2f, h * 0.55f, hintPaint)
            return
        }
        if (isResult) {
            textPaint.color = resultColor
            canvas.drawText(resultLabel, w / 2f, h * 0.35f, textPaint)
            if (currentAttempt <= MAX_ATTEMPTS && resultLabel != "CATTURATO!") {
                hintPaint.color = Color.parseColor("#888899")
                canvas.drawText("Tocca per riprovare", w / 2f, h * 0.48f, hintPaint)
            } else if (currentAttempt > MAX_ATTEMPTS) {
                hintPaint.color = Color.parseColor("#888899")
                canvas.drawText("Torna alla mappa per un'altra uova", w / 2f, h * 0.48f, hintPaint)
            }
            return
        }
        if (currentAttempt > MAX_ATTEMPTS) {
            textPaint.color = Color.parseColor("#FF3366")
            canvas.drawText("L'uovo è fuggito...", w / 2f, h * 0.35f, textPaint)
            return
        }

        val attemptsLeft = MAX_ATTEMPTS - currentAttempt + 1
        val attemptsText = "Tentativi: ${"●".repeat(attemptsLeft)}${"○".repeat(currentAttempt - 1)}"
        canvas.drawText(attemptsText, w / 2f, h - 20f, attemptPaint)

        val r = maxR * (0.5f + pulseScale * 0.5f)
        val alpha = (80 + (1f - pulseScale) * 175).toInt().coerceIn(0, 255)
        pulsePaint.alpha = (alpha * 0.25f).toInt()
        canvas.drawCircle(cx, cy, r * 1.6f, pulsePaint)

        pulseRingPaint.alpha = alpha
        pulseRingPaint.strokeWidth = 3f + pulseScale * 5f
        canvas.drawCircle(cx, cy, r, pulseRingPaint)

        pulsePaint.alpha = alpha
        canvas.drawCircle(cx, cy, r * 0.3f, pulsePaint)

        for (i in 0 until BEATS_PER_ATTEMPT) {
            val bx = w * 0.15f + i * (w * 0.7f / (BEATS_PER_ATTEMPT - 1))
            val by = h * 0.82f
            val isPast = i < beatResults.size
            val isCurrent = i == beatResults.size
            val hit = if (isPast) beatResults[i] else false

            when {
                isPast && hit -> canvas.drawCircle(bx, by, 16f, beatHitPaint)
                isPast && !hit -> canvas.drawCircle(bx, by, 16f, beatMissPaint)
                isCurrent -> {
                    beatGuidePaint.color = eggColor
                    beatGuidePaint.strokeWidth = 3f
                    canvas.drawCircle(bx, by, 18f, beatGuidePaint)
                }
                else -> canvas.drawCircle(bx, by, 12f, beatGuidePaint)
            }
        }

        val beatText = "Battito ${(beatResults.size + 1).coerceAtMost(BEATS_PER_ATTEMPT)}/$BEATS_PER_ATTEMPT"
        hintPaint.color = Color.parseColor("#888899")
        canvas.drawText(beatText, w / 2f, h * 0.88f, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isResult) {
                if (currentAttempt > MAX_ATTEMPTS) return true
                if (resultLabel == "CATTURATO!") return true
                isResult = false
                beatResults.clear()
                startRound()
                return true
            }
            if (!isPlaying && currentAttempt == 0) {
                startRound()
                return true
            }
            if (isPlaying) {
                registerTap()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startRound() {
        currentAttempt++
        isPlaying = true
        isResult = false
        beatIndex = 0
        beatsHit = 0
        beatResults.clear()
        beatInterval = (600L - (currentAttempt - 1) * 50L).coerceAtLeast(400L)

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = beatInterval
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = (1f - abs(it.animatedFraction - 0.5f) * 2f).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }

        lastBeatTime = System.currentTimeMillis()
        invalidate()
    }

    private fun registerTap() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastBeatTime
        val phase = (elapsed % beatInterval).toFloat() / beatInterval
        val tapQuality = 1f - abs(phase - 0.5f) * 2f
        val hit = tapQuality > 0.4f

        beatResults.add(hit)
        if (hit) beatsHit++

        if (beatResults.size >= BEATS_PER_ATTEMPT) {
            evaluateRound()
        }
        invalidate()
    }

    private fun evaluateRound() {
        isPlaying = false
        animator?.cancel()

        quality = (beatsHit.toFloat() / BEATS_PER_ATTEMPT).coerceIn(0f, 1f)
        miniGameListener?.onThrowAttempt(currentAttempt, quality)

        val catchChance = when {
            quality >= 0.8f -> 0.85f
            quality >= 0.5f -> 0.60f
            quality >= 0.3f -> 0.35f
            else -> 0.10f
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
        isResult = true
        invalidate()
    }
}
