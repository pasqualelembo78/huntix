package com.intelligame.huntix.minigames.ar

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.minigames.MiniGameBase
import com.intelligame.huntix.managers.MiniGameManager

class ARNumberPickActivity : MiniGameBase() {

    private var round = 0
    private var totalRounds = 5
    private var totalWon = 0
    private var winnerIndex = 0
    private lateinit var numViews: Array<TextView>
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var roundText: TextView
    private var canTap = false

    override fun onGameCreate() {
        setupUI()
        startRound()
    }

    private fun setupUI() {
        val c = this
        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(c, 16), UiKit.dp(c, 8), UiKit.dp(c, 16), UiKit.dp(c, 8))
        }

        val arLabel = TextView(c).apply {
            text = "📱 AR Mode — Numeri Fluttuanti"
            textSize = 11f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 8))
        }
        container.addView(arLabel)

        roundText = TextView(c).apply {
            text = "Round 1 / $totalRounds"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 6))
        }
        container.addView(roundText)

        scoreText = TextView(c).apply {
            text = "Totale: 0 MVC"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 12))
        }
        container.addView(scoreText)

        statusText = TextView(c).apply {
            text = "Scegli un numero fluttuante!"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 14))
        }
        container.addView(statusText)

        val grid1 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val grid2 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

        val numbers = (1..99).shuffled().take(6).toList()
        numViews = Array(6) { i ->
            val num = numbers[i]
            TextView(c).apply {
                text = "$num"
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                val sz = UiKit.dp(c, 70)
                val m = UiKit.dp(c, 5)
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                    marginStart = m; marginEnd = m; topMargin = m; bottomMargin = m
                }
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(c, 14).toFloat()
                    setColor(Color.parseColor("#2A1F4D"))
                }
                setPadding(0, UiKit.dp(c, 10), 0, 0)
                setOnClickListener { onNumberPicked(i) }
            }
        }

        numViews.take(3).forEach { grid1.addView(it) }
        numViews.drop(3).forEach { grid2.addView(it) }
        container.addView(grid1)
        container.addView(grid2)

        val gridBg = object : View(c) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val p = Paint().apply { color = Color.parseColor("#120828"); strokeWidth = 1f }
                val step = UiKit.dp(context, 40)
                var x = 0f; while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), p); x += step }
                var y = 0f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, p); y += step }
            }
        }

        val wrapper = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(gridBg)
            addView(container)
        }

        build("AR Scegli il Numero", "🔢",
            "In AR: indica il numero sospeso nell'aria e spera sia quello vincente.",
            wrapper)
    }

    private fun startRound() {
        canTap = false
        roundText.text = "Round ${round + 1} / $totalRounds"
        statusText.text = "Scegli un numero fluttuante!"
        statusText.setTextColor(Color.WHITE)

        val nums = (1..99).shuffled().take(6).toList()
        winnerIndex = (0..5).random()
        nums.forEachIndexed { i, n -> numViews[i].text = "$n" }

        numViews.forEach { tv ->
            tv.background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@ARNumberPickActivity, 14).toFloat()
                setColor(Color.parseColor("#2A1F4D"))
            }
            tv.animate().translationY(((Math.random() * 20f) - 10f).toFloat()).setDuration(300).start()
        }

        numViews.forEach { it.alpha = 1f; it.isClickable = true }
        canTap = true
    }

    private fun onNumberPicked(idx: Int) {
        if (!canTap) return
        canTap = false
        val correct = idx == winnerIndex

        numViews.forEachIndexed { i, tv ->
            tv.isClickable = false
            if (i == winnerIndex) {
                tv.background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@ARNumberPickActivity, 14).toFloat()
                    setColor(Color.parseColor("#004D25"))
                }
            } else if (i == idx && !correct) {
                tv.background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@ARNumberPickActivity, 14).toFloat()
                    setColor(Color.parseColor("#4D0020"))
                }
            } else {
                tv.alpha = 0.4f
            }
        }

        if (correct) {
            totalWon += 180
            statusText.text = "✅ Numero vincente! +180 MVC"
            statusText.setTextColor(Color.parseColor(UiKit.GREEN))
            MiniGameManager.applyReward(this, MiniGameManager.GameReward(
                mvcCoins = 180, label = "AR Numero Round ${round + 1}", isWin = true
            ), MiniGameManager.GAME_NUMBER_PICK)
        } else {
            totalWon += 20
            statusText.text = "❌ Sbagliato! +20 MVC consolation"
            statusText.setTextColor(Color.parseColor("#FF4444"))
            MiniGameManager.applyReward(this, MiniGameManager.GameReward(
                mvcCoins = 20, label = "AR Numero Round ${round + 1}", isWin = false
            ), MiniGameManager.GAME_NUMBER_PICK)
        }

        scoreText.text = "Totale: $totalWon MVC"
        round++

        if (round < totalRounds) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startRound() }, 2000)
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                statusText.text = "🎮 Fine! Totale: $totalWon MVC"
                statusText.setTextColor(Color.WHITE)
                val area = (statusText.parent as? LinearLayout)
                area?.addView(UiKit.button(this, "🔄 Gioca Ancora", UiKit.ACCENT) {
                    round = 0; totalWon = 0; scoreText.text = "Totale: 0 MVC"; startRound()
                })
            }, 2000)
        }
    }
}
