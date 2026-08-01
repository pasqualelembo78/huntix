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
 * 🔴🔵 Forza 4 — 2 giocatori (o contro CPU): allinea 4 dischi.
 * Griglia 7x6 (meccanica classica, riscritta nativa).
 */
class ConnectFourActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val COLS = 7
    private val ROWS = 6
    private val CELLS = COLS * ROWS

    private val board = IntArray(CELLS)
    private var currentPlayer = 1
    private var gameOver = false
    private var cpuMode = false
    private var statusText: TextView? = null
    private var gameView: ConnectView? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Forza 4", "🔵"))
        root.addView(TextView(ctx).apply {
            text = "Tocca una colonna per far cadere il disco. Allinea 4 per vincere!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        statusText = TextView(ctx).apply {
            text = "Scegli la modalità"; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(statusText!!)

        gameView = ConnectView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)

        val picker = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(UiKit.dp(ctx, 30), UiKit.dp(ctx, 40), UiKit.dp(ctx, 30), UiKit.dp(ctx, 40))
        }
        picker.addView(TextView(ctx).apply {
            text = "🎮"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        picker.addView(TextView(ctx).apply {
            text = "Scegli la modalità"; textSize = 20f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 16))
        })
        picker.addView(UiKit.button(ctx, "👥  Due Giocatori", UiKit.PURPLE) {
            cpuMode = false
            picker.removeAllViews()
            wrapper.removeView(picker)
            startGame()
        })
        picker.addView(UiKit.button(ctx, "🤖  Contro CPU", UiKit.ACCENT) {
            cpuMode = true
            picker.removeAllViews()
            wrapper.removeView(picker)
            startGame()
        })
        picker.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        val pickerFrame = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#CC0D0620"))
            isClickable = true; isFocusable = true
        }
        pickerFrame.addView(picker)
        wrapper.addView(pickerFrame)
    }

    private fun startGame() {
        board.fill(0)
        currentPlayer = 1
        gameOver = false
        updateStatus()
        gameView?.invalidate()
    }

    private fun updateStatus() {
        statusText?.text = when {
            cpuMode && currentPlayer == 2 -> "🤖 CPU sta pensando..."
            currentPlayer == 1 -> "🔴 Giocatore Rosso"
            else -> "🔵 Giocatore Blu"
        }
    }

    private fun drop(col: Int): Boolean {
        if (gameOver || col !in 0 until COLS) return false
        for (r in ROWS - 1 downTo 0) {
            val i = r * COLS + col
            if (board[i] == 0) {
                board[i] = currentPlayer
                if (checkWin(r, col, currentPlayer)) {
                    gameOver = true
                    endGame(currentPlayer)
                } else if (board.none { it == 0 }) {
                    gameOver = true
                    endGame(0)
                } else {
                    currentPlayer = if (currentPlayer == 1) 2 else 1
                    updateStatus()
                    if (cpuMode && currentPlayer == 2) {
                        handler.postDelayed({ cpuMove() }, 500)
                    }
                }
                gameView?.invalidate()
                return true
            }
        }
        return false
    }

    private fun cpuMove() {
        if (gameOver) return
        val col = bestCpuMove()
        drop(col)
    }

    private fun bestCpuMove(): Int {
        val winning = winningMove(2)
        if (winning != -1) return winning
        val blocking = winningMove(1)
        if (blocking != -1) return blocking
        val valid = (0 until COLS).filter { board[it] == 0 }
        val preferCenter = valid.filter { it in 2..4 }
        val pool = if (preferCenter.isNotEmpty() && Random.nextFloat() < 0.6) preferCenter else valid
        return pool[Random.nextInt(pool.size)]
    }

    private fun winningMove(player: Int): Int {
        for (col in 0 until COLS) {
            for (r in ROWS - 1 downTo 0) {
                val i = r * COLS + col
                if (board[i] == 0) {
                    board[i] = player
                    val wins = checkWin(r, col, player)
                    board[i] = 0
                    if (wins) return col
                    break
                }
            }
        }
        return -1
    }

    private fun checkWin(r: Int, c: Int, player: Int): Boolean {
        val dirs = arrayOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for ((dr, dc) in dirs) {
            var count = 1
            count += countDir(r, c, dr, dc, player)
            count += countDir(r, c, -dr, -dc, player)
            if (count >= 4) return true
        }
        return false
    }

    private fun countDir(r: Int, c: Int, dr: Int, dc: Int, player: Int): Int {
        var count = 0
        var rr = r + dr
        var cc = c + dc
        while (rr in 0 until ROWS && cc in 0 until COLS && board[rr * COLS + cc] == player) {
            count++
            rr += dr
            cc += dc
        }
        return count
    }

    private fun endGame(winner: Int) {
        handler.removeCallbacksAndMessages(null)
        val won = winner != 0
        val mvc = if (won) 50 else 15
        val xp = if (won) 16 else 4
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_CONNECT4)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc, xpPoints = xp,
                    label = when (winner) {
                        1 -> "Forza 4: vince il Rosso!"
                        2 -> "Forza 4: vince il Blu!"
                        else -> "Forza 4: pareggio"
                    },
                    isWin = won
                ),
                MiniGameManager.GAME_CONNECT4
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
            text = when (winner) { 1 -> "🔴"; 2 -> "🔵"; else -> "🤝" }
            textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = when (winner) {
                1 -> "Vince il Rosso!"
                2 -> "Vince il Blu!"
                else -> "Pareggio!"
            }
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
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
        gameOver = true
        handler.removeCallbacksAndMessages(null)
    }

    inner class ConnectView(context: android.content.Context) : View(context) {

        private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
        private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#42A5F5") }
        private val bgPaint = Paint().apply { color = Color.parseColor("#1A1030") }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val cw = w / COLS
            val ch = h / ROWS
            c.drawRoundRect(android.graphics.RectF(0f, 0f, w, h), 14f, 14f, bgPaint)
            for (r in 0 until ROWS) for (col in 0 until COLS) {
                val cx = col * cw + cw / 2
                val cy = r * ch + ch / 2
                val radius = cw * 0.38f
                when (board[r * COLS + col]) {
                    0 -> c.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D0620") })
                    1 -> c.drawCircle(cx, cy, radius, redPaint)
                    else -> c.drawCircle(cx, cy, radius, bluePaint)
                }
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    val col = (ev.x / (width / COLS)).toInt().coerceIn(0, COLS - 1)
                    if (cpuMode && currentPlayer == 2) return true
                    drop(col)
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}
