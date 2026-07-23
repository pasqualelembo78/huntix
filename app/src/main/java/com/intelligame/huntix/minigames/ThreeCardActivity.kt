package com.intelligame.huntix.minigames

import android.animation.ValueAnimator
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager

class ThreeCardActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val CUPS = 3
    private val TOTAL_ROUNDS = 5
    private val SHUFFLE_COUNT = 6

    private var cupPositions = floatArrayOf(0.2f, 0.5f, 0.8f)
    private var prizeIndex = 0
    private var round = 0
    private var wins = 0
    private var awaitingPick = false
    private var gameRunning = false
    private var isShuffling = false

    private var gameView: GameView? = null
    private var overlayContainer: FrameLayout? = null
    private var statusText: TextView? = null
    private var roundText: TextView? = null
    private var winsText: TextView? = null

    private val cupColors = intArrayOf(
        Color.parseColor("#E53935"),
        Color.parseColor("#1E88E5"),
        Color.parseColor("#43A047")
    )

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }

        root.addView(UiKit.title(ctx, "Tre Carte", "\uD83C\uDCCF"))

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        roundText = TextView(ctx).apply {
            text = "Round 1/$TOTAL_ROUNDS"; textSize = 15f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        winsText = TextView(ctx).apply {
            text = "Vinte: 0"; textSize = 15f; setTextColor(Color.parseColor(UiKit.GREEN))
        }
        header.addView(roundText); header.addView(winsText)
        root.addView(header)

        statusText = TextView(ctx).apply {
            text = "Preparati..."; textSize = 16f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 8))
        }
        root.addView(statusText)

        gameView = GameView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        round = 0
        wins = 0
        gameRunning = true
        updateHud()
        setupRound()
    }

    private fun setupRound() {
        if (round >= TOTAL_ROUNDS) {
            endGame()
            return
        }
        round++
        cupPositions = floatArrayOf(0.2f, 0.5f, 0.8f)
        prizeIndex = (Math.random() * CUPS).toInt()
        awaitingPick = false
        isShuffling = false
        updateHud()
        statusText?.text = "Dove si nasconde l'uovo d'oro?"
        gameView?.invalidate()
        handler.postDelayed({ shuffle(SHUFFLE_COUNT) }, 800)
    }

    private fun shuffle(remaining: Int) {
        if (!gameRunning) return
        if (remaining <= 0) {
            isShuffling = false
            awaitingPick = true
            statusText?.text = "Tocca a te! Scegli un calice"
            return
        }
        isShuffling = true
        var a = (0 until CUPS).random()
        var b = (0 until CUPS).random()
        while (b == a) b = (0 until CUPS).random()

        val animator = ValueAnimator.ofFloat(cupPositions[a], cupPositions[b]).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            var swapped = false
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                if (!swapped) {
                    cupPositions[a] = value
                    cupPositions[b] = cupPositions[a] - (cupPositions[a] - cupPositions[b])
                    gameView?.invalidate()
                }
            }
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                val tmp = cupPositions[a]
                cupPositions[a] = cupPositions[b]
                cupPositions[b] = tmp
                gameView?.invalidate()
                handler.postDelayed({ shuffle(remaining - 1) }, 150)
            }
        })
        animator.start()
    }

    private fun pickCup(index: Int) {
        if (!gameRunning || !awaitingPick || isShuffling) return
        awaitingPick = false

        if (index == prizeIndex) {
            wins++
            statusText?.text = "Hai trovato l'uovo d'oro!"
        } else {
            statusText?.text = "L'era il calice ${index + 1}. Il premio era al ${prizeIndex + 1}!"
        }
        updateHud()
        gameView?.invalidate()

        handler.postDelayed({
            if (gameRunning) setupRound()
        }, 1500)
    }

    private fun updateHud() {
        roundText?.text = "Round $round/$TOTAL_ROUNDS"
        winsText?.text = "Vinte: $wins"
    }

    private fun endGame() {
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
        val mvc = wins * 80
        val xp = wins * 40
        val isWin = wins >= 3
        val label = "Three Card ($wins/$TOTAL_ROUNDS)"
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_THREE_CARD)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc,
                    xpPoints = xp,
                    label = label,
                    isWin = isWin
                ),
                MiniGameManager.GAME_THREE_CARD
            )
        } catch (_: Exception) {}

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
        endLayout.addView(TextView(ctx).apply {
            text = if (isWin) "\uD83C\uDFC6" else "\uD83D\uDE22"; textSize = 48f; gravity = Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Partita Finita!"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Hai vinto $wins/$TOTAL_ROUNDS round"; textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "+$mvc MVC  \u2022  +$xp XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "\uD83D\uDD04  Gioca Ancora", UiKit.ACCENT) {
            overlayContainer?.removeView(overlay)
            startGame()
        })
        endLayout.addView(UiKit.button(ctx, "\u2B05  Indietro", UiKit.TEXT_DIM) { finish() })
        overlay.addView(endLayout)
        overlayContainer?.addView(overlay)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    inner class GameView(context: android.content.Context) : View(context) {
        private val cupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val prizePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; alpha = 40
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; color = Color.WHITE
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val groundY = h * 0.75f
            val cupW = w * 0.18f
            val cupH = h * 0.22f

            val groundPaint = Paint().apply { color = Color.parseColor("#1A3020") }
            c.drawRect(0f, groundY, w, h, groundPaint)
            val linePaint = Paint().apply { color = Color.parseColor("#2A5038"); strokeWidth = 3f }
            c.drawLine(0f, groundY, w, groundY, linePaint)

            for (i in 0 until CUPS) {
                val cx = w * cupPositions[i]
                val cy = groundY - cupH / 2

                c.drawOval(cx - cupW * 0.6f, groundY + 2f, cx + cupW * 0.6f, groundY + 10f, shadowPaint)

                cupPaint.color = cupColors[i]
                c.drawRoundRect(RectF(cx - cupW / 2, cy - cupH / 2, cx + cupW / 2, cy + cupH / 2), 16f, 16f, cupPaint)

                val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = cupColors[i]; alpha = 180
                }
                c.drawRoundRect(RectF(cx - cupW * 0.55f, cy - cupH / 2 - 8f, cx + cupW * 0.55f, cy - cupH / 2 + 12f), 8f, 8f, rimPaint)

                textPaint.textSize = 28f
                c.drawText("${i + 1}", cx, cy + 10f, textPaint)
            }

            if (awaitingPick) {
                textPaint.textSize = 18f
                textPaint.color = Color.parseColor(UiKit.ACCENT)
                c.drawText("Tocca un calice!", w / 2, groundY + 40f, textPaint)
                textPaint.color = Color.WHITE
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action != MotionEvent.ACTION_DOWN || !awaitingPick) return false
            val w = width.toFloat()
            val touchX = ev.x
            for (i in 0 until CUPS) {
                val cx = w * cupPositions[i]
                if (Math.abs(touchX - cx) < w * 0.12f) {
                    pickCup(i)
                    return true
                }
            }
            return true
        }
    }
}
