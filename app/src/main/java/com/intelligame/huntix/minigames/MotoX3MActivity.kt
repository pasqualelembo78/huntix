package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 🏍️ Moto X3M — Bike Race in stile Huntix!
 *
 * Corri su una strada con colline e ostacoli. Tocca per accelerare/saltare!
 * Raccogli le uova lungo la strada per punti extra. Evita rocce e barriere!
 * Più corri, più veloce diventa e più ostacoli ci sono.
 */
class MotoX3MActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val tickMs = 16L

    private lateinit var gameView: MotoView
    private lateinit var scoreText: TextView
    private lateinit var livesText: TextView
    private lateinit var overlayContainer: FrameLayout

    private var bikeX = 0f
    private var bikeY = 0f
    private var bikeVelY = 0f
    private var bikeOnGround = true
    private var speed = 2f
    private var distance = 0f
    private var score = 0
    private var lives = 3
    private var gameRunning = false
    private var frameCount = 0

    private val obstacles = mutableListOf<Obstacle>()
    private val eggs = mutableListOf<EggItem>()
    private val particles = mutableListOf<Particle>()

    private var lastSpawn = 0L
    private val spawnIntervalBase = 1200L

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            update()
            gameView.invalidate()
            handler.postDelayed(this, tickMs)
        }
    }

    data class Obstacle(
        var x: Float, val w: Float, val h: Float,
        val type: Int, val y: Float
    )

    data class EggItem(
        var x: Float, var y: Float, var collected: Boolean = false
    )

    data class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int
    )

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Moto X3M", "🏍️"))
        root.addView(TextView(ctx).apply {
            text = "Tocca per accelerare/saltare! Raccogli uova ed evita ostacoli 🥚"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_MOTO_X3M))

        val hudRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        scoreText = TextView(ctx).apply {
            text = "0m  🥚 0"; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, 0, UiKit.dp(ctx, 12), 0)
        }
        livesText = TextView(ctx).apply {
            text = "❤️❤️❤️"; textSize = 16f; setTextColor(Color.parseColor(UiKit.GREEN))
        }
        hudRow.addView(scoreText!!)
        hudRow.addView(livesText!!)
        root.addView(hudRow)

        gameView = MotoView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        val diff = levelDifficulty(MiniGameManager.GAME_MOTO_X3M)
        bikeX = 0f
        bikeY = 0f
        bikeVelY = 0f
        bikeOnGround = true
        speed = 2f + diff * 1.5f
        distance = 0f
        score = 0
        lives = 3
        frameCount = 0
        obstacles.clear()
        eggs.clear()
        particles.clear()
        lastSpawn = 0L
        gameRunning = true
        scoreText.text = "0m  🥚 0"
        livesText.text = "❤️".repeat(lives)
        gameView.setSizes(0f, 0f)
        gameView.invalidate()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, tickMs)
    }

    private fun update() {
        val diff = levelDifficulty(MiniGameManager.GAME_MOTO_X3M)
        val w = gameView.viewW
        val h = gameView.viewH
        if (w <= 0 || h <= 0) return

        frameCount++
        val dt = tickMs / 1000f
        speed = (2f + diff * 1.5f) + distance * 0.0003f

        bikeVelY += 0.5f * dt * 60f
        bikeY += bikeVelY * dt * 60f

        val groundY = h * 0.75f
        if (bikeY >= groundY) {
            bikeY = groundY
            bikeVelY = 0f
            bikeOnGround = true
        } else {
            bikeOnGround = false
        }

        distance += speed * dt * 60f
        bikeX = w * 0.2f

        if (frameCount % 3 == 0) {
            scoreText.text = "${String.format("%.0f", distance)}m  🥚 $score"
        }

        val now = System.currentTimeMillis()
        if (now - lastSpawn > spawnIntervalBase / (speed / 2f)) {
            spawnObstacle(w, h, diff)
            lastSpawn = now
        }

        if (frameCount % 60 == 0 && eggs.count { !it.collected } < 3) {
            spawnEgg(w, h)
        }

        val iter = obstacles.iterator()
        while (iter.hasNext()) {
            val obs = iter.next()
            obs.x -= speed * dt * 60f
            if (obs.x < -obs.w) { iter.remove(); continue }
            val bikeLeft = bikeX - 20f
            val bikeRight = bikeX + 20f
            val bikeTop = bikeY - 30f
            val bikeBottom = bikeY + 10f
            if (bikeRight > obs.x && bikeLeft < obs.x + obs.w &&
                bikeBottom > obs.y && bikeTop < obs.y + obs.h) {
                hitObstacle()
                iter.remove()
            }
        }

        val eggIter = eggs.iterator()
        while (eggIter.hasNext()) {
            val egg = eggIter.next()
            egg.x -= speed * dt * 60f
            if (egg.x < -20f) { eggIter.remove(); continue }
            if (!egg.collected && abs(bikeX - egg.x) < 35f && abs(bikeY - egg.y) < 35f) {
                egg.collected = true
                score++
                spawnParticles(egg.x, egg.y, Color.parseColor("#FFD700"), 5)
                eggIter.remove()
            }
        }

        val partIter = particles.iterator()
        while (partIter.hasNext()) {
            val p = partIter.next()
            p.x += p.vx * dt * 60f
            p.y += p.vy * dt * 60f
            p.vy += 0.1f * dt * 60f
            p.life -= dt
            if (p.life <= 0) partIter.remove()
        }

        if (lives <= 0) {
            endGame(false)
        }
    }

    private fun spawnObstacle(w: Float, h: Float, diff: Float) {
        val groundY = h * 0.75f
        val r = Math.random()
        when {
            r < 0.4 -> {
                val obsW = 20f + diff * 5f
                val obsH = 20f + diff * 5f
                obstacles.add(Obstacle(w + 50f, obsW, obsH, 0, groundY - obsH))
            }
            r < 0.7 -> {
                val obsW = 30f + diff * 3f
                val obsH = 50f + diff * 10f
                obstacles.add(Obstacle(w + 50f, obsW, obsH, 1, groundY - obsH))
            }
            else -> {
                val gapW = 60f + diff * 10f
                obstacles.add(Obstacle(w + 50f, gapW, h * 0.3f, 2, groundY))
            }
        }
    }

    private fun spawnEgg(w: Float, h: Float) {
        val groundY = h * 0.75f
        eggs.add(EggItem(w + 50f, groundY - 20f - (Math.random() * 40f).toFloat()))
    }

    private fun hitObstacle() {
        lives--
        livesText.text = "❤️".repeat(lives)
        speed = max(1f, speed - 0.5f)
        spawnParticles(bikeX, bikeY, Color.parseColor("#FF5252"), 8)
    }

    private fun spawnParticles(x: Float, y: Float, color: Int, count: Int) {
        repeat(count) {
            particles.add(Particle(
                x = x, y = y,
                vx = ((Math.random() - 0.5) * 4f).toFloat(),
                vy = ((Math.random() - 0.5) * 4f - 2f).toFloat(),
                life = (0.5f + (Math.random() * 0.3f).toFloat()),
                maxLife = 0.8f,
                color = color
            ))
        }
    }

    private fun jump() {
        if (bikeOnGround) {
            bikeVelY = -12f
            bikeOnGround = false
            spawnParticles(bikeX, bikeY + 10f, Color.parseColor("#A78BFA"), 3)
        }
    }

    private fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)

        val mvc = (score * 2).coerceAtLeast(10)
        val xp = (score + distance.toInt() / 10).coerceAtLeast(5)
        val result = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_MOTO_X3M, score * 10 + distance.toInt(),
                mvc = mvc, xp = xp,
                label = "Moto X3M: ${String.format("%.0f", distance)}m",
                isWin = won,
                giftEggRarityId = if (won) "common" else null
            )
        } catch (e: Exception) { Sentry.captureException(e); null }

        val ctx = this
        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#CC0D0620"))
            setPadding(UiKit.dp(ctx, 30), UiKit.dp(ctx, 40), UiKit.dp(ctx, 30), UiKit.dp(ctx, 40))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true; isFocusable = true
        }
        val endLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "🏆" else "🏍️"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Vittoria!" else "Game Over"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Distanza: ${String.format("%.0f", distance)}m  •  🥚 $score"; textSize = 18f
            setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        result?.let { endLayout.addView(levelResultView(it)) }
        endLayout.addView(TextView(ctx).apply {
            text = "+${result?.mvc ?: mvc} MVC  •  +${result?.xp ?: xp} XP"
            textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "🔄  Riprova", UiKit.ACCENT) {
            overlayContainer.removeView(overlay)
            startGame()
        })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    inner class MotoView(context: android.content.Context) : View(context) {

        private val roadPaint = Paint().apply { color = Color.parseColor("#1A1A2E") }
        private val groundPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val grassPaint = Paint().apply { color = Color.parseColor("#00CC66") }
        private val skyPaint = Paint().apply { color = Color.parseColor("#0A0A1A") }
        private val bikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6B35") }
        private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD700") }
        private val rockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8D6E63") }
        private val barrierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5252") }
        private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A0A30") }
        private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f; textAlign = Paint.Align.CENTER; color = Color.WHITE
        }

        var viewW = 0f
        var viewH = 0f

        fun setSizes(w: Float, h: Float) { viewW = w; viewH = h }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            viewW = w; viewH = h

            c.drawColor(skyPaint.color)

            val groundY = h * 0.75f
            c.drawRect(0f, groundY, w, h, groundPaint)

            for (i in 0..20) {
                val gx = (i * w / 20f - (distance * 0.5f % (w / 20f))) % w
                c.drawLine(gx, groundY, gx, h, grassPaint)
            }

            c.drawLine(0f, groundY, w, groundY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF6B35"); strokeWidth = 4f
            })

            for (obs in obstacles) {
                when (obs.type) {
                    0 -> {
                        c.drawRoundRect(RectF(obs.x, obs.y, obs.x + obs.w, obs.y + obs.h),
                            4f, 4f, rockPaint)
                    }
                    1 -> {
                        c.drawRoundRect(RectF(obs.x, obs.y, obs.x + obs.w, obs.y + obs.h),
                            8f, 8f, barrierPaint)
                        c.drawLine(obs.x, obs.y, obs.x + obs.w, obs.y + obs.h,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 2f })
                        c.drawLine(obs.x + obs.w, obs.y, obs.x, obs.y + obs.h,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 2f })
                    }
                    2 -> {
                        c.drawRect(RectF(obs.x, groundY - obs.h, obs.x + obs.w, groundY), gapPaint)
                        c.drawLine(obs.x, groundY - obs.h, obs.x + obs.w, groundY - obs.h,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5252"); strokeWidth = 3f })
                    }
                }
            }

            for (egg in eggs) {
                if (egg.collected) continue
                c.drawOval(RectF(egg.x - 10f, egg.y - 14f, egg.x + 10f, egg.y + 14f), eggPaint)
                c.drawCircle(egg.x, egg.y - 4f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFA000") })
            }

            for (p in particles) {
                val alpha = (p.life / p.maxLife * 255).toInt()
                particlePaint.color = Color.argb(alpha.coerceIn(0, 255),
                    Color.red(p.color), Color.green(p.color), Color.blue(p.color))
                c.drawCircle(p.x, p.y, 3f, particlePaint)
            }

            val bx = bikeX
            val by = bikeY
            c.drawRoundRect(RectF(bx - 25f, by - 15f, bx + 25f, by + 5f), 8f, 8f, bikePaint)
            c.drawCircle(bx - 15f, by + 8f, 10f, wheelPaint)
            c.drawCircle(bx + 15f, by + 8f, 10f, wheelPaint)
            c.drawCircle(bx - 15f, by + 8f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY })
            c.drawCircle(bx + 15f, by + 8f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY })
            c.drawRect(RectF(bx - 5f, by - 25f, bx + 5f, by - 15f), bikePaint)
            c.drawCircle(bx, by - 25f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FC3F7") })

            if (!bikeOnGround) {
                scorePaint.textSize = 24f
                c.drawText("🏍️", bx - 15f, by - 40f, scorePaint)
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    jump()
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}