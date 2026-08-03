package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

/**
 * 🐣 Flappy Egg — tocca per far volare l'uovo tra i tubi.
 * Clone Flappy Bird con tema uovo (riscritto nativo).
 */
class FlappyEggActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val TICK_MS = 30L
    private val GRAVITY = 0.0018f
    private val FLAP = -0.055f
    private val PIPE_W = 0.12f

    /** Difficoltà variabile: gap più stretto e tubi più veloci ai livelli alti. */
    private val diff: Float get() = levelDifficulty(MiniGameManager.GAME_FLAPPY)
    private val GAP_H: Float get() = 0.30f - 0.08f * diff
    private val PIPE_SPEED: Float get() = 0.008f + 0.006f * diff
    private val SPAWN_INTERVAL: Long get() = 1500L - (400L * diff).toLong()

    data class Pipe(var x: Float, val gapY: Float)

    private val pipes = mutableListOf<Pipe>()
    private var eggY = 0.5f
    private var eggVY = 0f
    private var score = 0
    private var gameRunning = false
    private var nextSpawn = 0L
    private var scoreText: TextView? = null
    private var gameView: FlappyView? = null
    private var overlayContainer: FrameLayout? = null

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            step()
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
        root.addView(UiKit.title(ctx, "Flappy Egg", "🐣"))
        root.addView(TextView(ctx).apply {
            text = "Tocca lo schermo per far volare l'uovo!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_FLAPPY))
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 18f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        gameView = FlappyView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        pipes.clear()
        eggY = 0.5f
        eggVY = 0f
        score = 0
        gameRunning = true
        nextSpawn = System.currentTimeMillis() + 1200
        scoreText?.text = "Punti: 0"
        gameView?.invalidate()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, TICK_MS)
    }

    private fun flap() {
        if (!gameRunning) return
        eggVY = FLAP
    }

    private fun step() {
        eggVY += GRAVITY
        eggY += eggVY
        if (eggY < 0.02f) { eggY = 0.02f; eggVY = 0f }
        if (eggY > 0.98f) {
            endGame()
            return
        }
        if (System.currentTimeMillis() >= nextSpawn) {
            pipes.add(Pipe(1.15f, 0.25f + (Math.random().toFloat() * 0.5f)))
            nextSpawn = System.currentTimeMillis() + SPAWN_INTERVAL
        }
        val iter = pipes.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x -= PIPE_SPEED
            if (p.x + PIPE_W < 0f) {
                iter.remove()
                score += 10
                scoreText?.text = "Punti: $score"
                continue
            }
            val eggLeft = 0.3f - 0.045f
            val eggRight = 0.3f + 0.045f
            val eggTop = eggY - 0.04f
            val eggBottom = eggY + 0.04f
            if (eggRight > p.x && eggLeft < p.x + PIPE_W) {
                val gapTop = p.gapY - GAP_H / 2
                val gapBottom = p.gapY + GAP_H / 2
                if (eggBottom < gapTop || eggTop > gapBottom) {
                    endGame()
                    return
                }
            }
        }
    }

    private fun endGame() {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
        val mvc = (score / 2).coerceAtLeast(5)
        val xp = (score / 5).coerceAtLeast(2)
        val result = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_FLAPPY, score,
                mvc = mvc, xp = xp,
                label = "Flappy Egg: $score punti",
                isWin = score >= 30
            )
        } catch (e: Exception) { Sentry.captureException(e); null }

        val ctx = this
        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#CC0D0620"))
            setPadding(UiKit.dp(ctx, 30), UiKit.dp(ctx, 40), UiKit.dp(ctx, 30), UiKit.dp(ctx, 40))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isClickable = true; isFocusable = true
        }
        val endLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        endLayout.addView(TextView(ctx).apply {
            text = "🐣"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Splash!"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $score"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        result?.let { endLayout.addView(levelResultView(it)) }
        endLayout.addView(TextView(ctx).apply {
            text = "+${result?.mvc ?: mvc} MVC  •  +${result?.xp ?: xp} XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "🔄  Gioca Ancora", UiKit.ACCENT) {
            overlayContainer?.removeView(overlay)
            startGame()
        })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer?.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    inner class FlappyView(context: android.content.Context) : View(context) {

        private val pipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#43A047") }
        private val pipeRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E7D32") }
        private val skyPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            c.drawRect(0f, 0f, w, h, skyPaint)
            val groundY = h * 0.95f
            c.drawRect(0f, groundY, w, h, Paint().apply { color = Color.parseColor("#1A4018") })

            for (p in pipes) {
                val px = p.x * w
                val pw = PIPE_W * w
                val gapTop = (p.gapY - GAP_H / 2) * h
                val gapBottom = (p.gapY + GAP_H / 2) * h
                val capH = w * 0.03f
                c.drawRoundRect(android.graphics.RectF(px, 0f, px + pw, gapTop), 8f, 8f, pipePaint)
                c.drawRoundRect(android.graphics.RectF(px - capH, gapTop - capH, px + pw + capH, gapTop), 8f, 8f, pipeRimPaint)
                c.drawRoundRect(android.graphics.RectF(px, gapBottom, px + pw, h), 8f, 8f, pipePaint)
                c.drawRoundRect(android.graphics.RectF(px - capH, gapBottom, px + pw + capH, gapBottom + capH), 8f, 8f, pipeRimPaint)
            }

            eggPaint.textSize = w * 0.11f
            val ex = 0.3f * w
            val ey = eggY * h
            c.drawCircle(ex, ey, w * 0.045f * 1.9f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; alpha = 40
            })
            c.drawText("🥚", ex, ey + w * 0.045f, eggPaint)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    flap()
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}
