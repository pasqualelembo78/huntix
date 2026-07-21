package com.intelligame.huntix.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * SwipeToCatchView — vista di cattura swipe-based per outdoor.
 *
 * Mostra un uovo in basso e un cestino in alto.
 * Il giocatore swipa verso l'alto per lanciare.
 * La qualità dello swipe (velocità + linearità) determina il successo.
 */
class SwipeToCatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnThrowResult {
        fun onResult(quality: Float)
    }

    var listener: OnThrowResult? = null

    private val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCC00")
        style = Paint.Style.FILL
    }
    private val basketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B4513")
        style = Paint.Style.FILL
    }
    private val basketRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0522D")
        style = Paint.Style.FILL
    }
    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFCC00")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888899")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val qualityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")
        textSize = 44f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var eggColor = Color.parseColor("#FFCC00")
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeStartTime = 0L
    private var animating = false
    private var throwProgress = 0f
    private var throwQuality = 0f
    private var throwResultShown = false
    private var trajectoryStartX = 0f
    private var trajectoryStartY = 0f

    fun setEggColor(color: Int) {
        eggColor = color
        eggPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val eggX = w / 2f
        val eggY = h - 80f
        val eggRadius = 30f

        val basketX = w / 2f
        val basketY = 80f
        val basketW = 60f
        val basketH = 35f

        if (!animating) {
            canvas.drawCircle(eggX, eggY, eggRadius, eggPaint)
            canvas.drawRoundRect(basketX - basketW / 2, basketY - basketH / 2,
                basketX + basketW / 2, basketY + basketH / 2, 8f, 8f, basketPaint)
            canvas.drawRoundRect(basketX - basketW / 2 - 4, basketY - basketH / 2 - 6,
                basketX + basketW / 2 + 4, basketY - basketH / 2 + 4, 6f, 6f, basketRimPaint)

            canvas.drawText(" Swipe verso l'alto per lanciare", w / 2f, h / 2f, hintPaint)
            return
        }

        val currentEggY = eggY + (basketY - eggY) * throwProgress
        val wobble = sin(throwProgress * Math.PI * 3).toFloat() * 15f * (1f - throwProgress)
        val currentEggX = eggX + wobble

        val path = Path()
        path.moveTo(eggX, eggY)
        path.quadTo(eggX + wobble * 2, (eggY + basketY) / 2, currentEggX, currentEggY)
        canvas.drawPath(path, trajectoryPaint)

        canvas.drawCircle(currentEggX, currentEggY, eggRadius * (1f - throwProgress * 0.3f), eggPaint)

        canvas.drawRoundRect(basketX - basketW / 2, basketY - basketH / 2,
            basketX + basketW / 2, basketY + basketH / 2, 8f, 8f, basketPaint)
        canvas.drawRoundRect(basketX - basketW / 2 - 4, basketY - basketH / 2 - 6,
            basketX + basketW / 2 + 4, basketY - basketH / 2 + 4, 6f, 6f, basketRimPaint)

        if (throwResultShown) {
            val label = when {
                throwQuality >= 0.8f -> "PERFETTO!"
                throwQuality >= 0.5f -> "BUONO!"
                throwQuality >= 0.3f -> "SCARSO..."
                else -> "MANCATO!"
            }
            val color = when {
                throwQuality >= 0.8f -> Color.parseColor("#00FF88")
                throwQuality >= 0.5f -> Color.parseColor("#FFCC00")
                throwQuality >= 0.3f -> Color.parseColor("#FF8800")
                else -> Color.parseColor("#FF3366")
            }
            qualityPaint.color = color
            canvas.drawText(label, w / 2f, h / 2f, qualityPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (animating) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
                swipeStartTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dy = event.y - swipeStartY
                val dt = (System.currentTimeMillis() - swipeStartTime).coerceAtLeast(1L)
                val velocity = (dy / dt) * 1000f

                if (dy < -50f && velocity < -200f) {
                    val speedScore = (abs(velocity) / 2000f).coerceIn(0f, 1f)
                    val straightness = (1f - abs(event.x - swipeStartX) / abs(dy).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    throwQuality = (speedScore * 0.6f + straightness * 0.4f).coerceIn(0f, 1f)
                    startThrowAnimation()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startThrowAnimation() {
        animating = true
        throwProgress = 0f
        throwResultShown = false
        invalidate()

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                throwProgress = it.animatedFraction
                if (throwProgress >= 0.95f && !throwResultShown) {
                    throwResultShown = true
                    listener?.onResult(throwQuality)
                }
                invalidate()
            }
            start()
        }
    }
}
