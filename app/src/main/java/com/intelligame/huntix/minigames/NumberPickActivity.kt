package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager

class NumberPickActivity : MiniGameBase() {

    private var wins = 0
    private var totalPlays = 0
    private lateinit var gameArea: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private var secretNumber = 0
    private var isRevealed = false
    private val numberButtons = mutableListOf<View>()

    override fun onGameCreate() {
        val c = this
        gameArea = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        build("Scegli il Numero", "\uD83D\uDD22",
            "Indovina il numero tra 1 e 10 per vincere 150 MVC + 50 XP! (max 3 partite)",
            gameArea)
        showStartScreen()
    }

    private fun showStartScreen() {
        val c = this
        isRevealed = false
        numberButtons.clear()
        gameArea.removeAllViews()

        val remaining = MiniGameManager.remainingPlays(c, MiniGameManager.GAME_NUMBER_PICK)
        if (remaining <= 0) {
            showGameOver()
            return
        }

        val remainText = TextView(c).apply {
            text = "Partite rimaste: $remaining"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
        }
        gameArea.addView(remainText)

        scoreText = TextView(c).apply {
            text = "Vittorie: $wins / $totalPlays"
            textSize = 12f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(c, 8))
        }
        gameArea.addView(scoreText)

        statusText = TextView(c).apply {
            text = "Scegli un numero!"
            textSize = 15f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 10))
        }
        gameArea.addView(statusText)

        secretNumber = (1..10).random()

        val grid = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        for (row in 0 until 5) {
            val rowLayout = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (col in 0 until 2) {
                val num = row * 2 + col + 1
                val btn = makeNumberButton(num)
                numberButtons.add(btn)
                rowLayout.addView(btn)
            }
            grid.addView(rowLayout)
        }
        gameArea.addView(grid)
    }

    private fun makeNumberButton(number: Int): View {
        val c = this
        val size = UiKit.dp(c, 52)
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = UiKit.dp(c, 6); marginEnd = UiKit.dp(c, 6)
                topMargin = UiKit.dp(c, 6)
            }
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 10).toFloat()
                setColor(Color.parseColor(UiKit.BG_CARD))
                setStroke(UiKit.dp(c, 2), Color.parseColor(UiKit.ACCENT))
            }
            isClickable = true; isFocusable = true
            setOnClickListener { onNumberPicked(number, this) }
            addView(TextView(c).apply {
                text = "$number"; textSize = 22f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            })
        }
    }

    private fun onNumberPicked(picked: Int, pickedView: View) {
        val c = this
        if (isRevealed) return
        isRevealed = true
        totalPlays++

        MiniGameManager.consumePlay(this, MiniGameManager.GAME_NUMBER_PICK)

        for (btn in numberButtons) {
            btn.isClickable = false
            btn.isFocusable = false
            val num = (btn as LinearLayout).getChildAt(0) as TextView
            val numVal = num.text.toString().toInt()
            if (numVal == secretNumber) {
                (btn as View).background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@NumberPickActivity, 10).toFloat()
                    setColor(Color.parseColor(UiKit.GREEN))
                    setStroke(UiKit.dp(this@NumberPickActivity, 2), Color.parseColor(UiKit.GREEN))
                }
                num.setTextColor(Color.BLACK)
            }
        }

        if (picked == secretNumber) {
            wins++
            pickedView.background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 10).toFloat()
                setColor(Color.parseColor(UiKit.GREEN))
                setStroke(UiKit.dp(c, 2), Color.parseColor(UiKit.GREEN))
            }
            (pickedView as LinearLayout).getChildAt(0).let {
                (it as TextView).setTextColor(Color.BLACK)
            }
            statusText.text = "HAI INDOVINATO! +150 MVC +50 XP"
            statusText.setTextColor(Color.parseColor(UiKit.GREEN))
            MiniGameManager.applyReward(c, MiniGameManager.GameReward(
                mvcCoins = 150, xpPoints = 50, label = "Numero indovinato!", isWin = true
            ), MiniGameManager.GAME_NUMBER_PICK)
        } else {
            pickedView.background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 10).toFloat()
                setColor(Color.parseColor("#CC3333"))
                setStroke(UiKit.dp(c, 2), Color.parseColor("#CC3333"))
            }
            (pickedView as LinearLayout).getChildAt(0).let {
                (it as TextView).setTextColor(Color.WHITE)
            }
            statusText.text = "Il numero era $secretNumber. +20 MVC consolation"
            statusText.setTextColor(Color.parseColor("#FF5555"))
            MiniGameManager.applyReward(c, MiniGameManager.GameReward(
                mvcCoins = 20, label = "Consolation", isWin = false
            ), MiniGameManager.GAME_NUMBER_PICK)
        }

        scoreText.text = "Vittorie: $wins / $totalPlays"
        gameArea.removeView(statusText)
        gameArea.addView(statusText)
        gameArea.removeView(gameArea.getChildAt(gameArea.childCount - 1))

        val remaining = MiniGameManager.remainingPlays(c, MiniGameManager.GAME_NUMBER_PICK)
        val btnLabel = if (remaining > 0) "Gioca Ancora" else "Risultato Finale"
        gameArea.addView(UiKit.button(c, btnLabel, UiKit.ACCENT) {
            if (remaining > 0) showStartScreen() else showGameOver()
        })
    }

    private fun showGameOver() {
        val c = this
        gameArea.removeAllViews()
        val msg = TextView(c).apply {
            text = "Partite esaurite!\nHai indovinato $wins su $totalPlays tentativi."
            textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 16), 0, UiKit.dp(c, 16))
        }
        gameArea.addView(msg)
    }
}
