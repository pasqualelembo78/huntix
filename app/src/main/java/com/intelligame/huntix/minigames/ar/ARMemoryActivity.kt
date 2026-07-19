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

class ARMemoryActivity : MiniGameBase() {

    private val cols = 4
    private val rows = 3
    private val totalCards = cols * rows
    private val pairs = totalCards / 2
    private val emojis = listOf("🥚", "🐣", "🐔", "🦜", "🦚", "🦩")
    private lateinit var board: IntArray
    private lateinit var revealed: BooleanArray
    private lateinit var matched: BooleanArray
    private var firstPick = -1
    private var secondPick = -1
    private var moves = 0
    private var matchedPairs = 0
    private var isLocked = false
    private var totalGames = 0
    private var totalScore = 0
    private lateinit var boardView: MemoryBoardView
    private lateinit var statusText: TextView
    private lateinit var infoText: TextView

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
            text = "📱 AR Mode — Memory 3×4"
            textSize = 11f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(arLabel)

        infoText = TextView(c).apply {
            text = "Partita 1 / 5 — Coppie: 0/$pairs"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(infoText)

        statusText = TextView(c).apply {
            text = "Tocca una carta per girarla"
            textSize = 14f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 10))
        }
        container.addView(statusText)

        boardView = MemoryBoardView(c)
        val boardSize = UiKit.dp(c, 300)
        boardView.layoutParams = LinearLayout.LayoutParams(boardSize, boardSize)
        container.addView(boardView)

        build("AR Memory", "🧠",
            "In AR: gira le carte ancorate nel mondo e trova le coppie di uova.",
            container)
    }

    private fun newGame() {
        firstPick = -1; secondPick = -1; moves = 0; matchedPairs = 0; isLocked = false
        board = IntArray(totalCards)
        revealed = BooleanArray(totalCards)
        matched = BooleanArray(totalCards)

        val pool = mutableListOf<Int>()
        repeat(pairs) { pool.add(it); pool.add(it) }
        board = pool.shuffled().toIntArray()

        infoText.text = "Partita ${totalGames + 1} / 5 — Coppie: 0/$pairs"
        statusText.text = "Tocca una carta per girarla"
        statusText.setTextColor(Color.parseColor(UiKit.ACCENT))
        boardView.invalidate()
    }

    inner class MemoryBoardView(ctx: android.content.Context) : View(ctx) {
        private val bgPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val gridPaint = Paint().apply { color = Color.parseColor("#1A1030"); strokeWidth = 1f }
        private val cardPaint = Paint().apply { color = Color.parseColor("#2A1F4D"); isAntiAlias = true }
        private val matchedPaint = Paint().apply { color = Color.parseColor("#1A3D2A"); isAntiAlias = true }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 60f
        }
        private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3D2A6E"); isAntiAlias = true
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A78BFA"); isAntiAlias = true; maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.OUTER)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(bgPaint.color)
            val cw = width.toFloat() / cols
            val ch = height.toFloat() / rows
            val pad = UiKit.dp(context, 4).toFloat()
            val radius = UiKit.dp(context, 10).toFloat()

            for (r in 0 until rows) {
                for (c_ in 0 until cols) {
                    val idx = r * cols + c_
                    val x = c_ * cw + pad
                    val y = r * ch + pad
                    val w = cw - pad * 2
                    val h = ch - pad * 2
                    val rect = RectF(x, y, x + w, y + h)

                    when {
                        matched[idx] -> {
                            canvas.drawRoundRect(rect, radius, radius, matchedPaint)
                            textPaint.textSize = UiKit.dp(context, 28).toFloat()
                            canvas.drawText(emojis[board[idx]], rect.centerX(), rect.centerY() + UiKit.dp(context, 10), textPaint)
                        }
                        revealed[idx] -> {
                            canvas.drawRoundRect(rect, radius, radius, cardPaint)
                            textPaint.textSize = UiKit.dp(context, 28).toFloat()
                            canvas.drawText(emojis[board[idx]], rect.centerX(), rect.centerY() + UiKit.dp(context, 10), textPaint)
                        }
                        else -> {
                            canvas.drawRoundRect(rect, radius, radius, backPaint)
                            canvas.drawRoundRect(rect, radius, radius, glowPaint)
                            textPaint.textSize = UiKit.dp(context, 18).toFloat()
                            textPaint.color = Color.parseColor("#6B5B95")
                            canvas.drawText("?", rect.centerX(), rect.centerY() + UiKit.dp(context, 6), textPaint)
                            textPaint.color = Color.WHITE
                        }
                    }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN || isLocked) return true
            val cw = width.toFloat() / cols
            val ch = height.toFloat() / rows
            val col = (event.x / cw).toInt().coerceIn(0, cols - 1)
            val row = (event.y / ch).toInt().coerceIn(0, rows - 1)
            val idx = row * cols + col
            if (idx < 0 || idx >= totalCards) return true
            if (revealed[idx] || matched[idx]) return true

            handlePick(idx)
            return true
        }
    }

    private fun handlePick(idx: Int) {
        if (firstPick == -1) {
            firstPick = idx
            revealed[idx] = true
            boardView.invalidate()
        } else if (secondPick == -1 && idx != firstPick) {
            secondPick = idx
            revealed[idx] = true
            moves++
            isLocked = true
            boardView.invalidate()

            Handler(Looper.getMainLooper()).postDelayed({
                if (board[firstPick] == board[secondPick]) {
                    matched[firstPick] = true
                    matched[secondPick] = true
                    matchedPairs++
                    statusText.text = "✅ Coppia trovata! ($matchedPairs/$pairs)"

                    if (matchedPairs == pairs) {
                        finishGame()
                    }
                } else {
                    revealed[firstPick] = false
                    revealed[secondPick] = false
                    statusText.text = "❌ Non corrispondono — mossa $moves"
                }
                firstPick = -1; secondPick = -1; isLocked = false
                infoText.text = "Partita ${totalGames + 1} / 5 — Mosse: $moves — Coppie: $matchedPairs/$pairs"
                boardView.invalidate()
            }, 800)
        }
    }

    private fun finishGame() {
        totalGames++
        val score = when {
            moves <= 7 -> { 250 }
            moves <= 11 -> { 150 }
            else -> { 70 }
        }
        totalScore += score
        val isWin = moves <= 11

        MiniGameManager.applyReward(this, MiniGameManager.GameReward(
            mvcCoins = score,
            label = "AR Memory Partita $totalGames ($moves mosse)",
            isWin = isWin
        ), MiniGameManager.GAME_MEMORY)

        statusText.text = "🎉 Completato in $moves mosse! +$score MVC"
        statusText.setTextColor(Color.parseColor(UiKit.GREEN))

        if (totalGames < 5) {
            Handler(Looper.getMainLooper()).postDelayed({
                val area = (statusText.parent as? LinearLayout)
                area?.addView(UiKit.button(this, "Prossima Partita (${totalGames + 1}/5)", UiKit.ACCENT) {
                    newGame()
                })
            }, 1500)
        } else {
            statusText.text = "🎮 Fine! Punteggio totale: $totalScore MVC"
            val area = (statusText.parent as? LinearLayout)
            area?.addView(UiKit.button(this, "🔄 Gioca Ancora", UiKit.ACCENT) {
                totalGames = 0; totalScore = 0; newGame()
            })
        }
    }
}
