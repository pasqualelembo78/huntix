package com.intelligame.huntix.ui

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.intelligame.huntix.managers.SavedManager

/**
 * BriscolaActivity — briscola semplificata vs CPU.
 *
 * 3 round: il giocatore e la CPU giocano 3 carte ciascuna.
 * Vince chi ha piu' punti alla fine.
 *
 * Punti carte:
 *  Asso = 11, Tre = 10, Re = 4, Cavallo = 3, Fante = 2, altre = 0
 *
 * Regola briscola: il seme della carta girata e' il seme di briscola (trump).
 * Una carta di briscola batte qualsiasi carta di altro seme.
 */
class BriscolaActivity : AppCompatActivity() {

    private data class Card(val suit: String, val value: String, val points: Int, val rank: Int) {
        val displayName: String get() = "$value $suit"
        val suitSymbol: String get() = when (suit) {
            "D" -> "\uD83D\uDCB0" // Denari
            "C" -> "\uD83C\uDFC6" // Coppe
            "S" -> "\u2694\uFE0F" // Spade
            "B" -> "\uD83E\uDE93" // Bastoni
            else -> ""
        }
    }

    private val allCards = mutableListOf<Card>()
    private val playerHand = mutableListOf<Card>()
    private val cpuHand = mutableListOf<Card>()
    private var briscolaSuit = ""
    private var roundNumber = 0
    private var playerPoints = 0
    private var cpuPoints = 0
    private var gameRunning = false

    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var briscolaText: TextView
    private lateinit var playerHandContainer: LinearLayout
    private lateinit var tableContainer: LinearLayout
    private lateinit var prizeLog: LinearLayout

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0620"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(80))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "\u2190 Indietro"; textSize = 14f
            setTextColor(Color.parseColor("#666699"))
            setOnClickListener { stopGame(); finish() }
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "\uD83C\uDCDC Briscola al Buio"
            textSize = 20f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })

        root.addView(TextView(this).apply {
            text = "Sfida la CPU a briscola!"
            textSize = 12f; setTextColor(Color.parseColor("#6B5B95"))
            setPadding(0, 0, 0, dp(8))
        })

        statusText = TextView(this).apply {
            text = "Tocca 'Gioca' per iniziare!"
            textSize = 14f; setTextColor(Color.parseColor("#4CAF50"))
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(statusText)

        scoreText = TextView(this).apply {
            text = "Tu: 0  |  CPU: 0  |  Round: 0/3"
            textSize = 13f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(scoreText)

        val playBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#4CAF50"))
            }
            setPadding(dp(24), dp(14), dp(24), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            isClickable = true; isFocusable = true
            setOnClickListener { startGame() }
        }
        playBtn.addView(TextView(this).apply {
            text = "\uD83C\uDCC3 Gioca"
            textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        root.addView(playBtn)

        val briscolaCard = CardView(this).apply {
            radius = dp(12).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1030"))
            cardElevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        val briscolaInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        briscolaText = TextView(this).apply {
            text = "Briscola: ?"
            textSize = 14f; setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        briscolaInner.addView(briscolaText)
        briscolaCard.addView(briscolaInner)
        root.addView(briscolaCard)

        root.addView(TextView(this).apply {
            text = "Tavolo"
            textSize = 12f; setTextColor(Color.parseColor("#6B5B95"))
            setPadding(0, dp(8), 0, dp(4))
        })

        tableContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        root.addView(tableContainer)

        root.addView(TextView(this).apply {
            text = "Le tue carte (tocca per giocare)"
            textSize = 12f; setTextColor(Color.parseColor("#6B5B95"))
            setPadding(0, 0, 0, dp(4))
        })

        playerHandContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(playerHandContainer)

        prizeLog = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(prizeLog)

        setContentView(scroll)
    }

    private fun initDeck() {
        allCards.clear()
        val suits = listOf("D", "C", "S", "B")
        val values = listOf(
            Triple("A", 11, 14), Triple("3", 10, 13), Triple("R", 4, 12),
            Triple("C", 3, 11), Triple("F", 2, 10),
            Triple("7", 0, 7), Triple("6", 0, 6), Triple("5", 0, 5),
            Triple("4", 0, 4), Triple("2", 0, 2)
        )
        for (suit in suits) {
            for ((value, points, rank) in values) {
                allCards.add(Card(suit, value, points, rank))
            }
        }
        allCards.shuffle()
    }

    private fun startGame() {
        if (gameRunning) return
        gameRunning = true
        roundNumber = 0
        playerPoints = 0
        cpuPoints = 0

        prizeLog.removeAllViews()
        initDeck()

        playerHand.clear()
        cpuHand.clear()
        for (i in 0 until 3) {
            playerHand.add(allCards.removeAt(0))
            cpuHand.add(allCards.removeAt(0))
        }

        briscolaSuit = allCards.removeAt(0).suit
        briscolaText.text = "Briscola: ${briscolaSymbol(briscolaSuit)}"

        statusText.text = "Tocca una carta per giocare!"
        updateScore()
        renderTable()
        renderPlayerHand()
    }

    private fun renderPlayerHand() {
        playerHandContainer.removeAllViews()
        for ((index, card) in playerHand.withIndex()) {
            val cardView = createCardView(card, clickable = gameRunning)
            if (gameRunning) {
                cardView.setOnClickListener { playCard(index) }
            }
            playerHandContainer.addView(cardView)
        }
    }

    private fun renderTable(playerCard: Card? = null, cpuCard: Card? = null) {
        tableContainer.removeAllViews()

        if (playerCard != null) {
            tableContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LP_WW, 1f)
                addView(createCardView(playerCard))
                addView(TextView(this@BriscolaActivity).apply {
                    text = "Tu"; textSize = 11f
                    setTextColor(Color.parseColor("#4CAF50"))
                    gravity = Gravity.CENTER
                })
            })
        }

        if (cpuCard != null) {
            tableContainer.addView(TextView(this).apply {
                text = "VS"; textSize = 16f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
            })
            tableContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LP_WW, 1f)
                addView(createCardView(cpuCard))
                addView(TextView(this@BriscolaActivity).apply {
                    text = "CPU"; textSize = 11f
                    setTextColor(Color.parseColor("#FF6F00"))
                    gravity = Gravity.CENTER
                })
            })
        }
    }

    private fun createCardView(card: Card, clickable: Boolean = false): CardView {
        val cardView = CardView(this).apply {
            radius = dp(8).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1030"))
            cardElevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(96)).also {
                it.marginEnd = dp(6)
            }
            isClickable = clickable
            isFocusable = true
        }

        cardView.foreground = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor("#332244"))
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        inner.addView(TextView(this).apply {
            text = card.suitSymbol; textSize = 22f; gravity = Gravity.CENTER
        })
        inner.addView(TextView(this).apply {
            text = card.value; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        inner.addView(TextView(this).apply {
            text = "${card.points} pt"; textSize = 9f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6B5B95"))
        })

        cardView.addView(inner)
        return cardView
    }

    private fun playCard(playerIndex: Int) {
        if (!gameRunning || playerIndex >= playerHand.size) return
        gameRunning = false

        val playerCard = playerHand.removeAt(playerIndex)
        val cpuCard = cpuPlay()

        renderTable(playerCard, cpuCard)
        renderPlayerHand()

        val (winner, points) = evaluateTrick(playerCard, cpuCard)
        if (winner == "player") playerPoints += points else cpuPoints += points
        roundNumber++

        updateScore()

        prizeLog.addView(TextView(this).apply {
            val label = if (winner == "player") "Tu vinci il round! +$points pt" else "CPU vince il round! +$points pt"
            text = "\uD83C\uDFC6 Round $roundNumber: $label"
            textSize = 12f
            setTextColor(if (winner == "player") Color.parseColor("#00FF88") else Color.parseColor("#FF6F00"))
            setPadding(0, dp(4), 0, 0)
        })

        handler.postDelayed({
            if (roundNumber >= 3) {
                endGame()
            } else {
                gameRunning = true
                statusText.text = "Tocca una carta per giocare!"
                renderTable()
                renderPlayerHand()
            }
        }, 1500)
    }

    private fun cpuPlay(): Card {
        if (cpuHand.isEmpty()) return Card("D", "?", 0, 0)

        val playable = cpuHand.filter { it.suit == briscolaSuit }
        val nonBriscola = cpuHand.filter { it.suit != briscolaSuit }

        return when {
            playable.isNotEmpty() -> playable.maxByOrNull { it.rank } ?: cpuHand[0]
            nonBriscola.isNotEmpty() -> nonBriscola.minByOrNull { it.points } ?: cpuHand[0]
            else -> cpuHand[0]
        }.also { cpuHand.remove(it) }
    }

    private fun evaluateTrick(player: Card, cpu: Card): Pair<String, Int> {
        val playerIsBriscola = player.suit == briscolaSuit
        val cpuIsBriscola = cpu.suit == briscolaSuit

        val totalPoints = player.points + cpu.points

        return when {
            playerIsBriscola && !cpuIsBriscola -> Pair("player", totalPoints)
            !playerIsBriscola && cpuIsBriscola -> Pair("cpu", totalPoints)
            playerIsBriscola && cpuIsBriscola -> {
                if (player.rank > cpu.rank) Pair("player", totalPoints)
                else Pair("cpu", totalPoints)
            }
            else -> {
                if (player.rank > cpu.rank) Pair("player", totalPoints)
                else Pair("cpu", totalPoints)
            }
        }
    }

    private fun endGame() {
        gameRunning = false
        val winner = if (playerPoints > cpuPoints) "player" else if (cpuPoints > playerPoints) "cpu" else "draw"
        val mvcReward = when (winner) {
            "player" -> 200
            "draw" -> 50
            else -> 0
        }

        statusText.text = when (winner) {
            "player" -> "Hai vinto! $playerPoints vs $cpuPoints"
            "draw" -> "Pareggio! $playerPoints vs $cpuPoints"
            else -> "CPU vince! $cpuPoints vs $playerPoints"
        }

        if (mvcReward > 0) {
            try { SavedManager.addMvc(this, mvcReward.toDouble()) } catch (_: Exception) {}
        }

        prizeLog.addView(TextView(this).apply {
            text = when (winner) {
                "player" -> "\uD83C\uDF89 Vittoria! +$mvcReward MVC"
                "draw" -> "\uD83E\uDD1D Pareggio! +$mvcReward MVC"
                else -> "\uD83D\uDE14 Sconfitta. Riprova!"
            }
            textSize = 14f
            setTextColor(if (winner == "player") Color.parseColor("#FFD700") else Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun updateScore() {
        scoreText.text = "Tu: $playerPoints  |  CPU: $cpuPoints  |  Round: $roundNumber/3"
    }

    private fun stopGame() {
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGame()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun briscolaSymbol(suit: String): String = when (suit) {
        "D" -> "\uD83D\uDCB0 Denari"
        "C" -> "\uD83C\uDFC6 Coppe"
        "S" -> "\u2694\uFE0F Spade"
        "B" -> "\uD83E\uDE93 Bastoni"
        else -> suit
    }

    companion object {
        private const val LP_WW = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
