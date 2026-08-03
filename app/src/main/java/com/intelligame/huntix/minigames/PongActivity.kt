package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 🏓 Breakout Eggs — brick-breaker in stile Huntix.
 *
 * Rompi un muro di uova con una palla rimbalzante. Ogni uovo distrutto conta
 * come raccolto. Raccogli tutte le uova del muro per vincere! Più hai di livello
 * più uova ci sono nel muro e più veloce è la palla.
 */
class PongActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val tickMs = 16L

    private lateinit var gameView: PongGameView
    private lateinit var scoreText: TextView
    private lateinit var livesText: TextView
    private lateinit var levelProgressText: TextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var overlayContainer: FrameLayout

    private var paddleX = 0f
    private var paddleW = 160f
    private var ballX = 0f
    private var ballY = 0f
    private var ballVX = 0f
    private var ballVY = 0f
    private var ballRadius = 14f
    private val bricks = mutableListOf<Brick>()
    private var brickCols = 8
    private var brickRows = 5
    private var destroyed = 0
    private var totalBricks = 0
    private var lives = 3
    private var score = 0
    private var gameRunning = false

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            update()
            gameView.invalidate()
            handler.postDelayed(this, tickMs)
        }
    }

    data class Brick(
        val cx: Float, val cy: Float, val w: Float, val h: Float,
        val color: Int, var alive: Boolean = true
    )

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Breakout Eggs", "🏓"))
        root.addView(TextView(ctx).apply {
            text = "Rompimi il muro di uova! Raccogli tutte le uova per vincere. 🥚"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_PONG))

        val hudRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        scoreText = TextView(ctx).apply {
            text = "Uova: 0 / 0"; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, 0, UiKit.dp(ctx, 12), 0)
        }
        livesText = TextView(ctx).apply {
            text = "Vite: 3"; textSize = 16f; setTextColor(Color.parseColor(UiKit.GREEN))
            setPadding(0, 0, 0, 0)
        }
        hudRow.addView(scoreText!!)
        hudRow.addView(livesText!!)
        root.addView(hudRow)
        root.addView(buildLevelProgress())

        gameView = PongGameView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        overlayContainer = wrapper
        setContentView(wrapper)
        root.post { startGame() }
    }

    private fun startGame() {
        val diff = levelDifficulty(MiniGameManager.GAME_PONG)
        val w = (resources.displayMetrics.widthPixels - UiKit.dp(this, 28) * 2).toFloat()
        val h = (resources.displayMetrics.heightPixels - UiKit.dp(this, 220) * 2).toFloat()

        paddleX = w / 2f
        paddleW = min(200f, w * 0.4f)
        ballX = w / 2f
        ballY = h - 100f
        ballVX = 6f * (1f + diff * 0.5f)
        ballVY = -8f * (1f + diff * 0.3f)
        ballRadius = 14f + diff * 6f

        brickCols = (6 + (3 * diff).toInt()).coerceIn(6, 12)
        brickRows = (3 + (2 * diff).toInt()).coerceIn(3, 6)
        buildBricks(w, h)

        destroyed = 0
        totalBricks = bricks.count { it.alive }
        lives = 3
        score = 0
        gameRunning = true
        scoreText.text = "Uova: 0 / $totalBricks"
        livesText.text = "Vite: $lives"
        updateLevelProgress()
        gameView.setSizes(w, h)
        gameView.invalidate()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, tickMs)
    }

    private fun buildLevelProgress(): View {
        val ctx = this
        val currentLevel = MiniGameManager.getLevel(this, MiniGameManager.GAME_PONG)
        val currentTarget = MiniGameManager.getLevelTarget(this, MiniGameManager.GAME_PONG)
        val nextTarget = MiniGameManager.getLevelTarget(this, MiniGameManager.GAME_PONG, currentLevel + 1)
        val progressRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, UiKit.dp(ctx, 6))
        }
        progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = nextTarget
            progress = score
            val bgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor(UiKit.BG))
                setShape(GradientDrawable.RECTANGLE)
                setCornerRadius(UiKit.dp(ctx, 4).toFloat())
            }
            setProgressDrawable(android.graphics.drawable.LayerDrawable(arrayOf(
                bgDrawable,
                ClipDrawable(GradientDrawable().apply {
                    setColor(Color.parseColor(UiKit.ACCENT))
                    setShape(GradientDrawable.RECTANGLE)
                    setCornerRadius(UiKit.dp(ctx, 4).toFloat())
                }, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL)
            )))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 10)
            )
        }
        levelProgressText = TextView(ctx).apply {
            text = "Livello $currentLevel  •  ${currentTarget} → $nextTarget "
            textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            gravity = android.view.Gravity.CENTER
        }
        progressRow.addView(progressBar)
        progressRow.addView(levelProgressText)
        return progressRow
    }

    private fun updateLevelProgress() {
        val currentLevel = MiniGameManager.getLevel(this, MiniGameManager.GAME_PONG)
        val nextTarget = MiniGameManager.getLevelTarget(this, MiniGameManager.GAME_PONG, currentLevel + 1)
        progressBar.max = nextTarget
        progressBar.progress = score
        levelProgressText.text = "Livello $currentLevel  •  $score / $nextTarget → prossimo"
    }

    private fun buildBricks(w: Float, h: Float) {
        bricks.clear()
        val brickW = w / brickCols
        val brickH = h * 0.04f
        val startX = brickW / 2f
        val startY = h * 0.15f
        val colors = listOf(
            0xFFFF6B6B.toInt(), 0xFFFFD166.toInt(), 0xFF00FF88.toInt(),
            0xFF6AD7FF.toInt(), 0xFFA78BFA.toInt()
        )
        for (row in 0 until brickRows) {
            val color = colors[row % colors.size]
            for (col in 0 until brickCols) {
                val cx = startX + col * brickW
                val cy = startY + row * (brickH + h * 0.01f)
                bricks.add(Brick(cx, cy, brickW - w * 0.01f, brickH, color))
            }
        }
    }

    private fun update() {
        val diff = levelDifficulty(MiniGameManager.GAME_PONG)
        val w = gameView.viewW
        val h = gameView.viewH

        ballX += ballVX
        ballY += ballVY

        if (ballX <= ballRadius || ballX >= w - ballRadius) ballVX = -ballVX
        if (ballY <= ballRadius) ballVY = -abs(ballVY)

        if (ballY >= h - ballRadius) {
            endGame(false)
            return
        }

        for (brick in bricks) {
            if (brick.alive &&
                ballX in (brick.cx - brick.w / 2f - ballRadius)..(brick.cx + brick.w / 2f + ballRadius) &&
                ballY in (brick.cy - brick.h / 2f - ballRadius)..(brick.cy + brick.h / 2f + ballRadius)
            ) {
                brick.alive = false
                destroyed++
                score += 10 + (diff * 10).toInt()
                scoreText.text = "Uova: $destroyed / $totalBricks"
                updateLevelProgress()
                ballVY = -ballVY
                break
            }
        }

        if (ballY + ballVY >= h - 60f - ballRadius &&
            abs(ballX - paddleX) < paddleW / 2f + ballRadius) {
            ballY = h - 60f - ballRadius
            ballVY = -abs(ballVY) * (1.02f + diff * 0.02f)
            val offset = (ballX - paddleX) / (paddleW / 2f)
            ballVX += offset * 2f * (1f + diff * 0.1f)
            ballVX = ballVX.coerceIn(-12f * (1f + diff), 12f * (1f + diff))
        }

        if (destroyed >= totalBricks) {
            endGame(true)
        }
    }

    private fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)

        val mul = 1f + levelDifficulty(MiniGameManager.GAME_PONG) * 0.5f
        val mvc = if (won) (score * 0.15f * mul).toInt().coerceAtLeast(20) else max(score / 4, 8)
        val xp = if (won) (score * 0.08f * mul).toInt().coerceAtLeast(10) else max(score / 6, 5)

        val result = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_PONG, score,
                mvc = mvc, xp = xp,
                label = "Breakout: $destroyed uova",
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
            text = if (won) "🏆" else "💧"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Hai vinto!" else "Game Over"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Uova: $destroyed / $totalBricks  •  Vite: $lives"; textSize = 16f
            setTextColor(Color.parseColor(if (won) UiKit.GREEN else UiKit.TEXT_DIM))
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

    /**
     * PongGameView — rendering Canvas del brick-breaker.
     * Il giocatore trascina la palettiera per deviare la palla.
     */
    inner class PongGameView(ctx: android.content.Context) : View(ctx) {

        private val bgPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A78BFA")
            strokeWidth = 2f
        }
        private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FF88") }
        private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        var viewW = 0f
        var viewH = 0f

        fun setSizes(w: Float, h: Float) { viewW = w; viewH = h }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawColor(bgPaint.color)

            val midX = w / 2f
            for (y in 0..h.toInt() step 40) {
                canvas.drawLine(midX, y.toFloat(), midX, (y + 20).toFloat(), midPaint)
            }

            val pH = 24f
            canvas.drawRoundRect(
                RectF(paddleX - paddleW / 2f, h - 60f - pH, paddleX + paddleW / 2f, h - 60f),
                12f, 12f, paddlePaint
            )

            canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)

            for (brick in bricks) {
                if (brick.alive) {
                    val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brick.color }
                    canvas.drawRoundRect(
                        RectF(brick.cx - brick.w / 2f, brick.cy - brick.h / 2f,
                            brick.cx + brick.w / 2f, brick.cy + brick.h / 2f),
                        4f, 4f, bp
                    )
                }
            }

            val diff = levelDifficulty(MiniGameManager.GAME_PONG)
            val speedLabel = "⚡ ${String.format("%.1f", 1f + diff * 0.5f)}x"
            val sl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER
            }
            canvas.drawText(speedLabel, midX, h - 10f, sl)
        }

        private fun dp(v: Float): Float = v * resources.displayMetrics.density

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    paddleX = ev.x.coerceIn(paddleW / 2f, width.toFloat() - paddleW / 2f)
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}
