package com.intelligame.huntix.battle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * ArenaView — rendering dell'arena di battaglia (sfondo AR trasparente + lottatori, effetti).
 */
class ArenaView(context: Context) : View(context) {
    var engine: FightingEngine? = null
    var animController: AnimationController? = null

    private val paint = Paint()
    private val handler = Handler(Looper.getMainLooper())
    private val render = object : Runnable {
        override fun run() {
            animController?.update(0.016f)
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(render)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(render)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val ground = h * 0.80f
        val u = (h / 600f).coerceAtLeast(0.6f)

        // Banda a terra (profondità su sfondo AR)
        paint.color = Color.BLACK; paint.alpha = 70
        canvas.drawRect(0f, ground, w, ground + 26f * u, paint); paint.alpha = 255

        val eng = engine ?: return
        val px = eng.playerController.positionX * w
        val ex = eng.enemyUnit.positionX * w
        val playerRight = px <= ex

        // Lottatori
        drawFighter(canvas, ex, ground, u, Color.parseColor("#EF5350"), false, !playerRight, eng.enemyUnit.attackTimer, eng.enemyUnit.hitFlash, "NEMICO", eng.enemyHpRatio, false)
        drawFighter(canvas, px, ground, u, Color.parseColor("#4FC3F7"), eng.playerController.isBlocking, playerRight, eng.playerController.attackTimer, eng.playerController.hitFlash, "TU", eng.playerHpRatio, true)

        // Timer round (centro alto)
        val secs = (eng.timeRemainingMs / 1000f).toInt()
        paint.color = Color.WHITE; paint.textSize = 30f * u; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format("%02d", secs), w / 2f, 56f * u, paint)
        paint.textAlign = Paint.Align.LEFT

        // Particelle
        animController?.particles?.forEach {
            paint.color = it.color; paint.alpha = (it.life * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(it.x, it.y, 4f * u, paint)
        }; paint.alpha = 255
        // Numeri danno
        animController?.damageNumbers?.forEach {
            paint.color = if (it.crit) Color.parseColor("#FFD700") else Color.WHITE
            paint.textSize = (if (it.crit) 34f else 24f) * u; paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${if (it.crit) "✦" else ""}${it.value}", it.x, it.y, paint)
        }; paint.textAlign = Paint.Align.LEFT
        // Combo
        animController?.comboText?.let {
            paint.color = Color.parseColor("#A78BFA"); paint.textSize = 28f * u; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(it, w / 2f, h * 0.25f, paint); paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawFighter(canvas: Canvas, x: Float, ground: Float, u: Float, color: Int, blocking: Boolean, facingRight: Boolean, attackT: Float, flashT: Float, name: String, hpRatio: Float, isPlayer: Boolean) {
        val dir = if (facingRight) 1f else -1f
        val hip = ground - 22f * u
        val shoulder = ground - 52f * u
        val headY = ground - 66f * u
        val headR = 11f * u
        val reach = if (attackT > 0f) (attackT / 0.4f).coerceIn(0f, 1f) * 30f * u else 0f

        fun drawBody(c: Int, alpha: Int) {
            paint.color = c; paint.alpha = alpha
            // gambe
            canvas.drawRect(x - 9f * u, hip, x - 2f * u, ground, paint)
            canvas.drawRect(x + 2f * u, hip, x + 9f * u, ground, paint)
            // torso
            canvas.drawRect(x - 11f * u, shoulder, x + 11f * u, hip + 2f * u, paint)
            // testa
            canvas.drawCircle(x, headY, headR, paint)
            // braccio frontale (pugno)
            paint.strokeWidth = 7f * u; paint.style = Paint.Style.STROKE
            canvas.drawLine(x + dir * 8f * u, shoulder + 4f * u, x + dir * (16f * u + reach), shoulder + 4f * u, paint)
            paint.style = Paint.Style.FILL
            // scudo
            if (blocking) {
                paint.color = Color.parseColor("#66CCFF"); paint.alpha = alpha
                canvas.drawRect(x + dir * (12f * u), shoulder - 6f * u, x + dir * (20f * u), ground - 6f * u, paint)
            }
        }

        drawBody(color, 255)
        if (flashT > 0f) drawBody(Color.WHITE, (flashT / 0.15f * 200f).toInt().coerceIn(0, 255))

        // Barra HP stile SF + nome
        val barW = width * 0.40f
        val bx = if (isPlayer) 24f else width - 24f - barW
        val by = 22f * u
        paint.color = Color.BLACK; paint.alpha = 180
        canvas.drawRect(bx - 3, by - 3, bx + barW + 3, by + 17f * u, paint); paint.alpha = 255
        paint.color = Color.DKGRAY; canvas.drawRect(bx, by, bx + barW, by + 14f * u, paint)
        paint.color = if (isPlayer) Color.parseColor("#37E06B") else Color.parseColor("#FF5252")
        val fillW = barW * hpRatio.coerceIn(0f, 1f)
        if (isPlayer) canvas.drawRect(bx, by, bx + fillW, by + 14f * u, paint)
        else canvas.drawRect(bx + barW - fillW, by, bx + barW, by + 14f * u, paint)
        paint.color = Color.WHITE; paint.textSize = 13f * u
        paint.textAlign = if (isPlayer) Paint.Align.LEFT else Paint.Align.RIGHT
        canvas.drawText(name, if (isPlayer) bx else bx + barW, by - 6f * u, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}
