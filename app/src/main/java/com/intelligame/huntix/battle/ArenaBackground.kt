package com.intelligame.huntix.battle

import android.graphics.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ArenaBackground {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crowdPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ambientDust = mutableListOf<Dust>()

    data class Dust(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var size: Float, var alpha: Float,
        var phase: Float
    )

    fun draw(canvas: Canvas, w: Float, h: Float, timeSec: Float) {
        val horizon = h * 0.52f
        drawSky(canvas, w, horizon, timeSec)
        drawStars(canvas, w, horizon, timeSec)
        drawMountains(canvas, w, horizon)
        drawCrowd(canvas, w, horizon, timeSec)
        drawArenaFloor(canvas, w, h, horizon, timeSec)
        drawNeonGrid(canvas, w, h, horizon, timeSec)
        drawAmbientDust(canvas, w, h, timeSec)
    }

    private fun drawSky(c: Canvas, w: Float, horizon: Float, t: Float) {
        val shift = sin(t * 0.1f) * 0.05f
        val g = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(
                Color.parseColor("#05051A"),
                Color.parseColor("#0E0A30"),
                Color.parseColor("#1A1050"),
                Color.parseColor("#3D1A6E"),
                Color.rgb((120 + shift * 200).toInt().coerceIn(60, 160), 30, 40),
                Color.rgb((200 + shift * 100).toInt().coerceIn(160, 240), 80, 30)
            ),
            floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = g
        paint.style = Paint.Style.FILL
        c.drawRect(0f, 0f, w, horizon, paint)
        paint.shader = null
    }

    private fun drawStars(c: Canvas, w: Float, horizon: Float, t: Float) {
        paint.style = Paint.Style.FILL
        val seed = 42L
        val rng = Random(seed)
        repeat(35) {
            val sx = rng.nextFloat() * w
            val sy = rng.nextFloat() * horizon * 0.7f
            val flicker = (sin(t * 2f + sx * 0.01f) * 0.3f + 0.7f)
            paint.alpha = (flicker * 200f).toInt().coerceIn(40, 220)
            paint.color = Color.WHITE
            c.drawCircle(sx, sy, 1.2f + rng.nextFloat() * 0.8f, paint)
        }
        paint.alpha = 255
    }

    private fun drawMountains(c: Canvas, w: Float, horizon: Float) {
        val path = Path()
        path.moveTo(0f, horizon)
        val rng = Random(123L)
        var x = 0f
        val step = w / 12f
        repeat(14) {
            val peakH = 30f + rng.nextFloat() * 60f
            val px = x + step * 0.5f
            path.lineTo(px, horizon - peakH)
            x += step
            path.lineTo(x, horizon - 10f - rng.nextFloat() * 20f)
        }
        path.lineTo(w, horizon)
        path.close()

        paint.color = Color.parseColor("#0A0820")
        paint.style = Paint.Style.FILL
        c.drawPath(path, paint)

        val path2 = Path()
        path2.moveTo(0f, horizon)
        x = 0f
        val rng2 = Random(456L)
        repeat(14) {
            val peakH = 15f + rng2.nextFloat() * 35f
            val px = x + step * 0.4f
            path2.lineTo(px, horizon - peakH)
            x += step * 1.1f
            path2.lineTo(x, horizon - 5f - rng2.nextFloat() * 12f)
        }
        path2.lineTo(w, horizon)
        path2.close()
        paint.color = Color.parseColor("#120E30")
        c.drawPath(path2, paint)
    }

    private fun drawCrowd(c: Canvas, w: Float, horizon: Float, t: Float) {
        val tribuneTop = horizon - 8f
        val tribuneBot = horizon + 18f
        val g = LinearGradient(0f, tribuneTop, 0f, tribuneBot,
            intArrayOf(Color.parseColor("#1A1440"), Color.parseColor("#251850")),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        paint.shader = g
        paint.style = Paint.Style.FILL
        c.drawRect(0f, tribuneTop, w, tribuneBot, paint)
        paint.shader = null

        val rng = Random(789L)
        val count = (w / 8f).toInt()
        repeat(count) {
            val cx = it * 8f + rng.nextFloat() * 4f
            val cy = tribuneTop + 5f + rng.nextFloat() * 8f
            val h = 5f + rng.nextFloat() * 4f
            val hue = rng.nextFloat() * 360f
            val color = Color.HSVToColor(160, floatArrayOf(hue, 0.4f, 0.5f))
            crowdPaint.color = color
            crowdPaint.style = Paint.Style.FILL
            crowdPaint.strokeWidth = 0f
            c.drawRect(cx - 2f, cy - h, cx + 2f, cy, crowdPaint)

            val headBob = sin(t * 3f + cx * 0.1f) * 1f
            crowdPaint.color = Color.HSVToColor(180, floatArrayOf(hue, 0.3f, 0.6f))
            c.drawCircle(cx, cy - h - 2.5f + headBob, 2f, crowdPaint)
        }
    }

    private fun drawArenaFloor(c: Canvas, w: Float, h: Float, horizon: Float, t: Float) {
        val floorTop = horizon + 18f
        val g = LinearGradient(0f, floorTop, 0f, h,
            intArrayOf(Color.parseColor("#1A1030"), Color.parseColor("#0D0820")),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        paint.shader = g
        paint.style = Paint.Style.FILL
        c.drawRect(0f, floorTop, w, h, paint)
        paint.shader = null

        val edgeGlow = Paint(Paint.ANTI_ALIAS_FLAG)
        edgeGlow.color = Color.parseColor("#3D1A6E")
        edgeGlow.strokeWidth = 2f
        edgeGlow.style = Paint.Style.STROKE
        c.drawLine(0f, floorTop, w, floorTop, edgeGlow)

        val ringPulse = (sin(t * 1.5f) * 0.15f + 0.85f)
        edgeGlow.color = Color.argb((40 * ringPulse).toInt(), 100, 60, 200)
        edgeGlow.strokeWidth = 4f
        c.drawLine(0f, floorTop, w, floorTop, edgeGlow)
    }

    private fun drawNeonGrid(c: Canvas, w: Float, h: Float, horizon: Float, t: Float) {
        val floorTop = horizon + 20f
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = 1f

        val alpha = (30 + sin(t * 0.8f) * 10f).toInt()
        gridPaint.color = Color.argb(alpha, 80, 40, 180)

        val vanishX = w * 0.5f
        val vanishY = floorTop
        val lines = 10
        repeat(lines) { i ->
            val frac = (i + 1).toFloat() / lines
            val y = floorTop + (h - floorTop) * frac * frac
            val spread = (y - vanishY) * 0.8f
            c.drawLine(vanishX - spread, y, vanishX + spread, y, gridPaint)
        }

        val vLines = 8
        repeat(vLines) { i ->
            val topX = vanishX + (i - vLines / 2) * 3f
            val botFrac = (i.toFloat() / vLines - 0.5f) * 2f
            val botX = vanishX + botFrac * w * 0.55f
            c.drawLine(topX, vanishY, botX, h, gridPaint)
        }

        gridPaint.color = Color.argb((alpha / 2), 60, 120, 220)
        gridPaint.strokeWidth = 0.5f
        val innerLines = 5
        repeat(innerLines) { i ->
            val frac = (i + 1).toFloat() / innerLines
            val y = floorTop + (h - floorTop) * frac * frac
            val spread = (y - vanishY) * 0.4f
            c.drawLine(vanishX - spread, y, vanishX + spread, y, gridPaint)
        }
    }

    private fun drawAmbientDust(c: Canvas, w: Float, h: Float, t: Float) {
        if (ambientDust.isEmpty()) {
            val rng = Random(System.nanoTime())
            repeat(20) {
                ambientDust.add(Dust(
                    x = rng.nextFloat() * w,
                    y = h * 0.5f + rng.nextFloat() * h * 0.4f,
                    vx = (rng.nextFloat() - 0.5f) * 0.3f,
                    vy = -0.1f - rng.nextFloat() * 0.2f,
                    size = 1f + rng.nextFloat() * 2f,
                    alpha = 0.2f + rng.nextFloat() * 0.3f,
                    phase = rng.nextFloat() * 6.28f
                ))
            }
        }

        paint.style = Paint.Style.FILL
        val it = ambientDust.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.x += d.vx + sin(t + d.phase) * 0.2f
            d.y += d.vy
            if (d.y < h * 0.3f || d.x < -20f || d.x > w + 20f) {
                d.x = Random.nextFloat() * w
                d.y = h * 0.85f + Random.nextFloat() * h * 0.15f
                d.vy = -0.1f - Random.nextFloat() * 0.2f
            }
            val pulse = sin(t * 2f + d.phase) * 0.1f + 0.9f
            paint.alpha = (d.alpha * pulse * 255f).toInt().coerceIn(0, 100)
            paint.color = Color.parseColor("#B090FF")
            c.drawCircle(d.x, d.y, d.size, paint)
        }
        paint.alpha = 255
    }
}
