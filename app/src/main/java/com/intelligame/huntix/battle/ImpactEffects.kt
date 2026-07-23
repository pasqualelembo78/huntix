package com.intelligame.huntix.battle

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class ImpactEffects {

    data class Spark(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float, var maxLife: Float,
        var length: Float, var angle: Float,
        var color: Int
    )

    data class StunStar(
        var angle: Float,
        var radius: Float,
        var speed: Float,
        var size: Float
    )

    data class GroundRing(
        var x: Float, var y: Float,
        var radius: Float, var maxRadius: Float,
        var life: Float, var color: Int
    )

    data class BlurLine(
        var x: Float, var y: Float,
        var length: Float, var angle: Float,
        var life: Float, var color: Int
    )

    private val sparks = mutableListOf<Spark>()
    private val stunStars = mutableListOf<StunStar>()
    private val groundRings = mutableListOf<GroundRing>()
    private val blurLines = mutableListOf<BlurLine>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var shakeX = 0f; private set
    var shakeY = 0f; private set
    private var shakeDuration = 0f
    private var shakeIntensity = 0f
    private var shakeTimer = 0f

    var screenFlashAlpha = 0f; private set
    private var screenFlashColor = Color.WHITE

    var slowMoFactor = 1f; private set
    private var slowMoTimer = 0f

    fun requestCameraShake(intensity: Float, duration: Float) {
        shakeIntensity = intensity
        shakeDuration = duration
        shakeTimer = duration
    }

    fun requestScreenFlash(color: Int, alpha: Float) {
        screenFlashColor = color
        screenFlashAlpha = alpha
    }

    fun requestSlowMo(factor: Float, duration: Float) {
        slowMoFactor = factor
        slowMoTimer = duration
    }

    fun spawnSparks(x: Float, y: Float, color: Int, count: Int, speed: Float = 8f) {
        val rng = Random(System.nanoTime())
        repeat(count) {
            val ang = rng.nextFloat() * PI.toFloat() * 2f
            val spd = speed * (0.4f + rng.nextFloat() * 0.6f)
            val life = 0.15f + rng.nextFloat() * 0.25f
            sparks.add(Spark(
                x = x, y = y,
                vx = cos(ang) * spd, vy = sin(ang) * spd - 2f,
                life = life, maxLife = life,
                length = 4f + rng.nextFloat() * 8f,
                angle = ang,
                color = color
            ))
        }
    }

    fun spawnMotionBlurLines(x: Float, y: Float, direction: Int, color: Int, count: Int = 4) {
        val rng = Random(System.nanoTime())
        repeat(count) {
            val offsetY = (rng.nextFloat() - 0.5f) * 40f
            blurLines.add(BlurLine(
                x = x, y = y + offsetY,
                length = 30f + rng.nextFloat() * 50f,
                angle = if (direction > 0) 0f else PI.toFloat(),
                life = 0.12f + rng.nextFloat() * 0.1f,
                color = color
            ))
        }
    }

    fun spawnGroundRing(x: Float, y: Float, color: Int, maxRadius: Float = 60f) {
        groundRings.add(GroundRing(x, y, 5f, maxRadius, 0.35f, color))
    }

    var stunStarX = 0f
    var stunStarY = 0f

    fun showStunStars(count: Int = 3, x: Float = 0f, y: Float = 0f) {
        stunStarX = x
        stunStarY = y
        stunStars.clear()
        val rng = Random(System.nanoTime())
        repeat(count) {
            stunStars.add(StunStar(
                angle = rng.nextFloat() * PI.toFloat() * 2f,
                radius = 14f + rng.nextFloat() * 6f,
                speed = 2f + rng.nextFloat() * 2f,
                size = 3f + rng.nextFloat() * 2f
            ))
        }
    }

    fun clearStunStars() {
        stunStars.clear()
    }

    fun update(dt: Float) {
        val effectiveDt = dt

        if (shakeTimer > 0f) {
            shakeTimer -= effectiveDt
            val decay = (shakeTimer / shakeDuration).coerceIn(0f, 1f)
            val intensity = shakeIntensity * decay * decay
            shakeX = (Random.nextFloat() - 0.5f) * intensity * 2f
            shakeY = (Random.nextFloat() - 0.5f) * intensity * 2f
        } else {
            shakeX = 0f; shakeY = 0f
        }

        if (screenFlashAlpha > 0f) {
            screenFlashAlpha = (screenFlashAlpha - effectiveDt * 6f).coerceAtLeast(0f)
        }

        if (slowMoTimer > 0f) {
            slowMoTimer -= effectiveDt
            if (slowMoTimer <= 0f) slowMoFactor = 1f
        }

        val sparkIt = sparks.iterator()
        while (sparkIt.hasNext()) {
            val s = sparkIt.next()
            s.x += s.vx * effectiveDt * 60f
            s.y += s.vy * effectiveDt * 60f
            s.vy += 5f * effectiveDt
            s.life -= effectiveDt
            if (s.life <= 0f) sparkIt.remove()
        }

        val blurIt = blurLines.iterator()
        while (blurIt.hasNext()) {
            val b = blurIt.next()
            b.life -= effectiveDt
            if (b.life <= 0f) blurIt.remove()
        }

        val ringIt = groundRings.iterator()
        while (ringIt.hasNext()) {
            val r = ringIt.next()
            val progress = 1f - (r.life / 0.35f)
            r.radius = r.maxRadius * progress
            r.life -= effectiveDt
            if (r.life <= 0f) ringIt.remove()
        }

        for (star in stunStars) {
            star.angle += star.speed * effectiveDt
        }
    }

    fun drawWorldEffects(canvas: Canvas, s: Float) {
        for (spark in sparks) {
            val alpha = ((spark.life / spark.maxLife) * 255f).toInt().coerceIn(0, 255)
            paint.color = spark.color
            paint.alpha = alpha
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * s
            paint.strokeCap = Paint.Cap.ROUND
            val endX = spark.x + cos(spark.angle) * spark.length * s * (spark.life / spark.maxLife)
            val endY = spark.y + sin(spark.angle) * spark.length * s * (spark.life / spark.maxLife)
            canvas.drawLine(spark.x, spark.y, endX, endY, paint)
        }
        paint.alpha = 255

        for (line in blurLines) {
            val alpha = ((line.life / 0.15f) * 150f).toInt().coerceIn(0, 180)
            paint.color = line.color
            paint.alpha = alpha
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * s
            paint.strokeCap = Paint.Cap.BUTT
            val endX = line.x + cos(line.angle.toDouble()).toFloat() * line.length * s
            canvas.drawLine(line.x, line.y, endX, line.y, paint)
        }
        paint.alpha = 255

        for (ring in groundRings) {
            val progress = 1f - (ring.life / 0.35f)
            val alpha = ((1f - progress) * 200f).toInt().coerceIn(0, 200)
            paint.color = ring.color
            paint.alpha = alpha
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (3f * (1f - progress) + 1f) * s
            canvas.drawOval(
                ring.x - ring.radius * s, ring.y - ring.radius * 0.3f * s,
                ring.x + ring.radius * s, ring.y + ring.radius * 0.3f * s,
                paint
            )
        }
        paint.alpha = 255

        for (star in stunStars) {
            val sx = stunStarX + cos(star.angle) * star.radius * s
            val sy = stunStarY - 90f * s + sin(star.angle) * star.radius * s * 0.5f
            drawStar(canvas, sx, sy, star.size * s, Color.parseColor("#FFD700"))
        }
    }

    private fun drawStar(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val path = Path()
        val spikes = 5
        val outerR = r
        val innerR = r * 0.4f
        repeat(spikes * 2) { i ->
            val ang = (i * PI / spikes - PI / 2).toFloat()
            val rad = if (i % 2 == 0) outerR else innerR
            val px = cx + cos(ang) * rad
            val py = cy + sin(ang) * rad
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.alpha = 220
        c.drawPath(path, paint)
        paint.alpha = 255
    }

    fun drawScreenOverlay(canvas: Canvas, w: Float, h: Float) {
        if (screenFlashAlpha > 0f) {
            val a = (screenFlashAlpha * 255f).toInt().coerceIn(0, 255)
            paint.color = screenFlashColor
            paint.alpha = a
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.alpha = 255
        }
    }

    fun currentSlowMoFactor(): Float = slowMoFactor

    fun reset() {
        sparks.clear()
        stunStars.clear()
        groundRings.clear()
        blurLines.clear()
        shakeX = 0f; shakeY = 0f; shakeTimer = 0f
        screenFlashAlpha = 0f
        slowMoFactor = 1f; slowMoTimer = 0f
    }
}
