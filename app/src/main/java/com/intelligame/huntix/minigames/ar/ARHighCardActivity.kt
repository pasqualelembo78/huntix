package com.intelligame.huntix.minigames.ar

import android.graphics.*
import android.view.View
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.minigames.MiniGameBase
import com.intelligame.huntix.managers.MiniGameManager

class ARHighCardActivity : MiniGameBase() {

    private val suits = listOf("♠", "♥", "♦", "♣")
    private val values = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")
    private val allCards = suits.flatMap { s -> values.mapIndexed { i, v -> "$v$s" to i + 2 } }
    private val handler = Handler(Looper.getMainLooper())
    private var round = 0
    private var totalRounds = 5
    private var wins = 0
    private var canPlay = false
    private lateinit var playerCard: TextView
    private lateinit var dealerCard: TextView
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var roundText: TextView
    private lateinit var btnDraw: LinearLayout
    private lateinit var resultText: TextView
    private var deck = allCards.toMutableList()

    override fun onGameCreate() {
        setupUI()
    }

    private fun setupUI() {
        val c = this
        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(c, 16), UiKit.dp(c, 8), UiKit.dp(c, 16), UiKit.dp(c, 8))
        }

        val arLabel = TextView(c).apply {
            text = "📱 AR Mode — Carta Alta"
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
            setPadding(0, 0, 0, UiKit.dp(c, 4))
        }
        container.addView(roundText)

        scoreText = TextView(c).apply {
            text = "Vittorie: 0"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 10))
        }
        container.addView(scoreText)

        val cardRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val playerLabel = TextView(c).apply {
            text = "👤 Tu"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        val dealerLabel = TextView(c).apply {
            text = "🤖 Dealer"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }

        val pcContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(playerLabel)
            playerCard = TextView(c).apply {
                text = "🂠"; textSize = 50f; gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 8))
            }
            addView(playerCard)
        }

        val vsText = TextView(c).apply {
            text = "VS"; textSize = 20f; setTextColor(Color.parseColor("#6B5B95"))
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 8), UiKit.dp(c, 20), UiKit.dp(c, 8), 0)
        }

        val dcContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(dealerLabel)
            dealerCard = TextView(c).apply {
                text = "🂠"; textSize = 50f; gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 8))
            }
            addView(dealerCard)
        }

        cardRow.addView(pcContainer)
        cardRow.addView(vsText)
        cardRow.addView(dcContainer)
        container.addView(cardRow)

        statusText = TextView(c).apply {
            text = "Tocca Estrai per giocare!"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 10), 0, UiKit.dp(c, 8))
        }
        container.addView(statusText)

        resultText = TextView(c).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 8))
        }
        container.addView(resultText)

        btnDraw = UiKit.button(c, "🃏 Estrai Carte", UiKit.ACCENT) { drawCards() }
        container.addView(btnDraw)

        val gridBg = object : View(c) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val p = Paint().apply { color = Color.parseColor("#1A1030"); strokeWidth = 1f }
                val step = UiKit.dp(context, 35)
                var x = 0f; while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), p); x += step }
                var y = 0f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, p); y += step }
            }
        }

        val wrapper = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(gridBg)
            addView(container)
        }

        build("AR Carta Alta", "🃏",
            "In AR: pesca una carta gigante e sfida l'avversario. Vinci 5 round!",
            wrapper)
    }

    private fun drawCards() {
        if (round >= totalRounds) return
        canPlay = false

        deck = allCards.toMutableList()
        val playerPair = deck.random(); deck.remove(playerPair)
        val dealerPair = deck.random(); deck.remove(dealerPair)

        playerCard.text = "🂠"
        dealerCard.text = "🂠"
        statusText.text = "Carte estratte..."
        statusText.setTextColor(Color.WHITE)
        resultText.text = ""

        handler.postDelayed({
            playerCard.text = playerPair.first
            playerCard.setTextColor(if (playerPair.first.contains("♥") || playerPair.first.contains("♦"))
                Color.parseColor("#FF4444") else Color.WHITE)

            handler.postDelayed({
                dealerCard.text = dealerPair.first
                dealerCard.setTextColor(if (dealerPair.first.contains("♥") || dealerPair.first.contains("♦"))
                    Color.parseColor("#FF4444") else Color.WHITE)

                val playerWon = playerPair.second > dealerPair.second
                val tie = playerPair.second == dealerPair.second

                round++
                roundText.text = "Round $round / $totalRounds"

                when {
                    tie -> {
                        statusText.text = "🤝 Pareggio! Nessun punto"
                        statusText.setTextColor(Color.parseColor("#FFAA00"))
                        MiniGameManager.applyReward(this, MiniGameManager.GameReward(
                            mvcCoins = 20, label = "AR Carta Alta Round $round (Pareggio)", isWin = false
                        ), MiniGameManager.GAME_HIGH_CARD)
                    }
                    playerWon -> {
                        wins++
                        val reward = playerPair.second * 18
                        statusText.text = "✅ Vinto! +$reward MVC"
                        statusText.setTextColor(Color.parseColor(UiKit.GREEN))
                        MiniGameManager.applyReward(this, MiniGameManager.GameReward(
                            mvcCoins = reward, label = "AR Carta Alta Round $round", isWin = true
                        ), MiniGameManager.GAME_HIGH_CARD)
                    }
                    else -> {
                        val reward = 20
                        statusText.text = "❌ Perso! +$reward MVC consolation"
                        statusText.setTextColor(Color.parseColor("#FF4444"))
                        MiniGameManager.applyReward(this, MiniGameManager.GameReward(
                            mvcCoins = reward, label = "AR Carta Alta Round $round", isWin = false
                        ), MiniGameManager.GAME_HIGH_CARD)
                    }
                }

                scoreText.text = "Vittorie: $wins / $round"
                resultText.text = "Tu: ${playerPair.first} (${playerPair.second}) vs Dealer: ${dealerPair.first} (${dealerPair.second})"

                if (round >= totalRounds) {
                    handler.postDelayed({
                        statusText.text = "🎮 Fine! $wins/$totalRounds vinti"
                        statusText.setTextColor(Color.WHITE)
                        val area = (statusText.parent as? LinearLayout)
                        area?.addView(UiKit.button(this@ARHighCardActivity, "🔄 Gioca Ancora", UiKit.ACCENT) {
                            round = 0; wins = 0; scoreText.text = "Vittorie: 0"
                            roundText.text = "Round 1 / $totalRounds"
                            playerCard.text = "\uD83C\uDCA0"; dealerCard.text = "\uD83C\uDCA0"; resultText.text = ""
                            statusText.text = "Tocca Estrai per giocare!"
                        })
                    }, 2000)
                }
            }, 800)
        }, 600)
    }
}
