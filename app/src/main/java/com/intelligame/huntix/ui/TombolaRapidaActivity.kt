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
import com.intelligame.huntix.gamification.DailyEventRegistry
import com.intelligame.huntix.managers.SavedManager

/**
 * TombolaRapidaActivity — Tombola 3x3, numeri 1-30.
 *
 * Estrazione automatica ogni2 secondi.
 * Obiettivo: completa righe per vincere MVC.
 * 3 righe = TOMBOLA = jackpot.
 *
 * Ricompense per riga completata:
 * - 1 riga: 50 MVC
 * - 2 righe: 150 MVC
 * - 3 righe (TOMBOLA): 300 MVC + bonus
 */
class TombolaRapidaActivity : AppCompatActivity() {

    private var cardNumbers = Array(3) { IntArray(3) }
    private var cardMarked = Array(3) { BooleanArray(3) }
    private var extractedNumbers = mutableListOf<Int>()
    private var totalMVC = 0
    private var rowsCompleted = 0
    private var gameRunning = false

    private lateinit var statusText: TextView
    private lateinit var lastNumberText: TextView
    private lateinit var mvcText: TextView
    private lateinit var cardContainer: LinearLayout
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
            text = "\uD83C\uDFB2 Tombola Rapida"
            textSize = 20f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })

        root.addView(TextView(this).apply {
            text = "Cartella 3x3 \u2014 Numeri 1-30"
            textSize = 12f; setTextColor(Color.parseColor("#6B5B95"))
            setPadding(0, 0, 0, dp(8))
        })

        statusText = TextView(this).apply {
            text = "Tocca 'Gioca' per iniziare!"
            textSize = 14f; setTextColor(Color.parseColor("#FF6F00"))
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(statusText)

        mvcText = TextView(this).apply {
            text = "\uD83D\uDCB0 0 MVC"
            textSize = 14f; setTextColor(Color.parseColor("#00FF88"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(mvcText)

        val playBtn = LinearLayout(this).apply {
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
        playBtn.addView(TextView(this).apply {
            text = "\uD83C\uDFB2 Gioca"
            textSize = 14f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        root.addView(playBtn)

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
        val available = (1..30).toMutableList()
        available.shuffle()
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                cardNumbers[row][col] = available[row * 3 + col]
            }
        }
    }

    private fun renderCard() {
        cardContainer.removeAllViews()

        for (row in 0 until 3) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            for (col in 0 until 3) {
                val num = cardNumbers[row][col]
                val isMarked = cardMarked[row][col]
                val wasExtracted = num in extractedNumbers

                val cell = CardView(this).apply {
                    radius = dp(10).toFloat()
                    cardElevation = dp(2).toFloat()
                    setCardBackgroundColor(Color.parseColor(
                        if (isMarked) "#2A1A00" else "#1A1030"
                    ))
                    layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).also {
                        it.marginEnd = dp(4)
                    }
                    isClickable = wasExtracted && !isMarked && gameRunning
                    isFocusable = true
                    if (isClickable) {
                        setOnClickListener { markNumber(row, col) }
                    }
                }

                cell.foreground = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(if (isMarked) 2 else 1), Color.parseColor(
                        if (isMarked) "#FF6F00" else if (wasExtracted) "#FFD700" else "#332244"
                    ))
                }

                val cellInner = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                }

                if (isMarked) {
                    cellInner.addView(TextView(this).apply {
                        text = "\u2714"; textSize = 22f; gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#FF6F00"))
                    })
                } else {
                    cellInner.addView(TextView(this).apply {
                        text = "$num"; textSize = 16f; gravity = Gravity.CENTER
                        setTextColor(
                            if (wasExtracted) Color.parseColor("#FFD700") else Color.WHITE
                        )
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
        renderCard()
        checkPrizes()
    }

    private fun startGame() {
        if (gameRunning) return
        gameRunning = true
        extractedNumbers.clear()
        cardMarked = Array(3) { BooleanArray(3) }
        totalMVC = 0
        rowsCompleted = 0

        prizeLog.removeAllViews()
        statusText.text = "Estrazione in corso..."
        mvcText.text = "\uD83D\uDCB0 0 MVC"
        renderCard()

        extractNext()
    }

    private fun extractNext() {
        if (!gameRunning) return

        val available = (1..30).filter { it !in extractedNumbers }
        if (available.isEmpty()) {
            stopGame()
            statusText.text = "Fine! Hai guadagnato $totalMVC MVC"
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
        var newPrizes = 0

        for (row in 0 until 3) {
            val allMarked = (0 until 3).all { cardMarked[row][it] }
            if (allMarked) {
                val reward = when {
                    rowsCompleted == 0 -> 50
                    rowsCompleted == 1 -> 150
                    else -> 300
                }
                totalMVC += reward
                newPrizes += reward
                rowsCompleted++

                runOnUiThread {
                    val label = when (rowsCompleted) {
                        1 -> "Prima riga! +50 MVC"
                        2 -> "Seconda riga! +150 MVC"
                        else -> "TOMBOLA! +300 MVC"
                    }
                    prizeLog.addView(TextView(this).apply {
                        text = "\uD83C\uDF86 $label"
                        textSize = 13f; setTextColor(Color.parseColor("#00FF88"))
                        setPadding(0, dp(4), 0, 0)
                    })
                    mvcText.text = "\uD83D\uDCB0 $totalMVC MVC"
                }

                if (rowsCompleted >= 3) {
                    stopGame()
                    runOnUiThread {
                        statusText.text = "TOMBOLA! Hai vinto $totalMVC MVC!"
                    }
                    // Save MVC
                    try {
                        SavedManager.addMvc(this, totalMVC.toDouble())
                    } catch (_: Exception) {}
                    return
                }
            }
        }

        if (newPrizes > 0) {
            runOnUiThread {
                statusText.text = "Righe completate: $rowsCompleted/3"
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
