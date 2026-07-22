package com.intelligame.huntix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.intelligame.huntix.WeatherType
import kotlin.math.sin
import kotlin.random.Random

class WeatherParticleOverlay(context: Context) : View(context) {

    private var weatherType: WeatherType = WeatherType.CLEAR
    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maxParticles = 80
    private var frameCount = 0

    private data class Particle(
        var x: Float,
        var y: Float,
        var speed: Float,
        var size: Float,
        var alpha: Int,
        var drift: Float = 0f,
        var phase: Float = 0f
    )

    fun setWeatherType(type: WeatherType) {
        weatherType = type
        particles.clear()
        when (type) {
            WeatherType.RAIN -> {
                for (i in 0 until maxParticles) {
                    particles.add(Particle(
                        x = Random.nextFloat() * width,
                        y = Random.nextFloat() * height,
                        speed = 12f + Random.nextFloat() * 10f,
                        size = 1.5f + Random.nextFloat() * 1.5f,
                        alpha = 60 + Random.nextInt(80),
                        drift = -1f + Random.nextFloat() * 0.5f
                    ))
                }
            }
            WeatherType.STORM -> {
                for (i in 0 until maxParticles) {
                    particles.add(Particle(
                        x = Random.nextFloat() * width,
                        y = Random.nextFloat() * height,
                        speed = 14f + Random.nextFloat() * 12f,
                        size = 1.5f + Random.nextFloat() * 2f,
                        alpha = 70 + Random.nextInt(90),
                        drift = -1.5f + Random.nextFloat() * 0.5f
                    ))
                }
                for (i in 0 until 5) {
                    particles.add(Particle(
                        x = Random.nextFloat() * width,
                        y = 0f,
                        speed = 0f,
                        size = 0f,
                        alpha = 0,
                        phase = Random.nextFloat() * 200f
                    ))
                }
            }
            WeatherType.SNOW -> {
                for (i in 0 until maxParticles / 2) {
                    particles.add(Particle(
                        x = Random.nextFloat() * width,
                        y = Random.nextFloat() * height,
                        speed = 1.5f + Random.nextFloat() * 2f,
                        size = 3f + Random.nextFloat() * 4f,
                        alpha = 100 + Random.nextInt(100),
                        drift = -0.5f + Random.nextFloat(),
                        phase = Random.nextFloat() * 6.28f
                    ))
                }
            }
            WeatherType.FOG -> {
                for (i in 0 until 12) {
                    particles.add(Particle(
                        x = Random.nextFloat() * width,
                        y = Random.nextFloat() * height,
                        speed = 0.3f + Random.nextFloat() * 0.5f,
                        size = 80f + Random.nextFloat() * 120f,
                        alpha = 25 + Random.nextInt(35),
                        drift = 0.2f + Random.nextFloat() * 0.3f
                    ))
                }
            }
            else -> {}
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (weatherType == WeatherType.CLEAR || weatherType == WeatherType.WIND ||
            weatherType == WeatherType.NIGHT || weatherType == WeatherType.CLOUDY) {
            return
        }

        frameCount++

        when (weatherType) {
            WeatherType.RAIN -> drawRain(canvas)
            WeatherType.STORM -> {
                drawRain(canvas)
                drawLightning(canvas)
            }
            WeatherType.SNOW -> drawSnow(canvas)
            WeatherType.FOG -> drawFog(canvas)
            else -> {}
        }

        invalidate()
    }

    private fun drawRain(canvas: Canvas) {
        paint.strokeCap = Paint.Cap.ROUND
        for (p in particles) {
            paint.color = Color.argb(p.alpha, 100, 180, 255)
            paint.strokeWidth = p.size
            canvas.drawLine(p.x, p.y, p.x + p.drift * 3, p.y + p.speed * 2, paint)
            p.y += p.speed
            p.x += p.drift
            if (p.y > height) {
                p.y = -p.speed * 2
                p.x = Random.nextFloat() * width
            }
        }
    }

    private fun drawSnow(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (p in particles) {
            val wobble = sin(frameCount * 0.02f + p.phase) * 1.5f
            paint.color = Color.argb(p.alpha, 240, 245, 255)
            canvas.drawCircle(p.x + wobble, p.y, p.size, paint)
            p.y += p.speed
            p.x += wobble * 0.3f
            if (p.y > height + p.size) {
                p.y = -p.size
                p.x = Random.nextFloat() * width
            }
        }
    }

    private fun drawFog(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (p in particles) {
            paint.color = Color.argb(p.alpha, 180, 180, 200)
            canvas.drawCircle(p.x, p.y, p.size, paint)
            p.x += p.speed
            if (p.x > width + p.size) {
                p.x = -p.size
                p.y = Random.nextFloat() * height
            }
        }
    }

    private fun drawLightning(canvas: Canvas) {
        if (frameCount % 120 < 3 && frameCount > 20) {
            paint.color = Color.argb(180, 255, 255, 240)
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}
