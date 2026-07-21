package com.intelligame.huntix.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * CatchTimingBarView — barra orizzontale con indicatore mobile.
 * L'utente tocca per fermare l'indicatore nella zona dorata.
 */
class CatchTimingBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnTimingResult {
        fun onResult(success: Boolean, zoneMultiplier: Float)
    }

    var listener: OnTimingResult? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD54F")
        style = Paint.Style.FILL
        alpha = 180
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(6f, 0f, 0f, Color.parseColor("#80000000"))
    }
    private val perfectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
        alpha = 160
    }

    private var position = 0f       // 0..1
    private var speed = 0.025f
    private var direction = 1f
    private var running = false
    private var stopped = false

    // Zone in the center: 0.40 .. 0.60
    private val zoneStart = 0.38f
    private val zoneEnd = 0.62f
    // Perfect zone: 0.46 .. 0.54
    private val perfectStart = 0.45f
    private val perfectEnd = 0.55f

    private var animator: ValueAnimator? = null

    fun startTiming() {
        position = 0f
        direction = 1f
        running = true
        stopped = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (!stopped) {
                    position += speed * direction
                    if (position >= 1f) { position = 1f; direction = -1f }
                    if (position <= 0f) { position = 0f; direction = 1f }
                    invalidate()
                }
            }
            start()
        }
    }

    fun stopTiming() {
        if (!running || stopped) return
        stopped = true
        running = false
        animator?.cancel()
        animator = null
        val success = position in zoneStart..zoneEnd
        val multiplier = when {
            !success -> 0f
            position in perfectStart..perfectEnd -> 1.5f
            else -> 1.0f
        }
        listener?.onResult(success, multiplier)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()
        val barH = h * 0.35f
        val top = (h - barH) / 2f

        // Track
        canvas.drawRoundRect(RectF(0f, top, w, top + barH), barH / 2, barH / 2, trackPaint)

        // Green perfect zone
        val perfL = w * perfectStart
        val perfR = w * perfectEnd
        canvas.drawRoundRect(RectF(perfL, top, perfR, top + barH), barH / 4, barH / 4, perfectPaint)

        // Yellow zone
        val zoneL = w * zoneStart
        val zoneR = w * zoneEnd
        canvas.drawRect(zoneL, top, zoneL + 1, top + barH, zonePaint)
        canvas.drawRect(zoneR - 1, top, zoneR, top + barH, zonePaint)

        // Indicator
        val indX = w * position
        val indR = barH * 0.45f
        canvas.drawCircle(indX, top + barH / 2, indR, indicatorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN && running) {
            stopTiming()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
