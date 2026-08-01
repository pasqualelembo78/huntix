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
 * 🙈 Impiccato — indovina la parola italiana prima di finire i tentativi.
 * (meccanica classica, riscritta nativa)
 */
class HangmanActivity : MiniGameBase() {

    private val WORDS = listOf(
        "casa", "cane", "gatto", "albero", "pizza", "pasta", "uovo", "treno",
        "mare", "sole", "luna", "stella", "pesce", "fiore", "libro", "scuola",
        "amico", "gelato", "cioccolato", "bicicletta", "calcio", "musica",
        "macchina", "montagna", "neve", "pioggia", "ragazzo", "sorella",
        "strada", "telefono", "torre", "uccello", "zaino", "aereo", "fiume",
        "castello", "cucina", "festa", "giardino", "nuvola"
    )

    private val HANGMAN = arrayOf("😊", "🙂", "😐", "😟", "😣", "😵", "💀")
    private val MAX_WRONG = HANGMAN.size - 1

    private var word = ""
    private val guessed = mutableSetOf<Char>()
    private var wrong = 0
    private var gameRunning = false
    private var hangmanText: TextView? = null
    private var wordText: TextView? = null
    private var guessedText: TextView? = null
    private var letterGrid: GridLayout? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Impiccato", "🙈"))
        root.addView(TextView(ctx).apply {
            text = "Indovina la parola italiana!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        hangmanText = TextView(ctx).apply {
            text = HANGMAN[0]; textSize = 40f
            setPadding(0, 0, 0, UiKit.dp(ctx, 6))
        }
        root.addView(hangmanText!!)
        wordText = TextView(ctx).apply {
            textSize = 26f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, UiKit.dp(ctx, 12))
        }
        root.addView(wordText!!)
        guessedText = TextView(ctx).apply {
            textSize = 12f; setTextColor(Color.parseColor(UiKit.ACCENT))
            setPadding(0, 0, 0, UiKit.dp(ctx, 12))
        }
        root.addView(guessedText!!)

        letterGrid = GridLayout(ctx).apply {
            columnCount = 6
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(letterGrid!!)

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        buildBoard()
    }

    private fun buildBoard() {
        word = WORDS[Random.nextInt(WORDS.size)]
        guessed.clear()
        wrong = 0
        gameRunning = true
        updateWordDisplay()
        hangmanText?.text = HANGMAN[0]
        guessedText?.text = "Lettere provate: "
        buildLetters()
    }

    private fun buildLetters() {
        letterGrid?.removeAllViews()
        for (ch in 'a'..'z') {
            val btn = Button(this).apply {
                text = ch.toString().uppercase()
                textSize = 12f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = UiKit.dp(this@HangmanActivity, 4).toFloat()
                    setColor(Color.parseColor("#3A2A5A"))
                }
                setPadding(0, 0, 0, 0)
                isAllCaps = false
                setOnClickListener { onGuess(ch) }
            }
            letterGrid?.addView(
                btn,
                GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = (resources.displayMetrics.density * 40).toInt()
                }
            )
        }
    }

    private fun onGuess(ch: Char) {
        if (!gameRunning || ch in guessed) return
        guessed.add(ch)
        val letterBtns = letterGrid?.let { g ->
            (0 until g.childCount).map { g.getChildAt(it) as Button }
        } ?: return
        val letterBtn = letterBtns[ch - 'a']
        letterBtn.isEnabled = false
        if (ch in word) {
            letterBtn.setTextColor(Color.parseColor(UiKit.GREEN))
            updateWordDisplay()
            if (word.all { it in guessed }) {
                gameRunning = false
                endGame(true)
            }
        } else {
            wrong++
            letterBtn.setTextColor(Color.RED)
            hangmanText?.text = HANGMAN[wrong.coerceAtMost(MAX_WRONG)]
            if (wrong >= MAX_WRONG) {
                gameRunning = false
                updateWordDisplay(forceShow = true)
                endGame(false)
            }
        }
        guessedText?.text = "Lettere provate: ${guessed.sorted().joinToString(" ").uppercase()}"
    }

    private fun updateWordDisplay(forceShow: Boolean = false) {
        wordText?.text = word.map { ch ->
            if (forceShow || ch in guessed) ch.uppercase() else "_"
        }.joinToString(" ")
    }

    private fun endGame(won: Boolean) {
        if (!gameRunning) gameRunning = false
        val mvc = if (won) 40 else 10
        val xp = if (won) 12 else 3
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_HANGMAN)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc, xpPoints = xp,
                    giftEggRarityId = if (won) "common" else null,
                    label = if (won) "Impiccato: indovinato \"$word\"!" else "Impiccato: era \"$word\"",
                    isWin = won
                ),
                MiniGameManager.GAME_HANGMAN
            )
        } catch (e: Exception) { Sentry.captureException(e) }

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
            text = if (won) "🎉" else "💀"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Hai indovinato!" else "Impiccato!"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Parola: ${word.uppercase()}"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "+$mvc MVC  •  +$xp XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "🔄  Gioca Ancora", UiKit.ACCENT) {
            overlayContainer?.removeView(overlay)
            buildBoard()
        })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer?.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
    }
}
