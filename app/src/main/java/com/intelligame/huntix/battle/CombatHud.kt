package com.intelligame.huntix.battle

import android.graphics.*
import kotlin.math.sin

class CombatHud {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    data class Portrait(val name: String, val color: Int, val level: Int)

    fun draw(
        canvas: Canvas, w: Float, h: Float,
        playerHp: Float, enemyHp: Float,
        playerMaxHp: Float, enemyMaxHp: Float,
        timeRemainingMs: Long,
        round: Int, maxRounds: Int,
        playerWins: Int, enemyWins: Int,
        superBar: Float,
        playerPortrait: Portrait, enemyPortrait: Portrait,
        scale: Float
    ) {
        val s = scale
        val barH = 18f * s
        val segCount = 10
        val segGap = 2f * s
        val portraitR = 18f * s
        val topY = 12f * s
        val nameH = 10f * s

        drawPlayerHud(canvas, w, h, playerHp, playerMaxHp, barH, segCount, segGap,
            portraitR, topY, nameH, playerPortrait, s, isPlayer = true)
        drawEnemyHud(canvas, w, h, enemyHp, enemyMaxHp, barH, segCount, segGap,
            portraitR, topY, nameH, enemyPortrait, s, isPlayer = false)

        drawTimer(canvas, w, timeRemainingMs, s, topY, barH)
        drawRoundIndicator(canvas, w, round, maxRounds, playerWins, enemyWins, s, topY, barH)
        drawSpecialBar(canvas, w, h, superBar, s)
    }

    private fun drawPlayerHud(
        c: Canvas, w: Float, h: Float,
        hp: Float, maxHp: Float, barH: Float,
        segCount: Int, segGap: Float,
        portraitR: Float, topY: Float, nameH: Float,
        portrait: Portrait, s: Float, isPlayer: Boolean
    ) {
        val barMaxW = w * 0.34f
        val bx = 50f * s
        val by = topY + nameH + 4f * s
        val ratio = (hp / maxHp).coerceIn(0f, 1f)
        val segW = (barMaxW - segGap * (segCount - 1)) / segCount

        bgPaint.color = Color.parseColor("#1A0A2A")
        bgPaint.style = Paint.Style.FILL
        c.drawRoundRect(bx - 3f, by - 3f, bx + barMaxW + 3f, by + barH + 3f, 4f * s, 4f * s, bgPaint)

        bgPaint.color = Color.parseColor("#2A1840")
        c.drawRoundRect(bx, by, bx + barMaxW, by + barH, 3f * s, 3f * s, bgPaint)

        val filledSegs = (ratio * segCount).toInt()
        repeat(segCount) { i ->
            val sx = bx + i * (segW + segGap)
            val healthRatio = i.toFloat() / segCount
            val segColor = when {
                healthRatio > 0.6f -> Color.parseColor("#37E06B")
                healthRatio > 0.3f -> Color.parseColor("#FFD740")
                else -> Color.parseColor("#FF5252")
            }
            val alpha = if (i < filledSegs) 255 else 40
            segPaint.color = segColor
            segPaint.alpha = alpha
            segPaint.style = Paint.Style.FILL
            c.drawRoundRect(sx, by + 1f, sx + segW, by + barH - 1f, 2f * s, 2f * s, segPaint)

            if (i < filledSegs) {
                segPaint.color = Color.WHITE
                segPaint.alpha = 30
                c.drawRoundRect(sx, by + 1f, sx + segW, by + barH * 0.4f, 2f * s, 2f * s, segPaint)
            }
        }
        segPaint.alpha = 255

        drawPortrait(c, bx - portraitR - 8f * s, by + barH / 2f, portraitR, portrait, s)

        paint.color = Color.WHITE
        paint.textSize = 11f * s
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        c.drawText(portrait.name, bx, by - 2f * s, paint)
        paint.typeface = Typeface.DEFAULT

        paint.color = Color.parseColor("#9090B0")
        paint.textSize = 8f * s
        c.drawText("Lv.${portrait.level}", bx, by + barH + 10f * s, paint)
    }

    private fun drawEnemyHud(
        c: Canvas, w: Float, h: Float,
        hp: Float, maxHp: Float, barH: Float,
        segCount: Int, segGap: Float,
        portraitR: Float, topY: Float, nameH: Float,
        portrait: Portrait, s: Float, isPlayer: Boolean
    ) {
        val barMaxW = w * 0.34f
        val bx = w - 50f * s - barMaxW
        val by = topY + nameH + 4f * s
        val ratio = (hp / maxHp).coerceIn(0f, 1f)
        val segW = (barMaxW - segGap * (segCount - 1)) / segCount

        bgPaint.color = Color.parseColor("#1A0A2A")
        bgPaint.style = Paint.Style.FILL
        c.drawRoundRect(bx - 3f, by - 3f, bx + barMaxW + 3f, by + barH + 3f, 4f * s, 4f * s, bgPaint)

        bgPaint.color = Color.parseColor("#2A1840")
        c.drawRoundRect(bx, by, bx + barMaxW, by + barH, 3f * s, 3f * s, bgPaint)

        val filledSegs = (ratio * segCount).toInt()
        repeat(segCount) { i ->
            val sx = bx + barMaxW - (i + 1) * (segW + segGap) + segGap
            val healthRatio = i.toFloat() / segCount
            val segColor = when {
                healthRatio > 0.6f -> Color.parseColor("#FF5252")
                healthRatio > 0.3f -> Color.parseColor("#FF8A50")
                else -> Color.parseColor("#FF1744")
            }
            val alpha = if (i < filledSegs) 255 else 40
            segPaint.color = segColor
            segPaint.alpha = alpha
            segPaint.style = Paint.Style.FILL
            c.drawRoundRect(sx, by + 1f, sx + segW, by + barH - 1f, 2f * s, 2f * s, segPaint)

            if (i < filledSegs) {
                segPaint.color = Color.WHITE
                segPaint.alpha = 25
                c.drawRoundRect(sx, by + 1f, sx + segW, by + barH * 0.35f, 2f * s, 2f * s, segPaint)
            }
        }
        segPaint.alpha = 255

        drawPortrait(c, bx + barMaxW + portraitR + 8f * s, by + barH / 2f, portraitR, portrait, s)

        paint.color = Color.WHITE
        paint.textSize = 11f * s
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        c.drawText(portrait.name, bx + barMaxW, by - 2f * s, paint)
        paint.typeface = Typeface.DEFAULT

        paint.color = Color.parseColor("#9090B0")
        paint.textSize = 8f * s
        paint.textAlign = Paint.Align.RIGHT
        c.drawText("Lv.${portrait.level}", bx + barMaxW, by + barH + 10f * s, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawPortrait(c: Canvas, cx: Float, cy: Float, r: Float, p: Portrait, s: Float) {
        bgPaint.color = Color.parseColor("#0D0820")
        bgPaint.style = Paint.Style.FILL
        c.drawCircle(cx, cy, r + 3f * s, bgPaint)

        bgPaint.color = p.color
        bgPaint.style = Paint.Style.STROKE
        bgPaint.strokeWidth = 2.5f * s
        c.drawCircle(cx, cy, r, bgPaint)

        bgPaint.color = p.color
        bgPaint.alpha = 60
        bgPaint.style = Paint.Style.FILL
        c.drawCircle(cx, cy, r, bgPaint)
        bgPaint.alpha = 255

        paint.color = Color.WHITE
        paint.textSize = 14f * s
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        val initial = p.name.first().toString()
        val textY = cy + 5f * s
        c.drawText(initial, cx, textY, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTimer(c: Canvas, w: Float, ms: Long, s: Float, topY: Float, barH: Float) {
        val secs = (ms / 1000f).toInt()
        val cx = w / 2f
        val cy = topY + barH / 2f + 10f * s
        val timerR = 22f * s

        bgPaint.color = Color.parseColor("#1A0A2A")
        bgPaint.style = Paint.Style.FILL
        c.drawCircle(cx, cy, timerR + 4f * s, bgPaint)

        bgPaint.color = Color.parseColor("#2A1850")
        bgPaint.style = Paint.Style.STROKE
        bgPaint.strokeWidth = 2f * s
        c.drawCircle(cx, cy, timerR, bgPaint)

        val timeRatio = secs.toFloat() / 90f
        val ringColor = when {
            secs > 30 -> Color.parseColor("#66FF88")
            secs > 10 -> Color.parseColor("#FFD740")
            else -> Color.parseColor("#FF5252")
        }
        bgPaint.color = ringColor
        bgPaint.strokeWidth = 3f * s
        val sweepAngle = timeRatio * 360f
        c.drawArc(
            cx - timerR, cy - timerR, cx + timerR, cy + timerR,
            -90f, sweepAngle, false, bgPaint
        )

        paint.color = Color.WHITE
        paint.textSize = 16f * s
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("monospace", Typeface.BOLD)
        c.drawText(String.format("%02d", secs), cx, cy + 6f * s, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawRoundIndicator(
        c: Canvas, w: Float,
        round: Int, maxRounds: Int,
        playerWins: Int, enemyWins: Int,
        s: Float, topY: Float, barH: Float
    ) {
        val cx = w / 2f
        val cy = topY + barH + 30f * s
        val dotR = 4f * s
        val gap = 14f * s
        val totalW = (maxRounds - 1) * gap
        val startX = cx - totalW / 2f

        paint.textSize = 8f * s
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.parseColor("#8080A0")
        c.drawText("ROUND $round", cx, cy - dotR - 4f * s, paint)

        repeat(maxRounds) { i ->
            val dx = startX + i * gap
            when {
                i < playerWins -> {
                    bgPaint.color = Color.parseColor("#37E06B")
                    bgPaint.style = Paint.Style.FILL
                    c.drawCircle(dx, cy, dotR, bgPaint)
                }
                i < playerWins + enemyWins -> {
                    bgPaint.color = Color.parseColor("#FF5252")
                    bgPaint.style = Paint.Style.FILL
                    c.drawCircle(dx, cy, dotR, bgPaint)
                }
                else -> {
                    bgPaint.color = Color.parseColor("#3A2860")
                    bgPaint.style = Paint.Style.FILL
                    c.drawCircle(dx, cy, dotR, bgPaint)
                    bgPaint.color = Color.parseColor("#6050A0")
                    bgPaint.style = Paint.Style.STROKE
                    bgPaint.strokeWidth = 1f * s
                    c.drawCircle(dx, cy, dotR, bgPaint)
                }
            }
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawSpecialBar(c: Canvas, w: Float, h: Float, bar: Float, s: Float) {
        val barW = w * 0.22f
        val barH = 5f * s
        val cx = w / 2f
        val by = h - 70f * s
        val bx = cx - barW / 2f
        val ratio = bar.coerceIn(0f, 1f)

        bgPaint.color = Color.parseColor("#1A0A2A")
        bgPaint.style = Paint.Style.FILL
        c.drawRoundRect(bx - 2f, by - 2f, bx + barW + 2f, by + barH + 2f, 3f * s, 3f * s, bgPaint)

        val fillColor = if (ratio >= 1f) {
            val pulse = (System.currentTimeMillis() % 500).toFloat() / 500f
            val bright = (sin(pulse * Math.PI * 2.0).toFloat() * 0.3f + 0.7f)
            Color.rgb(
                (255f * bright).toInt(),
                (200f * bright).toInt(),
                (50f * bright).toInt()
            )
        } else {
            Color.parseColor("#8B5CF6")
        }
        bgPaint.color = fillColor
        c.drawRoundRect(bx, by, bx + barW * ratio, by + barH, 2f * s, 2f * s, bgPaint)

        if (ratio >= 1f) {
            bgPaint.color = Color.WHITE
            bgPaint.alpha = 80
            c.drawRoundRect(bx, by, bx + barW, by + barH * 0.4f, 2f * s, 2f * s, bgPaint)
            bgPaint.alpha = 255
        }

        paint.color = Color.parseColor("#A080D0")
        paint.textSize = 7f * s
        paint.textAlign = Paint.Align.CENTER
        c.drawText("SPECIAL", cx, by - 3f * s, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}
