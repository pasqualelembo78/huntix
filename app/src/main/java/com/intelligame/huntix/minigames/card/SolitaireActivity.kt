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
import com.intelligame.huntix.managers.MiniGameManager.GAME_SOLITAIRE
import io.sentry.Sentry

/**
 * 🃏 Solitaire — classico Klondike con uova.
 *
 * Disponi le carte in ordine crescente (A→K) nei 4 fondali.
 * Le carte sono alternate rosse/nero. Trascina le carte per
 * muoverle. Raccogli tutte le carte per vincere!
 */
class SolitaireActivity : CardGameBase() {

    override val config = CardGameConfig(
        gameId = GAME_SOLITAIRE,
        title = "Solitaire",
        emoji = "🃏",
        rules = "Disponi le carte in ordine crescente (A→K) nei 4 fondali.\n" +
                "Le carte devono alternare colore (rosso/nero).\n" +
                "Tocca una carta per selezionarla, tocca un fondale per posizionarla.\n" +
                "Raccogli tutte le 48 carte per vincere!"
    )

    private val suits = listOf(Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS, Suit.SPADES)
    private val ranks = listOf(
        Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE,
        Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
        Rank.JACK, Rank.QUEEN, Rank.KING
    )

    private val foundations = Array(4) { mutableListOf<Card>() }
    private val tableau = Array(7) { mutableListOf<Card>() }
    private val drawPile = mutableListOf<Card>()
    private val discardPile = mutableListOf<Card>()

    private var selectedCard: Card? = null
    private var selectedSource: Source? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private var cardW = 50f
    private var cardH = 70f
    private var gap = 2f

    enum class Source { TABLEAU, DRAW, DISCARD, FOUNDATION }

    override fun onSetupGame() {
        drawPile.clear()
        discardPile.clear()
        for (f in foundations) f.clear()
        for (t in tableau) t.clear()

        for (suit in suits) {
            for (rank in ranks) {
                drawPile.add(Card(suit, rank))
            }
        }
        CardDeck.shuffle(drawPile)

        for (i in 0 until 7) {
            for (j in 0..i) {
                val card = drawPile.removeAt(drawPile.size - 1)
                card.isFaceUp = (j == i)
                tableau[i].add(card)
            }
        }

        selectedCard = null
        selectedSource = null
        score = 0
        lives = 3
        updateHUD()
        statusText.text = "Tocca una carta per selezionarla"
    }

    override fun onDrawGame(canvas: Canvas, w: Float, h: Float) {
        cardW = kotlin.math.min(50f, w / 10f)
        cardH = cardW * 1.4f
        gap = cardW * 0.04f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF4444") }
        val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A2E") }
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00CC88") }
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#42A5F5") }

        // Draw draw pile
        val dpX = cardW + gap
        val dpY = h - cardH - gap * 2f
        drawPileRect(canvas, dpX, dpY, cardW, cardH, paint, whitePaint)
        if (drawPile.isNotEmpty()) {
            canvas.drawText("${drawPile.size}", dpX + cardW / 2f, dpY + cardH / 2f + 5f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = cardW * 0.35f; textAlign = Paint.Align.CENTER; color = Color.WHITE })
        }

        // Draw discard pile
        val discX = dpX + cardW + gap
        val discY = dpY
        if (discardPile.isNotEmpty()) {
            val top = discardPile.last()
            drawCard(canvas, top, discX, discY, cardW, cardH, paint, redPaint, blackPaint)
        } else {
            drawPileRect(canvas, discX, discY, cardW, cardH, paint, whitePaint)
        }

        // Draw foundations
        val totalTableW = 7 * cardW + 6 * gap
        val startX = (w - totalTableW) / 2f
        val foundationY = gap * 2f
        for (i in 0 until 4) {
            val fx = startX + i * (cardW + gap)
            if (foundations[i].isNotEmpty()) {
                drawCard(canvas, foundations[i].last(), fx, foundationY, cardW, cardH, paint, redPaint, blackPaint)
            } else {
                drawPileRect(canvas, fx, foundationY, cardW, cardH, paint, greenPaint)
            }
        }

        // Draw tableau columns
        val tableauY = foundationY + cardH + gap * 3f
        for (col in 0 until 7) {
            val tx = startX + col * (cardW + gap)
            val cards = tableau[col]
            for ((idx, card) in cards.withIndex()) {
                val cy = tableauY + idx * (cardH * 0.7f)
                if (card == selectedCard && selectedSource == Source.TABLEAU) {
                    // Draw slightly offset for drag effect
                    drawCard(canvas, card, tx + dragOffsetX, cy + dragOffsetY, cardW, cardH, paint, redPaint, blackPaint)
                } else {
                    drawCard(canvas, card, tx, cy, cardW, cardH, paint, redPaint, blackPaint)
                }
            }
        }
    }

    private fun drawPileRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, border: Paint, fill: Paint) {
        fill.color = Color.parseColor("#2A2C55")
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, fill)
        border.color = Color.parseColor("#3A3C66")
        border.strokeWidth = 1f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, border)
    }

    private fun drawCard(canvas: Canvas, card: Card, x: Float, y: Float, w: Float, h: Float,
                         border: Paint, redPaint: Paint, blackPaint: Paint) {
        val bg = if (card.isFaceUp) Color.WHITE else Color.parseColor("#1A3A5A")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 5f, 5f, paint)
        border.color = Color.parseColor("#CCCCCC")
        border.strokeWidth = 1f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 5f, 5f, border)

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

    override fun onPlayerTap(x: Float, y: Float) {
        if (!gameRunning) return

        val w = gameView.viewW
        val h = gameView.viewH
        if (w <= 0 || h <= 0) return

        val totalTableW = 7 * cardW + 6 * gap
        val startX = (w - totalTableW) / 2f
        val foundationY = gap * 2f
        val tableauY = foundationY + cardH + gap * 3f

        // Check draw pile
        val dpX = cardW + gap
        val dpY = h - cardH - gap * 2f
        if (inRect(x, y, dpX, dpY, cardW, cardH)) {
            if (drawPile.isNotEmpty()) {
                val card = drawPile.removeAt(drawPile.size - 1)
                card.isFaceUp = true
                discardPile.add(card)
                score += 1
                updateHUD()
                checkGameOver()
                gameView.invalidate()
            } else if (discardPile.isNotEmpty()) {
                drawPile.addAll(discardPile.reversed())
                discardPile.clear()
                CardDeck.shuffle(drawPile)
                for (c in drawPile) c.isFaceUp = false
                statusText.text = "Mazzo ricaricato!"
                gameView.invalidate()
            }
            return
        }

        // Check discard pile
        val discX = dpX + cardW + gap
        val discY = dpY
        if (inRect(x, y, discX, discY, cardW, cardH) && discardPile.isNotEmpty()) {
            selectCard(discardPile.last(), Source.DISCARD, x, y)
            return
        }

        // Check foundations
        for (i in 0 until 4) {
            val fx = startX + i * (cardW + gap)
            if (inRect(x, y, fx, foundationY, cardW, cardH)) {
                if (selectedCard != null) {
                    tryPlaceOnFoundation(i)
                } else if (foundations[i].isNotEmpty()) {
                    selectCard(foundations[i].last(), Source.FOUNDATION, x, y)
                }
                return
            }
        }

        // Check tableau
        for (col in 0 until 7) {
            val tx = startX + col * (cardW + gap)
            val cards = tableau[col]
            for ((idx, card) in cards.withIndex()) {
                val cy = tableauY + idx * (cardH * 0.7f)
                if (inRect(x, y, tx, cy, cardW, cardH)) {
                    if (selectedCard == null && card.isFaceUp) {
                        selectCard(card, Source.TABLEAU, x, y)
                    } else if (selectedCard != null) {
                        tryPlaceOnTableau(col, idx)
                    }
                    return
                }
            }
        }

        // Tap on empty space deselects
        if (selectedCard != null) {
            selectedCard = null
            selectedSource = null
            gameView.invalidate()
        }
    }

    private fun selectCard(card: Card, source: Source, x: Float, y: Float) {
        selectedCard = card
        selectedSource = source
        dragOffsetX = 0f
        dragOffsetY = 0f
        gameView.invalidate()
    }

    private fun tryPlaceOnFoundation(foundationIdx: Int) {
        val card = selectedCard ?: return
        val foundation = foundations[foundationIdx]
        if (foundation.isEmpty()) {
            if (card.rank == Rank.ACE) {
                moveCardToFoundation(foundationIdx)
            }
        } else {
            val top = foundation.last()
            if (card.suit == top.suit && card.rank.value == top.rank.value + 1) {
                moveCardToFoundation(foundationIdx)
            }
        }
    }

    private fun moveCardToFoundation(foundationIdx: Int) {
        val card = selectedCard!!
        val source = selectedSource!!
        when (source) {
            Source.TABLEAU -> {
                val col = findCardInTableau(card)
                if (col >= 0) {
                    tableau[col].remove(card)
                    flipTopCard(col)
                }
            }
            Source.DISCARD -> discardPile.remove(card)
            Source.FOUNDATION -> {
                val fIdx = findCardInFoundation(card)
                if (fIdx >= 0) foundations[fIdx].remove(card)
            }
            else -> {}
        }
        foundations[foundationIdx].add(card)
        score += 10
        updateHUD()
        selectedCard = null
        selectedSource = null
        gameView.invalidate()
        checkGameOver()
    }

    private fun tryPlaceOnTableau(col: Int, targetIdx: Int) {
        val card = selectedCard ?: return
        val source = selectedSource!!
        val target = tableau[col]

        if (targetIdx < target.size && target[targetIdx].isFaceUp) {
            val targetCard = target[targetIdx]
            if (card.suit != targetCard.suit && card.rank.value == targetCard.rank.value - 1) {
                moveCardToTableau(col, targetIdx)
            }
        } else if (targetIdx == target.size && target.isEmpty()) {
            if (card.rank == Rank.KING) {
                moveCardToTableau(col, targetIdx)
            }
        }
    }

    private fun moveCardToTableau(col: Int, targetIdx: Int) {
        val card = selectedCard!!
        val source = selectedSource!!
        when (source) {
            Source.TABLEAU -> {
                val srcCol = findCardInTableau(card)
                if (srcCol >= 0) {
                    val cards = tableau[srcCol]
                    val idx = cards.indexOf(card)
                    if (idx >= 0) {
                        val moved = cards.drop(idx).toMutableList()
                        tableau[srcCol].removeAll(moved)
                        flipTopCard(srcCol)
                        tableau[col].addAll(targetIdx, moved)
                    }
                }
            }
            Source.DISCARD -> {
                discardPile.remove(card)
                tableau[col].add(targetIdx, card)
            }
            Source.FOUNDATION -> {
                val fIdx = findCardInFoundation(card)
                if (fIdx >= 0) foundations[fIdx].remove(card)
                tableau[col].add(targetIdx, card)
            }
            else -> {}
        }
        score += 5
        updateHUD()
        selectedCard = null
        selectedSource = null
        gameView.invalidate()
        checkGameOver()
    }

    private fun findCardInTableau(card: Card): Int {
        for (i in tableau.indices) {
            if (card in tableau[i]) return i
        }
        return -1
    }

    private fun findCardInFoundation(card: Card): Int {
        for (i in foundations.indices) {
            if (card in foundations[i]) return i
        }
        return -1
    }

    private fun flipTopCard(col: Int) {
        if (tableau[col].isNotEmpty()) {
            val top = tableau[col].last()
            if (!top.isFaceUp) {
                top.isFaceUp = true
                score += 5
                updateHUD()
            }
        }
    }

    private fun inRect(x: Float, y: Float, rx: Float, ry: Float, rw: Float, rh: Float): Boolean {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh
    }

    override fun checkGameOver() {
        val totalInFoundations = foundations.sumOf { it.size }
        if (totalInFoundations == 48) {
            endGame(true)
        } else if (lives <= 0) {
            endGame(false)
        }
    }
}