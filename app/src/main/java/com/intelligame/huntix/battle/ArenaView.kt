package com.intelligame.huntix.battle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import com.intelligame.huntix.battle.CharacterRenderer.AnimState
import kotlin.math.sin

class ArenaView(context: Context) : View(context) {

    var engine: FightingEngine? = null
    var animController: AnimationController? = null
    var impactEffects: ImpactEffects? = null

    private val characterRenderer = SpriteSheetRenderer(context)
    private val arenaBackground = ArenaBackground()
    private val combatHud = CombatHud()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var timeSec = 0f
    private var comboScale = 1f
    private var comboScaleTarget = 1f
    private var comboAnimActive = false
    private var spritesLoaded = false

    private var hitFreezeFrames = 0
    private var cameraZoomTarget = 1f
    private var cameraZoom = 1f

    private val render = object : Runnable {
        override fun run() {
            val dt = 0.016f
            if (hitFreezeFrames > 0) {
                hitFreezeFrames--
                invalidate()
                handler.postDelayed(this, 32)
                return
            }
            timeSec += dt
            animController?.update(dt)
            if (comboScaleTarget != comboScale) {
                comboScale += (comboScaleTarget - comboScale) * 0.15f
            }
            if (cameraZoom != cameraZoomTarget) {
                cameraZoom += (cameraZoomTarget - cameraZoom) * 0.12f
            }
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    fun triggerHitFreeze(frames: Int) {
        hitFreezeFrames = maxOf(hitFreezeFrames, frames)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!spritesLoaded) {
            characterRenderer.loadSprites()
            spritesLoaded = true
        }
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
        val groundY = h * 0.82f
        val s = (h / 380f).coerceIn(1.4f, 2.8f) * cameraZoom

        val shakeX = impactEffects?.shakeX ?: 0f
        val shakeY = impactEffects?.shakeY ?: 0f

        canvas.save()
        canvas.translate(shakeX, shakeY)

        val zoomOffsetX = (w * (1f - cameraZoom)) / 2f
        val zoomOffsetY = (h * (1f - cameraZoom)) / 2f
        canvas.translate(zoomOffsetX, zoomOffsetY)

        arenaBackground.draw(canvas, w, h, timeSec)

        drawShadow(canvas, eng, groundY, s)

        val px = eng.playerController.positionX * w
        val ex = eng.enemyUnit.positionX * w

        if (eng.enemyUnit.telegraphActive) {
            drawTelegraphIndicator(canvas, ex, groundY, s, eng.enemyUnit.telegraphType)
        }

        drawElementAura(canvas, ex, groundY, s, eng.enemyUnit.element, false, eng.enemyUnit.hitFlash)
        characterRenderer.draw(
            canvas,
            eng.enemyUnit.animState,
            eng.enemyUnit.animProgress,
            ex, groundY - eng.enemyUnit.posY * s,
            s * 1.1f,
            eng.enemyUnit.facing,
            getEnemyBodyColor(eng.enemyUnit),
            getEnemyOutlineColor(eng.enemyUnit),
            eng.enemyUnit.hitFlash,
            eng.enemyUnit.isStunned()
        )

        drawElementAura(canvas, px, groundY, s, eng.playerController.elementType, true, eng.playerController.hitFlash)
        characterRenderer.draw(
            canvas,
            eng.playerController.animState,
            eng.playerController.animProgress,
            px, groundY - eng.playerController.posY * s,
            s * 1.1f,
            eng.playerController.facing,
            getPlayerBodyColor(eng.playerController),
            getPlayerOutlineColor(eng.playerController),
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
            s / cameraZoom
        )

        drawDamageNumbers(canvas, w, h, s / cameraZoom)
        drawComboText(canvas, w, h, s / cameraZoom)
        drawBanner(canvas, w, h, s / cameraZoom)
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
            if (!comboAnimActive) {
                comboScale = 1.4f
                comboScaleTarget = 1f
                comboAnimActive = true
            }

            val baseSize = 26f * s * comboScale

            paint.color = Color.parseColor("#A78BFA")
            paint.alpha = 100
            paint.textSize = baseSize + 2f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            c.drawText(text, w / 2f + 1f, h * 0.18f + 1f, paint)

            paint.color = Color.parseColor("#E879F9")
            paint.alpha = 255
            paint.textSize = baseSize
            c.drawText(text, w / 2f, h * 0.18f, paint)
        } ?: run {
            comboAnimActive = false
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

    fun getCharRenderer(): SpriteSheetRenderer = characterRenderer

    private fun getPlayerBodyColor(pc: PlayerController): Int {
        return when (pc.elementType) {
            ElementType.FIRE -> Color.parseColor("#FF6B35")
            ElementType.WATER -> Color.parseColor("#1E88E5")
            ElementType.AIR -> Color.parseColor("#7CB342")
            ElementType.EARTH -> Color.parseColor("#8D6E63")
            ElementType.NORMAL -> Color.parseColor("#1E88E5")
        }
    }

    private fun getPlayerOutlineColor(pc: PlayerController): Int {
        return when (pc.elementType) {
            ElementType.FIRE -> Color.parseColor("#C0392B")
            ElementType.WATER -> Color.parseColor("#0D47A1")
            ElementType.AIR -> Color.parseColor("#33691E")
            ElementType.EARTH -> Color.parseColor("#4E342E")
            ElementType.NORMAL -> Color.parseColor("#0D47A1")
        }
    }

    private fun getEnemyBodyColor(en: Enemy): Int {
        return when (en.element) {
            ElementType.FIRE -> Color.parseColor("#E53935")
            ElementType.WATER -> Color.parseColor("#00ACC1")
            ElementType.AIR -> Color.parseColor("#FDD835")
            ElementType.EARTH -> Color.parseColor("#6D4C41")
            ElementType.NORMAL -> Color.parseColor("#E53935")
        }
    }

    private fun getEnemyOutlineColor(en: Enemy): Int {
        return when (en.element) {
            ElementType.FIRE -> Color.parseColor("#B71C1C")
            ElementType.WATER -> Color.parseColor("#00838F")
            ElementType.AIR -> Color.parseColor("#F9A825")
            ElementType.EARTH -> Color.parseColor("#3E2723")
            ElementType.NORMAL -> Color.parseColor("#B71C1C")
        }
    }

    private fun drawTelegraphIndicator(c: Canvas, x: Float, groundY: Float, s: Float, type: Enemy.TelegraphType) {
        val pulse = (sin(timeSec * 10f) * 0.3f + 0.7f)
        val indicatorY = groundY - 90f * s

        paint.style = Paint.Style.FILL

        val warnColor = when (type) {
            Enemy.TelegraphType.WARNING -> Color.parseColor("#FFD740")
            Enemy.TelegraphType.HEAVY -> Color.parseColor("#FF6D00")
            Enemy.TelegraphType.SPECIAL -> Color.parseColor("#D500F9")
            Enemy.TelegraphType.NONE -> Color.TRANSPARENT
        }
        paint.color = warnColor
        paint.alpha = (180f * pulse).toInt()

        val warnSize = 14f * s * (0.9f + (1f - pulse) * 0.2f)
        val path = Path()
        path.moveTo(x, indicatorY - warnSize)
        path.lineTo(x - warnSize * 0.6f, indicatorY + warnSize * 0.6f)
        path.lineTo(x + warnSize * 0.6f, indicatorY + warnSize * 0.6f)
        path.close()
        c.drawPath(path, paint)

        paint.color = Color.WHITE
        paint.alpha = (200f * pulse).toInt()

        if (type == Enemy.TelegraphType.HEAVY || type == Enemy.TelegraphType.SPECIAL) {
            val extraSize = warnSize * 1.5f
            paint.color = warnColor
            paint.alpha = (60f * pulse).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * s
            c.drawCircle(x, indicatorY, extraSize * (0.8f + (1f - pulse) * 0.3f), paint)
            paint.style = Paint.Style.FILL
        }

        paint.textSize = 10f * s
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.alpha = (255f * pulse).toInt()
        val label = when (type) {
            Enemy.TelegraphType.HEAVY -> "!"
            Enemy.TelegraphType.SPECIAL -> "!!"
            else -> "?"
        }
        c.drawText(label, x, indicatorY + 4f * s, paint)

        paint.alpha = 255
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawElementAura(c: Canvas, x: Float, groundY: Float, s: Float, element: ElementType, isPlayer: Boolean, hitFlash: Float) {
        val auraColor = when (element) {
            ElementType.FIRE -> Color.parseColor("#FF6B35")
            ElementType.WATER -> Color.parseColor("#2979FF")
            ElementType.AIR -> Color.parseColor("#76FF03")
            ElementType.EARTH -> Color.parseColor("#8D6E63")
            ElementType.NORMAL -> Color.TRANSPARENT
        }
        if (auraColor == Color.TRANSPARENT) return

        val pulse = sin(timeSec * 3f + x * 2f) * 0.15f + 0.85f
        val alpha = ((if (isPlayer) 25 else 30) * pulse).toInt()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * s
        paint.color = auraColor
        paint.alpha = alpha
        c.drawOval(
            x - 30f * s, groundY - 75f * s,
            x + 30f * s, groundY + 3f * s,
            paint
        )

        paint.style = Paint.Style.FILL
        paint.alpha = (alpha / 3)
        c.drawOval(
            x - 28f * s, groundY - 73f * s,
            x + 28f * s, groundY + 1f * s,
            paint
        )
        paint.alpha = 255
    }

    fun triggerDynamicZoom(intensity: Float) {
        cameraZoomTarget = 1f + intensity
        handler.postDelayed({
            cameraZoomTarget = 1f
        }, 300)
    }
}
