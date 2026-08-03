package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.minigames.card.Card
import com.intelligame.huntix.minigames.card.CardDeck
import io.sentry.Sentry

/**
 * 🃏 Carta Alta — tu e la CPU pescate una carta: vince la più alta.
 * Partita al meglio di 5 mani (3 vittorie).
 */
class HighCardActivity : MiniGameBase() {

    private val BEST_OF = 5
    private val TARGET = 3

    private val deck = CardDeck.standardDeck().toMutableList()
    private var playerWins = 0
    private var cpuWins = 0
    private var rounds = 0
    private var gameOver = false
    private var statusText: TextView? = null
    private var playerCardText: TextView? = null
    private var cpuCardText: TextView? = null
    private var scoreText: TextView? = null
    private var drawButton: Button? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Carta Alta", "🃏"))
        root.addView(TextView(ctx).apply {
            text = "Pesca una carta: vince la più alta. Al meglio di $BEST_OF mani!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_HIGH_CARD))
        scoreText = TextView(ctx).apply {
            text = "Tu 0 – 0 CPU"
            textSize = 18f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        playerCardText = TextView(ctx).apply {
            text = "🂠  ???"
            textSize = 26f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            gravity = android.view.Gravity.CENTER
            setPadding(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 2))
            background = cardBackground("#3A2A5A")
        }
        root.addView(playerCardText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 72)
        ).apply { bottomMargin = UiKit.dp(ctx, 8) })

        cpuCardText = TextView(ctx).apply {
            text = "🂠  ???"
            textSize = 26f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            gravity = android.view.Gravity.CENTER
            setPadding(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 2))
            background = cardBackground("#3A2A5A")
        }
        root.addView(cpuCardText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 72)
        ).apply { bottomMargin = UiKit.dp(ctx, 10) })

        statusText = TextView(ctx).apply {
            text = "Tocca Pesa per iniziare"; textSize = 14f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        }
        root.addView(statusText!!)

        drawButton = UiKit.button(ctx, "🎴  Pesca", UiKit.ACCENT) { drawRound() }
        root.addView(drawButton)

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun cardBackground(hex: String) = GradientDrawable().apply {
        cornerRadius = UiKit.dp(this@HighCardActivity, 10).toFloat()
        setColor(Color.parseColor(hex))
    }

    private fun startGame() {
        deck.clear()
        deck.addAll(CardDeck.standardDeck())
        CardDeck.shuffle(deck)
        playerWins = 0
        cpuWins = 0
        rounds = 0
        gameOver = false
        playerCardText?.text = "🂠  ???"
        cpuCardText?.text = "🂠  ???"
        scoreText?.text = "Tu 0 – 0 CPU"
        statusText?.text = "Tocca Pesca per iniziare"
        drawButton?.isEnabled = true
    }

    private fun renderCard(text: TextView?, card: Card, who: String) {
        text?.text = "${card.displayName}  ${card.rank.label}"
        text?.setTextColor(card.suit.color)
        text?.textSize = 24f
        text?.text = "$who\n${card.displayName}"
    }

    private fun drawRound() {
        if (gameOver) return
        if (deck.size < 2) { endGame(playerWins >= TARGET); return }
        val playerCard = deck.removeAt(0)
        val cpuCard = deck.removeAt(0)
        rounds++

        renderCard(playerCardText, playerCard, "TU")
        renderCard(cpuCardText, cpuCard, "CPU")

        when {
            playerCard.pointValue > cpuCard.pointValue -> {
                playerWins++
                statusText?.text = "✅ Hai vinto la mano $rounds (${playerCard.pointValue} > ${cpuCard.pointValue})"
            }
            playerCard.pointValue < cpuCard.pointValue -> {
                cpuWins++
                statusText?.text = "❌ Mano $rounds alla CPU (${playerCard.pointValue} < ${cpuCard.pointValue})"
            }
            else -> statusText?.text = "🤝 Pareggio in mano $rounds (${playerCard.pointValue} = ${cpuCard.pointValue})"
        }
        scoreText?.text = "Tu $playerWins – $cpuWins CPU"
        if (playerWins >= TARGET || cpuWins >= TARGET || rounds >= BEST_OF) {
            endGame(playerWins >= TARGET)
        }
    }

    private fun endGame(won: Boolean) {
        gameOver = true
        drawButton?.isEnabled = false
        val mvc = if (won) 50 else 15
        val xp = (mvc / 4).coerceAtLeast(2)
        val lr = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_HIGH_CARD,
                score = playerWins,
                mvc = mvc, xp = xp,
                label = if (won) "Carta Alta: vittoria ($playerWins-$cpuWins)!" else "Carta Alta: $playerWins-$cpuWins",
                isWin = won
            )
        } catch (e: Exception) { Sentry.captureException(e); null }

        val ctx = this
        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#CC0D0620"))
            setPadding(UiKit.dp(ctx, 30), UiKit.dp(ctx, 40), UiKit.dp(ctx, 30), UiKit.dp(ctx, 40))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isClickable = true; isFocusable = true
        }
        val endLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "🏆" else "🤖"
            textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Hai vinto la partita!\n$playerWins – $cpuWins" else "Vince la CPU!\n$playerWins – $cpuWins"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        lr?.let { endLayout.addView(levelResultView(it)) }
        endLayout.addView(TextView(ctx).apply {
            text = "+${lr?.mvc ?: mvc} MVC  •  +${lr?.xp ?: xp} XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "🔄  Gioca Ancora", UiKit.ACCENT) {
            overlayContainer?.removeView(overlay)
            startGame()
        })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer?.addView(overlay)
    }
}
