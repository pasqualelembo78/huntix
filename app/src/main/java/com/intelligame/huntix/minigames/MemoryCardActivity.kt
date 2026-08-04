package com.intelligame.huntix.minigames

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.random.Random

class MemoryCardActivity : MiniGameBase() {

    private val EMOJIS = arrayOf("🥚❤️", "🥚💙", "🥚💚", "🥚💛", "🥚💜", "🥚⭐", "🥚🔥", "🥚⚡")
    private val cards = mutableListOf<String>()
    private val cardViews = mutableListOf<View>()
    private val flipped = mutableListOf<Int>()
    private var moves = 0
    private var matchedPairs = 0
    private var locked = false
    private var gameRunning = false
    private var gameOver = false
    private var handler = Handler(Looper.getMainLooper())
    private var scoreText: TextView? = null
    private var movesText: TextView? = null
    private var statusText: TextView? = null
    private var gridArea: LinearLayout? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Memory", "🧠"))
        root.addView(TextView(ctx).apply {
            text = "Trova tutte le 8 coppie di uova!"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_MEMORY))

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(0, 0, UiKit.dp(ctx, 12), 0)
        }
        movesText = TextView(ctx).apply {
            text = "Mosse: 0"; textSize = 16f; setTextColor(Color.WHITE)
        }
        header.addView(scoreText!!)
        header.addView(movesText!!)
        root.addView(header)

        statusText = TextView(ctx).apply {
            text = "Tocca una carta per iniziare"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 8), 0, 0)
        }
        root.addView(statusText!!)

        gridArea = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(gridArea!!)

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        cards.clear()
        cardViews.clear()
        flipped.clear()
        moves = 0
        matchedPairs = 0
        locked = false
        gameRunning = true
        gameOver = false
        scoreText?.text = "Punti: 0"
        movesText?.text = "Mosse: 0"
        statusText?.text = "Tocca una carta per iniziare"

        cards.addAll(EMOJIS)
        cards.addAll(EMOJIS)
        cards.shuffle()

        gridArea?.removeAllViews()
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        for (row in 0 until 4) {
            val rowLayout = LinearLayout(this).apply {
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
        gridArea?.addView(grid)
    }

    private fun makeCardButton(index: Int): View {
        val c = this
        val size = UiKit.dp(c, 65)
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = UiKit.dp(c, 3)
                marginEnd = UiKit.dp(c, 3)
                topMargin = UiKit.dp(c, 3)
            }
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 8).toFloat()
                setColor(Color.parseColor(UiKit.BG_CARD))
                setStroke(UiKit.dp(c, 2), Color.parseColor(UiKit.TEXT_DIM))
            }
            isClickable = true; isFocusable = true
            setOnClickListener { onCardFlipped(index, this) }
            addView(TextView(c).apply {
                text = "❓"; textSize = 24f; gravity = Gravity.CENTER
            })
        }
    }

    private fun onCardFlipped(index: Int, view: View) {
        if (locked || flipped.contains(index) || gameOver) return
        if (index < 0 || index >= cards.size) return

        val c = this
        val ll = view as LinearLayout
        ll.removeAllViews()
        ll.background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(c, 8).toFloat()
            setColor(Color.WHITE)
            setStroke(UiKit.dp(c, 2), Color.parseColor("#CCCCCC"))
        }
        ll.addView(TextView(c).apply {
            text = cards[index]; textSize = 22f; gravity = Gravity.CENTER
        })
        view.isClickable = false
        flipped.add(index)

        if (flipped.size == 2) {
            moves++
            movesText?.text = "Mosse: $moves"
            locked = true

            val i1 = flipped[0]; val i2 = flipped[1]
            if (cards[i1] == cards[i2]) {
                matchedPairs++
                flashGreen(cardViews[i1])
                flashGreen(cardViews[i2])
                flipped.clear()
                locked = false
                scoreText?.text = "Punti: ${matchedPairs * 10}"
                if (matchedPairs == 8) {
                    handler.postDelayed({ onGameWon() }, 400)
                }
            } else {
                handler.postDelayed({
                    flipBack(cardViews[i1])
                    flipBack(cardViews[i2])
                    flipped.clear()
                    locked = false
                }, 800)
            }
        }
    }

    private fun flashGreen(view: View) {
        val ll = view as LinearLayout
        ll.background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(this@MemoryActivity, 8).toFloat()
            setColor(Color.parseColor(UiKit.GREEN))
            setStroke(UiKit.dp(this@MemoryActivity, 2), Color.parseColor(UiKit.GREEN))
        }
    }

    private fun flipBack(view: View) {
        val c = this
        val ll = view as LinearLayout
        ll.removeAllViews()
        ll.background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(c, 8).toFloat()
            setColor(Color.parseColor(UiKit.BG_CARD))
            setStroke(UiKit.dp(c, 2), Color.parseColor(UiKit.TEXT_DIM))
        }
        ll.addView(TextView(c).apply {
            text = "❓"; textSize = 24f; gravity = Gravity.CENTER
        })
        ll.isClickable = true
        ll.isFocusable = true
        val idx = cardViews.indexOf(view)
        ll.setOnClickListener { onCardFlipped(idx, it) }
    }

    private fun onGameWon() {
        gameOver = true
        gameRunning = false
        val mvc = (matchedPairs * 15).coerceAtLeast(20)
        val xp = (mvc / 3).coerceAtLeast(5)
        val lr = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_MEMORY,
                score = 1,
                mvc = mvc, label = "Memory: $moves mosse!",
                isWin = true
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
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        endLayout.addView(TextView(ctx).apply { text = "🧠"; textSize = 48f; gravity = Gravity.CENTER })
        endLayout.addView(TextView(ctx).apply { text = "Perfetto!"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6)) })
        endLayout.addView(TextView(ctx).apply { text = "Mosse: $moves  •  Coppie: $matchedPairs"; textSize = 16f; setTextColor(Color.parseColor(UiKit.GREEN)); gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8)) })
        lr?.let { endLayout.addView(levelResultView(it)) }
        endLayout.addView(TextView(ctx).apply { text = "+${lr?.mvc ?: mvc} MVC  •  +${lr?.xp ?: xp} XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT)); gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16)) })
        endLayout.addView(UiKit.button(ctx, "🔄  Gioca Ancora", UiKit.ACCENT) { overlayContainer?.removeView(overlay); startGame() })
        endLayout.addView(UiKit.button(ctx, "⬅  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer?.addView(overlay)
    }
}