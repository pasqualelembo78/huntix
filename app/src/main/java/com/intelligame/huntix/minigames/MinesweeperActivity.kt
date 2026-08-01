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
 * 💣 Campo Minato — scova le uova-bomba senza farle esplodere.
 * Griglia 9x9 con 10 mine (meccanica classica, riscritta nativa).
 */
class MinesweeperActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val SIZE = 9
    private val MINES = 10
    private val CELLS = SIZE * SIZE

    private val mines = BooleanArray(CELLS)
    private val revealed = BooleanArray(CELLS)
    private val flagged = BooleanArray(CELLS)
    private val cells = arrayOfNulls<Button>(CELLS)
    private var firstClick = true
    private var gameOver = false
    private var win = false
    private var flagsUsed = 0
    private var seconds = 0
    private var mineText: TextView? = null
    private var timerText: TextView? = null
    private var grid: GridLayout? = null
    private var overlayContainer: FrameLayout? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (gameOver) return
            seconds++
            timerText?.text = "⏱ ${seconds}s"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Campo Minato", "💣"))
        root.addView(TextView(ctx).apply {
            text = "Tocca per scoprire, tieni premuto per la bandierina 🚩"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        mineText = TextView(ctx).apply {
            text = "💣 $MINES"; textSize = 16f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timerText = TextView(ctx).apply {
            text = "⏱ 0s"; textSize = 16f; setTextColor(Color.parseColor(UiKit.ACCENT))
        }
        header.addView(mineText!!); header.addView(timerText!!)
        root.addView(header)

        grid = GridLayout(ctx).apply {
            columnCount = SIZE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(grid!!)

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        buildBoard()
    }

    private fun buildBoard() {
        grid?.removeAllViews()
        firstClick = true
        gameOver = false
        win = false
        flagsUsed = 0
        seconds = 0
        mines.fill(false)
        revealed.fill(false)
        flagged.fill(false)
        timerText?.text = "⏱ 0s"
        mineText?.text = "💣 $MINES"
        for (i in 0 until CELLS) {
            val btn = Button(this).apply {
                text = ""
                textSize = 13f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@MinesweeperActivity, 3).toFloat()
                    setColor(Color.parseColor("#3A2A5A"))
                }
                setPadding(0, 0, 0, 0)
                isAllCaps = false
                val idx = i
                setOnClickListener { onTap(idx) }
                setOnLongClickListener {
                    onFlag(idx)
                    true
                }
            }
            cells[i] = btn
            grid?.addView(
                btn,
                GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = (resources.displayMetrics.density * 40).toInt()
                }
            )
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(timerRunnable, 1000)
    }

    private fun onTap(i: Int) {
        if (gameOver || revealed[i] || flagged[i]) return
        if (firstClick) {
            firstClick = false
            placeMines(i)
        }
        if (mines[i]) {
            revealAllMines()
            endGame(false)
            return
        }
        floodReveal(i)
        updateButtons()
        if (checkWin()) {
            win = true
            endGame(true)
        }
    }

    private fun onFlag(i: Int) {
        if (gameOver || revealed[i]) return
        if (firstClick) return
        flagged[i] = !flagged[i]
        flagsUsed += if (flagged[i]) 1 else -1
        cells[i]?.text = if (flagged[i]) "🚩" else ""
        cells[i]?.setTextColor(Color.WHITE)
        mineText?.text = "💣 ${(MINES - flagsUsed).coerceAtLeast(0)}"
    }

    private fun placeMines(safe: Int) {
        var placed = 0
        while (placed < MINES) {
            val idx = Random.nextInt(CELLS)
            if (idx != safe && !mines[idx]) {
                mines[idx] = true
                placed++
            }
        }
    }

    private fun neighborMines(i: Int): Int {
        var n = 0
        for (each in neighbors(i)) if (mines[each]) n++
        return n
    }

    private fun neighbors(i: Int): List<Int> {
        val r = i / SIZE
        val c = i % SIZE
        val out = mutableListOf<Int>()
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until SIZE && nc in 0 until SIZE) out.add(nr * SIZE + nc)
        }
        return out
    }

    private fun floodReveal(start: Int) {
        val stack = ArrayDeque<Int>()
        stack.add(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (revealed[i] || flagged[i] || mines[i]) continue
            revealed[i] = true
            if (neighborMines(i) == 0) {
                for (each in neighbors(i)) if (!revealed[each]) stack.add(each)
            }
        }
    }

    private fun revealAllMines() {
        for (i in 0 until CELLS) if (mines[i] && !flagged[i]) revealed[i] = true
    }

    private fun checkWin(): Boolean {
        var revealedCount = 0
        for (i in 0 until CELLS) if (revealed[i]) revealedCount++
        return revealedCount == CELLS - MINES
    }

    private fun updateButtons() {
        for (i in 0 until CELLS) {
            if (!revealed[i]) continue
            val btn = cells[i] ?: continue
            if (mines[i]) {
                btn.text = "💣"
                btn.setTextColor(Color.RED)
            } else {
                val n = neighborMines(i)
                btn.text = if (n == 0) "" else "$n"
                btn.setTextColor(numberColor(n))
            }
            btn.setBackgroundColor(Color.parseColor("#2A1A44"))
            btn.isClickable = false
        }
    }

    private fun numberColor(n: Int): Int = when (n) {
        1 -> Color.parseColor("#4FC3F7")
        2 -> Color.parseColor("#66BB6A")
        3 -> Color.parseColor("#EF5350")
        4 -> Color.parseColor("#AB47BC")
        else -> Color.parseColor("#FFA726")
    }

    private fun endGame(won: Boolean) {
        if (gameOver) return
        gameOver = true
        handler.removeCallbacksAndMessages(null)
        val mvc = if (won) 60 else 10
        val xp = if (won) 20 else 3
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_MINESWEEPER)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc, xpPoints = xp,
                    giftEggRarityId = if (won) "common" else null,
                    label = if (won) "Campo Minato: vittoria!" else "Campo Minato: boom!",
                    isWin = won
                ),
                MiniGameManager.GAME_MINESWEEPER
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
            text = if (won) "🎉" else "💥"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Hai vinto!" else "Boom!"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Tempo: ${seconds}s"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "+$mvc MVC  •  +$xp XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "🔄  Gioca Ancora", UiKit.ACCENT) {
            overlayContainer?.removeView(overlay)
            buildBoard()
        })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer?.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
