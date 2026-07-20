package com.intelligame.huntix.battle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * ArenaView — rendering dell'arena di battaglia (fondale, lottatori, effetti).
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
        val ground = h * 0.78f
        // Ombra a terra (sfondo AR trasparente: si vede la camera)
        paint.color = Color.parseColor("#000000"); paint.alpha = 60
        canvas.drawOval(0f, ground - 6f, w, ground + 18f, paint); paint.alpha = 255

        val eng = engine
        val anim = animController
        if (eng != null) {
            // Lottatore giocatore
            drawFighter(canvas, eng.playerController.positionX * w, ground, Color.parseColor("#4FC3F7"), eng.playerController.isBlocking)
            // Nemico
            drawFighter(canvas, eng.enemyUnit.positionX * w, ground, Color.parseColor("#EF5350"), false)
            // Barre HP
            drawBar(canvas, 20f, 30f, w - 40f, 12f, 1f, Color.GREEN)
            val ehp = (eng.enemyUnit.hp / eng.enemyUnit.maxHp).coerceIn(0f, 1f)
            drawBar(canvas, 20f, 50f, (w - 40f) * ehp, 12f, 1f, Color.RED)
        }
        // Particelle
        anim?.particles?.forEach {
            paint.color = it.color
            paint.alpha = (it.life * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(it.x, it.y, 4f, paint)
        }
        // Numeri danno
        anim?.damageNumbers?.forEach {
            paint.color = if (it.crit) Color.parseColor("#FFD700") else Color.WHITE
            paint.textSize = if (it.crit) 34f else 24f
            canvas.drawText("${if (it.crit) "✦" else ""}${it.value}", it.x, it.y, paint)
        }
        // Combo
        anim?.comboText?.let {
            paint.color = Color.parseColor("#A78BFA")
            paint.textSize = 28f
            canvas.drawText(it, w / 2f - 60f, h * 0.25f, paint)
        }
    }

    private fun drawFighter(canvas: Canvas, x: Float, ground: Float, color: Int, blocking: Boolean) {
        paint.color = color
        canvas.drawCircle(x, ground - 40f, 26f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(x, ground - 40f, 26f, paint.apply { style = Paint.Style.STROKE; strokeWidth = 3f })
        paint.style = Paint.Style.FILL
        if (blocking) {
            paint.color = Color.parseColor("#66CCFF")
            canvas.drawRect(x - 34f, ground - 70f, x - 28f, ground - 10f, paint)
        }
    }

    private fun drawBar(canvas: Canvas, x: Float, y: Float, w: Float, hgt: Float, _fill: Float, color: Int) {
        paint.color = Color.DKGRAY
        canvas.drawRect(x, y, x + w + 0f, y + hgt, paint)
        paint.color = color
        canvas.drawRect(x, y, x + w.coerceAtLeast(0f), y + hgt, paint)
    }
}
