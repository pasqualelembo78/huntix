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

class TetrisActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val BOARD_W = 10
    private val BOARD_H = 20
    private var board = Array(BOARD_H) { IntArray(BOARD_W) }
    private var gameRunning = false
    private var score = 0
    private var scoreText: TextView? = null
    private var gameView: TetrisView? = null
    private var overlayContainer: FrameLayout? = null
    private var currentPiece: Piece? = null
    private var nextPiece: Piece? = null
    private var pieceX = 0
    private var pieceY = 0
    private var dropInterval = 800L
    private var lastDrop = 0L
    private var linesCleared = 0
    private var gameOverFlag = false

    private val pieceTypes = listOf(
        intArrayOf(1,1,1,1),
        intArrayOf(1,1,1,0, 0,1,0,0),
        intArrayOf(1,1,0,0, 0,1,1,0),
        intArrayOf(0,1,1,0, 1,1,0,0),
        intArrayOf(1,1,0,0, 0,0,1,1),
        intArrayOf(1,1,1,0, 0,0,1,0),
        intArrayOf(1,1,1,0, 0,1,0,0)
    )

    private val pieceColors = listOf(
        Color.parseColor("#00FFFF"),
        Color.parseColor("#FFA500"),
        Color.parseColor("#FF0000"),
        Color.parseColor("#00FF00"),
        Color.parseColor("#0000FF"),
        Color.parseColor("#FFFF00"),
        Color.parseColor("#FF00FF")
    )

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Tetris", "🎮"))
        root.addView(TextView(ctx).apply {
            text = "Scorri per muovere, tocca per ruotare, scendi per cadere più veloce"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_TETRIS))
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 18f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        gameView = TetrisView(ctx)
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
        score = 0; linesCleared = 0; gameOverFlag = false
        scoreText?.text = "Punti: 0"
        gameRunning = true
        currentPiece = null
        nextPiece = null
        spawnPiece()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, 16L)
    }

    private fun spawnPiece() {
        val idx = Random.nextInt(pieceTypes.size)
        currentPiece = Piece(idx, pieceTypes[idx], pieceColors[idx])
        nextPiece = Piece(Random.nextInt(pieceTypes.size), pieceTypes[Random.nextInt(pieceTypes.size)], pieceColors[Random.nextInt(pieceColors.size)])
        pieceX = BOARD_W / 2 - 2
        pieceY = 0
        if (collides(pieceX, pieceY, currentPiece!!.cells)) {
            endGame(false)
        }
    }

    private fun collides(px: Int, py: Int, cells: IntArray): Boolean {
        for (r in 0 until 4) for (c in 0 until 4) {
            val idx = r * 4 + c
            if (cells[idx] == 0) continue
            val bx = px + c
            val by = py + r
            if (bx < 0 || bx >= BOARD_W || by >= BOARD_H) return true
            if (by >= 0 && board[by][bx] != 0) return true
        }
        return false
    }

    private fun lockPiece() {
        val p = currentPiece ?: return
        for (r in 0 until 4) for (c in 0 until 4) {
            val idx = r * 4 + c
            if (p.cells[idx] == 0) continue
            val bx = pieceX + c
            val by = pieceY + r
            if (by in 0 until BOARD_H && bx in 0 until BOARD_W) {
                board[by][bx] = p.colorIdx + 1
            }
        }
        clearLines()
        spawnPiece()
    }

    private fun clearLines() {
        var cleared = 0
        var r = BOARD_H - 1
        while (r >= 0) {
            if (board[r].all { it != 0 }) {
                for (i in r until BOARD_H - 1) board[i] = board[i + 1]
                board[BOARD_H - 1].fill(0)
                cleared++
            } else r--
        }
        if (cleared > 0) {
            linesCleared += cleared
            score += when (cleared) {
                1 -> 100; 2 -> 300; 3 -> 500; else -> 800
            }
            scoreText?.text = "Punti: $score"
        }
    }

    private fun movePiece(dx: Int, dy: Int): Boolean {
        val p = currentPiece ?: return false
        if (!collides(pieceX + dx, pieceY + dy, p.cells)) {
            pieceX += dx; pieceY += dy
            return true
        }
        return false
    }

    private fun rotatePiece() {
        val p = currentPiece ?: return
        val rotated = IntArray(16)
        for (r in 0 until 4) for (c in 0 until 4) {
            rotated[c * 4 + (3 - r)] = p.cells[r * 4 + c]
        }
        if (!collides(pieceX, pieceY, rotated)) {
            currentPiece = Piece(p.type, rotated, p.color)
        }
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            if (System.currentTimeMillis() - lastDrop > dropInterval) {
                if (!movePiece(0, 1)) lockPiece()
                lastDrop = System.currentTimeMillis()
            }
            gameView?.invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    private fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        gameOverFlag = true
        handler.removeCallbacksAndMessages(null)
        val mvc = (score / 10).coerceAtLeast(5)
        val xp = (score / 20).coerceAtLeast(2)
        try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_TETRIS, score,
                mvc = mvc, xp = xp,
                label = "Tetris: $score pt, ${linesCleared} righe",
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
        endLayout.addView(TextView(ctx).apply { text = "Game Over"; textSize = 22f; setTextColor(Color.WHITE); gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6)) })
        endLayout.addView(TextView(ctx).apply { text = "Punti: $score  •  Righe: $linesCleared"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN)); gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8)) })
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

    inner class TetrisView(context: android.content.Context) : View(context) {
        private val cellSize = 30f
        private val offsetX = 2f
        private val offsetY = 2f
        private var startX = 0f
        private var startY = 0f

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            c.drawColor(Color.parseColor("#0D0620"))
            val cs = cellSize
            for (r in 0 until BOARD_H) for (col in 0 until BOARD_W) {
                val x = offsetX + col * cs
                val y = offsetY + r * cs
                if (board[r][col] != 0) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pieceColors[board[r][col] - 1] }
                    c.drawRoundRect(x + 1, y + 1, x + cs - 1, y + cs - 1, 3f, 3f, paint)
                } else {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1030") }
                    c.drawRoundRect(x + 1, y + 1, x + cs - 1, y + cs - 1, 3f, 3f, paint)
                }
            }
            val p = currentPiece
            if (p != null) {
                for (r in 0 until 4) for (c in 0 until 4) {
                    val idx = r * 4 + c
                    if (p.cells[idx] == 0) continue
                    val x = offsetX + (pieceX + c) * cs
                    val y = offsetY + (pieceY + r) * cs
                    if (pieceY + r >= 0) {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.color }
                        c.drawRoundRect(x + 1, y + 1, x + cs - 1, y + cs - 1, 3f, 3f, paint)
                    }
                }
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!gameRunning) return true
                    startX = ev.x
                    startY = ev.y
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!gameRunning) return true
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    if (Math.abs(dx) < 30 && Math.abs(dy) < 30) {
                        rotatePiece()
                        invalidate()
                        return true
                    }
                    if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0) movePiece(1, 0) else movePiece(-1, 0)
                    } else {
                        if (dy > 0) movePiece(0, 1) else movePiece(0, -1)
                    }
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    private data class Piece(val type: Int, val cells: IntArray, val color: Int) {
        val colorIdx: Int get() = type
    }
}