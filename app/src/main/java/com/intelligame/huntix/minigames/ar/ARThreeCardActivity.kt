package com.intelligame.huntix.minigames.ar

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.minigames.MiniGameBase
import com.intelligame.huntix.managers.MiniGameManager

class ARThreeCardActivity : MiniGameBase() {

    private var round = 0
    private var totalRounds = 5
    private var wins = 0
    private var eggPos = 0
    private var isShuffling = false
    private var isRevealing = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var cardViews: Array<TextView>
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var roundText: TextView
    private val eggPositions = IntArray(totalRounds)

    override fun onGameCreate() {
        for (i in 0 until totalRounds) eggPositions[i] = (0..2).random()
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
            text = "📱 AR Mode"
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
            text = "Vittorie: 0"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 12))
        }
        container.addView(scoreText)

        statusText = TextView(c).apply {
            text = "Guarda dov'è l'uovo 🥚..."
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 16))
        }
        container.addView(statusText)

        val cardRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        cardViews = Array(3) { i ->
            TextView(c).apply {
                text = "❓"
                textSize = 42f
                gravity = Gravity.CENTER
                val sz = UiKit.dp(c, 90)
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                    marginStart = UiKit.dp(c, 6)
                    marginEnd = UiKit.dp(c, 6)
                }
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(c, 12).toFloat()
                    setColor(Color.parseColor("#2A1F4D"))
                }
                setPadding(0, UiKit.dp(c, 14), 0, 0)
                setOnClickListener { onCardTapped(i) }
            }
        }

        cardViews.forEach { cardRow.addView(it) }
        container.addView(cardRow)

        val gridBg = object : View(c) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val p = Paint().apply { color = Color.parseColor("#1A1030"); strokeWidth = 1f }
                val step = UiKit.dp(context, 30)
                var x = 0f
                while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), p); x += step }
                var y = 0f
                while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, p); y += step }
            }
        }
        val wrapper = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(gridBg)
            addView(container)
        }

        build("AR Tre Carte", "🎴",
            "In AR: segui l'uovo sotto una delle tre carte ancorate nel mondo.",
            wrapper)
    }

    private fun startRound() {
        isRevealing = false
        isShuffling = false
        roundText.text = "Round ${round + 1} / $totalRounds"
        statusText.text = "Guarda dov'è l'uovo 🥚..."
        statusText.setTextColor(Color.WHITE)

        cardViews.forEachIndexed { i, tv ->
            tv.text = "❓"
            tv.background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@ARThreeCardActivity, 12).toFloat()
                setColor(Color.parseColor("#2A1F4D"))
            }
        }

        eggPos = eggPositions[round]

        handler.postDelayed({
            cardViews[eggPos].text = "🥚"
            cardViews[eggPos].background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@ARThreeCardActivity, 12).toFloat()
                setColor(Color.parseColor("#3D2A6E"))
            }
            statusText.text = "L'uovo è sotto la carta ${eggPos + 1}! Memorizza!"
            statusText.setTextColor(Color.parseColor(UiKit.GREEN))

            handler.postDelayed({ shuffleCards(0) }, 1200)
        }, 800)
    }

    private fun shuffleCards(step: Int) {
        if (step >= 7) {
            isShuffling = false
            cardViews.forEach { it.text = "❓"; it.background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@ARThreeCardActivity, 12).toFloat()
                setColor(Color.parseColor("#2A1F4D"))
            }}
            statusText.text = "Dov'è l'uovo? Tocca una carta!"
            statusText.setTextColor(Color.parseColor("#A78BFA"))
            isRevealing = true
            return
        }

        isShuffling = true
        var a = (0..2).random()
        var b = (0..2).random()
        while (b == a) b = (0..2).random()

        val tempText = cardViews[a].text.toString()
        cardViews[a].text = cardViews[b].text.toString()
        cardViews[b].text = tempText

        if (a == eggPos) eggPos = b
        else if (b == eggPos) eggPos = a

        cardViews[a].animate().translationX(if (a < b) 40f else -40f).setDuration(120).withEndAction {
            cardViews[a].translationX = 0f
        }
        cardViews[b].animate().translationX(if (b < a) 40f else -40f).setDuration(120).withEndAction {
            cardViews[b].translationX = 0f
        }

        handler.postDelayed({ shuffleCards(step + 1) }, 280)
    }

    private fun onCardTapped(pos: Int) {
        if (isShuffling || !isRevealing) return
        isRevealing = false

        val correct = pos == eggPos
        if (correct) {
            wins++
            cardViews[pos].text = "🥚"
            cardViews[pos].background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@ARThreeCardActivity, 12).toFloat()
                setColor(Color.parseColor("#004D25"))
            }
            statusText.text = "✅ Corretto! +200 MVC"
            statusText.setTextColor(Color.parseColor(UiKit.GREEN))
            MiniGameManager.applyReward(this, MiniGameManager.GameReward(
                mvcCoins = 200, label = "AR Tre Carte Round ${round + 1}", isWin = true
            ), MiniGameManager.GAME_THREE_CARD)
        } else {
            cardViews[pos].text = "❌"
            cardViews[pos].background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@ARThreeCardActivity, 12).toFloat()
                setColor(Color.parseColor("#4D0020"))
            }
            cardViews[eggPos].text = "🥚"
            cardViews[eggPos].background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(this@ARThreeCardActivity, 12).toFloat()
                setColor(Color.parseColor("#004D25"))
            }
            statusText.text = "❌ Sbagliato! +30 MVC consolation"
            statusText.setTextColor(Color.parseColor("#FF4444"))
            MiniGameManager.applyReward(this, MiniGameManager.GameReward(
                mvcCoins = 30, label = "AR Tre Carte Round ${round + 1}", isWin = false
            ), MiniGameManager.GAME_THREE_CARD)
        }

        scoreText.text = "Vittorie: $wins / ${round + 1}"

        round++
        if (round < totalRounds) {
            handler.postDelayed({ startRound() }, 2000)
        } else {
            handler.postDelayed({
                val totalReward = wins * 200 + (totalRounds - wins) * 30
                statusText.text = "🎮 Fine! $wins/$totalRounds corretti"
                statusText.setTextColor(Color.WHITE)
                val summary = UiKit.section(this, "Punteggio: $wins/$totalRounds — +$totalReward MVC totale")
                val restartBtn = UiKit.button(this, "🔄 Gioca Ancora", UiKit.ACCENT) {
                    round = 0; wins = 0; for (i in 0 until totalRounds) eggPositions[i] = (0..2).random()
                    scoreText.text = "Vittorie: 0"; startRound()
                }
                val area = (statusText.parent as? LinearLayout)
                area?.addView(summary)
                area?.addView(restartBtn)
            }, 2500)
        }
    }
}
