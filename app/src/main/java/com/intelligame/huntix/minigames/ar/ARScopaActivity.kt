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
 * 🃏 AR Scopa — gioco di carte italiano in Realtà Aumentata.
 *
 * Le carte sono posizionate come ImageNode su una superficie reale.
 * Il giocatore tocca una carta dalla mano per selezionarla,
 * poi tocca le carte sul tavolo per catturarle (somma 15).
 * Prendi il settebello per la scopa (+1 punto extra).
 */
class ARScopaActivity : ARGameActivity() {

    companion object {
        private const val CARD_W = 0.07f
        private const val CARD_H = CARD_W * 1.4f
        private const val GAP = 0.006f
    }

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
    private var arenaNode: AnchorNode? = null
    private var score = 0

    private val cardNodes = mutableListOf<ImageNode>()
    private val cardEggs = mutableListOf<AREgg>()
    private var backBmp: Bitmap? = null
    private val frontBmps = mutableMapOf<String, Bitmap>()

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        updateLevelHud(MiniGameManager.GAME_SCOPA)
        livesText.text = "🃏 Scopa"
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
        startNewHand()
    }

    private fun startNewHand() {
        clearCardNodes()
        val deck = CardDeck.italianDeck().toMutableList()
        CardDeck.shuffle(deck)

        playerHand.clear()
        cpuHand.clear()
        tableCards.clear()
        selectedCard = null
        gamePhase = "play"

        for (i in 0 until 3) {
            playerHand.add(deck.removeAt(deck.size - 1))
            cpuHand.add(deck.removeAt(deck.size - 1))
        }
        for (i in 0 until 4) {
            tableCards.add(deck.removeAt(deck.size - 1))
        }

        statusText.text = "Cattura carte sommando 15! Tocca una carta."
        updateHud()
        layoutCards()
    }

    private fun clearCardNodes() {
        cardNodes.forEach { it.destroy() }
        cardEggs.forEach { removeEgg(it) }
        cardNodes.clear()
        cardEggs.clear()
    }

    private fun layoutCards() {
        val a = arenaNode ?: return
        val yaw = yawToFaceCamera(a)
        val yawCos = cos(yaw)
        val yawSin = sin(yaw)
        val rotOff: (Float, Float) -> Pair<Float, Float> = { x, z ->
            (x * yawCos + z * yawSin) to (-x * yawSin + z * yawCos)
        }

        clearCardNodes()

        // Table cards in center
        val tableY = 0.35f
        for (i in tableCards.indices) {
            val x = -(tableCards.size * (CARD_W + GAP * 0.5f)) / 2f + i * (CARD_W + GAP * 0.5f)
            val (rx, rz) = rotOff(x, tableY)
            val pose = Pose(floatArrayOf(rx, 0.15f, rz), floatArrayOf(0f, 0f, 0f, 1f))
            val anchor = spawnAnchorAt(pose) ?: continue
            val card = tableCards[i]
            val bmp = frontBmps["${card.rank.label}${card.suit.emoji}"] ?: backBmp!!
            val node = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = bmp,
                size = Size(CARD_W, CARD_H),
                normal = Direction(0f, 0f, 1f)
            )
            anchor.addChildNode(node)
            val egg = AREgg(anchor, node, 50 + i, phase = i.toFloat())
            registerEgg(egg)
            cardNodes.add(node)
            cardEggs.add(egg)
        }

        // CPU hand (face down) at top
        val cpuY = 0.55f
        for (i in cpuHand.indices) {
            val x = -(cpuHand.size * (CARD_W + GAP * 0.5f)) / 2f + i * (CARD_W + GAP * 0.5f)
            val (rx, rz) = rotOff(x, cpuY)
            val pose = Pose(floatArrayOf(rx, 0.15f, rz), floatArrayOf(0f, 0f, 0f, 1f))
            val anchor = spawnAnchorAt(pose) ?: continue
            val node = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = backBmp!!,
                size = Size(CARD_W, CARD_H),
                normal = Direction(0f, 0f, 1f)
            )
            anchor.addChildNode(node)
            val egg = AREgg(anchor, node, 60 + i, phase = i.toFloat())
            registerEgg(egg)
            cardNodes.add(node)
            cardEggs.add(egg)
        }

        // Player hand at bottom
        val playerY = 0.12f
        for (i in playerHand.indices) {
            val x = -(playerHand.size * (CARD_W + GAP * 0.5f)) / 2f + i * (CARD_W + GAP * 0.5f)
            val (rx, rz) = rotOff(x, playerY)
            val pose = Pose(floatArrayOf(rx, 0.15f, rz), floatArrayOf(0f, 0f, 0f, 1f))
            val anchor = spawnAnchorAt(pose) ?: continue
            val card = playerHand[i]
            val bmp = frontBmps["${card.rank.label}${card.suit.emoji}"] ?: backBmp!!
            val node = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = bmp,
                size = Size(CARD_W, CARD_H),
                normal = Direction(0f, 0f, 1f)
            )
            anchor.addChildNode(node)
            val egg = AREgg(anchor, node, 70 + i, phase = i.toFloat())
            registerEgg(egg)
            cardNodes.add(node)
            cardEggs.add(egg)
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || gameOver) return
        val idx = egg.phase.toInt()

        when {
            idx >= 70 -> handlePlayerHandTap(idx - 70)
            idx >= 50 && idx < 60 -> handleTableCardTap(idx - 50)
            idx >= 60 -> { /* CPU hand face down, ignore */ }
        }
    }

    private fun handlePlayerHandTap(handIdx: Int) {
        if (handIdx !in playerHand.indices) return
        val card = playerHand[handIdx]
        selectedCard = card
        statusText.text = "Selezionata: ${card.rank.label}${card.suit.emoji}"
        layoutCards()
    }

    private fun handleTableCardTap(tableIdx: Int) {
        if (tableIdx !in tableCards.indices) return
        if (selectedCard == null) return
        tryCapture(selectedCard!!, tableIdx)
    }

    private fun tryCapture(card: Card, tableIdx: Int) {
        val cardValue = cardValue(card)
        val target = 15 - cardValue

        // Check if the tapped table card alone matches
        if (cardValue(tableCards[tableIdx]) == target) {
            performCapture(card, listOf(tableCards[tableIdx]))
            return
        }

        // Check pairs
        for (i in tableCards.indices) {
            for (j in i + 1 until tableCards.size) {
                if (cardValue(tableCards[i]) + cardValue(tableCards[j]) == target) {
                    performCapture(card, listOf(tableCards[i], tableCards[j]))
                    return
                }
            }
        }

        // Check triplets
        for (i in tableCards.indices) {
            for (j in i + 1 until tableCards.size) {
                for (k in j + 1 until tableCards.size) {
                    if (cardValue(tableCards[i]) + cardValue(tableCards[j]) + cardValue(tableCards[k]) == target) {
                        performCapture(card, listOf(tableCards[i], tableCards[j], tableCards[k]))
                        return
                    }
                }
            }
        }

        // No capture: place card on table
        tableCards.add(card)
        playerHand.remove(card)
        selectedCard = null
        statusText.text = "Nessuna cattura — carta sul tavolo"
        updateHud()
        layoutCards()
        if (playerHand.isEmpty()) {
            endHand()
        } else {
            postDelayed(500) { drawNewCards() }
        }
    }

    private fun performCapture(card: Card, captureSet: List<Card>) {
        playerCaptured.add(card)
        playerCaptured.addAll(captureSet)
        playerHand.remove(card)
        tableCards.removeAll(captureSet)

        if (tableCards.isEmpty()) {
            playerScore += 1
            statusText.text = "Scopa! +1 punto"
        } else {
            statusText.text = "Catturato ${captureSet.size + 1} carte!"
        }

        selectedCard = null
        updateHud()
        layoutCards()

        if (playerHand.isEmpty()) {
            endHand()
        } else {
            postDelayed(500) { drawNewCards() }
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
        layoutCards()
    }

    private fun endHand() {
        handNum++

        if (tableCards.isNotEmpty()) {
            cpuCaptured.addAll(tableCards)
            cpuScore += 1
            tableCards.clear()
        }

        val deck = CardDeck.italianDeck().toMutableList()
        CardDeck.shuffle(deck)
        while (cpuHand.size < 3 && deck.isNotEmpty()) {
            cpuHand.add(deck.removeAt(deck.size - 1))
        }

        statusText.text = "Mano ${handNum} finita!"
        layoutCards()

        postDelayed(1500) {
            if (!running || gameOver) return@postDelayed
            if (handNum >= 3 || cpuHand.isEmpty()) {
                endGame(countFinalScore())
            } else {
                statusText.text = "Mano ${handNum + 1}/3 — Tocca una carta!"
                layoutCards()
            }
        }
    }

    private fun countFinalScore(): Boolean {
        val playerDenari = playerCaptured.count { it.suit == Suit.DIAMONDS }
        val cpuDenari = cpuCaptured.count { it.suit == Suit.DIAMONDS }
        val playerSettebello = playerCaptured.any { it.suit == Suit.DIAMONDS && it.rank == Rank.SEVEN }
        val cpuSettebello = cpuCaptured.any { it.suit == Suit.DIAMONDS && it.rank == Rank.SEVEN }

        var pScore = 0
        var cScore = 0

        if (playerCaptured.size > cpuCaptured.size) pScore += 1
        else if (cpuCaptured.size > playerCaptured.size) cScore += 1

        if (playerDenari > cpuDenari) pScore += 1
        else if (cpuDenari > playerDenari) cScore += 1

        if (playerSettebello) pScore += 1
        if (cpuSettebello) cScore += 1

        playerScore = pScore
        cpuScore = cScore
        return pScore > cScore
    }

    private fun endGame(won: Boolean) {
        gameOver = true
        postDelayed(500) {
            finishGame(
                if (won) 80 else 15,
                "AR Scopa — ${if (won) "Vittoria!" else "Sconfitta"} ($playerScore-$cpuScore)",
                won, MiniGameManager.GAME_SCOPA, score = playerScore
            )
        }
    }

    private fun updateHud() {
        livesText.text = "🃏 Scopa"
        scoreText.text = playerScore.toString()
        timerText.text = "Tu: $playerScore | CPU: $cpuScore"
    }

    private fun cardValue(card: Card): Int = when (card.rank) {
        Rank.ACE -> 1
        Rank.JACK -> 8
        Rank.QUEEN -> 9
        Rank.KING -> 10
        else -> card.rank.value
    }

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