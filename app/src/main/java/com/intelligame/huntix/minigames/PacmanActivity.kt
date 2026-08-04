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

class PacmanActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val BOARD_W = 21
    private val BOARD_H = 21
    private var board = Array(BOARD_H) { IntArray(BOARD_W) }
    private var gameRunning = false
    private var score = 0
    private var scoreText: TextView? = null
    private var gameView: PacmanView? = null
    private var overlayContainer: FrameLayout? = null
    private var pacmanX = 0f
    private var pacmanY = 0f
    private var pacmanDirX = 0f
    private var pacmanDirY = 0f
    private var pacmanAngle = 0f
    private var gameOverFlag = false
    private var lives = 3
    private var tickCount = 0

    private val maze = arrayOf(
        "111111111111111111111",
        "100000100000100000001",
        "101100101100101101101",
        "100000000000000000001",
        "111101111111111011111",
        "100000000000000000001",
        "101101101101101101101",
        "100001000000000100001",
        "111101111111111101111",
        "100000000000000000001",
        "101101111111111011101",
        "100000000000000000001",
        "111101101111011011111",
        "100001000000000100001",
        "101101111111111101101",
        "100000000000000000001",
        "101101101101101101101",
        "100000100000000100001",
        "111111111111111111111"
    )

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Pacman", "🎮"))
        root.addView(TextView(ctx).apply {
            text = "Tocca per muovere Pacman! Mangia tutti i pallini"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_PACMAN))
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 18f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        gameView = PacmanView(ctx)
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
        board = Array(BOARD_H) { IntArray(BOARD_W) }
        for (r in maze.indices) for (c in maze[r].indices) {
            board[r][c] = maze[r][c].digitToInt()
        }
        score = 0; lives = 3; gameOverFlag = false; tickCount = 0
        pacmanX = BOARD_W / 2f
        pacmanY = BOARD_H / 2f
        pacmanDirX = 0f; pacmanDirY = 0f; pacmanAngle = 0f
        scoreText?.text = "Punti: 0"
        gameRunning = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, 150L)
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            tickCount++
            movePacman()
            checkPellets()
            checkGhosts()
            gameView?.invalidate()
            if (lives <= 0 || board.all { row -> row.all { it != 0 } }) {
                endGame(lives > 0)
                return
            }
            handler.postDelayed(this, 150L)
        }
    }

    private fun movePacman() {
        if (pacmanDirX != 0f || pacmanDirY != 0f) {
            val nx = pacmanX + pacmanDirX
            val ny = pacmanY + pacmanDirY
            if (nx in 0f until BOARD_W && ny in 0f until BOARD_H) {
                val bx = nx.toInt()
                val by = ny.toInt()
                if (board[by][bx] != 1) {
                    pacmanX = nx
                    pacmanY = ny
                    pacmanAngle = kotlin.math.atan2(pacmanDirY.toDouble(), pacmanDirX.toDouble()).toFloat()
                }
            }
        }
    }

    private fun checkPellets() {
        val bx = pacmanX.toInt()
        val by = pacmanY.toInt()
        if (bx in 0 until BOARD_W && by in 0 until BOARD_H && board[by][bx] == 0) {
            board[by][bx] = 2
            score += 10
            scoreText?.text = "Punti: $score"
        }
    }

    private fun checkGhosts() {
        if (tickCount % 30 == 0) {
            val gx = Random.nextInt(BOARD_W)
            val gy = Random.nextInt(BOARD_H)
            if (gx == pacmanX.toInt() && gy == pacmanY.toInt()) {
                lives--
                pacmanX = BOARD_W / 2f
                pacmanY = BOARD_H / 2f
                scoreText?.text = "Vite: $lives  •  Punti: $score"
            }
        }
    }

    private fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        gameOverFlag = true
        handler.removeCallbacksAndMessages(null)
        val mvc = if (won) (score / 5).coerceAtLeast(10) else kotlin.math.max(score / 10, 3)
        val xp = if (won) (score / 10).coerceAtLeast(5) else kotlin.math.max(score / 20, 2)
        try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_PACMAN, score,
                mvc = mvc, xp = xp,
                label = "Pacman: $score pt",
                isWin = won
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
        endLayout.addView(TextView(ctx).apply { text = "🎮"; textSize = 48f; gravity = android.view.Gravity.CENTER })
        endLayout.addView(TextView(ctx).apply { text = if (won) "Vittoria!" else "Game Over"; textSize = 22f; setTextColor(Color.WHITE); gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6)) })
        endLayout.addView(TextView(ctx).apply { text = "Punti: $score  •  Vite: $lives"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN)); gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8)) })
        endLayout.addView(TextView(ctx).apply { text = "+${mvc} MVC  •  +${xp} XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT)); gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16)) })
        endLayout.addView(UiKit.button(ctx, "🔄  Gioca Ancora", UiKit.ACCENT) { overlayContainer?.removeView(overlay); startGame() })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer?.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    inner class PacmanView(context: android.content.Context) : View(context) {
        private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1030") }
        private val pelletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD700") }
        private val eatenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A1A44") }
        private val pacmanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFE500") }
        private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF4444") }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val cellW = w / BOARD_W
            val cellH = h / BOARD_H
            c.drawColor(Color.parseColor("#0D0620"))
            for (r in 0 until BOARD_H) for (col in 0 until BOARD_W) {
                val x = col * cellW
                val y = r * cellH
                when (board[r][col]) {
                    1 -> c.drawRect(x, y, x + cellW, y + cellH, wallPaint)
                    0 -> {
                        c.drawRect(x, y, x + cellW, y + cellH, eatenPaint)
                        c.drawCircle(x + cellW / 2, y + cellH / 2, cellW * 0.15f, pelletPaint)
                    }
                    2 -> c.drawRect(x, y, x + cellW, y + cellH, eatenPaint)
                }
            }
            val px = pacmanX * cellW + cellW / 2
            val py = pacmanY * cellH + cellH / 2
            val pr = cellW * 0.4f
            c.drawCircle(px, py, pr, pacmanPaint)
            c.drawArc(px - pr, py - pr, px + pr, py + pr, pacmanAngle * 57.3f - 45f, 90f, true, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D0620") })
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    val dx = ev.x - pacmanX * (width / BOARD_W)
                    val dy = ev.y - pacmanY * (height / BOARD_H)
                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        pacmanDirX = if (dx > 0) 1f else -1f
                        pacmanDirY = 0f
                    } else {
                        pacmanDirY = if (dy > 0) 1f else -1f
                        pacmanDirX = 0f
                    }
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}