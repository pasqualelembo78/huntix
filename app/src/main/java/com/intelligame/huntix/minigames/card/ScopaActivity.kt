package com.intelligame.huntix.minigames.card

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.managers.MiniGameManager.GAME_SCOPA
import io.sentry.Sentry

/**
 * 🃏 Scopa — classico gioco di carte italiano a 2 giocatori.
 *
 * L'obiettivo è catturare le carte sul tavolo sommando 15.
 * Prendi il settebello (7 di denari) per fare la scopa.
 * Vince chi cattura più carte e più denari.
 */
class ScopaActivity : CardGameBase() {

    override val config = CardGameConfig(
        gameId = GAME_SCOPA,
        title = "Scopa",
        emoji = "🃏",
        rules = "Cattura le carte sul tavolo sommando 15!\n" +
                "Le carte hanno valore: A=1, 2-7=valore facciale, J=8, Q=9, K=10.\n" +
                "Prendi il 7 di denari per la scopa (+1 punto extra).\n" +
                "Vinci chi cattura più carte e più denari in 3 mani!"
    )

    private val suits = listOf(Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS, Suit.SPADES)
    private val ranks = listOf(
        Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE,
        Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
        Rank.JACK, Rank.QUEEN, Rank.KING
    )

    private var playerHand = mutableListOf<Card>()
    private var cpuHand = mutableListOf<Card>()
    private var tableCards = mutableListOf<Card>()
    private var playerCaptured = mutableListOf<Card>()
    private var cpuCaptured = mutableListOf<Card>()
    private var playerScore = 0
    private var cpuScore = 0
    private var handNum = 0
    private var gamePhase = "play"
    private var selectedCard: Card? = null
    private var gameOver = false
    private val gap = 2f

    override fun onSetupGame() {
        val deck = CardDeck.italianDeck().toMutableList()
        CardDeck.shuffle(deck)

        playerHand.clear()
        cpuHand.clear()
        tableCards.clear()
        playerCaptured.clear()
        cpuCaptured.clear()
        playerScore = 0
        cpuScore = 0
        handNum = 0
        gamePhase = "play"
        selectedCard = null
        gameOver = false

        for (i in 0 until 3) {
            playerHand.add(deck.removeAt(deck.size - 1))
            cpuHand.add(deck.removeAt(deck.size - 1))
        }
        for (i in 0 until 4) {
            tableCards.add(deck.removeAt(deck.size - 1))
        }

        statusText.text = "Cattura carte sommando 15! Tocca una carta."
        updateHUD()
    }

    override fun onDrawGame(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF4444") }
        val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A2E") }

        val cardW = kotlin.math.min(45f, w / 10f)
        val cardH = cardW * 1.4f

        // Table cards
        val tableY = h / 2f - cardH / 2f
        for ((i, card) in tableCards.withIndex()) {
            val tx = gap + i * (cardW + gap * 0.5f)
            drawCard(canvas, card, tx, tableY, cardW, cardH, paint, redPaint, blackPaint)
        }

        // CPU hand (face down)
        val cpuY = gap * 2f
        for (i in cpuHand.indices) {
            val cx = gap + i * (cardW + gap * 0.5f)
            drawCard(canvas, Card(cpuHand[i].suit, cpuHand[i].rank, false), cx, cpuY, cardW, cardH, paint, redPaint, blackPaint)
        }

        // Player hand
        val playerY = h - cardH - gap * 2f
        for ((i, card) in playerHand.withIndex()) {
            val px = gap + i * (cardW + gap * 0.5f)
            if (card == selectedCard) {
                drawCard(canvas, card, px, playerY - 10f, cardW, cardH, paint, redPaint, blackPaint)
            } else {
                drawCard(canvas, card, px, playerY, cardW, cardH, paint, redPaint, blackPaint)
            }
        }

        // Scores
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f; textAlign = Paint.Align.CENTER; color = Color.WHITE
        }
        canvas.drawText("Tu: $playerScore | CPU: $cpuScore | Mano: ${handNum + 1}/3",
            w / 2f, cardH + 30f, scorePaint)

        // Captured counts
        canvas.drawText("Catture: 🟢${playerCaptured.size} 🔴${cpuCaptured.size}",
            w / 2f, cardH + 50f, scorePaint)
    }

    override fun onPlayerTap(x: Float, y: Float) {
        if (!gameRunning || gameOver) return

        val w = gameView.viewW
        val h = gameView.viewH
        if (w <= 0 || h <= 0) return

        val cardW = kotlin.math.min(45f, w / 10f)
        val cardH = cardW * 1.4f

        // Check player hand
        val playerY = h - cardH - gap * 2f
        for ((i, card) in playerHand.withIndex()) {
            val cx = gap + i * (cardW + gap * 0.5f)
            if (x in cx..cx + cardW && y in playerY..playerY + cardH) {
                selectedCard = card
                gameView.invalidate()
                return
            }
        }

        // If a card is selected, try to capture
        if (selectedCard != null) {
            tryCapture(selectedCard!!)
        }
    }

    private fun tryCapture(card: Card) {
        val cardValue = cardValue(card)
        val target = 15 - cardValue

        // Check if any table card(s) sum to target
        val captureSet = findCaptureSet(target)

        if (captureSet != null) {
            // Capture!
            playerCaptured.add(card)
            playerCaptured.addAll(captureSet)
            playerHand.remove(card)
            tableCards.removeAll(captureSet)

            // Check for scopa (capturing last table card)
            if (tableCards.isEmpty()) {
                playerScore += 1
                statusText.text = "Scopa! +1 punto"
            } else {
                statusText.text = "Catturato ${captureSet.size + 1} carte!"
            }

            selectedCard = null
            score = playerScore
            updateHUD()
            gameView.invalidate()

            // Check if hand is empty
            if (playerHand.isEmpty()) {
                endHand()
            } else {
                // Draw new cards
                handler.postDelayed({ drawNewCards() }, 500)
            }
        } else {
            // No capture possible, place card on table
            tableCards.add(card)
            playerHand.remove(card)
            selectedCard = null
            statusText.text = "Nessuna cattura — carta sul tavolo"
            gameView.invalidate()

            if (playerHand.isEmpty()) {
                endHand()
            } else {
                handler.postDelayed({ drawNewCards() }, 500)
            }
        }
    }

    private fun findCaptureSet(target: Int): List<Card>? {
        if (target <= 0) return null

        // Try single card
        for (card in tableCards) {
            if (cardValue(card) == target) return listOf(card)
        }

        // Try pairs
        for (i in tableCards.indices) {
            for (j in i + 1 until tableCards.size) {
                if (cardValue(tableCards[i]) + cardValue(tableCards[j]) == target) {
                    return listOf(tableCards[i], tableCards[j])
                }
            }
        }

        // Try triplets
        for (i in tableCards.indices) {
            for (j in i + 1 until tableCards.size) {
                for (k in j + 1 until tableCards.size) {
                    if (cardValue(tableCards[i]) + cardValue(tableCards[j]) + cardValue(tableCards[k]) == target) {
                        return listOf(tableCards[i], tableCards[j], tableCards[k])
                    }
                }
            }
        }

        return null
    }

    private fun cardValue(card: Card): Int = when (card.rank) {
        Rank.ACE -> 1
        Rank.JACK -> 8
        Rank.QUEEN -> 9
        Rank.KING -> 10
        else -> card.rank.value
    }

    private fun drawCard(canvas: Canvas, card: Card, x: Float, y: Float, w: Float, h: Float,
                         paint: Paint, redPaint: Paint, blackPaint: Paint) {
        val bg = if (card.isFaceUp) Color.WHITE else Color.parseColor("#1A3A5A")
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 5f, 5f, fill)
        paint.color = Color.parseColor("#CCCCCC")
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 5f, 5f, paint)

        if (card.isFaceUp) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = w * 0.35f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val color = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) redPaint else blackPaint
            textPaint.color = color.color
            canvas.drawText(card.rank.label, x + w / 2f, y + h * 0.35f, textPaint)
            canvas.drawText(card.suit.emoji, x + w / 2f, y + h * 0.75f, textPaint)
        } else {
            canvas.drawText("🂠", x + w / 2f, y + h / 2f + 5f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = w * 0.5f; textAlign = Paint.Align.CENTER; color = Color.WHITE })
        }
    }

    private fun drawNewCards() {
        val deck = CardDeck.italianDeck().toMutableList()
        CardDeck.shuffle(deck)
        val allCards = (playerHand + cpuHand + tableCards).toMutableList()
        deck.removeAll(allCards)
        while (playerHand.size < 3 && deck.isNotEmpty()) {
            playerHand.add(deck.removeAt(deck.size - 1))
        }
        while (cpuHand.size < 3 && deck.isNotEmpty()) {
            cpuHand.add(deck.removeAt(deck.size - 1))
        }
        while (tableCards.size < 4 && deck.isNotEmpty()) {
            tableCards.add(deck.removeAt(deck.size - 1))
        }

        statusText.text = "Tocca una carta per catturare!"
        gameView.invalidate()
    }

    private fun endHand() {
        handNum++

        // CPU captures remaining table cards
        if (tableCards.isNotEmpty()) {
            cpuCaptured.addAll(tableCards)
            cpuScore += 1 // scopa for CPU if table was not empty
            tableCards.clear()
        }

        // CPU draws
        val deck = CardDeck.italianDeck().toMutableList()
        CardDeck.shuffle(deck)
        while (cpuHand.size < 3 && deck.isNotEmpty()) {
            cpuHand.add(deck.removeAt(deck.size - 1))
        }

        statusText.text = "Mano ${handNum} finita! Tocca per continuare..."
        gameView.invalidate()

        handler.postDelayed({
            if (!gameRunning || gameOver) return@postDelayed
            if (handNum >= 3 || cpuHand.isEmpty()) {
                endGame(countFinalScore())
            } else {
                // Continue to next hand
                statusText.text = "Mano ${handNum + 1}/3 — Tocca una carta!"
                gameView.invalidate()
            }
        }, 1500)
    }

    private fun countFinalScore(): Boolean {
        val playerDenari = playerCaptured.count { it.suit == Suit.DIAMONDS }
        val cpuDenari = cpuCaptured.count { it.suit == Suit.DIAMONDS }
        val playerSettebello = playerCaptured.any { it.suit == Suit.DIAMONDS && it.rank == Rank.SEVEN }
        val cpuSettebello = cpuCaptured.any { it.suit == Suit.DIAMONDS && it.rank == Rank.SEVEN }

        var pScore = 0
        var cScore = 0

        // Carte (most cards)
        if (playerCaptured.size > cpuCaptured.size) pScore += 1
        else if (cpuCaptured.size > playerCaptured.size) cScore += 1

        // Denari (most diamonds)
        if (playerDenari > cpuDenari) pScore += 1
        else if (cpuDenari > playerDenari) cScore += 1

        // Settebello
        if (playerSettebello) pScore += 1
        if (cpuSettebello) cScore += 1

        playerScore = pScore
        cpuScore = cScore
        return pScore > cScore
    }

    override fun checkGameOver() {
        if (handNum >= 3 && !gameOver) {
            gameOver = true
            endGame(countFinalScore())
        }
    }

}