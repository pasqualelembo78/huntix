package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * SkyEventOverlay — disegna elementi animati nel cielo della mappa:
 *  - Palloni colorati durante eventi Egg Rush
 *  - Fulmini durante Double XP
 *  - Uova giganti durante Mystery Eggs
 *  - Stelle durante Golden Hour
 *
 * Aggiungere al FrameLayout della mappa MapLibre.
 * Chiamare setEventType() con il tipo di evento attivo.
 * L'overlay si anima automaticamente con frame-based animation.
 */
class SkyEventOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class SkyEvent { NONE, EGG_RUSH, DOUBLE_XP, MYSTERY_EGGS, GOLDEN_HOUR, LEGENDARY_WEEK }

    private var eventType: SkyEvent = SkyEvent.NONE
    private var animFrame = 0
    private val maxFrames = 300

    // Balloon data (x position, speed, color)
    private data class Balloon(val startX: Float, val y: Float, val speed: Float, val color: Int, val size: Float)
    private val balloons = mutableListOf<Balloon>()

    // Lightning bolt data
    private data class Lightning(val x: Float, val startY: Float, val length: Float, val alpha: Int)
    private val lightnings = mutableListOf<Lightning>()

    // Star data
    private data class Star(val x: Float, val y: Float, val size: Float, val twinklePhase: Float)
    private val stars = mutableListOf<Star>()

    private val balloonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFEB3B.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD700.toInt()
    }
    private val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (eventType != SkyEvent.NONE) {
                animFrame = (animFrame + 1) % maxFrames
                invalidate()
                handler.postDelayed(this, 33) // ~30fps
            }
        }
    }

    fun setEventType(type: SkyEvent) {
        if (type == eventType) return
        eventType = type
        animFrame = 0
        generateElements()
        if (type != SkyEvent.NONE) {
            handler.removeCallbacks(animRunnable)
            handler.post(animRunnable)
        } else {
            handler.removeCallbacks(animRunnable)
            invalidate()
        }
    }

    private fun generateElements() {
        balloons.clear()
        lightnings.clear()
        stars.clear()

        val w = width.toFloat().coerceAtLeast(1080f)
        val h = height.toFloat().coerceAtLeast(1920f)

        when (eventType) {
            SkyEvent.EGG_RUSH -> {
                // Colorful balloons floating across
                val colors = intArrayOf(
                    0xFFE91E63.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(),
                    0xFFFF9800.toInt(), 0xFF9C27B0.toInt()
                )
                for (i in 0..4) {
                    balloons.add(Balloon(
                        startX = -100f + i * (w / 4),
                        y = 80f + (i % 3) * 60f,
                        speed = 1.5f + (i % 3) * 0.5f,
                        color = colors[i % colors.size],
                        size = 40f + (i % 2) * 10f
                    ))
                }
            }
            SkyEvent.DOUBLE_XP -> {
                // Lightning bolts
                for (i in 0..2) {
                    lightnings.add(Lightning(
                        x = w * 0.2f + i * w * 0.3f,
                        startY = 50f,
                        length = 200f + (i % 2) * 100f,
                        alpha = 200
                    ))
                }
            }
            SkyEvent.MYSTERY_EGGS -> {
                // Floating eggs
                for (i in 0..2) {
                    balloons.add(Balloon(
                        startX = w * 0.15f + i * w * 0.3f,
                        y = 100f + (i % 2) * 80f,
                        speed = 0.8f + i * 0.3f,
                        color = 0xFF9C27B0.toInt(),
                        size = 50f
                    ))
                }
            }
            SkyEvent.GOLDEN_HOUR -> {
                // Twinkling stars
                for (i in 0..8) {
                    stars.add(Star(
                        x = (50..(w.toInt() - 50)).random().toFloat(),
                        y = (30..300).random().toFloat(),
                        size = 3f + (i % 3) * 2f,
                        twinklePhase = i * 0.7f
                    ))
                }
            }
            SkyEvent.LEGENDARY_WEEK -> {
                // Golden sparkle stars
                for (i in 0..6) {
                    stars.add(Star(
                        x = (80..(w.toInt() - 80)).random().toFloat(),
                        y = (40..250).random().toFloat(),
                        size = 4f + (i % 2) * 3f,
                        twinklePhase = i * 1.2f
                    ))
                }
            }
            SkyEvent.NONE -> {}
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (eventType == SkyEvent.NONE) return

        when (eventType) {
            SkyEvent.EGG_RUSH, SkyEvent.MYSTERY_EGGS -> drawBalloons(canvas)
            SkyEvent.DOUBLE_XP -> drawLightning(canvas)
            SkyEvent.GOLDEN_HOUR, SkyEvent.LEGENDARY_WEEK -> drawStars(canvas)
            else -> {}
        }
    }

    private fun drawBalloons(canvas: Canvas) {
        for (b in balloons) {
            val x = (b.startX + animFrame * b.speed) % (width + 200f) - 100f
            val bobY = b.y + Math.sin(animFrame * 0.05 + b.startX).toFloat() * 8f

            // Balloon body (oval)
            balloonPaint.color = b.color
            canvas.drawOval(x - b.size * 0.5f, bobY - b.size, x + b.size * 0.5f, bobY, balloonPaint)

            // Shine
            balloonPaint.color = Color.argb(60, 255, 255, 255)
            canvas.drawOval(x - b.size * 0.2f, bobY - b.size * 0.8f, x + b.size * 0.1f, bobY - b.size * 0.4f, balloonPaint)

            // String
            canvas.drawLine(x, bobY, x, bobY + 40f, stringPaint)
        }
    }

    private fun drawLightning(canvas: Canvas) {
        val flashAlpha = if ((animFrame % 30) in 0..5) 255 else 0
        if (flashAlpha == 0) return

        for (l in lightnings) {
            lightningPaint.alpha = flashAlpha
            lightningPaint.strokeWidth = 4f

            val path = android.graphics.Path()
            path.moveTo(l.x, l.startY)
            var cx = l.x
            var cy = l.startY
            val segments = 6
            val segLen = l.length / segments
            for (s in 0 until segments) {
                cx += (Math.random() * 60 - 30).toFloat()
                cy += segLen
                path.lineTo(cx, cy)
            }
            canvas.drawPath(path, lightningPaint)
        }
    }

    private fun drawStars(canvas: Canvas) {
        for (s in stars) {
            val twinkle = Math.sin(animFrame * 0.08 + s.twinklePhase).toFloat()
            val alpha = ((twinkle + 1f) / 2f * 255).toInt().coerceIn(50, 255)
            val scale = 0.7f + ((twinkle + 1f) / 2f) * 0.6f

            starPaint.alpha = alpha
            val size = s.size * scale

            // 4-pointed star
            val path = android.graphics.Path()
            path.moveTo(s.x, s.y - size * 2)
            path.lineTo(s.x + size * 0.4f, s.y - size * 0.4f)
            path.lineTo(s.x + size * 2, s.y)
            path.lineTo(s.x + size * 0.4f, s.y + size * 0.4f)
            path.lineTo(s.x, s.y + size * 2)
            path.lineTo(s.x - size * 0.4f, s.y + size * 0.4f)
            path.lineTo(s.x - size * 2, s.y)
            path.lineTo(s.x - size * 0.4f, s.y - size * 0.4f)
            path.close()

            canvas.drawPath(path, starPaint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (eventType != SkyEvent.NONE) generateElements()
    }

    fun stop() {
        handler.removeCallbacks(animRunnable)
        eventType = SkyEvent.NONE
        invalidate()
    }
}
