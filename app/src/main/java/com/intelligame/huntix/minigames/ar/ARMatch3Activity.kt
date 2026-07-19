package com.intelligame.huntix.minigames.ar

import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.minigames.MiniGameBase
import com.intelligame.huntix.managers.MiniGameManager

class ARMatch3Activity : MiniGameBase() {

    private val gridSize = 6
    private val gemColors = intArrayOf(
        Color.parseColor("#FF4444"),
        Color.parseColor("#44FF44"),
        Color.parseColor("#4488FF"),
        Color.parseColor("#FFAA00"),
        Color.parseColor("#FF44FF")
    )
    private val gemEmojis = arrayOf("🔴", "🟢", "🔵", "🟡", "🟣")
    private lateinit var grid: Array<IntArray>
    private lateinit var boardView: Match3BoardView
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var timerText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var score = 0
    private var timeLeft = 45
    private var timerRunnable: Runnable? = null
    private var selectedRow = -1
    private var selectedCol = -1
    private var totalGames = 0
    private var totalScore = 0
    private var isAnimating = false

    override fun onGameCreate() {
        setupUI()
        newGame()
    }

    private fun setupUI() {
        val c = this
        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(c, 16), UiKit.dp(c, 4), UiKit.dp(c, 16), UiKit.dp(c, 8))
        }

        val arLabel = TextView(c).apply {
            text = "📱 AR Mode — Match 3 (6×6)"
            textSize = 11f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(arLabel)

        timerText = TextView(c).apply {
            text = "⏱ 45s"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 4))
        }
        container.addView(timerText)

        scoreText = TextView(c).apply {
            text = "Punteggio: 0"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(scoreText)

        statusText = TextView(c).apply {
            text = "Tocca una gemma e poi una adiacente"
            textSize = 14f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 8))
        }
        container.addView(statusText)

        boardView = Match3BoardView(c)
        val boardSize = UiKit.dp(c, 300)
        boardView.layoutParams = LinearLayout.LayoutParams(boardSize, boardSize)
        container.addView(boardView)

        build("AR Match 3", "💎",
            "In AR: allinea cristalli fluttuanti nello spazio intorno a te. 45 secondi!",
            container)
    }

    private fun newGame() {
        score = 0; timeLeft = 45; selectedRow = -1; selectedCol = -1; isAnimating = false
        scoreText.text = "Punteggio: 0"
        timerText.text = "⏱ 45s"
        statusText.text = "Tocca una gemma e poi una adiacente"

        do {
            grid = Array(gridSize) { IntArray(gridSize) { (0..4).random() } }
            removeInitialMatches()
        } while (findAllMatches().isNotEmpty())

        startTimer()
        boardView.invalidate()
    }

    private fun removeInitialMatches() {
        var found = true
        while (found) {
            found = false
            for (r in 0 until gridSize) {
                for (c_ in 0 until gridSize) {
                    if (c_ >= 2 && grid[r][c_] == grid[r][c_ - 1] && grid[r][c_] == grid[r][c_ - 2]) {
                        do { grid[r][c_] = (0..4).random() } while (grid[r][c_] == grid[r][c_ - 1])
                        found = true
                    }
                    if (r >= 2 && grid[r][c_] == grid[r - 1][c_] && grid[r][c_] == grid[r - 2][c_]) {
                        do { grid[r][c_] = (0..4).random() } while (grid[r][c_] == grid[r - 1][c_])
                        found = true
                    }
                }
            }
        }
    }

    private fun startTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = object : Runnable {
            override fun run() {
                if (timeLeft <= 0) { finishGame(); return }
                timeLeft--
                timerText.text = "⏱ ${timeLeft}s"
                if (timeLeft <= 10) timerText.setTextColor(Color.parseColor("#FF4444"))
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(timerRunnable!!, 1000)
    }

    private fun findAllMatches(): List<Pair<Int, Int>> {
        val matched = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until gridSize) {
            for (c_ in 0 until gridSize - 2) {
                if (grid[r][c_] >= 0 && grid[r][c_] == grid[r][c_ + 1] && grid[r][c_] == grid[r][c_ + 2]) {
                    var end = c_ + 2
                    while (end + 1 < gridSize && grid[r][end + 1] == grid[r][c_]) end++
                    for (i in c_..end) matched.add(r to i)
                }
            }
        }
        for (c_ in 0 until gridSize) {
            for (r in 0 until gridSize - 2) {
                if (grid[r][c_] >= 0 && grid[r][c_] == grid[r + 1][c_] && grid[r][c_] == grid[r + 2][c_]) {
                    var end = r + 2
                    while (end + 1 < gridSize && grid[end + 1][c_] == grid[r][c_]) end++
                    for (i in r..end) matched.add(i to c_)
                }
            }
        }
        return matched.toList()
    }

    private fun processMatches() {
        val matches = findAllMatches()
        if (matches.isEmpty()) {
            isAnimating = false
            statusText.text = "Tocca una gemma e poi una adiacente"
            return
        }

        isAnimating = true
        val pts = matches.size * 10
        score += pts
        scoreText.text = "Punteggio: $score"

        if (matches.size >= 5) {
            statusText.text = "💥 CASCADE! +$pts"
            score += 100
        } else {
            statusText.text = "✨ Match! +$pts punti"
        }

        matches.forEach { (r, c_) -> grid[r][c_] = -1 }
        boardView.invalidate()

        handler.postDelayed({
            for (c_ in 0 until gridSize) {
                var emptySpots = 0
                for (r in gridSize - 1 downTo 0) {
                    if (grid[r][c_] == -1) {
                        emptySpots++
                    } else if (emptySpots > 0) {
                        grid[r + emptySpots][c_] = grid[r][c_]
                        grid[r][c_] = -1
                    }
                }
                for (r in 0 until gridSize) {
                    if (grid[r][c_] == -1) grid[r][c_] = (0..4).random()
                }
            }
            boardView.invalidate()
            handler.postDelayed({ processMatches() }, 300)
        }, 400)
    }

    private fun trySwap(r1: Int, c1: Int, r2: Int, c2: Int) {
        if (isAnimating) return
        val tmp = grid[r1][c1]
        grid[r1][c1] = grid[r2][c2]
        grid[r2][c2] = tmp

        if (findAllMatches().isNotEmpty()) {
            boardView.invalidate()
            isAnimating = true
            handler.postDelayed({ processMatches() }, 200)
        } else {
            grid[r2][c2] = grid[r1][c1]
            grid[r1][c1] = tmp
            statusText.text = "❌ Nessun match — riprova"
            boardView.invalidate()
        }
    }

    private fun finishGame() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        totalGames++
        totalScore += score
        val isWin = score > 100
        MiniGameManager.applyReward(this, MiniGameManager.GameReward(
            mvcCoins = score.coerceAtMost(300),
            label = "AR Match3 Partita $totalGames ($score pt)",
            isWin = isWin
        ), MiniGameManager.GAME_MATCH3)

        statusText.text = "⏱ Tempo! +${score.coerceAtMost(300)} MVC"
        statusText.setTextColor(Color.parseColor(UiKit.GREEN))

        if (totalGames < 5) {
            Handler(Looper.getMainLooper()).postDelayed({
                val area = (statusText.parent as? LinearLayout)
                area?.addView(UiKit.button(this, "Prossima Partita (${totalGames + 1}/5)", UiKit.ACCENT) {
                    newGame()
                })
            }, 1500)
        } else {
            statusText.text = "🎮 Fine! Totale: $totalScore pt"
            val area = (statusText.parent as? LinearLayout)
            area?.addView(UiKit.button(this, "🔄 Gioca Ancora", UiKit.ACCENT) {
                totalGames = 0; totalScore = 0; newGame()
            })
        }
    }

    inner class Match3BoardView(ctx: android.content.Context) : View(ctx) {
        private val bgPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val cellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1030") }
        private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A78BFA"); style = Paint.Style.STROKE; strokeWidth = 4f
        }
        private val gemPaints = gemColors.map { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it } }
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 40f
        }
        private val gridPaint = Paint().apply { color = Color.parseColor("#120828"); strokeWidth = 1f }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(bgPaint.color)
            val cellW = width.toFloat() / gridSize
            val cellH = height.toFloat() / gridSize
            val pad = UiKit.dp(context, 2).toFloat()
            val radius = UiKit.dp(context, 8).toFloat()

            for (r in 0 until gridSize) {
                for (c_ in 0 until gridSize) {
                    val x = c_ * cellW + pad
                    val y = r * cellH + pad
                    val rect = RectF(x, y, x + cellW - pad * 2, y + cellH - pad * 2)
                    canvas.drawRoundRect(rect, radius, radius, cellBg)

                    if (r == selectedRow && c_ == selectedCol) {
                        canvas.drawRoundRect(rect, radius, radius, selPaint)
                    }

                    if (grid[r][c_] >= 0) {
                        textP.textSize = cellW * 0.5f
                        canvas.drawText(gemEmojis[grid[r][c_]], rect.centerX(), rect.centerY() + cellW * 0.18f, textP)
                    }
                }
            }

            for (i in 0..gridSize) {
                canvas.drawLine(i * cellW, 0f, i * cellW, height.toFloat(), gridPaint)
                canvas.drawLine(0f, i * cellH, width.toFloat(), i * cellH, gridPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN || isAnimating) return true
            val cw = width.toFloat() / gridSize
            val ch = height.toFloat() / gridSize
            val col = (event.x / cw).toInt().coerceIn(0, gridSize - 1)
            val row = (event.y / ch).toInt().coerceIn(0, gridSize - 1)

            if (selectedRow == -1) {
                selectedRow = row; selectedCol = col
                statusText.text = "Ora tocca una gemma adiacente"
            } else {
                val dr = Math.abs(row - selectedRow)
                val dc = Math.abs(col - selectedCol)
                if ((dr == 1 && dc == 0) || (dr == 0 && dc == 1)) {
                    trySwap(selectedRow, selectedCol, row, col)
                } else {
                    statusText.text = "Solo adiacenti! Tocca di nuovo"
                }
                selectedRow = -1; selectedCol = -1
            }
            invalidate()
            return true
        }
    }
}
