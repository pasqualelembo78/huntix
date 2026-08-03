package com.intelligame.huntix.minigames.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.minigames.card.Card
import com.intelligame.huntix.minigames.card.CardDeck
import com.intelligame.huntix.minigames.card.Rank
import com.intelligame.huntix.minigames.card.Suit
import com.google.ar.core.Pose
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🃏 AR Solitaire — Klondike in Realtà Aumentata.
 *
 * Le carte sono posizionate come ImageNode su una superficie reale
 * rilevata con Plane Detection. Il giocatore inquadra il tavolo e
 * tocca le carte per selezionarle e spostarle nei fondali.
 *
 * Layout 3D:
 * - 4 fondali in alto
 * - Draw pile e discard pile accanto ai fondali
 * - 7 colonne del tableau sotto
 */
class ARSolitaireActivity : ARGameActivity() {

    companion object {
        private const val CARD_W = 0.08f
        private const val CARD_H = CARD_W * 1.4f
        private const val GAP = 0.008f
        private const val COL_SPACING = 0.09f
        private const val ROW_SPACING = 0.012f
    }

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
    private var gameOver = false
    private var arenaNode: AnchorNode? = null
    private var score = 0
    private var lives = 3

    private val cardNodes = mutableListOf<ImageNode>()
    private val cardEggs = mutableListOf<AREgg>()
    private var backBmp: Bitmap? = null
    private val frontBmps = mutableMapOf<String, Bitmap>()

    enum class Source { TABLEAU, DRAW, DISCARD, FOUNDATION }

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        updateLevelHud(MiniGameManager.GAME_SOLITAIRE)
        livesText.text = "🃏 Solitaire"
        timerText.text = ""
        scoreText.text = "0"
        statusText.text = "🔍 Inquadra una superficie piana…"
        startGame()
        backBmp = cardBitmap("🂠", 0xFF1A3A5A.toInt(), border = true)
        for (suit in suits) {
            for (rank in ranks) {
                val key = "${rank.label}${suit.emoji}"
                frontBmps[key] = cardBitmap(key, 0xFFFFFFFF.toInt(), border = false)
            }
        }
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        arenaNode = a
        resetGame()
        layoutCards(a)
        statusText.text = "Tocca una carta per selezionarla"
    }

    private fun resetGame() {
        clearCardNodes()
        foundations.forEach { it.clear() }
        tableau.forEach { it.clear() }
        drawPile.clear()
        discardPile.clear()
        selectedCard = null
        selectedSource = null
        gameOver = false
        score = 0
        lives = 3
        updateHud()

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
    }

    private fun clearCardNodes() {
        cardNodes.forEach { it.destroy() }
        cardEggs.forEach { removeEgg(it) }
        cardNodes.clear()
        cardEggs.clear()
    }

    private fun layoutCards(a: AnchorNode) {
        val yaw = yawToFaceCamera(a)
        val yawCos = cos(yaw)
        val yawSin = sin(yaw)
        val rotOff: (Float, Float) -> Pair<Float, Float> = { x, z ->
            (x * yawCos + z * yawSin) to (-x * yawSin + z * yawCos)
        }

        val foundationY = 0.4f
        val foundationX = -(3 * COL_SPACING) / 2f

        // Foundations
        for (i in 0 until 4) {
            val fx = foundationX + i * COL_SPACING
            val (rx, rz) = rotOff(fx, foundationY)
            val pose = Pose(
                floatArrayOf(rx, 0.15f, rz),
                floatArrayOf(0f, 0f, 0f, 1f)
            )
            val anchor = spawnAnchorAt(pose) ?: continue
            val node = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = backBmp!!,
                size = Size(CARD_W, CARD_H),
                normal = Direction(0f, 0f, 1f)
            )
            anchor.addChildNode(node)
            val egg = AREgg(anchor, node, 5, phase = i.toFloat())
            registerEgg(egg)
            cardNodes.add(node)
            cardEggs.add(egg)
        }

        // Draw pile
        val dpX = foundationX + 4 * COL_SPACING
        val (dpx, dpz) = rotOff(dpX, foundationY)
        val dpPose = Pose(
            floatArrayOf(dpx, 0.15f, dpz),
            floatArrayOf(0f, 0f, 0f, 1f)
        )
        val dpAnchor = spawnAnchorAt(dpPose)
        if (dpAnchor != null) {
            val dpNode = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = backBmp!!,
                size = Size(CARD_W, CARD_H),
                normal = Direction(0f, 0f, 1f)
            )
            dpAnchor.addChildNode(dpNode)
            val dpEgg = AREgg(dpAnchor, dpNode, 6, phase = 0f)
            registerEgg(dpEgg)
            cardNodes.add(dpNode)
            cardEggs.add(dpEgg)
        }

        // Discard pile
        val discX = dpX + COL_SPACING
        val (discx, discz) = rotOff(discX, foundationY)
        val discPose = Pose(
            floatArrayOf(discx, 0.15f, discz),
            floatArrayOf(0f, 0f, 0f, 1f)
        )
        val discAnchor = spawnAnchorAt(discPose)
        if (discAnchor != null) {
            val discNode = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = backBmp!!,
                size = Size(CARD_W, CARD_H),
                normal = Direction(0f, 0f, 1f)
            )
            discAnchor.addChildNode(discNode)
            val discEgg = AREgg(discAnchor, discNode, 7, phase = 0f)
            registerEgg(discEgg)
            cardNodes.add(discNode)
            cardEggs.add(discEgg)
        }

        // Tableau columns
        val tableauY = 0.25f
        for (col in 0 until 7) {
            val tx = -(3 * COL_SPACING) / 2f + col * COL_SPACING
            for (row in 0 until tableau[col].size) {
                val (rx, rz) = rotOff(tx, tableauY - row * ROW_SPACING)
                val pose = Pose(
                    floatArrayOf(rx, 0.15f, rz),
                    floatArrayOf(0f, 0f, 0f, 1f)
                )
                val anchor = spawnAnchorAt(pose) ?: continue
                val card = tableau[col][row]
                val bmp = if (card.isFaceUp) frontBmps["${card.rank.label}${card.suit.emoji}"] else backBmp
                val node = ImageNode(
                    materialLoader = sceneView.materialLoader,
                    bitmap = bmp ?: backBmp!!,
                    size = Size(CARD_W, CARD_H),
                    normal = Direction(0f, 0f, 1f)
                )
                anchor.addChildNode(node)
                val egg = AREgg(anchor, node, 8, phase = col.toFloat() + row * 0.1f)
                registerEgg(egg)
                cardNodes.add(node)
                cardEggs.add(egg)
            }
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || gameOver) return
        val idx = egg.phase.toInt()
        when (idx) {
            0, 1, 2, 3 -> handleFoundationTap(idx)
            4 -> handleDrawPileTap()
            5 -> handleDiscardPileTap()
            else -> handleTableauTap(idx)
        }
    }

    private fun handleFoundationTap(idx: Int) {
        if (selectedCard == null) return
        tryPlaceOnFoundation(idx)
    }

    private fun handleDrawPileTap() {
        if (drawPile.isNotEmpty()) {
            val card = drawPile.removeAt(drawPile.size - 1)
            card.isFaceUp = true
            discardPile.add(card)
            score += 1
            updateHud()
            refreshCards()
            checkWin()
        } else if (discardPile.isNotEmpty()) {
            drawPile.addAll(discardPile.reversed())
            discardPile.clear()
            CardDeck.shuffle(drawPile)
            for (c in drawPile) c.isFaceUp = false
            statusText.text = "Mazzo ricaricato!"
            refreshCards()
        }
    }

    private fun handleDiscardPileTap() {
        if (discardPile.isNotEmpty()) {
            selectedCard = discardPile.last()
            selectedSource = Source.DISCARD
            statusText.text = "Selezionata: ${selectedCard!!.rank.label}${selectedCard!!.suit.emoji}"
        }
    }

    private fun handleTableauTap(idx: Int) {
        val col = idx / 10
        val row = idx % 10
        if (col !in 0..6 || row !in tableau[col].indices) return
        val card = tableau[col][row]
        if (!card.isFaceUp) return
        if (selectedCard == null) {
            selectedCard = card
            selectedSource = Source.TABLEAU
            statusText.text = "Selezionata: ${card.rank.label}${card.suit.emoji}"
        } else {
            tryPlaceOnTableau(col, row)
        }
    }

    private fun tryPlaceOnFoundation(foundationIdx: Int) {
        val card = selectedCard ?: return
        val foundation = foundations[foundationIdx]
        val canPlace = when {
            foundation.isEmpty() -> card.rank == Rank.ACE
            card.suit == foundation.last().suit && card.rank.value == foundation.last().rank.value + 1 -> true
            else -> false
        }
        if (canPlace) {
            moveCardToFoundation(foundationIdx)
        } else {
            selectedCard = null
            selectedSource = null
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
        updateHud()
        selectedCard = null
        selectedSource = null
        refreshCards()
        checkWin()
    }

    private fun tryPlaceOnTableau(col: Int, targetIdx: Int) {
        val card = selectedCard ?: return
        val target = tableau[col]
        val canPlace = when {
            targetIdx < target.size && target[targetIdx].isFaceUp ->
                card.suit != target[targetIdx].suit && card.rank.value == target[targetIdx].rank.value - 1
            targetIdx == target.size && target.isEmpty() -> card.rank == Rank.KING
            else -> false
        }
        if (canPlace) {
            moveCardToTableau(col, targetIdx)
        } else {
            selectedCard = null
            selectedSource = null
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
        updateHud()
        selectedCard = null
        selectedSource = null
        refreshCards()
        checkWin()
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
                updateHud()
            }
        }
    }

    private fun checkWin() {
        val totalInFoundations = foundations.sumOf { it.size }
        if (totalInFoundations == 52) {
            gameOver = true
            postDelayed(500) {
                finishGame(100, "AR Solitaire — Vittoria!", true, MiniGameManager.GAME_SOLITAIRE, score = totalInFoundations)
            }
        }
    }

    private fun refreshCards() {
        clearCardNodes()
        arenaNode?.let { layoutCards(it) }
    }

    private fun updateHud() {
        livesText.text = "🃏 Solitaire"
        scoreText.text = score.toString()
        timerText.text = ""
    }

    /** Disegna una carta come bitmap quadrata 256x256 con seme e valore. */
    private fun cardBitmap(symbol: String, bg: Int, border: Boolean): Bitmap {
        val px = 256f
        val bmp = Bitmap.createBitmap(px.toInt(), px.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(px * 0.06f, px * 0.06f, px * 0.94f, px * 0.94f)
        canvas.drawRoundRect(rect, px * 0.10f, px * 0.10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        if (border) {
            val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = px * 0.02f
            }
            canvas.drawRoundRect(rect, px * 0.10f, px * 0.10f, borderP)
        }
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = px * 0.42f
        }
        val baseline = px / 2f - (textP.ascent() + textP.descent()) / 2f
        canvas.drawText(symbol, px / 2f, baseline, textP)
        return bmp
    }
}