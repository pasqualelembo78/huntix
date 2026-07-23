package com.intelligame.huntix.ui

import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.MoneyManager
import kotlin.random.Random

/**
 * CookMiniGameActivity — mini-game cuoco.
 * Appare un ordine (es. "🍝 Pasta + 🧀 Formaggio"), il giocatore tocca gli ingredienti
 * nell'ordine corretto. 5 ordini, punteggio basato su velocità + accuratezza.
 */
class CookMiniGameActivity : AppCompatActivity() {

    private data class Order(val emoji: String, val name: String)
    private data class Challenge(val needed: List<Order>, val label: String)

    private val INGREDIENTS = listOf(
        Order("🍝", "Pasta"), Order("🧀", "Formaggio"), Order("🍅", "Pomodoro"),
        Order("🥩", "Carne"), Order("🐟", "Pesce"), Order("🥬", "Insalata"),
        Order("🍞", "Pane"), Order("🧅", "Cipolla"), Order("🌶️", "Peperoncino")
    )

    private var currentRound = 0
    private val totalRounds = 5
    private var score = 0
    private var startTime = 0L
    private var neededOrders = listOf<Order>()
    private var selectedOrders = mutableListOf<Order>()
    private var challengeLabel = ""

    private lateinit var orderText: TextView
    private lateinit var scoreText: TextView
    private lateinit var roundText: TextView
    private lateinit var ingredientContainer: LinearLayout
    private lateinit var selectedText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
        }

        // Top bar
        val topBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
        }
        topBar.addView(TextView(c).apply {
            text = "← "; textSize = 20f; setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; setOnClickListener { finish() }
        })
        topBar.addView(TextView(c).apply {
            text = "👨‍🍳  Cuoco"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        scoreText = TextView(c).apply {
            text = "💰 \$0"; textSize = 14f; setTextColor(Color.parseColor("#FFD86B"))
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(scoreText)
        root.addView(topBar)

        // Round indicator
        roundText = TextView(c).apply {
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 6), 0, UiKit.dp(c, 4))
        }
        root.addView(roundText)

        // Current order
        orderText = TextView(c).apply {
            textSize = 16f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 8), UiKit.dp(c, 14), UiKit.dp(c, 4))
            gravity = Gravity.CENTER
        }
        root.addView(orderText)

        // Selected ingredients display
        selectedText = TextView(c).apply {
            textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 4), UiKit.dp(c, 14), UiKit.dp(c, 8))
        }
        root.addView(selectedText)

        // Ingredient buttons grid (3x3)
        ingredientContainer = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(c, 14), 0, UiKit.dp(c, 14), 0)
        }
        for (row in 0..2) {
            val rowLayout = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiKit.dp(c, 6) }
            }
            for (col in 0..2) {
                val idx = row * 3 + col
                if (idx < INGREDIENTS.size) {
                    val ing = INGREDIENTS[idx]
                    val btn = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            cornerRadius = UiKit.dp(c, 12).toFloat()
                            setColor(0x33FFFFFF)
                            setStroke(1, 0x44FFFFFF)
                        }
                        val size = UiKit.dp(c, 80)
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            marginStart = UiKit.dp(c, 4); marginEnd = UiKit.dp(c, 4)
                        }
                        isClickable = true; isFocusable = true
                        setOnClickListener { onIngredientTap(ing) }
                    }
                    btn.addView(TextView(c).apply {
                        text = ing.emoji; textSize = 30f; gravity = Gravity.CENTER
                    })
                    btn.addView(TextView(c).apply {
                        text = ing.name; textSize = 10f; setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    })
                    rowLayout.addView(btn)
                }
            }
            ingredientContainer.addView(rowLayout)
        }
        root.addView(ingredientContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
        nextRound()
    }

    private fun nextRound() {
        if (currentRound >= totalRounds) {
            finishGame()
            return
        }
        currentRound++
        roundText.text = "Ordine $currentRound / $totalRounds"

        // Generate random order (2-3 ingredients)
        val count = 2 + Random.nextInt(2)
        neededOrders = INGREDIENTS.shuffled().take(count)
        selectedOrders.clear()
        challengeLabel = neededOrders.joinToString(" + ") { "${it.emoji} ${it.name}" }
        orderText.text = "Prepara: $challengeLabel"
        selectedText.text = "Tocca gli ingredienti..."
        startTime = System.currentTimeMillis()
    }

    private fun onIngredientTap(ing: Order) {
        if (currentRound > totalRounds) return
        selectedOrders.add(ing)
        selectedText.text = selectedOrders.joinToString(" ") { it.emoji }

        // Check if we've selected enough
        if (selectedOrders.size >= neededOrders.size) {
            val elapsed = System.currentTimeMillis() - startTime
            val correct = selectedOrders.zip(neededOrders).all { (a, b) -> a.emoji == b.emoji }
            if (correct) {
                val speedBonus = (5000f - elapsed.coerceAtMost(5000L)).toInt().coerceAtLeast(0) / 50
                val roundPay = 50 + speedBonus
                score += roundPay
                scoreText.text = "💰 \$$score"
                Toast.makeText(this, "✅ Corretto! +\$$roundPay", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Sbagliato!", Toast.LENGTH_SHORT).show()
            }
            // Next round after short delay
            orderText.postDelayed({ nextRound() }, 800)
        }
    }

    private fun finishGame() {
        if (score > 0) {
            MoneyManager.addCash(this, score)
            MoneyManager.incrementJobsDone(this)
            Toast.makeText(this, "🎉 Paga: +\$$score!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Nessun guadagno questa volta.", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
