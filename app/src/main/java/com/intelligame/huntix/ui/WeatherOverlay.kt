package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.random.Random

/**
 * WeatherOverlay — vista sovrapposta per effetti meteo (pioggia, nebbia, lampi).
 * Integra con DayNightOverlay: si posiziona sopra di esso.
 */
class WeatherOverlay(ctx: Context) : View(ctx) {

    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF90CAF9.toInt()
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFBDBDBD.toInt()
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    // Rain drops
    private data class RainDrop(var x: Float, var y: Float, var speed: Float, var length: Float)
    private val rainDrops = mutableListOf<RainDrop>()
    private var initialized = false

    // Current weather
    private var weather = "Soleggiato"
    private var flashAlpha = 0f

    fun setWeather(w: String) {
        weather = w
        if (!initialized && (w == "Pioggia" || w == "Temporale")) {
            for (i in 0 until 150) {
                rainDrops.add(RainDrop(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speed = 0.008f + Random.nextFloat() * 0.006f,
                    length = 0.015f + Random.nextFloat() * 0.01f
                ))
            }
            initialized = true
        }
    }

    fun triggerFlash() {
        flashAlpha = 0.6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        when (weather) {
            "Pioggia", "Temporale" -> drawRain(canvas, w, h)
            "Nebbia" -> drawFog(canvas, w, h)
        }

        // Flash (temporale)
        if (flashAlpha > 0.01f) {
            flashPaint.alpha = (flashAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, w, h, flashPaint)
            flashAlpha *= 0.85f
        }
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float) {
        for (drop in rainDrops) {
            drop.y += drop.speed
            if (drop.y > 1.05f) {
                drop.y = -0.05f
                drop.x = Random.nextFloat()
            }
            val x = drop.x * w
            val y = drop.y * h
            val len = drop.length * h
            canvas.drawLine(x, y, x - 2f, y + len, rainPaint)
        }

        // Slight blue overlay for rain atmosphere
        fogPaint.alpha = 25
        fogPaint.color = 0xFF42A5F5.toInt()
        canvas.drawRect(0f, 0f, w, h, fogPaint)
    }

    private fun drawFog(canvas: Canvas, w: Float, h: Float) {
        fogPaint.alpha = 80
        fogPaint.color = 0xFFBDBDBD.toInt()
        canvas.drawRect(0f, 0f, w, h, fogPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }
}
