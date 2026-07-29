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
import kotlin.math.sin

class QuickCatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), CaptureMiniGame {

    companion object {
        const val MAX_ATTEMPTS = 3
    }

    private var miniGameListener: CaptureMiniGame.Listener? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1030")
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#00FF88")
        alpha = 100
    }
    private val zoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00FF88")
        alpha = 30
    }
    private val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFCC00")
    }
    private val eggGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFCC00")
        alpha = 30
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
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

    private var eggX = 0f
    private var eggY = 0f
    private var eggRadius = 36f
    private var zoneLeft = 0f
    private var zoneRight = 0f
    private var eggVel = 0f
    private var animator: ValueAnimator? = null
    private var h = 0f
    private var w = 0f
    private var laneY = 0f
    private var speedLevel = 0

    override fun getView(): View = this

    override fun setEggColor(color: Int) {
        eggColor = color
        eggPaint.color = color
        eggGlowPaint.color = color
        invalidate()
    }

    override fun reset() {
        currentAttempt = 0
        isPlaying = false
        isResult = false
        resultLabel = ""
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
        laneY = h * 0.55f
        eggY = laneY
        val zoneWidth = w * 0.2f
        zoneLeft = w / 2f - zoneWidth / 2
        zoneRight = w / 2f + zoneWidth / 2
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isPlaying && !isResult && currentAttempt == 0) {
            canvas.drawText("⚡ Quick Catch", w / 2f, h * 0.3f, textPaint)
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Tocca per iniziare", w / 2f, h * 0.45f, hintPaint)
            return
        }
        if (isResult) {
            drawResult(canvas)
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

        canvas.drawRoundRect(zoneLeft, laneY - 50f, zoneRight, laneY + 50f, 12f, 12f, zoneFillPaint)
        canvas.drawRoundRect(zoneLeft, laneY - 50f, zoneRight, laneY + 50f, 12f, 12f, zonePaint)

        eggGlowPaint.alpha = 40
        canvas.drawCircle(eggX, eggY, eggRadius * 1.8f, eggGlowPaint)
        canvas.drawCircle(eggX, eggY, eggRadius, eggPaint)

        val inZone = eggX >= zoneLeft && eggX <= zoneRight
        hintPaint.color = if (inZone) Color.parseColor("#00FF88") else Color.parseColor("#888899")
        val hintText = if (inZone) "COLPIRE!" else "Aspetta il momento..."
        canvas.drawText(hintText, w / 2f, laneY - 90f, hintPaint)

        val zoneMid = (zoneLeft + zoneRight) / 2
        val distFromCenter = abs(eggX - zoneMid)
        val maxDist = (zoneRight - zoneLeft) / 2 + eggRadius * 2
        val proximity = (1f - distFromCenter / maxDist).coerceIn(0f, 1f)
        hintPaint.color = Color.parseColor("#FFCC00")
        hintPaint.textSize = 18f
        canvas.drawText("Precisione: ${(proximity * 100).toInt()}%", w / 2f, laneY + 90f, hintPaint)
    }

    private fun drawResult(canvas: Canvas) {
        textPaint.color = resultColor
        canvas.drawText(resultLabel, w / 2f, h * 0.35f, textPaint)
        if (currentAttempt <= MAX_ATTEMPTS && resultLabel != "CATTURATO!") {
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Tocca per riprovare", w / 2f, h * 0.48f, hintPaint)
        } else if (currentAttempt > MAX_ATTEMPTS) {
            hintPaint.color = Color.parseColor("#888899")
            canvas.drawText("Torna alla mappa per un'altra uova", w / 2f, h * 0.48f, hintPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isResult) {
                if (currentAttempt > MAX_ATTEMPTS) return true
                if (resultLabel == "CATTURATO!") return true
                isResult = false
                startRound()
                return true
            }
            if (!isPlaying && currentAttempt == 0) {
                startRound()
                return true
            }
            if (isPlaying) {
                val zoneMid = (zoneLeft + zoneRight) / 2
                val distFromCenter = abs(eggX - zoneMid)
                val maxDist = (zoneRight - zoneLeft) / 2 + eggRadius * 2
                quality = (1f - distFromCenter / maxDist).coerceIn(0f, 1f)
                evaluateCatch()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startRound() {
        currentAttempt++
        isPlaying = true
        isResult = false
        speedLevel = currentAttempt

        val amplitude = w * 0.35f
        val centerX = w / 2f
        eggX = centerX - amplitude

        animator?.cancel()
        val duration = (1200 - speedLevel * 150).coerceAtLeast(600).toLong()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val t = it.animatedFraction
                eggX = centerX + sin(t * Math.PI * 2).toFloat() * amplitude
                invalidate()
            }
            start()
        }
        invalidate()
    }

    private fun evaluateCatch() {
        isPlaying = false
        isResult = true
        animator?.cancel()

        miniGameListener?.onThrowAttempt(currentAttempt, quality)

        val catchChance = when {
            quality >= 0.85f -> 0.90f
            quality >= 0.6f -> 0.65f
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
        invalidate()
    }
}
