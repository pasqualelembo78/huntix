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

class Match3Activity : MiniGameBase() {

    private val COLS = 8
    private val ROWS = 8
    private val GEM_COLORS = intArrayOf(
        Color.parseColor("#FF4444"),
        Color.parseColor("#4488FF"),
        Color.parseColor("#44DD88"),
        Color.parseColor("#FFCC22"),
        Color.parseColor("#CC66FF")
    )
    private val GEM_EMOJIS = arrayOf("\uD83D\uDD34", "\uD83D\uDD35", "\uD83D\uDFE2", "\uD83D\uDFE1", "\uD83D\uDFE3")
    private val handler = Handler(Looper.getMainLooper())

    private var board = Array(ROWS) { IntArray(COLS) }
    private var selectedR = -1
    private var selectedC = -1
    private var score = 0
    private var combo = 1
    private var timeLeft = 60
    private var gameRunning = false
    private var scoreText: TextView? = null
    private var timeText: TextView? = null
    private var gridView: GridView? = null
    private var busy = false

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

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }

        root.addView(UiKit.title(ctx, "Match 3", "\uD83D\uDC8E"))

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 16f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeText = TextView(ctx).apply {
            text = "Tempo: 60s"; textSize = 16f; setTextColor(Color.parseColor(UiKit.ACCENT))
        }
        header.addView(scoreText)
        header.addView(timeText)
        root.addView(header)

        gridView = GridView(ctx)
        root.addView(gridView)

        setContentView(root)
        startGame()
    }

    private fun startGame() {
        score = 0
        combo = 1
        timeLeft = 60
        gameRunning = true
        busy = false
        scoreText?.text = "Punti: 0"
        timeText?.text = "Tempo: 60s"
        selectedR = -1; selectedC = -1
        initBoard()
        gridView?.invalidate()
        handler.postDelayed(timerRunnable, 1000)
    }

    private fun initBoard() {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                do {
                    board[r][c] = (Math.random() * GEM_COLORS.size).toInt()
                } while (hasMatchAt(r, c))
            }
        }
    }

    private fun hasMatchAt(r: Int, c: Int): Boolean {
        val v = board[r][c]
        if (c >= 2 && board[r][c - 1] == v && board[r][c - 2] == v) return true
        if (r >= 2 && board[r - 1][c] == v && board[r - 2][c] == v) return true
        return false
    }

    private fun findMatches(): List<Pair<Int, Int>> {
        val matched = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until ROWS) {
            var run = 1
            for (c in 1 until COLS) {
                if (board[r][c] == board[r][c - 1] && board[r][c] >= 0) run++
                else {
                    if (run >= 3) for (i in 0 until run) matched.add(r to c - 1 - i)
                    run = 1
                }
            }
            if (run >= 3) for (i in 0 until run) matched.add(r to COLS - 1 - i)
        }
        for (c in 0 until COLS) {
            var run = 1
            for (r in 1 until ROWS) {
                if (board[r][c] == board[r - 1][c] && board[r][c] >= 0) run++
                else {
                    if (run >= 3) for (i in 0 until run) matched.add(r - 1 - i to c)
                    run = 1
                }
            }
            if (run >= 3) for (i in 0 until run) matched.add(ROWS - 1 - i to c)
        }
        return matched.toList()
    }

    private fun removeAndRefill(matches: List<Pair<Int, Int>>) {
        for ((r, c) in matches) board[r][c] = -1
        score += matches.size * 10 * combo
        combo++
        scoreText?.text = "Punti: $score"
        for (c in 0 until COLS) {
            var write = ROWS - 1
            for (r in ROWS - 1 downTo 0) {
                if (board[r][c] >= 0) {
                    board[write][c] = board[r][c]
                    if (write != r) board[r][c] = -1
                    write--
                }
            }
            for (r in write downTo 0) {
                board[r][c] = (Math.random() * GEM_COLORS.size).toInt()
            }
        }
    }

    private fun processMatches() {
        if (!gameRunning) return
        val matches = findMatches()
        if (matches.isNotEmpty()) {
            removeAndRefill(matches)
            gridView?.invalidate()
            handler.postDelayed({ processMatches() }, 300)
        } else {
            busy = false
            combo = 1
        }
    }

    private fun swapGems(r1: Int, c1: Int, r2: Int, c2: Int) {
        val tmp = board[r1][c1]
        board[r1][c1] = board[r2][c2]
        board[r2][c2] = tmp
    }

    private fun trySwap(r1: Int, c1: Int, r2: Int, c2: Int) {
        if (busy || !gameRunning) return
        swapGems(r1, c1, r2, c2)
        val matches = findMatches()
        if (matches.isNotEmpty()) {
            busy = true
            combo = 1
            gridView?.invalidate()
            handler.postDelayed({ processMatches() }, 250)
        } else {
            swapGems(r1, c1, r2, c2)
        }
    }

    private fun endGame() {
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
        val mvc = (score / 20).coerceAtLeast(5)
        val xp = (score / 10).coerceAtLeast(2)
        val isWin = score > 50
        val label = if (isWin) "Match3: $score punti!" else "Match3: $score punti"
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_MATCH3)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc,
                    xpPoints = xp,
                    label = label,
                    isWin = isWin
                ),
                MiniGameManager.GAME_MATCH3
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
            text = "+$mvc MVC  •  +$xp XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "\uD83D\uDD04  Gioca Ancora", UiKit.ACCENT) {
            (overlay.parent as? FrameLayout)?.removeView(overlay)
            startGame()
        })
        endLayout.addView(UiKit.button(ctx, "\u2B05  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        (gridView?.parent as? FrameLayout)?.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    inner class GridView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 32f
        }
        private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.WHITE
        }
        private var cellSize = 0f
        private var offsetX = 0f
        private var offsetY = 0f

        override fun onMeasure(w: Int, h: Int) {
            val dm = resources.displayMetrics
            val maxW = dm.widthPixels - UiKit.dp(context, 28)
            cellSize = (maxW / COLS).toFloat()
            val totalH = (cellSize * ROWS).toInt() + UiKit.dp(context, 20)
            setMeasuredDimension(maxW, totalH)
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            offsetX = 0f; offsetY = 0f
            for (r in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val v = board[r][col]
                    val x = offsetX + col * cellSize
                    val y = offsetY + r * cellSize
                    val cx = x + cellSize / 2
                    val cy = y + cellSize / 2
                    val rad = cellSize * 0.38f

                    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (v >= 0) GEM_COLORS[v] else Color.DKGRAY
                    }
                    c.drawCircle(cx, cy, rad, bg)
                    if (r == selectedR && col == selectedC) {
                        c.drawCircle(cx, cy, rad + 4, selPaint)
                    }
                    if (v >= 0) {
                        val ePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textAlign = Paint.Align.CENTER
                            textSize = cellSize * 0.55f
                        }
                        val bounds = Rect()
                        ePaint.getTextBounds(GEM_EMOJIS[v], 0, GEM_EMOJIS[v].length, bounds)
                        c.drawText(GEM_EMOJIS[v], cx, cy + bounds.height() / 2f, ePaint)
                    }
                }
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action != MotionEvent.ACTION_DOWN || !gameRunning || busy) return true
            val c = ((ev.x - offsetX) / cellSize).toInt().coerceIn(0, COLS - 1)
            val r = ((ev.y - offsetY) / cellSize).toInt().coerceIn(0, ROWS - 1)
            if (selectedR < 0) {
                selectedR = r; selectedC = c
                invalidate()
            } else {
                val dr = Math.abs(r - selectedR)
                val dc = Math.abs(c - selectedC)
                if ((dr == 1 && dc == 0) || (dr == 0 && dc == 1)) {
                    trySwap(selectedR, selectedC, r, c)
                }
                selectedR = -1; selectedC = -1
                invalidate()
            }
            return true
        }
    }
}
