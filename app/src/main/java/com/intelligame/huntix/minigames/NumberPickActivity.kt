package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🔢 Scegli il Numero — indovina il numero segreto (1–10) prima di esaurire
 * i tentativi. La CPU ti guida con indizi "più alto / più basso".
 */
class NumberPickActivity : MiniGameBase() {

    private val MAX = 10
    private val ATTEMPTS = 4

    private val buttons = arrayOfNulls<Button>(MAX)
    private var secret = 0
    private var attemptsLeft = ATTEMPTS
    private var gameOver = false
    private var statusText: TextView? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Scegli il Numero", "🔢"))
        root.addView(TextView(ctx).apply {
            text = "Indovina il numero segreto (1–10) in $ATTEMPTS tentativi!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_NUMBER_PICK))
        statusText = TextView(ctx).apply {
            text = "Tocca un numero"; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        }
        root.addView(statusText!!)

        val grid = GridLayout(ctx).apply {
            columnCount = 5
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        for (i in 1..MAX) {
            val btn = Button(this).apply {
                text = "$i"
                textSize = 22f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@NumberPickActivity, 6).toFloat()
                    setColor(Color.parseColor("#3A2A5A"))
                }
                setPadding(0, 0, 0, 0)
                val value = i
                setOnClickListener { onPick(value) }
            }
            buttons[i - 1] = btn
            grid.addView(
                btn,
                GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = (resources.displayMetrics.density * 64).toInt()
                    setMargins(UiKit.dp(this@NumberPickActivity, 4), UiKit.dp(this@NumberPickActivity, 4), UiKit.dp(this@NumberPickActivity, 4), UiKit.dp(this@NumberPickActivity, 4))
                }
            )
        }
        root.addView(grid)

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        secret = Random.nextInt(1, MAX + 1)
        attemptsLeft = ATTEMPTS
        gameOver = false
        for (i in 1..MAX) {
            buttons[i - 1]?.text = "$i"
            buttons[i - 1]?.isEnabled = true
            (buttons[i - 1]?.background as? GradientDrawable)?.setColor(Color.parseColor("#3A2A5A"))
        }
        statusText?.text = "Numero segreto scelto! Tentativi: $attemptsLeft"
    }

    private fun onPick(value: Int) {
        if (gameOver) return
        val btn = buttons[value - 1] ?: return
        btn.isEnabled = false
        when {
            value == secret -> {
                gameOver = true
                statusText?.text = "🎉 Indovinato: era $secret!"
                endGame(true)
            }
            value < secret -> {
                attemptsLeft--
                btn.text = "▲ $value"
                (btn.background as? GradientDrawable)?.setColor(Color.parseColor("#7B1FA2"))
                statusText?.text = "Il numero è più ALTO di $value — tentativi rimasti: $attemptsLeft"
                if (attemptsLeft <= 0) endGame(false)
            }
            else -> {
                attemptsLeft--
                btn.text = "▼ $value"
                (btn.background as? GradientDrawable)?.setColor(Color.parseColor("#7B1FA2"))
                statusText?.text = "Il numero è più BASSO di $value — tentativi rimasti: $attemptsLeft"
                if (attemptsLeft <= 0) endGame(false)
            }
        }
    }

    private fun endGame(won: Boolean) {
        if (gameOver && !won) gameOver = true
        val base = if (won) 60 else 15
        val mvc = base + attemptsLeft.coerceAtLeast(0) * 10
        val xp = (mvc / 4).coerceAtLeast(2)
        val lr = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_NUMBER_PICK,
                score = if (won) 1 else 0,
                mvc = mvc, xp = xp,
                label = if (won) "Numero: indovinato ($secret)!" else "Numero: $secret non trovato",
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
            text = if (won) "Hai indovinato!\nEra il $secret." else "Hai perso!\nIl numero era $secret."
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
