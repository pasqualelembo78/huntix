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

class ARColorBombActivity : MiniGameBase() {

    private val gridSize = 7
    private val numColors = 5
    private val eggColors = intArrayOf(
        Color.parseColor("#FF4444"),
        Color.parseColor("#44FF44"),
        Color.parseColor("#4488FF"),
        Color.parseColor("#FFAA00"),
        Color.parseColor("#FF44FF")
    )
    private val eggEmojis = arrayOf("🔴", "🟢", "🔵", "🟡", "🟣")

    private lateinit var grid: Array<IntArray>
    private var score = 0
    private var totalGames = 0
    private var totalScore = 0
    private var isAnimating = false
    private lateinit var bombView: ColorBombCanvasView
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var gameText: TextView
    private val handler = Handler(Looper.getMainLooper())

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
            text = "📱 AR Mode — Color Bomb (7×7)"
            textSize = 11f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(arLabel)

        gameText = TextView(c).apply {
            text = "Partita 1 / 3"
            textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 4))
        }
        container.addView(gameText)

        scoreText = TextView(c).apply {
            text = "Punteggio: 0"
            textSize = 13f; setTextColor(Color.parseColor(UiKit.GREEN)); gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(scoreText)

        statusText = TextView(c).apply {
            text = "Tocca un gruppo di 2+ uove uguali per farle esplodere!"
            textSize = 13f; setTextColor(Color.parseColor(UiKit.ACCENT)); gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 8))
        }
        container.addView(statusText)

        bombView = ColorBombCanvasView(c)
        val boardSize = UiKit.dp(c, 310)
        bombView.layoutParams = LinearLayout.LayoutParams(boardSize, boardSize)
        container.addView(bombView)

        build("AR Color Bomb", "💣",
            "In AR: fai esplodere gruppi di uova dello stesso colore. group_size² × 10!",
            container)
    }

    private fun newGame() {
        score = 0; isAnimating = false
        scoreText.text = "Punteggio: 0"
        gameText.text = "Partita ${totalGames + 1} / 3"
        grid = Array(gridSize) { IntArray(gridSize) { (0 until numColors).random() } }
        if (!hasValidMoves()) regenerateGrid()
        statusText.text = "Tocca un gruppo di 2+ uove uguali!"
        statusText.setTextColor(Color.parseColor(UiKit.ACCENT))
        bombView.invalidate()
    }

    private fun regenerateGrid() {
        grid = Array(gridSize) { IntArray(gridSize) { (0 until numColors).random() } }
        if (!hasValidMoves()) regenerateGrid()
    }

    private fun findGroup(startR: Int, startC: Int): List<Pair<Int, Int>> {
        val color = grid[startR][startC]
        if (color < 0) return emptyList()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startR to startC)
        visited.add(startR to startC)
        val result = mutableListOf<Pair<Int, Int>>()

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            result.add(r to c)
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nr = r + dr; val nc = c + dc
                if (nr in 0 until gridSize && nc in 0 until gridSize &&
                    (nr to nc) !in visited && grid[nr][nc] == color) {
                    visited.add(nr to nc)
                    queue.add(nr to nc)
                }
            }
        }
        return result
    }

    private fun hasValidMoves(): Boolean {
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (findGroup(r, c).size >= 2) return true
            }
        }
        return false
    }

    private fun popGroup(startR: Int, startC: Int) {
        if (isAnimating) return
        val group = findGroup(startR, startC)
        if (group.size < 2) {
            statusText.text = "Servono almeno 2 uove uguali adiacenti!"
            return
        }

        isAnimating = true
        val pts = group.size * group.size * 10
        var bonus = 0
        if (group.size >= 5) bonus = 100
        score += pts + bonus
        scoreText.text = "Punteggio: $score"

        if (bonus > 0) {
            statusText.text = "💥 COLOR BOMB! +${pts + bonus} (${group.size} uove)"
            statusText.setTextColor(Color.parseColor("#FFD700"))
        } else {
            statusText.text = "✨ Pop! +$pts (${group.size} uove)"
            statusText.setTextColor(Color.parseColor(UiKit.GREEN))
        }

        group.forEach { (r, c) -> grid[r][c] = -1 }
        bombView.invalidate()

        handler.postDelayed({
            for (c in 0 until gridSize) {
                var empty = 0
                for (r in gridSize - 1 downTo 0) {
                    if (grid[r][c] == -1) empty++
                    else if (empty > 0) { grid[r + empty][c] = grid[r][c]; grid[r][c] = -1 }
                }
                for (r in 0 until empty) grid[r][c] = (0 until numColors).random()
            }
            bombView.invalidate()

            handler.postDelayed({
                if (!hasValidMoves()) {
                    finishRound()
                } else {
                    isAnimating = false
                }
            }, 300)
        }, 400)
    }

    private fun finishRound() {
        totalGames++
        totalScore += score
        val isWin = score > 50
        MiniGameManager.applyReward(this, MiniGameManager.GameReward(
            mvcCoins = score.coerceAtMost(300),
            label = "AR Color Bomb Partita $totalGames ($score pt)",
            isWin = isWin
        ), MiniGameManager.GAME_AR_BOMB)

        statusText.text = "🏁 Nessuna mossa! +${score.coerceAtMost(300)} MVC"
        statusText.setTextColor(Color.parseColor(UiKit.GREEN))

        if (totalGames < 3) {
            handler.postDelayed({
                val area = (statusText.parent as? LinearLayout)
                area?.addView(UiKit.button(this, "Prossima Partita (${totalGames + 1}/3)", UiKit.ACCENT) {
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

    inner class ColorBombCanvasView(ctx: android.content.Context) : View(ctx) {
        private val bgPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val cellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1030") }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A78BFA"); alpha = 60
        }
        private val gridLinePaint = Paint().apply { color = Color.parseColor("#120828"); strokeWidth = 1f }
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(bgPaint.color)
            val cw = width.toFloat() / gridSize
            val ch = height.toFloat() / gridSize
            val pad = UiKit.dp(context, 2).toFloat()
            val radius = UiKit.dp(context, 8).toFloat()

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    val x = c * cw + pad; val y = r * ch + pad
                    val rect = RectF(x, y, x + cw - pad * 2, y + ch - pad * 2)
                    canvas.drawRoundRect(rect, radius, radius, cellBg)
                    if (grid[r][c] >= 0) {
                        textP.textSize = cw * 0.5f
                        canvas.drawText(eggEmojis[grid[r][c]], rect.centerX(), rect.centerY() + cw * 0.18f, textP)
                    }
                }
            }
            for (i in 0..gridSize) {
                canvas.drawLine(i * cw, 0f, i * cw, height.toFloat(), gridLinePaint)
                canvas.drawLine(0f, i * ch, width.toFloat(), i * ch, gridLinePaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN || isAnimating) return true
            val cw = width.toFloat() / gridSize
            val ch = height.toFloat() / gridSize
            val col = (event.x / cw).toInt().coerceIn(0, gridSize - 1)
            val row = (event.y / ch).toInt().coerceIn(0, gridSize - 1)
            popGroup(row, col)
            return true
        }
    }
}
