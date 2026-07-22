package com.intelligame.huntix.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * SwipeToCatchView — vista di cattura swipe-based per outdoor.
 *
 * Mostra un uovo/creatura in basso e un cestino in alto.
 * Il giocatore swipa verso l'alto per lanciare.
 * 3 tentativi massimo. La qualità dello swipe determina il successo.
 *
 * Stati: IDLE → THROWING → SHAKE (test cattura) → CAPTURED / ESCAPED / IDLE
 */
class SwipeToCatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val SHAKE_ANIM_MS = 1200L
        private const val CAPTURE_FLASH_MS = 600L
        private const val ESCAPE_DRIFT_MS = 800L
    }

    interface OnCatchResult {
        fun onCaptured(totalAttempts: Int)
        fun onEscaped(totalAttempts: Int)
        fun onThrowAttempt(attempt: Int, quality: Float)
    }

    var listener: OnCatchResult? = null

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
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888899")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val qualityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val attemptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 0
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val successPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")
        textSize = 52f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var eggColor = Color.parseColor("#FFCC00")
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeStartTime = 0L

    private var state = State.IDLE
    private var throwProgress = 0f
    private var throwQuality = 0f
    private var currentAttempt = 0
    private var shakeProgress = 0f
    private var shakeRotation = 0f
    private var flashAlpha = 0
    private var captureScale = 1f
    private var escapeDriftX = 0f
    private var escapeDriftY = 0f
    private var successScale = 0f
    private var ringRadius = 0f
    private var ringAlpha = 0

    private var stateLabel = ""

    private enum class State {
        IDLE, THROWING, SHAKE, CAPTURED, ESCAPED, FAILED_ALL
    }

    fun setEggColor(color: Int) {
        eggColor = color
        eggPaint.color = color
        invalidate()
    }

    fun reset() {
        state = State.IDLE
        currentAttempt = 0
        throwProgress = 0f
        shakeProgress = 0f
        flashAlpha = 0
        captureScale = 1f
        escapeDriftX = 0f
        escapeDriftY = 0f
        successScale = 0f
        ringRadius = 0f
        stateLabel = ""
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val eggX = w / 2f
        val eggY = h - 100f
        val eggRadius = 32f
        val basketX = w / 2f
        val basketY = 80f
        val basketW = 60f
        val basketH = 35f

        // Attempts indicator
        val attemptsLeft = MAX_ATTEMPTS - currentAttempt
        val attemptsText = when {
            state == State.FAILED_ALL -> "Nessun tentativo rimasto"
            state == State.CAPTURED -> ""
            attemptsLeft <= 0 -> ""
            else -> "Tentativi: ${"●".repeat(attemptsLeft)}${"○".repeat(currentAttempt)}"
        }
        if (attemptsText.isNotEmpty()) {
            canvas.drawText(attemptsText, w / 2f, h - 20f, attemptPaint)
        }

        when (state) {
            State.IDLE -> {
                // Draw egg
                canvas.drawCircle(eggX, eggY, eggRadius, eggPaint)
                // Draw basket
                canvas.drawRoundRect(
                    basketX - basketW / 2, basketY - basketH / 2,
                    basketX + basketW / 2, basketY + basketH / 2, 8f, 8f, basketPaint
                )
                canvas.drawRoundRect(
                    basketX - basketW / 2 - 4, basketY - basketH / 2 - 6,
                    basketX + basketW / 2 + 4, basketY - basketH / 2 + 4, 6f, 6f, basketRimPaint
                )
                canvas.drawText("Swipe verso l'alto per lanciare", w / 2f, h / 2f, hintPaint)
            }

            State.THROWING -> {
                // Animate egg flying up
                val currentEggY = eggY + (basketY - eggY) * throwProgress
                val wobble = sin(throwProgress * Math.PI * 3).toFloat() * 15f * (1f - throwProgress)
                val currentEggX = eggX + wobble

                // Trajectory arc
                val path = Path()
                path.moveTo(eggX, eggY)
                path.quadTo(eggX + wobble * 2, (eggY + basketY) / 2, currentEggX, currentEggY)
                canvas.drawPath(path, trajectoryPaint)

                // Egg (shrinks as it goes up)
                canvas.drawCircle(
                    currentEggX, currentEggY,
                    eggRadius * (1f - throwProgress * 0.3f), eggPaint
                )

                // Basket
                canvas.drawRoundRect(
                    basketX - basketW / 2, basketY - basketH / 2,
                    basketX + basketW / 2, basketY + basketH / 2, 8f, 8f, basketPaint
                )
                canvas.drawRoundRect(
                    basketX - basketW / 2 - 4, basketY - basketH / 2 - 6,
                    basketX + basketW / 2 + 4, basketY - basketH / 2 + 4, 6f, 6f, basketRimPaint
                )

                // Quality label
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
                if (throwProgress > 0.6f) {
                    canvas.drawText(label, w / 2f, h / 2f, qualityPaint)
                }
            }

            State.SHAKE -> {
                // Egg in basket, shaking
                canvas.save()
                canvas.rotate(shakeRotation, basketX, basketY)

                // Glow ring while shaking
                if (ringAlpha > 0) {
                    ringPaint.alpha = ringAlpha
                    canvas.drawCircle(basketX, basketY, ringRadius, ringPaint)
                }

                canvas.drawCircle(basketX, basketY, eggRadius, eggPaint)
                canvas.drawRoundRect(
                    basketX - basketW / 2, basketY - basketH / 2,
                    basketX + basketW / 2, basketY + basketH / 2, 8f, 8f, basketPaint
                )
                canvas.drawRoundRect(
                    basketX - basketW / 2 - 4, basketY - basketH / 2 - 6,
                    basketX + basketW / 2 + 4, basketY - basketH / 2 + 4, 6f, 6f, basketRimPaint
                )

                canvas.restore()

                // Shake dots
                val dots = (shakeProgress * 3).toInt().coerceIn(0, 3)
                if (dots > 0) {
                    val dotsText = "•".repeat(dots)
                    qualityPaint.color = Color.parseColor("#FFCC00")
                    qualityPaint.textSize = 48f
                    canvas.drawText(dotsText, w / 2f, h / 2f + 60f, qualityPaint)
                }
            }

            State.CAPTURED -> {
                canvas.save()
                val scale = captureScale
                canvas.translate(w / 2f, h / 2f)
                canvas.scale(scale, scale)

                // Flash overlay
                if (flashAlpha > 0) {
                    flashPaint.alpha = flashAlpha
                    canvas.drawRect(-w / 2f, -h / 2f, w / 2f, h / 2f, flashPaint)
                }

                // Success!
                canvas.drawText("CATTURATO!", 0f, 0f, successPaint)
                canvas.restore()

                // Star burst particles
                if (successScale > 0.1f) {
                    drawStarBurst(canvas, w / 2f, h / 2f, 120f * successScale, eggPaint)
                }
            }

            State.ESCAPED -> {
                // Egg drifts away
                canvas.save()
                val escapedX = basketX + escapeDriftX
                val escapedY = basketY - 150f + escapeDriftY
                canvas.drawCircle(escapedX, escapedY, eggRadius, eggPaint)
                canvas.restore()

                qualityPaint.color = Color.parseColor("#FF3366")
                qualityPaint.textSize = 36f
                canvas.drawText("Fuggito!", w / 2f, h / 2f, qualityPaint)

                canvas.drawText(
                    "Tenta di nuovo! (${MAX_ATTEMPTS - currentAttempt} rimasti)",
                    w / 2f, h / 2f + 50f, hintPaint
                )
            }

            State.FAILED_ALL -> {
                qualityPaint.color = Color.parseColor("#FF3366")
                qualityPaint.textSize = 36f
                canvas.drawText("L'uovo è fuggito...", w / 2f, h / 2f, qualityPaint)
                hintPaint.color = Color.parseColor("#888899")
                canvas.drawText("Torna alla mappa per un'altra uova", w / 2f, h / 2f + 40f, hintPaint)
            }
        }
    }

    private fun drawStarBurst(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val points = 8
        for (i in 0 until points) {
            val angle = (i * 360f / points) * Math.PI.toFloat() / 180f
            val x = cx + kotlin.math.cos(angle) * radius
            val y = cy + kotlin.math.sin(angle) * radius
            canvas.drawCircle(x, y, 6f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (state) {
            State.SHAKE, State.THROWING, State.CAPTURED, State.FAILED_ALL -> return true
            else -> {}
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
                swipeStartTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (state != State.IDLE) return true
                val dy = event.y - swipeStartY
                val dt = (System.currentTimeMillis() - swipeStartTime).coerceAtLeast(1L)
                val velocity = (dy / dt) * 1000f

                if (dy < -50f && velocity < -200f) {
                    val speedScore = (abs(velocity) / 2000f).coerceIn(0f, 1f)
                    val straightness =
                        (1f - abs(event.x - swipeStartX) / abs(dy).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    throwQuality = (speedScore * 0.6f + straightness * 0.4f).coerceIn(0f, 1f)
                    startThrowAnimation()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startThrowAnimation() {
        state = State.THROWING
        throwProgress = 0f
        currentAttempt++
        invalidate()

        listener?.onThrowAttempt(currentAttempt, throwQuality)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                throwProgress = it.animatedFraction
                invalidate()
            }
            start()
        }.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                startShakeAnimation()
            }
        })
    }

    private fun startShakeAnimation() {
        state = State.SHAKE
        shakeProgress = 0f
        shakeRotation = 0f
        ringRadius = 40f
        ringAlpha = 255
        invalidate()

        // Shake animation (wobble basket)
        val shakeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SHAKE_ANIM_MS
            addUpdateListener {
                val t = it.animatedFraction
                shakeProgress = t
                // Shake pattern: left-right-left with damping
                val dampening = 1f - t
                shakeRotation = sin(t * Math.PI * 6).toFloat() * 15f * dampening
                // Ring grows
                ringRadius = 40f + t * 60f
                ringAlpha = ((1f - t) * 255).toInt()
                invalidate()
            }
        }

        // Determine success based on quality + randomness
        val catchChance = when {
            throwQuality >= 0.8f -> 0.85f
            throwQuality >= 0.5f -> 0.60f
            throwQuality >= 0.3f -> 0.35f
            else -> 0.15f
        }
        val caught = Math.random() < catchChance

        shakeAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (caught) {
                    startCaptureAnimation()
                } else {
                    startEscapeAnimation()
                }
            }
        })

        shakeAnim.start()
    }

    private fun startCaptureAnimation() {
        state = State.CAPTURED
        flashAlpha = 255
        captureScale = 0.5f
        successScale = 0f
        invalidate()

        val flashAnim = ValueAnimator.ofInt(255, 0).apply {
            duration = CAPTURE_FLASH_MS
            addUpdateListener {
                flashAlpha = it.animatedValue as Int
                invalidate()
            }
        }

        val scaleAnim = ObjectAnimator.ofFloat(this, "captureScale", 0.5f, 1.2f, 1f).apply {
            duration = CAPTURE_FLASH_MS
            interpolator = OvershootInterpolator()
        }

        val starAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CAPTURE_FLASH_MS
            addUpdateListener {
                successScale = it.animatedFraction
                invalidate()
            }
        }

        AnimatorSet().apply {
            playTogether(flashAnim, scaleAnim, starAnim)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    listener?.onCaptured(currentAttempt)
                }
            })
            start()
        }
    }

    private fun startEscapeAnimation() {
        state = State.ESCAPED
        escapeDriftX = 0f
        escapeDriftY = 0f
        invalidate()

        val driftX = (Math.random() * 200 - 100).toFloat()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ESCAPE_DRIFT_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedFraction
                escapeDriftX = driftX * t
                escapeDriftY = -30f * t
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (currentAttempt >= MAX_ATTEMPTS) {
                        state = State.FAILED_ALL
                        invalidate()
                        listener?.onEscaped(currentAttempt)
                    } else {
                        state = State.IDLE
                        shakeProgress = 0f
                        ringRadius = 0f
                        ringAlpha = 0
                        invalidate()
                    }
                }
            })
            start()
        }
    }
}
