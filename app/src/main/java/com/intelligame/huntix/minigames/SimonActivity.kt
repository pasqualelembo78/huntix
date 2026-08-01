package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
 * 🎨 Simon — memorizza e ripeti la sequenza di colori che cresce.
 * (meccanica classica, riscritta nativa)
 */
class SimonActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val COLORS = intArrayOf(0xFFEF5350.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFFFCA28.toInt())

    private val sequence = mutableListOf<Int>()
    private var inputIndex = 0
    private var score = 0
    private var playing = false
    private var gameOver = false
    private var scoreText: TextView? = null
    private var statusText: TextView? = null
    private val buttons = arrayOfNulls<Button>(4)
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Simon", "🎨"))
        root.addView(TextView(ctx).apply {
            text = "Ripeti la sequenza di colori!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 16f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusText = TextView(ctx).apply {
            text = "Memorizza la sequenza"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
        }
        header.addView(scoreText!!); header.addView(statusText!!)
        root.addView(header)

        val grid = GridLayout(ctx).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, UiKit.dp(ctx, 10), 0, 0)
        }
        for (i in 0 until 4) {
            val btn = Button(this).apply {
                background = dim(i)
                setPadding(0, 0, 0, 0)
                val idx = i
                setOnClickListener { onTap(idx) }
            }
            buttons[i] = btn
            grid.addView(
                btn,
                GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = (resources.displayMetrics.density * 110).toInt()
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

    private fun dim(i: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 10f
        setColor(dimColor(i))
    }

    private fun bright(i: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 10f
        setColor(Color.WHITE)
    }

    private fun dimColor(i: Int): Int {
        val c = COLORS[i]
        return Color.argb(160, Color.red(c), Color.green(c), Color.blue(c))
    }

    private fun startGame() {
        sequence.clear()
        sequence.add(Random.nextInt(4))
        score = 0
        gameOver = false
        playing = false
        scoreText?.text = "Punti: 0"
        handler.removeCallbacksAndMessages(null)
        playSequence()
    }

    private fun flash(i: Int) {
        buttons[i]?.background = bright(i)
        handler.postDelayed({ buttons[i]?.background = dim(i) }, 300)
    }

    private fun playSequence() {
        playing = true
        statusText?.text = "Memorizza..."
        val seq = sequence.toList()
        var i = 0
        val runnable = object : Runnable {
            override fun run() {
                if (gameOver) return
                if (i >= seq.size) {
                    playing = false
                    inputIndex = 0
                    statusText?.text = "Ripeti i colori!"
                    return
                }
                flash(seq[i])
                i++
                handler.postDelayed(this, 600)
            }
        }
        handler.postDelayed(runnable, 500)
    }

    private fun onTap(i: Int) {
        if (playing || gameOver) return
        flash(i)
        if (i == sequence[inputIndex]) {
            inputIndex++
            if (inputIndex == sequence.size) {
                score += 10
                scoreText?.text = "Punti: $score"
                sequence.add(Random.nextInt(4))
                handler.postDelayed({ playSequence() }, 650)
            }
        } else {
            endGame()
        }
    }

    private fun endGame() {
        if (gameOver) return
        gameOver = true
        handler.removeCallbacksAndMessages(null)
        val mvc = (score / 2).coerceAtLeast(8)
        val xp = (score / 4).coerceAtLeast(2)
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_SIMON)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc, xpPoints = xp,
                    label = "Simon: $score punti",
                    isWin = score >= 40
                ),
                MiniGameManager.GAME_SIMON
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
            text = "🎨"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Sequenza sbagliata!"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $score"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "+$mvc MVC  •  +$xp XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
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

    override fun onDestroy() {
        super.onDestroy()
        gameOver = true
        handler.removeCallbacksAndMessages(null)
    }
}
