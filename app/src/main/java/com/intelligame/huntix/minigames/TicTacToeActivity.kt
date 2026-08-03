package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.random.Random

/**
 * ⭕❌ Tris — affronta la CPU. Allinea 3 simboli per vincere.
 * (meccanica classica, riscritta nativa)
 */
class TicTacToeActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val CELLS = 9
    private val EMPTY = 0
    private val PLAYER = 1
    private val CPU = 2

    private val board = IntArray(CELLS)
    private val buttons = arrayOfNulls<Button>(CELLS)
    private var gameOver = false
    private var statusText: TextView? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Tris", "⭕"))
        root.addView(TextView(ctx).apply {
            text = "Tu sei ❌, la CPU è ⭕. Allinea 3!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_TIC_TAC_TOE))
        statusText = TextView(ctx).apply {
            text = "Tocca una casella"; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        }
        root.addView(statusText!!)

        val grid = GridLayout(ctx).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for (i in 0 until CELLS) {
            val btn = Button(this).apply {
                text = ""
                textSize = 30f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@TicTacToeActivity, 6).toFloat()
                    setColor(Color.parseColor("#3A2A5A"))
                }
                setPadding(0, 0, 0, 0)
                val idx = i
                setOnClickListener { onPlayerTap(idx) }
            }
            buttons[i] = btn
            grid.addView(
                btn,
                GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = (resources.displayMetrics.density * 96).toInt()
                }
            )
        }
        root.addView(grid)

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        board.fill(EMPTY)
        gameOver = false
        for (i in 0 until CELLS) {
            buttons[i]?.text = ""
            buttons[i]?.isEnabled = true
        }
        statusText?.text = "Tocca una casella"
    }

    private fun onPlayerTap(i: Int) {
        if (gameOver || board[i] != EMPTY) return
        place(i, PLAYER)
        statusText?.text = "CPU sta pensando..."
        for (b in buttons) b?.isEnabled = false
        handler.postDelayed({ cpuTurn() }, 450)
    }

    private fun cpuTurn() {
        if (gameOver) return
        val move = bestCpuMove()
        if (move == -1) {
            endGame(0)
            return
        }
        place(move, CPU)
        if (!gameOver) {
            statusText?.text = "Tocca a te"
            for (b in buttons) b?.isEnabled = true
        }
    }

    private fun place(i: Int, who: Int) {
        board[i] = who
        buttons[i]?.text = if (who == PLAYER) "❌" else "⭕"
        buttons[i]?.setTextColor(if (who == PLAYER) Color.parseColor("#4FC3F7") else Color.parseColor("#EF5350"))
        val winner = winner()
        if (winner != EMPTY) {
            gameOver = true
            endGame(winner)
        } else if (board.none { it == EMPTY }) {
            gameOver = true
            endGame(0)
        }
    }

    private fun winner(): Int {
        val lines = arrayOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
        )
        for (line in lines) {
            val v = board[line[0]]
            if (v != EMPTY && board[line[1]] == v && board[line[2]] == v) return v
        }
        return EMPTY
    }

    private fun bestCpuMove(): Int {
        val winning = findWinningMove(CPU)
        if (winning != -1) return winning
        val blocking = findWinningMove(PLAYER)
        if (blocking != -1) return blocking
        val empty = board.indices.filter { board[it] == EMPTY }
        if (board[4] == EMPTY) return 4
        val corners = intArrayOf(0, 2, 6, 8).filter { board[it] == EMPTY }
        val cornerChance = 0.7f + 0.3f * levelDifficulty(MiniGameManager.GAME_TIC_TAC_TOE)
        if (corners.isNotEmpty() && Random.nextFloat() < cornerChance) return corners[Random.nextInt(corners.size)]
        return empty[Random.nextInt(empty.size)]
    }

    private fun findWinningMove(who: Int): Int {
        for (i in 0 until CELLS) {
            if (board[i] != EMPTY) continue
            board[i] = who
            val w = winner() == who
            board[i] = EMPTY
            if (w) return i
        }
        return -1
    }

    private fun endGame(result: Int) {
        handler.removeCallbacksAndMessages(null)
        val won = result == PLAYER
        val mvc = when (result) {
            PLAYER -> 35
            CPU -> 5
            else -> 12
        }
        val xp = when (result) {
            PLAYER -> 12
            CPU -> 1
            else -> 4
        }
        val lr = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_TIC_TAC_TOE,
                score = if (won) 1 else 0,
                mvc = mvc, xp = xp,
                label = when (result) {
                    PLAYER -> "Tris: hai vinto!"
                    CPU -> "Tris: vince la CPU"
                    else -> "Tris: pareggio"
                },
                isWin = won
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
            text = when (result) { PLAYER -> "🏆"; CPU -> "🤖"; else -> "🤝" }
            textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = when (result) {
                PLAYER -> "Hai vinto!"
                CPU -> "Vince la CPU"
                else -> "Pareggio!"
            }
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        lr?.let { endLayout.addView(levelResultView(it)) }
        endLayout.addView(TextView(ctx).apply {
            text = "+${lr?.mvc ?: mvc} MVC  •  +${lr?.xp ?: xp} XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
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
}
