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
 * 🃏 AR Briscola — gioco di carte italiano in Realtà Aumentata.
 *
 * Le carte sono posizionate come ImageNode su una superficie reale.
 * Il giocatore seleziona il seme di Briscola toccando le carte
 * in alto, poi gioca le carte dalla mano toccandole.
 * La CPU gioca automaticamente dopo un ritardo.
 */
class ARBriscolaActivity : ARGameActivity() {

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
    private var gamePhase = "choose"
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
        updateLevelHud(MiniGameManager.GAME_BRISCOLA)
        livesText.text = "🃏 Briscola"
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
        startNewGame()
    }

    private fun startNewGame() {
        clearCardNodes()
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
        updateHud()
        layoutChoosePhase()
    }

    private fun clearCardNodes() {
        cardNodes.forEach { it.destroy() }
        cardEggs.forEach { removeEgg(it) }
        cardNodes.clear()
        cardEggs.clear()
    }

    private fun layoutChoosePhase() {
        val a = arenaNode ?: return
        val yaw = yawToFaceCamera(a)
        val yawCos = cos(yaw)
        val yawSin = sin(yaw)
        val rotOff: (Float, Float) -> Pair<Float, Float> = { x, z ->
            (x * yawCos + z * yawSin) to (-x * yawSin + z * yawCos)
        }

        val centerY = 0.35f
        val startX = -(3 * CARD_W + 3 * GAP) / 2f

        for (i in suits.indices) {
            val x = startX + i * (CARD_W + GAP)
            val (rx, rz) = rotOff(x, centerY)
            val pose = Pose(
                floatArrayOf(rx, 0.15f, rz),
                floatArrayOf(0f, 0f, 0f, 1f)
            )
            val anchor = spawnAnchorAt(pose) ?: continue
            val node = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = frontBmps["${Rank.ACE.label}${suits[i].emoji}"] ?: backBmp!!,
                size = Size(CARD_W, CARD_H),
                normal = Direction(0f, 0f, 1f)
            )
            anchor.addChildNode(node)
            val egg = AREgg(anchor, node, 10 + i, phase = i.toFloat())
            registerEgg(egg)
            cardNodes.add(node)
            cardEggs.add(egg)
        }

        if (briscolaCard != null) {
            statusText.text = "Briscola: ${briscolaSuit?.emoji} — Scegli!"
        }
    }

    private fun layoutPlayPhase() {
        val a = arenaNode ?: return
        val yaw = yawToFaceCamera(a)
        val yawCos = cos(yaw)
        val yawSin = sin(yaw)
        val rotOff: (Float, Float) -> Pair<Float, Float> = { x, z ->
            (x * yawCos + z * yawSin) to (-x * yawSin + z * yawCos)
        }

        clearCardNodes()

        // CPU hand (face down) at top
        val cpuY = 0.5f
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
            val egg = AREgg(anchor, node, 20 + i, phase = i.toFloat())
            registerEgg(egg)
            cardNodes.add(node)
            cardEggs.add(egg)
        }

        // Current trick in middle
        val trickY = 0.35f
        for (i in currentTrick.indices) {
            val x = -(currentTrick.size * (CARD_W + GAP)) / 2f + i * (CARD_W + GAP)
            val (rx, rz) = rotOff(x, trickY)
            val pose = Pose(floatArrayOf(rx, 0.15f, rz), floatArrayOf(0f, 0f, 0f, 1f))
            val anchor = spawnAnchorAt(pose) ?: continue
            val card = currentTrick[i]
            val bmp = frontBmps["${card.rank.label}${card.suit.emoji}"] ?: backBmp!!
            val node = ImageNode(
                materialLoader = sceneView.materialLoader,
                bitmap = bmp,
                size = Size(CARD_W, CARD_H),
                normal = Direction(0f, 0f, 1f)
            )
            anchor.addChildNode(node)
            val egg = AREgg(anchor, node, 30 + i, phase = i.toFloat())
            registerEgg(egg)
            cardNodes.add(node)
            cardEggs.add(egg)
        }

        // Player hand at bottom
        val playerY = 0.15f
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
            val egg = AREgg(anchor, node, 40 + i, phase = i.toFloat())
            registerEgg(egg)
            cardNodes.add(node)
            cardEggs.add(egg)
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || gameOver) return
        val idx = egg.phase.toInt()

        when (gamePhase) {
            "choose" -> {
                if (idx in 0..3) {
                    briscolaSuit = suits[idx]
                    statusText.text = "Briscola: ${suits[idx].emoji} — Gioca!"
                    gamePhase = "play"
                    layoutPlayPhase()
                }
            }
            "play" -> {
                if (idx >= 40) {
                    val handIdx = idx - 40
                    if (handIdx in playerHand.indices) {
                        selectedCard = playerHand[handIdx]
                        statusText.text = "Selezionata: ${selectedCard!!.rank.label}${selectedCard!!.suit.emoji}"
                        layoutPlayPhase()
                    }
                }
                if (selectedCard != null) {
                    playCard(selectedCard!!)
                }
            }
        }
    }

    private fun playCard(card: Card) {
        playerHand.remove(card)
        currentTrick.add(card)
        playerTrick.add(card)
        gamePhase = "show"
        statusText.text = "Hai giocato ${card.rank.label}${card.suit.emoji}"
        selectedCard = null
        layoutPlayPhase()

        postDelayed(800) {
            if (!running || gameOver) return@postDelayed
            cpuPlay()
        }
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
        layoutPlayPhase()

        postDelayed(1000) {
            if (!running || gameOver) return@postDelayed
            resolveTrick()
        }
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
        updateHud()
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
        layoutPlayPhase()
    }

    private fun endGame(won: Boolean) {
        gameOver = true
        postDelayed(500) {
            finishGame(
                if (won) 80 else 15,
                "AR Briscola — ${if (won) "Vittoria!" else "Sconfitta"} ($playerScore-$cpuScore)",
                won, MiniGameManager.GAME_BRISCOLA, score = playerScore
            )
        }
    }

    private fun updateHud() {
        livesText.text = "🃏 Briscola"
        scoreText.text = playerScore.toString()
        timerText.text = "Tu: $playerScore | CPU: $cpuScore"
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