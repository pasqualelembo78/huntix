package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
 * 🧩 2048 — unisci le tessere uguali scorrendo, fino a 2048.
 * Classico puzzle (meccanica pubblica, ispirato a 2048 MIT, riscritto nativo).
 */
class Game2048Activity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private var board = IntArray(16)
    private var score = 0
    private var gameRunning = false
    private var reached2048 = false
    private var scoreText: TextView? = null
    private var boardView: BoardView? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "2048", "🧩"))
        root.addView(TextView(ctx).apply {
            text = "Scorri per unire le tessere. Arriva a 2048!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 18f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(scoreText!!)
        header.addView(TextView(ctx).apply {
            text = "🎯 2048"; textSize = 18f; setTextColor(Color.parseColor(UiKit.ACCENT))
        })
        root.addView(header)

        boardView = BoardView(ctx)
        root.addView(boardView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        board = IntArray(16)
        score = 0
        reached2048 = false
        gameRunning = true
        scoreText?.text = "Punti: 0"
        addRandomTile()
        addRandomTile()
        boardView?.invalidate()
    }

    private fun addRandomTile() {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return
        val idx = empty[Random.nextInt(empty.size)]
        board[idx] = if (Random.nextFloat() < 0.9f) 2 else 4
    }

    /** Unisce una riga verso sinistra. Ritorna la nuova riga e i punti guadagnati. */
    private fun mergeLeft(line: IntArray): Pair<IntArray, Int> {
        val nz = line.filter { it != 0 }
        val out = mutableListOf<Int>()
        var pts = 0
        var i = 0
        while (i < nz.size) {
            if (i + 1 < nz.size && nz[i] == nz[i + 1]) {
                val v = nz[i] * 2
                out.add(v); pts += v; i += 2
            } else {
                out.add(nz[i]); i += 1
            }
        }
        while (out.size < 4) out.add(0)
        return out.toIntArray() to pts
    }

    private fun move(direction: Int): Boolean {
        val before = board.copyOf()
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                val i = r * 4 + c
                val line = when (direction) {
                    0 -> intArrayOf(board[r * 4 + 0], board[r * 4 + 1], board[r * 4 + 2], board[r * 4 + 3])       // left
                    1 -> intArrayOf(board[r * 4 + 3], board[r * 4 + 2], board[r * 4 + 1], board[r * 4 + 0])       // right
                    2 -> intArrayOf(board[0 * 4 + c], board[1 * 4 + c], board[2 * 4 + c], board[3 * 4 + c])       // up
                    else -> intArrayOf(board[3 * 4 + c], board[2 * 4 + c], board[1 * 4 + c], board[0 * 4 + c])    // down
                }
                val (merged, pts) = mergeLeft(line)
                when (direction) {
                    0 -> for (k in 0 until 4) board[r * 4 + k] = merged[k]
                    1 -> for (k in 0 until 4) board[r * 4 + k] = merged[3 - k]
                    2 -> for (k in 0 until 4) board[k * 4 + c] = merged[k]
                    else -> for (k in 0 until 4) board[k * 4 + c] = merged[3 - k]
                }
                score += pts
            }
        }
        if (!board.contentEquals(before)) {
            addRandomTile()
            scoreText?.text = "Punti: $score"
            if (board.any { it >= 2048 }) reached2048 = true
            if (!hasMoves()) endGame()
            return true
        }
        return false
    }

    private fun hasMoves(): Boolean {
        if (board.any { it == 0 }) return true
        for (r in 0 until 4) for (c in 0 until 4) {
            val v = board[r * 4 + c]
            if (c < 3 && board[r * 4 + c + 1] == v) return true
            if (r < 3 && board[(r + 1) * 4 + c] == v) return true
        }
        return false
    }

    private fun endGame() {
        if (!gameRunning) return
        gameRunning = false
        val won = reached2048
        val mvc = (score / 2).coerceAtLeast(10).coerceAtMost(500)
        val xp = (score / 4).coerceAtLeast(3).coerceAtMost(150)
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_2048)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc, xpPoints = xp,
                    giftEggRarityId = if (won) "common" else null,
                    label = if (won) "2048: hai raggiunto 2048!" else "2048: $score punti",
                    isWin = won
                ),
                MiniGameManager.GAME_2048
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
            text = if (won) "🏆" else "🎮"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Hai raggiunto 2048!" else "Nessuna mossa disponibile"; textSize = 22f; setTextColor(Color.WHITE)
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

    inner class BoardView(context: android.content.Context) : View(context) {

        private val colors = mapOf(
            0 to "#CDC1B4", 2 to "#EEE4DA", 4 to "#EDE0C8", 8 to "#F2B179", 16 to "#F59563",
            32 to "#F67C5F", 64 to "#F65E3B", 128 to "#EDCF72", 256 to "#EDCC61", 512 to "#EDC850",
            1024 to "#EDC53F", 2048 to "#EDC22E"
        )

        private val textDark = Color.parseColor("#776E65")
        private val textLight = Color.WHITE

        private var startX = 0f
        private var startY = 0f

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val gap = w * 0.02f
            val size = (w - gap * 5) / 4
            val cell = RectF()
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
            for (r in 0 until 4) for (col in 0 until 4) {
                val v = board[r * 4 + col]
                val left = gap + col * (size + gap)
                val top = gap + r * (size + gap)
                cell.set(left, top, left + size, top + size)
                c.drawRoundRect(cell, size * 0.08f, size * 0.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(colors[v] ?: "#EDC22E")
                })
                if (v != 0) {
                    textPaint.textSize = if (v < 100) size * 0.42f else if (v < 1000) size * 0.34f else size * 0.28f
                    textPaint.color = if (v <= 4) textDark else textLight
                    textPaint.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                    val baseline = top + (size - textPaint.textSize) / 2 - textPaint.ascent() / 2
                    c.drawText(v.toString(), left + size / 2, baseline, textPaint)
                }
            }
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
                    val direction = if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0) 1 else 0
                    } else {
                        if (dy > 0) 3 else 2
                    }
                    move(direction)
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}
