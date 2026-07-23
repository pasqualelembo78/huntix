package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.intelligame.huntix.reallife.DayNightManager
import kotlin.random.Random

/**
 * DayNightOverlay — Custom View trasparente sovrapposta alla scena 3D.
 *
 * Disegna:
 * - Gradiente cielo (sfondo semitrasparente che tinge la scena)
 * - Sole (cerchio giallo in arco)
 * - Luna (mezzaluna bianca)
 * - Stelle (punti bianchi random, visibili di notte)
 * - Overlay scuro per la notte
 *
 * Va posizionato sopra SceneView nel FrameLayout con background trasparente.
 */
class DayNightOverlay(ctx: Context) : View(ctx) {

    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt()
        setShadowLayer(20f, 0f, 0f, 0x88FFB300.toInt())
    }
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFD54F.toInt()
    }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        setShadowLayer(12f, 0f, 0f, 0x66FFFFFF)
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    // Stars: pre-generated positions (normalized 0-1)
    private data class Star(val x: Float, val y: Float, val size: Float, val twinkleOffset: Float)
    private val stars = mutableListOf<Star>()
    private var starsGenerated = false

    // Clouds: pre-generated, drift slowly
    private data class Cloud(val baseX: Float, val y: Float, val scale: Float, val speed: Float, val blobs: List<Pair<Float, Float>>)
    private val clouds = mutableListOf<Cloud>()
    private var cloudsGenerated = false

    private var skyColors: DayNightManager.SkyColors? = null
    private var sunPos: Pair<Float, Float>? = null
    private var moonPos: Pair<Float, Float>? = null
    private var windowBrightness = 0f
    private var time = 0f  // for twinkle animation

    fun update(manager: DayNightManager) {
        skyColors = manager.getSkyColors()
        sunPos = manager.getSunPosition()
        moonPos = manager.getMoonPosition()
        windowBrightness = manager.getWindowBrightness()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        time += 0.016f

        // Generate stars once
        if (!starsGenerated) {
            for (i in 0 until 70) {
                stars.add(Star(
                    x = Random.nextFloat(),
                    y = Random.nextFloat() * 0.6f,  // upper 60% of sky
                    size = 1f + Random.nextFloat() * 2f,
                    twinkleOffset = Random.nextFloat() * 6.28f
                ))
            }
            starsGenerated = true
        }

        // Generate clouds once
        if (!cloudsGenerated) {
            for (i in 0 until 10) {
                val blobCount = 3 + Random.nextInt(3)
                val blobs = (0 until blobCount).map {
                    Pair(
                        (Random.nextFloat() - 0.5f) * 0.08f,
                        (Random.nextFloat() - 0.5f) * 0.03f
                    )
                }
                clouds.add(Cloud(
                    baseX = Random.nextFloat(),
                    y = 0.08f + Random.nextFloat() * 0.25f,
                    scale = 0.7f + Random.nextFloat() * 0.6f,
                    speed = 0.0002f + Random.nextFloat() * 0.0003f,
                    blobs = blobs
                ))
            }
            cloudsGenerated = true
        }

        // 1. Sky gradient background
        skyColors?.let { sc ->
            skyPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                sc.topColor, sc.bottomColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, skyPaint)
        }

        // 2. Stars (visible at night)
        skyColors?.let { sc ->
            val starAlpha = (sc.overlayAlpha * 2.5f).coerceIn(0f, 1f)
            if (starAlpha > 0.05f) {
                for (star in stars) {
                    val twinkle = (sin(time * 2f + star.twinkleOffset) * 0.3f + 0.7f).coerceIn(0f, 1f)
                    starPaint.alpha = (starAlpha * twinkle * 255).toInt()
                    canvas.drawCircle(star.x * w, star.y * h, star.size, starPaint)
                }
            }
        }

        // 3. Sun
        sunPos?.let { (nx, ny) ->
            val sx = nx * w
            val sy = (1f - ny) * h * 0.7f + h * 0.05f  // arco nella parte alta
            val sunRadius = w * 0.04f

            // Glow
            canvas.drawCircle(sx, sy, sunRadius * 3f, sunGlowPaint)
            // Sun body
            canvas.drawCircle(sx, sy, sunRadius, sunPaint)
        }

        // 3.5 Clouds (drift slowly, visible mainly during day)
        val cloudAlpha = if (windowBrightness < 0.3f) 0.8f else 0.3f
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = (cloudAlpha * 200).toInt()
        }
        for (cloud in clouds) {
            val cx = ((cloud.baseX + time * cloud.speed) % 1.2f - 0.1f) * w
            val cy = cloud.y * h
            val r = w * 0.025f * cloud.scale
            for ((bx, by) in cloud.blobs) {
                canvas.drawCircle(cx + bx * w, cy + by * h, r, cloudPaint)
            }
        }

        // 4. Moon
        moonPos?.let { (nx, ny) ->
            val mx = nx * w
            val my = (1f - ny) * h * 0.65f + h * 0.05f
            val moonRadius = w * 0.03f

            // Moon glow
            val moonGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x22FFFFFF
            }
            canvas.drawCircle(mx, my, moonRadius * 2.5f, moonGlow)

            // Moon body
            canvas.drawCircle(mx, my, moonRadius, moonPaint)

            // Crescent shadow (offset circle to create crescent)
            canvas.drawCircle(mx + moonRadius * 0.4f, my - moonRadius * 0.2f,
                moonRadius * 0.85f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = skyColors?.topColor ?: Color.rgb(10, 10, 26)
                })
        }

        // 5. Night overlay (darkens the whole scene)
        skyColors?.let { sc ->
            if (sc.overlayAlpha > 0.01f) {
                overlayPaint.color = sc.overlayColor
                overlayPaint.alpha = (sc.overlayAlpha * 255).toInt()
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Match parent size
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }
}
