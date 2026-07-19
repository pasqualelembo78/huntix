package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager

class MemoryGameActivity : MiniGameBase() {

    private val eggPairs = arrayOf("\uD83E\uDD5A\u2764\uFE0F", "\uD83E\uDD5A\uD83D\uDD35",
        "\uD83E\uDD5A\uD83D\uDFE2", "\uD83E\uDD5A\uD83D\uDFE1", "\uD83E\uDD5A\uD83D\uDFE3",
        "\uD83E\uDD5A\u2B50", "\uD83E\uDD5A\uD83D\uDC8E", "\uD83E\uDD5A\uD83D\uDC51")
    private val cards = mutableListOf<String>()
    private val cardViews = mutableListOf<View>()
    private val flipped = mutableListOf<Int>()
    private var moves = 0
    private var matchedPairs = 0
    private var locked = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gameArea: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var movesText: TextView
    private var wins = 0
    private var totalPlays = 0

    override fun onGameCreate() {
        val c = this
        gameArea = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        build("Memory", "\uD83E\uDDE0",
            "Trova tutte le 8 coppie di uova! Meno mosse = piu' MVC. (max 3 partite)",
            gameArea)
        showStartScreen()
    }

    private fun showStartScreen() {
        val c = this
        totalPlays++
        moves = 0
        matchedPairs = 0
        flipped.clear()
        locked = false
        cards.clear()
        cardViews.clear()
        gameArea.removeAllViews()

        val remaining = MiniGameManager.remainingPlays(c, MiniGameManager.GAME_MEMORY)
        if (remaining <= 0) {
            showGameOver()
            return
        }

        val remainText = TextView(c).apply {
            text = "Partite rimaste: $remaining"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 6))
        }
        gameArea.addView(remainText)

        cards.addAll(eggPairs)
        cards.addAll(eggPairs)
        cards.shuffle()

        movesText = TextView(c).apply {
            text = "Mosse: 0"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
        }
        gameArea.addView(movesText)

        val grid = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        for (row in 0 until 4) {
            val rowLayout = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (col in 0 until 4) {
                val idx = row * 4 + col
                val cardBtn = makeCardButton(idx)
                cardViews.add(cardBtn)
                rowLayout.addView(cardBtn)
            }
            grid.addView(rowLayout)
        }
        gameArea.addView(grid)

        statusText = TextView(c).apply {
            text = ""; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(c, 8), 0, 0)
        }
        gameArea.addView(statusText)
    }

    private fun makeCardButton(index: Int): View {
        val c = this
        val size = UiKit.dp(c, 70)
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = UiKit.dp(c, 4); marginEnd = UiKit.dp(c, 4)
                topMargin = UiKit.dp(c, 4)
            }
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 10).toFloat()
                setColor(Color.parseColor(UiKit.BG_CARD))
                setStroke(UiKit.dp(c, 2), Color.parseColor(UiKit.TEXT_DIM))
            }
            isClickable = true; isFocusable = true
            setOnClickListener { onCardFlipped(index, this) }
            addView(TextView(c).apply {
                text = "\u2753"; textSize = 28f; gravity = Gravity.CENTER
            })
        }
    }

    private fun onCardFlipped(index: Int, view: View) {
        if (locked || flipped.contains(index)) return
        if (index < 0 || index >= cards.size) return

        val c = this
        val ll = view as LinearLayout
        ll.removeAllViews()
        ll.background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(c, 10).toFloat()
            setColor(Color.WHITE)
            setStroke(UiKit.dp(c, 2), Color.parseColor("#CCCCCC"))
        }
        ll.addView(TextView(c).apply {
            text = cards[index]; textSize = 24f; gravity = Gravity.CENTER
        })
        view.isClickable = false

        flipped.add(index)

        if (flipped.size == 2) {
            moves++
            movesText.text = "Mosse: $moves"
            locked = true

            val i1 = flipped[0]; val i2 = flipped[1]
            if (cards[i1] == cards[i2]) {
                matchedPairs++
                flashGreen(cardViews[i1])
                flashGreen(cardViews[i2])
                flipped.clear()
                locked = false

                if (matchedPairs == 8) {
                    handler.postDelayed({ onGameWon() }, 400)
                }
            } else {
                handler.postDelayed({
                    flipBack(cardViews[i1])
                    flipBack(cardViews[i2])
                    flipped.clear()
                    locked = false
                }, 900)
            }
        }
    }

    private fun flashGreen(view: View) {
        val ll = view as LinearLayout
        ll.background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(this@MemoryGameActivity, 10).toFloat()
            setColor(Color.parseColor(UiKit.GREEN))
            setStroke(UiKit.dp(this@MemoryGameActivity, 2), Color.parseColor(UiKit.GREEN))
        }
    }

    private fun flipBack(view: View) {
        val c = this
        val ll = view as LinearLayout
        ll.removeAllViews()
        ll.background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(c, 10).toFloat()
            setColor(Color.parseColor(UiKit.BG_CARD))
            setStroke(UiKit.dp(c, 2), Color.parseColor(UiKit.TEXT_DIM))
        }
        ll.addView(TextView(c).apply {
            text = "\u2753"; textSize = 28f; gravity = Gravity.CENTER
        })
        ll.isClickable = true
        ll.isFocusable = true
        val idx = cardViews.indexOf(view)
        ll.setOnClickListener { onCardFlipped(idx, it) }
    }

    private fun onGameWon() {
        val c = this
        val (mvc, label) = when {
            moves < 10 -> 200 to "Eccellente! <$moves mosse!"
            moves < 15 -> 120 to "Bravo! <$moves mosse"
            moves < 20 -> 80 to "Bene! <$moves mosse"
            else -> 40 to "$moves mosse - ci riprovi?"
        }
        MiniGameManager.consumePlay(c, MiniGameManager.GAME_MEMORY)
        wins++
        MiniGameManager.applyReward(c, MiniGameManager.GameReward(
            mvcCoins = mvc, label = label, isWin = true
        ), MiniGameManager.GAME_MEMORY)

        statusText.text = "$label\n+$mvc MVC"
        statusText.setTextColor(Color.parseColor(UiKit.GREEN))
        movesText.text = "Mosse: $moves | Coppie trovate: $matchedPairs"

        val remaining = MiniGameManager.remainingPlays(c, MiniGameManager.GAME_MEMORY)
        val btnLabel = if (remaining > 0) "Gioca Ancora" else "Risultato Finale"
        gameArea.addView(UiKit.button(c, btnLabel, UiKit.ACCENT) {
            if (remaining > 0) showStartScreen() else showGameOver()
        })
    }

    private fun showGameOver() {
        val c = this
        gameArea.removeAllViews()
        val msg = TextView(c).apply {
            text = "Partite esaurite!\nCoppie trovate: $matchedPairs | Mosse: $moves"
            textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 16), 0, UiKit.dp(c, 16))
        }
        gameArea.addView(msg)
    }
}
