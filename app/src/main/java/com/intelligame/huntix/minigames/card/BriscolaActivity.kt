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
import com.intelligame.huntix.managers.MiniGameManager.GAME_BRISCOLA
import io.sentry.Sentry

/**
 * 🃏 Briscola — classico gioco di carte italiano a 2 giocatori.
 *
 * Si sceglie il seme di Briscola (il seme vincente).
 * Ogni mano si gioca una carta: vince chi ha il seme più alto,
 * o la carta più alta del seme giocato se nessuno ha briscola.
 * Vince chi totalizza più punti nelle mani.
 */
class BriscolaActivity : CardGameBase() {

    override val config = CardGameConfig(
        gameId = GAME_BRISCOLA,
        title = "Briscola",
        emoji = "🃏",
        rules = "Scegli il seme di Briscola!\n" +
                "Ogni mano: gioca una carta. Il seme di briscola vince sempre.\n" +
                "Se nessuno ha briscola, vince il seme più alto della carta giocata.\n" +
                "Punti: A=11, 3=10, K=4, Q=3, J=2, 2-7=0.\n" +
                "Vinci chi fa più punti in 6 mani!"
    )

    private val suits = listOf(Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS, Suit.SPADES)
    private val ranks = listOf(
        Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE,
        Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
        Rank.JACK, Rank.QUEEN, Rank.KING
    )

    private val briscolaPoints = mapOf(
        Rank.ACE to 11, Rank.THREE to 10, Rank.KING to 4,
        Rank.QUEEN to 3, Rank.JACK to 2
    )

    private var playerHand = mutableListOf<Card>()
    private var cpuHand = mutableListOf<Card>()
    private var briscolaSuit: Suit? = null
    private var briscolaCard: Card? = null
    private var currentTrick = mutableListOf<Card>()
    private var playerTrick = mutableListOf<Card>()
    private var cpuTrick = mutableListOf<Card>()
    private var playerScore = 0
    private var cpuScore = 0
    private var trickNum = 0
    private var gamePhase = "choose" // choose, play, show
    private var selectedCard: Card? = null
    private var gameOver = false
    private val gap = 2f

    override fun onSetupGame() {
        val deck = CardDeck.italianDeck().toMutableList()
        CardDeck.shuffle(deck)

        briscolaSuit = null
        briscolaCard = null
        playerScore = 0
        cpuScore = 0
        trickNum = 0
        gamePhase = "choose"
        selectedCard = null
        gameOver = false

        playerHand.clear()
        cpuHand.clear()
        currentTrick.clear()
        playerTrick.clear()
        cpuTrick.clear()

        for (i in 0 until 3) {
            briscolaCard = deck.removeAt(deck.size - 1)
        }
        briscolaSuit = briscolaCard?.suit

        for (i in 0 until 3) {
            playerHand.add(deck.removeAt(deck.size - 1))
            cpuHand.add(deck.removeAt(deck.size - 1))
        }

        statusText.text = "Scegli il seme di Briscola! 🎴"
        updateHUD()
    }

    override fun onDrawGame(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF4444") }
        val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A2E") }

        val cardW = kotlin.math.min(45f, w / 10f)
        val cardH = cardW * 1.4f

        if (gamePhase == "choose") {
            drawChoosePhase(canvas, w, h, cardW, cardH, paint, whitePaint, redPaint, blackPaint)
        } else {
            drawPlayPhase(canvas, w, h, cardW, cardH, paint, whitePaint, redPaint, blackPaint)
        }
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

    private fun drawChoosePhase(canvas: Canvas, w: Float, h: Float, cardW: Float, cardH: Float,
                                  paint: Paint, whitePaint: Paint, redPaint: Paint, blackPaint: Paint) {
        val centerY = h / 2f
        val startX = (w - cardW * 4 - gap * 3f) / 2f

        for (i in suits.indices) {
            val x = startX + i * (cardW + gap)
            val y = centerY - cardH / 2f
            drawCard(canvas, Card(suits[i], Rank.ACE, true), x, y, cardW, cardH, paint, redPaint, blackPaint)
            canvas.drawText(suits[i].emoji, x + cardW / 2f, y + cardH + 20f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; textAlign = Paint.Align.CENTER; color = Color.WHITE })
        }

        if (briscolaCard != null) {
            canvas.drawText("Briscola: ${briscolaSuit?.emoji} ${briscolaCard?.rank?.label}",
                w / 2f, h - 40f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; textAlign = Paint.Align.CENTER; color = Color.parseColor(UiKit.ACCENT) })
        }
    }

    private fun drawPlayPhase(canvas: Canvas, w: Float, h: Float, cardW: Float, cardH: Float,
                               paint: Paint, whitePaint: Paint, redPaint: Paint, blackPaint: Paint) {
        // CPU hand (face down)
        val cpuY = gap * 2f
        for (i in cpuHand.indices) {
            val x = gap + i * (cardW + gap * 0.5f)
            drawCard(canvas, Card(cpuHand[i].suit, cpuHand[i].rank, false), x, cpuY, cardW, cardH, paint, redPaint, blackPaint)
        }

        // Current trick
        val trickY = h / 2f - cardH / 2f
        if (currentTrick.isNotEmpty()) {
            for ((i, card) in currentTrick.withIndex()) {
                val x = gap + i * (cardW + gap)
                drawCard(canvas, card, x, trickY, cardW, cardH, paint, redPaint, blackPaint)
            }
        }

        // Player hand
        val playerY = h - cardH - gap * 2f
        for ((i, card) in playerHand.withIndex()) {
            val x = gap + i * (cardW + gap * 0.5f)
            if (card == selectedCard) {
                drawCard(canvas, card, x, playerY - 10f, cardW, cardH, paint, redPaint, blackPaint)
            } else {
                drawCard(canvas, card, x, playerY, cardW, cardH, paint, redPaint, blackPaint)
            }
        }

        // Scores
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f; textAlign = Paint.Align.CENTER; color = Color.WHITE
        }
        canvas.drawText("Tu: $playerScore  |  CPU: $cpuScore  |  Mano: ${trickNum + 1}/6",
            w / 2f, cardH + 30f, scorePaint)
    }

    override fun onPlayerTap(x: Float, y: Float) {
        if (!gameRunning || gameOver) return

        val w = gameView.viewW
        val h = gameView.viewH
        if (w <= 0 || h <= 0) return

        val cardW = kotlin.math.min(45f, w / 10f)
        val cardH = cardW * 1.4f

        if (gamePhase == "choose") {
            val centerY = h / 2f
            val startX = (w - cardW * 4 - gap * 3f) / 2f
            for (i in suits.indices) {
                val sx = startX + i * (cardW + gap)
                if (x in sx..sx + cardW && y in centerY - cardH / 2f..centerY + cardH / 2f) {
                    briscolaSuit = suits[i]
                    statusText.text = "Briscola: ${suits[i].emoji} — Gioca!"
                    gamePhase = "play"
                    gameView.invalidate()
                    return
                }
            }
            return
        }

        // Play phase
        val playerY = h - cardH - gap * 2f
        for ((i, card) in playerHand.withIndex()) {
            val cx = gap + i * (cardW + gap * 0.5f)
            if (x in cx..cx + cardW && y in playerY..playerY + cardH) {
                selectedCard = card
                gameView.invalidate()
                return
            }
        }

        // If a card is selected, play it
        if (selectedCard != null) {
            playCard(selectedCard!!)
        }
    }

    private fun playCard(card: Card) {
        playerHand.remove(card)
        currentTrick.add(card)
        playerTrick.add(card)
        gamePhase = "show"
        statusText.text = "Hai giocato ${card.rank.label}${card.suit.emoji}"
        selectedCard = null
        gameView.invalidate()

        // CPU plays after a delay
        handler.postDelayed({
            if (!gameRunning || gameOver) return@postDelayed
            cpuPlay()
        }, 600)
    }

    private fun cpuPlay() {
        if (cpuHand.isEmpty()) return

        val cpuCard = if (briscolaSuit != null && cpuHand.any { it.suit == briscolaSuit }) {
            cpuHand.filter { it.suit == briscolaSuit }.minByOrNull { it.rank.value }!!
        } else {
            cpuHand.random()
        }

        cpuHand.remove(cpuCard)
        currentTrick.add(cpuCard)
        cpuTrick.add(cpuCard)
        statusText.text = "CPU ha giocato ${cpuCard.rank.label}${cpuCard.suit.emoji}"
        gamePhase = "show"
        gameView.invalidate()

        handler.postDelayed({
            if (!gameRunning || gameOver) return@postDelayed
            resolveTrick()
        }, 800)
    }

    private fun resolveTrick() {
        val playerCard = playerTrick.lastOrNull()
        val cpuCard = cpuTrick.lastOrNull()
        if (playerCard == null || cpuCard == null) {
            nextTrick()
            return
        }

        val playerWins = when {
            playerCard.suit == briscolaSuit && cpuCard.suit != briscolaSuit -> true
            cpuCard.suit == briscolaSuit && playerCard.suit != briscolaSuit -> false
            playerCard.suit == cpuCard.suit -> playerCard.rank.value > cpuCard.rank.value
            else -> false
        }

        val playerPts = briscolaPoints[playerCard.rank] ?: 0
        val cpuPts = briscolaPoints[cpuCard.rank] ?: 0

        if (playerWins) {
            playerScore += playerPts + cpuPts
            statusText.text = "Vinci il punto! +${playerPts + cpuPts}pt"
        } else {
            cpuScore += playerPts + cpuPts
            statusText.text = "CPU vince il punto! +${playerPts + cpuPts}pt"
        }

        score = playerScore
        updateHUD()
        gamePhase = "play"
        nextTrick()
    }

    private fun nextTrick() {
        trickNum++
        currentTrick.clear()
        playerTrick.clear()
        cpuTrick.clear()

        if (trickNum >= 6 || playerHand.isEmpty() || cpuHand.isEmpty()) {
            endGame(playerScore > cpuScore)
            return
        }

        // Draw new cards from fresh deck
        val deck = CardDeck.italianDeck().toMutableList()
        CardDeck.shuffle(deck)
        val allCards = (playerHand + cpuHand + currentTrick).toMutableList()
        deck.removeAll(allCards)
        while (playerHand.size < 3 && deck.isNotEmpty()) {
            playerHand.add(deck.removeAt(deck.size - 1))
        }
        while (cpuHand.size < 3 && deck.isNotEmpty()) {
            cpuHand.add(deck.removeAt(deck.size - 1))
        }

        statusText.text = "Mano ${trickNum + 1}/6 — Tocca una carta!"
        gameView.invalidate()
    }

    override fun checkGameOver() {
        if (trickNum >= 6 && !gameOver) {
            gameOver = true
            endGame(playerScore > cpuScore)
        }
    }

}