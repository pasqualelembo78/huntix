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
import kotlin.random.Random

/**
 * 🐍 Snake — il serpente cresce mangiando uova. Non sbattere contro muri o te stesso.
 * Riscritto nativo (meccanica classica, stile Huntix).
 */
class SnakeActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val TICK_MS = 120L
    private val COLS = 22
    private val ROWS = 26

    private val snake = ArrayDeque<Pair<Int, Int>>()
    private var food = 5 to 5
    private var dirX = 1
    private var dirY = 0
    private var nextDirX = 1
    private var nextDirY = 0
    private var score = 0
    private var gameRunning = false
    private var scoreText: TextView? = null
    private var gameView: SnakeView? = null
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
        root.addView(UiKit.title(ctx, "Snake", "🐍"))
        root.addView(TextView(ctx).apply {
            text = "Scorri per cambiare direzione. Mangia le uova 🥚!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 18f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        gameView = SnakeView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        snake.clear()
        snake.addFirst(6 to ROWS / 2)
        snake.addLast(5 to ROWS / 2)
        snake.addLast(4 to ROWS / 2)
        dirX = 1; dirY = 0; nextDirX = 1; nextDirY = 0
        score = 0
        gameRunning = true
        scoreText?.text = "Punti: 0"
        placeFood()
        gameView?.invalidate()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, TICK_MS)
    }

    private fun placeFood() {
        val free = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until COLS) for (y in 0 until ROWS) {
            if (x to y !in snake) free.add(x to y)
        }
        food = if (free.isEmpty()) (0 to 0) else free[Random.nextInt(free.size)]
    }

    private fun step() {
        dirX = nextDirX; dirY = nextDirY
        val head = snake.first()
        val nx = head.first + dirX
        val ny = head.second + dirY
        if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) {
            endGame()
            return
        }
        val newHead = nx to ny
        if (newHead in snake) {
            endGame()
            return
        }
        snake.addFirst(newHead)
        if (newHead == food) {
            score += 10
            scoreText?.text = "Punti: $score"
            placeFood()
        } else {
            snake.removeLast()
        }
    }

    private fun endGame() {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
        val mvc = (score / 2).coerceAtLeast(8)
        val xp = (score / 4).coerceAtLeast(2)
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_SNAKE)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc, xpPoints = xp,
                    label = "Snake: $score punti",
                    isWin = score >= 30
                ),
                MiniGameManager.GAME_SNAKE
            )
        } catch (e: Exception) { Sentry.captureException(e) }

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
            text = "🐍"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Partita Finita!"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $score"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "+$mvc MVC  •  +$xp XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
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

    inner class SnakeView(context: android.content.Context) : View(context) {

        private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FF88") }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00C86A") }
        private val gridPaint = Paint().apply { color = Color.parseColor("#1A1030") }
        private var startX = 0f
        private var startY = 0f

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val cw = w / COLS
            val ch = h / ROWS
            c.drawRect(0f, 0f, w, h, Paint().apply { color = Color.parseColor("#0D0620") })
            for (x in 0 until COLS) {
                c.drawLine(x * cw, 0f, x * cw, h, gridPaint)
            }
            for (y in 0 until ROWS) {
                c.drawLine(0f, y * ch, w, y * ch, gridPaint)
            }
            for ((i, p) in snake.withIndex()) {
                val paint = if (i == 0) headPaint else bodyPaint
                val inset = cw * 0.08f
                c.drawRoundRect(
                    android.graphics.RectF(p.first * cw + inset, p.second * ch + inset, (p.first + 1) * cw - inset, (p.second + 1) * ch - inset),
                    cw * 0.25f, ch * 0.25f, paint
                )
            }
            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = ch * 0.8f }
            c.drawText("🥚", food.first * cw + cw / 2, food.second * ch + ch * 0.85f, fp)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x; startY = ev.y
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!gameRunning) return true
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    if (Math.abs(dx) < 30 && Math.abs(dy) < 30) return true
                    if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0 && dirX != -1) { nextDirX = 1; nextDirY = 0 }
                        else if (dx < 0 && dirX != 1) { nextDirX = -1; nextDirY = 0 }
                    } else {
                        if (dy > 0 && dirY != -1) { nextDirX = 0; nextDirY = 1 }
                        else if (dy < 0 && dirY != 1) { nextDirX = 0; nextDirY = -1 }
                    }
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}
