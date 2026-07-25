package com.intelligame.huntix.ui

import android.animation.ObjectAnimator
import android.app.AlertDialog
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
import com.intelligame.huntix.gamification.TombolataManager

/**
 * TombolataActivity — Tombola italiana 5x5 (numeri 1-75).
 *
 * I numeri vengono estratti ogni2 secondi.
 * Il giocatore tocca i numeri sulla cartella per marcarli.
 *
 * Premi:
 * - Ambo (2 in fila): 100 MVC
 * - Terno (3 in fila): 200 MVC
 * - Quaterna (4 in fila): 350 MVC
 * - Cinquina (riga completa): 500 MVC
 * - Tombola (cartella completa): 1000 MVC
 */
class TombolataActivity : AppCompatActivity() {

    private var tombolataDay: Int = 27
    private var extractedNumbers = mutableListOf<Int>()
    private var markedNumbers = mutableSetOf<Int>()
    private var cardNumbers = Array(5) { IntArray(5) }
    private var cardMarked = Array(5) { BooleanArray(5) }

    private lateinit var statusText: TextView
    private lateinit var instructionText: TextView
    private lateinit var lastNumberText: TextView
    private lateinit var cardContainer: LinearLayout
    private lateinit var prizeLog: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var gameRunning = false
    private var tombola = false
    private var cinquina = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tombolataDay = intent.getIntExtra("day", 27).coerceIn(27, 31)

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
            text = "\uD83C\uDF86 Tombolata \u2014 Giorno $tombolataDay"
            textSize = 20f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })

        statusText = TextView(this).apply {
            text = "Tocca 'Inizia' per giocare!"
            textSize = 14f; setTextColor(Color.parseColor("#A78BFA"))
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(statusText)

        val startBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#FF6F00"))
            }
            setPadding(dp(24), dp(14), dp(24), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            isClickable = true; isFocusable = true
            setOnClickListener { startGame() }
        }
        startBtn.addView(TextView(this).apply {
            text = "\uD83C\uDFB2 Inizia Gioco"
            textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        root.addView(startBtn)

        val lastNumCard = CardView(this).apply {
            radius = dp(12).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1030"))
            cardElevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        val lastNumInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        lastNumInner.addView(TextView(this).apply {
            text = "Ultimo Numero"
            textSize = 11f; setTextColor(Color.parseColor("#6B5B95"))
            gravity = Gravity.CENTER
        })
        lastNumberText = TextView(this).apply {
            text = "-"
            textSize = 36f; setTextColor(Color.parseColor("#FF6F00"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        lastNumInner.addView(lastNumberText)
        lastNumCard.addView(lastNumInner)
        root.addView(lastNumCard)

        root.addView(TextView(this).apply {
            text = "La Tua Cartella"
            textSize = 13f; setTextColor(Color.parseColor("#6B5B95"))
            setPadding(0, 0, 0, dp(6))
        })

        cardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(cardContainer)

        prizeLog = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(prizeLog)

        generateCard()
        renderCard()
        setContentView(scroll)
    }

    private fun generateCard() {
        val rng = java.util.Random()
        for (col in 0 until 5) {
            val min = col * 15 + 1
            val max = min + 14
            val available = (min..max).toMutableList()
            available.shuffle(rng)
            for (row in 0 until 5) {
                cardNumbers[row][col] = available[row]
            }
        }
    }

    private fun renderCard() {
        cardContainer.removeAllViews()

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        val colHeaders = listOf("B", "I", "N", "G", "O")
        for (col in 0 until 5) {
            headerRow.addView(TextView(this).apply {
                text = colHeaders[col]
                textSize = 12f
                setTextColor(Color.parseColor("#FF6F00"))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f)
            })
        }
        cardContainer.addView(headerRow)

        for (row in 0 until 5) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            for (col in 0 until 5) {
                val num = cardNumbers[row][col]
                val isMarked = cardMarked[row][col]
                val wasExtracted = num in extractedNumbers

                val cellBg = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor(if (isMarked) "#2A1A00" else "#1A1030"))
                    setStroke(dp(1), Color.parseColor(
                        if (isMarked) "#FF6F00" else "#332244"
                    ))
                }

                val cell = CardView(this).apply {
                    radius = dp(8).toFloat()
                    cardElevation = dp(1).toFloat()
                    setCardBackgroundColor(Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).also {
                        it.marginEnd = dp(4)
                    }
                    isClickable = wasExtracted && !isMarked && gameRunning
                    isFocusable = true
                    if (isClickable) {
                        setOnClickListener { markNumber(row, col) }
                    }
                }

                cell.foreground = cellBg

                val cellInner = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                }

                if (isMarked) {
                    cellInner.addView(TextView(this).apply {
                        text = "\u2714"
                        textSize = 20f; gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#FF6F00"))
                    })
                } else {
                    cellInner.addView(TextView(this).apply {
                        text = "$num"
                        textSize = 14f; gravity = Gravity.CENTER
                        setTextColor(if (wasExtracted) Color.parseColor("#FFD700") else Color.WHITE)
                        typeface = if (wasExtracted)
                            Typeface.create("sans-serif-medium", Typeface.BOLD) else null
                    })
                }

                cell.addView(cellInner)
                rowLayout.addView(cell)
            }
            cardContainer.addView(rowLayout)
        }
    }

    private fun markNumber(row: Int, col: Int) {
        val num = cardNumbers[row][col]
        if (num !in extractedNumbers || cardMarked[row][col]) return

        cardMarked[row][col] = true
        markedNumbers.add(num)
        renderCard()
        checkPrizes()
    }

    private fun startGame() {
        if (gameRunning) return
        gameRunning = true
        extractedNumbers.clear()
        markedNumbers.clear()
        cardMarked = Array(5) { BooleanArray(5) }
        tombola = false
        cinquina = false

        prizeLog.removeAllViews()
        statusText.text = "Estrazione in corso... tocca i numeri sulla cartella!"
        renderCard()

        extractNext()
    }

    private fun extractNext() {
        if (!gameRunning || tombola) return

        val available = (1..75).filter { it !in extractedNumbers }
        if (available.isEmpty()) {
            stopGame()
            statusText.text = "Tutti i numeri estratti!"
            return
        }

        val next = available.random()
        extractedNumbers.add(next)

        runOnUiThread {
            lastNumberText.text = "$next"
            renderCard()
        }

        handler.postDelayed({ extractNext() }, 2000)
    }

    private fun checkPrizes() {
        var totalPrize = 0
        val prizes = mutableListOf<String>()

        for (row in 0 until 5) {
            val markedInRow = (0 until 5).count { cardMarked[row][it] }
            when (markedInRow) {
                2 -> { totalPrize += TombolataManager.rewardForAmbo(); prizes.add("Ambo Riga ${row + 1}") }
                3 -> { totalPrize += TombolataManager.rewardForTerno(); prizes.add("Terno Riga ${row + 1}") }
                4 -> { totalPrize += TombolataManager.rewardForQuaterna(); prizes.add("Quaterna Riga ${row + 1}") }
                5 -> {
                    if (!cinquina) {
                        cinquina = true
                        totalPrize += TombolataManager.rewardForCinquina()
                        prizes.add("Cinquina! Riga ${row + 1}")
                    }
                }
            }
        }

        val allMarked = cardMarked.all { row -> row.all { it } }
        if (allMarked && !tombola) {
            tombola = true
            totalPrize += TombolataManager.rewardForTombola()
            prizes.add("TOMBOLA!")
            stopGame()
        }

        if (prizes.isNotEmpty()) {
            runOnUiThread {
                for (prize in prizes) {
                    val prizeView = TextView(this).apply {
                        text = "\uD83C\uDF86 $prize +${totalPrize} MVC"
                        textSize = 13f; setTextColor(Color.parseColor("#00FF88"))
                        setPadding(0, dp(4), 0, 0)
                    }
                    prizeLog.addView(prizeView)
                }
                statusText.text = if (tombola) "TOMBOLA! Hai vinto!" else "Premi vinti! Continua a giocare!"
            }
        }
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
}
