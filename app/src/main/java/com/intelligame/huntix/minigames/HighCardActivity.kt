package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager

class HighCardActivity : MiniGameBase() {

    private val suits = arrayOf("\u2660", "\u2665", "\u2666", "\u2663")
    private var wins = 0
    private var losses = 0
    private var ties = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gameArea: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var playerCard: View
    private lateinit var dealerCard: View
    private lateinit var actionButton: View
    private var isBusy = false

    override fun onGameCreate() {
        val c = this
        gameArea = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        build("Carta Alta", "\uD83C\uDCCF",
            "Pesca una carta: se batte quella del dealer vinci MVC! (max 5 partite)",
            gameArea)
        showStartScreen()
    }

    private fun showStartScreen() {
        val c = this
        isBusy = false
        gameArea.removeAllViews()

        val remaining = MiniGameManager.remainingPlays(c, MiniGameManager.GAME_HIGH_CARD)
        if (remaining <= 0) {
            showGameOver()
            return
        }

        val remainText = TextView(c).apply {
            text = "Partite rimaste: $remaining"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
        }
        gameArea.addView(remainText)

        val cardRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 10), 0, UiKit.dp(c, 10))
        }

        playerCard = makeCardPlaceholder("Tua")
        dealerCard = makeCardPlaceholder("Dealer")
        cardRow.addView(playerCard)
        cardRow.addView(makeSeparator())
        cardRow.addView(dealerCard)
        gameArea.addView(cardRow)

        scoreText = TextView(c).apply {
            text = "Vittorie: $wins  |  Sconfitte: $losses  |  Pareggi: $ties"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 10))
            gravity = Gravity.CENTER
        }
        gameArea.addView(scoreText)

        statusText = TextView(c).apply {
            text = "Tocca \"Pesca Carta\" per iniziare!"
            textSize = 14f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 10))
        }
        gameArea.addView(statusText)

        actionButton = UiKit.button(c, "\uD83C\uDCCF  Pesca Carta", UiKit.ACCENT) {
            if (!isBusy) drawCards()
        }
        gameArea.addView(actionButton)
    }

    private fun drawCards() {
        val c = this
        isBusy = true
        gameArea.removeView(actionButton)

        val deck = (1..12).toList().shuffled()
        val playerVal = deck[0]
        val dealerVal = deck[1]

        updateCardFace(playerCard, playerVal, true)
        statusText.text = "Hai pescato un $playerVal${getSuit(playerVal)}..."
        statusText.setTextColor(Color.WHITE)

        handler.postDelayed({
            updateCardFace(dealerCard, dealerVal, true)
            MiniGameManager.consumePlay(c, MiniGameManager.GAME_HIGH_CARD)

            if (playerVal > dealerVal) {
                wins++
                val mvc = playerVal * 15
                MiniGameManager.applyReward(c, MiniGameManager.GameReward(
                    mvcCoins = mvc, label = "Carta Alta vinta!", isWin = true
                ), MiniGameManager.GAME_HIGH_CARD)
                statusText.text = "HAI VINTO! +$mvc MVC"
                statusText.setTextColor(Color.parseColor(UiKit.GREEN))
            } else if (playerVal < dealerVal) {
                losses++
                MiniGameManager.applyReward(c, MiniGameManager.GameReward(
                    mvcCoins = 0, label = "Carta Alta persa", isWin = false
                ), MiniGameManager.GAME_HIGH_CARD)
                statusText.text = "Hai perso..."
                statusText.setTextColor(Color.parseColor("#FF5555"))
            } else {
                ties++
                statusText.text = "Pareggio! Nessun premio."
                statusText.setTextColor(Color.parseColor(UiKit.ACCENT))
            }

            scoreText.text = "Vittorie: $wins  |  Sconfitte: $losses  |  Pareggi: $ties"

            val remaining = MiniGameManager.remainingPlays(c, MiniGameManager.GAME_HIGH_CARD)
            val btnLabel = if (remaining > 0) "Nuova Partita" else "Risultato Finale"
            actionButton = UiKit.button(c, btnLabel, UiKit.ACCENT) {
                if (remaining > 0) showStartScreen() else showGameOver()
            }
            gameArea.addView(actionButton)
        }, 800)
    }

    private fun makeCardPlaceholder(label: String): LinearLayout {
        val c = this
        val size = UiKit.dp(c, 120)
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size)
                .apply { marginStart = UiKit.dp(c, 8); marginEnd = UiKit.dp(c, 8) }
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 14).toFloat()
                setColor(Color.parseColor("#2A1A4A"))
                setStroke(UiKit.dp(c, 2), Color.parseColor(UiKit.TEXT_DIM))
            }
            setPadding(UiKit.dp(c, 8), UiKit.dp(c, 8), UiKit.dp(c, 8), UiKit.dp(c, 8))
        }
        card.addView(TextView(c).apply {
            text = "?"; textSize = 36f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(c).apply {
            text = label; textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            gravity = Gravity.CENTER
        })
        return card
    }

    private fun updateCardFace(card: View, value: Int, faceUp: Boolean) {
        val c = this
        val ll = card as LinearLayout
        ll.removeAllViews()
        val isRed = getSuit(value) == "\u2665" || getSuit(value) == "\u2666"
        val bgColor = if (faceUp) Color.WHITE else Color.parseColor("#2A1A4A")
        ll.background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(c, 14).toFloat()
            setColor(bgColor)
            setStroke(UiKit.dp(c, 2), Color.parseColor("#555555"))
        }
        if (faceUp) {
            ll.addView(TextView(c).apply {
                text = "$value"; textSize = 40f; gravity = Gravity.CENTER
                setTextColor(if (isRed) Color.parseColor("#CC0000") else Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
            })
            ll.addView(TextView(c).apply {
                text = getSuit(value); textSize = 28f; gravity = Gravity.CENTER
                setTextColor(if (isRed) Color.parseColor("#CC0000") else Color.BLACK)
            })
        } else {
            ll.addView(TextView(c).apply {
                text = "?"; textSize = 40f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                typeface = Typeface.DEFAULT_BOLD
            })
        }
    }

    private fun getSuit(value: Int): String = suits[(value - 1) % 4]

    private fun makeSeparator(): TextView {
        val c = this
        return TextView(c).apply {
            text = "VS"; textSize = 16f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 6), 0, UiKit.dp(c, 6), 0)
        }
    }

    private fun showGameOver() {
        val c = this
        gameArea.removeAllViews()
        val totalMvc = wins * 60
        val msg = TextView(c).apply {
            text = "Partite esaurite!\nVittorie: $wins  Sconfitte: $losses  Pareggi: $ties\nMVC guadagnati: ~$totalMvc"
            textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 16), 0, UiKit.dp(c, 16))
        }
        gameArea.addView(msg)
    }
}
