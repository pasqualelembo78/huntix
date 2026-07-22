package com.intelligame.huntix.battle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import com.intelligame.huntix.battle.CharacterRenderer.AnimState

class ArenaView(context: Context) : View(context) {

    var engine: FightingEngine? = null
    var animController: AnimationController? = null
    var impactEffects: ImpactEffects? = null

    private val characterRenderer = CharacterRenderer()
    private val arenaBackground = ArenaBackground()
    private val combatHud = CombatHud()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var timeSec = 0f
    private var comboScale = 1f
    private var comboScaleTarget = 1f

    private val render = object : Runnable {
        override fun run() {
            val dt = 0.016f
            timeSec += dt
            animController?.update(dt)
            if (comboScaleTarget != comboScale) {
                comboScale += (comboScaleTarget - comboScale) * 0.15f
            }
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
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val eng = engine ?: return
        val groundY = h * 0.78f
        val s = (h / 580f).coerceIn(0.55f, 1.2f)

        val shakeX = impactEffects?.shakeX ?: 0f
        val shakeY = impactEffects?.shakeY ?: 0f

        canvas.save()
        canvas.translate(shakeX, shakeY)

        arenaBackground.draw(canvas, w, h, timeSec)

        drawShadow(canvas, eng, groundY, s)

        val px = eng.playerController.positionX * w
        val ex = eng.enemyUnit.positionX * w

        characterRenderer.draw(
            canvas,
            eng.enemyUnit.animState,
            eng.enemyUnit.animProgress,
            ex, groundY - eng.enemyUnit.posY * s,
            s * 1.1f,
            eng.enemyUnit.facing,
            Color.parseColor("#E53935"),
            Color.parseColor("#B71C1C"),
            eng.enemyUnit.hitFlash,
            eng.enemyUnit.isStunned()
        )

        characterRenderer.draw(
            canvas,
            eng.playerController.animState,
            eng.playerController.animProgress,
            px, groundY - eng.playerController.posY * s,
            s * 1.1f,
            eng.playerController.facing,
            Color.parseColor("#1E88E5"),
            Color.parseColor("#0D47A1"),
            eng.playerController.hitFlash,
            eng.playerController.isBlocking
        )

        impactEffects?.drawWorldEffects(canvas, s)

        animController?.motionTrails?.forEach { trail ->
            paint.color = trail.color
            paint.alpha = (trail.life * 200f).toInt().coerceIn(0, 200)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(trail.x, trail.y, trail.size * s, paint)
        }
        paint.alpha = 255

        canvas.restore()

        combatHud.draw(
            canvas, w, h,
            eng.playerHpRatio * 100f,
            eng.enemyHpRatio * eng.enemyUnit.maxHp,
            100f, eng.enemyUnit.maxHp,
            eng.timeRemainingMs,
            eng.currentRound, eng.maxRounds,
            eng.playerRoundWins, eng.enemyRoundWins,
            eng.playerController.superBar,
            CombatHud.Portrait("TU", Color.parseColor("#1E88E5"), 5),
            CombatHud.Portrait(eng.enemyUnit.creature.name ?: "NEMICO", Color.parseColor("#E53935"), 8),
            s
        )

        drawDamageNumbers(canvas, w, h, s)
        drawComboText(canvas, w, h, s)
        drawBanner(canvas, w, h, s)
        impactEffects?.drawScreenOverlay(canvas, w, h)
    }

    private fun drawShadow(c: Canvas, eng: FightingEngine, groundY: Float, s: Float) {
        paint.color = Color.BLACK
        paint.alpha = 40
        paint.style = Paint.Style.FILL

        val px = eng.playerController.positionX * width.toFloat()
        val ex = eng.enemyUnit.positionX * width.toFloat()
        val shadowW = 20f * s
        val shadowH = 4f * s

        c.drawOval(
            px - shadowW, groundY + 2f,
            px + shadowW, groundY + shadowH + 2f,
            paint
        )
        c.drawOval(
            ex - shadowW, groundY + 2f,
            ex + shadowW, groundY + shadowH + 2f,
            paint
        )
        paint.alpha = 255
    }

    private fun drawDamageNumbers(c: Canvas, w: Float, h: Float, s: Float) {
        animController?.damageNumbers?.forEach { d ->
            val alpha = (d.life * 255f).toInt().coerceIn(0, 255)
            val size = if (d.crit) 30f * s else 20f * s
            val sc = if (d.crit) (1f + (1f - d.life) * 0.5f) else 1f

            paint.color = if (d.crit) Color.parseColor("#FFD700") else Color.WHITE
            paint.alpha = alpha
            paint.textSize = size * sc
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)

            val prefix = if (d.crit) "CRIT " else ""
            c.drawText("$prefix${d.value}", d.x, d.y, paint)

            if (d.crit) {
                paint.color = Color.parseColor("#FF8F00")
                paint.alpha = (alpha * 0.5f).toInt()
                c.drawText("$prefix${d.value}", d.x + 1f, d.y + 1f, paint)
            }
        }
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.alpha = 255
    }

    private fun drawComboText(c: Canvas, w: Float, h: Float, s: Float) {
        animController?.comboText?.let { text ->
            if (comboScale < 1.01f) comboScaleTarget = 1.3f

            val alpha = 255
            val baseSize = 26f * s * comboScale

            paint.color = Color.parseColor("#A78BFA")
            paint.alpha = (alpha * 0.4f).toInt()
            paint.textSize = baseSize + 2f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            c.drawText(text, w / 2f + 1f, h * 0.18f + 1f, paint)

            paint.color = Color.parseColor("#E879F9")
            paint.alpha = alpha
            paint.textSize = baseSize
            c.drawText(text, w / 2f, h * 0.18f, paint)

            comboScaleTarget = 1f
        }
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.alpha = 255
    }

    private var bannerText = ""
    private var bannerAlpha = 0f

    fun showBanner(text: String) {
        bannerText = text
        bannerAlpha = 1f
    }

    private fun drawBanner(c: Canvas, w: Float, h: Float, s: Float) {
        if (bannerAlpha <= 0f || bannerText.isEmpty()) return

        paint.color = Color.parseColor("#FFD700")
        paint.alpha = (bannerAlpha * 255f).toInt()
        paint.textSize = 42f * s
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)

        paint.color = Color.BLACK
        paint.alpha = (bannerAlpha * 120f).toInt()
        c.drawText(bannerText, w / 2f + 2f, h * 0.35f + 2f, paint)

        paint.color = Color.parseColor("#FFD700")
        paint.alpha = (bannerAlpha * 255f).toInt()
        c.drawText(bannerText, w / 2f, h * 0.35f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.alpha = 255
        bannerAlpha = (bannerAlpha - 0.02f).coerceAtLeast(0f)
    }

    fun getCharRenderer(): CharacterRenderer = characterRenderer
}
