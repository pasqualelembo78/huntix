package com.intelligame.huntix.minigames

import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager

class CatchEggActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val TICK_MS = 30L
    private val BASKET_W = 0.18f
    private val EGG_R = 0.035f

    data class Egg(var x: Float, var y: Float, var speed: Float, var type: Int)

    private var eggs = mutableListOf<Egg>()
    private var basketX = 0.5f
    private var score = 0
    private var lives = 3
    private var timeLeft = 45
    private var gameRunning = false
    private var nextSpawn = 0L
    private var spawnInterval = 1000L
    private var elapsed = 0L
    private var scoreText: TextView? = null
    private var timeText: TextView? = null
    private var livesText: TextView? = null
    private var gameView: GameCanvasView? = null
    private var dragging = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            timeLeft--
            timeText?.text = "Tempo: ${timeLeft}s"
            if (timeLeft <= 0) {
                gameRunning = false
                endGame()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            elapsed += TICK_MS
            spawnInterval = (1000L - elapsed / 50).coerceAtLeast(350L)
            if (System.currentTimeMillis() >= nextSpawn) {
                spawnEgg()
                nextSpawn = System.currentTimeMillis() + spawnInterval
            }
            updateEggs()
            gameView?.invalidate()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }

        root.addView(UiKit.title(ctx, "Prendi l'Uovo", "\uD83E\uDD5A"))

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 15f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        livesText = TextView(ctx).apply {
            text = "\u2764\uFE0F\u2764\uFE0F\u2764\uFE0F"; textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeText = TextView(ctx).apply {
            text = "Tempo: 45s"; textSize = 15f; setTextColor(Color.parseColor(UiKit.ACCENT))
        }
        header.addView(scoreText); header.addView(livesText); header.addView(timeText)
        root.addView(header)

        gameView = GameCanvasView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        startGame()
    }

    private fun startGame() {
        eggs.clear()
        basketX = 0.5f
        score = 0
        lives = 3
        timeLeft = 45
        elapsed = 0
        gameRunning = true
        spawnInterval = 1000L
        nextSpawn = System.currentTimeMillis() + 500
        scoreText?.text = "Punti: 0"
        timeText?.text = "Tempo: 45s"
        updateLivesUI()
        gameView?.invalidate()
        handler.postDelayed(timerRunnable, 1000)
        handler.postDelayed(gameLoop, TICK_MS)
    }

    private fun updateLivesUI() {
        livesText?.text = "\u2764\uFE0F".repeat(lives)
    }

    private fun spawnEgg() {
        val type = if (Math.random() < 0.2) 2 else if (Math.random() < 0.25) 1 else 0
        val speed = 0.003f + elapsed / 30000f * 0.004f
        eggs.add(Egg(Math.random().toFloat(), -0.05f, speed, type))
    }

    private fun updateEggs() {
        val iter = eggs.iterator()
        val dm = resources.displayMetrics
        val h = dm.heightPixels.toFloat()
        while (iter.hasNext()) {
            val e = iter.next()
            e.y += e.speed
            if (e.y > 1.1f) {
                iter.remove()
                if (e.type != 2) {
                    lives--
                    updateLivesUI()
                    if (lives <= 0) {
                        gameRunning = false
                        handler.removeCallbacksAndMessages(null)
                        endGame()
                        return
                    }
                }
                continue
            }
            val basketLeft = basketX - BASKET_W / 2
            val basketRight = basketX + BASKET_W / 2
            val basketTop = 0.9f
            if (e.y + EGG_R > basketTop && e.y - EGG_R < 1.0f && e.x > basketLeft && e.x < basketRight) {
                iter.remove()
                when (e.type) {
                    0 -> score += 10
                    1 -> score += 30
                    2 -> {
                        lives--
                        updateLivesUI()
                        if (lives <= 0) {
                            gameRunning = false
                            handler.removeCallbacksAndMessages(null)
                            endGame()
                            return
                        }
                    }
                }
                scoreText?.text = "Punti: $score"
            }
        }
    }

    private fun endGame() {
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
        val mvc = (score / 10).coerceAtLeast(5)
        val xp = (score / 5).coerceAtLeast(2)
        val isWin = score > 40
        val label = if (isWin) "CatchEgg: $score punti!" else "CatchEgg: $score punti"
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_CATCH_EGG)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc,
                    xpPoints = xp,
                    label = label,
                    isWin = isWin
                ),
                MiniGameManager.GAME_CATCH_EGG
            )
        } catch (_: Exception) {}

        val ctx = this
        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#CC0D0620"))
            setPadding(UiKit.dp(ctx, 30), UiKit.dp(ctx, 40), UiKit.dp(ctx, 30), UiKit.dp(ctx, 40))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isClickable = true; isFocusable = true
        }
        val endLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        endLayout.addView(TextView(ctx).apply {
            text = "\uD83C\uDFC6"; textSize = 48f; gravity = Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Partita Finita!"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $score"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "+$mvc MVC  \u2022  +$xp XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "\uD83D\uDD04  Gioca Ancora", UiKit.ACCENT) {
            (overlay.parent as? FrameLayout)?.removeView(overlay)
            startGame()
        })
        endLayout.addView(UiKit.button(ctx, "\u2B05  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        (gameView?.parent as? FrameLayout)?.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    inner class GameCanvasView(context: android.content.Context) : View(context) {
        private val eggEmojis = arrayOf("\uD83E\uDD5A", "\uD83E\uDD5A\u2728", "\uD83D\uDCA3")
        private val eggColors = intArrayOf(Color.WHITE, Color.parseColor("#FFD700"), Color.parseColor("#FF4444"))
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val groundY = h * 0.92f
            val groundPaint = Paint().apply { color = Color.parseColor("#1A4018") }
            c.drawRect(0f, groundY, w, h, groundPaint)
            val linePaint = Paint().apply { color = Color.parseColor("#2A6028"); strokeWidth = 3f }
            c.drawLine(0f, groundY, w, groundY, linePaint)

            val bx = w * basketX
            val bw = w * BASKET_W
            val bh = h * 0.06f
            val by = groundY - bh / 2
            val basketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8B4513") }
            c.drawRoundRect(RectF(bx - bw / 2, by, bx + bw / 2, by + bh), 12f, 12f, basketPaint)
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A0522D") }
            c.drawRoundRect(RectF(bx - bw * 0.55f, by - bh * 0.3f, bx + bw * 0.55f, by + bh * 0.15f), 10f, 10f, rimPaint)

            val eTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER; textSize = w * 0.065f
            }
            for (e in eggs) {
                val ex = e.x * w
                val ey = e.y * h
                val gPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eggColors[e.type]; alpha = 60 }
                c.drawCircle(ex, ey, w * EGG_R * 1.8f, gPaint)
                c.drawText(eggEmojis[e.type], ex, ey + w * 0.025f, eTextPaint)
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    dragging = true
                    basketX = (ev.x / width).coerceIn(BASKET_W / 2, 1f - BASKET_W / 2)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    dragging = false
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}
