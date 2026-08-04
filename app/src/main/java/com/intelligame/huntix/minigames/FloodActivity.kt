package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🌊 Flood — riempi la griglia con un solo colore.
 *
 * Originale: GunshipPenguin/open_flood (MIT)
 * Adattamento: logica di gioco preservata, rendering nel pattern Canvas di Huntix.
 */
class FloodActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val BOARD = 14
    private val NUM_COLORS = 6
    private val MAX_STEPS = 30 * (BOARD * NUM_COLORS) / (17 * 6)

    private val palette = listOf(
        Color.parseColor("#EF5350"), // 1 rosso
        Color.parseColor("#42A5F5"), // 2 blu
        Color.parseColor("#66BB6A"), // 3 verde
        Color.parseColor("#FFCA28"), // 4 giallo
        Color.parseColor("#AB47BC"), // 5 viola
        Color.parseColor("#26C6DA")  // 6 ciano
    )

    private var board = Array(BOARD) { IntArray(BOARD) }
    private var steps = 0
    private var lastColor = 0
    private var score = 0
    private var gameRunning = false
    private var scoreText: TextView? = null
    private var stepsText: TextView? = null
    private var gameView: FloodView? = null
    private var overlayContainer: FrameLayout? = null

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + UiKit.dp(ctx, 14))
            insets
        }
        root.addView(UiKit.title(ctx, "Flood", "🌊"))
        root.addView(TextView(ctx).apply {
            text = "Tocca i colori per inondare la griglia. Un solo colore vince!"
            textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_FLOOD))
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 18f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 4))
        }
        root.addView(scoreText!!)
        stepsText = TextView(ctx).apply {
            text = "Mosse: 0 / $MAX_STEPS"; textSize = 14f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(stepsText!!)

        gameView = FloodView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(colorButtons(ctx))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun colorButtons(ctx: android.content.Context): View {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, UiKit.dp(ctx, 10), 0, 0)
        }
        for (i in 0 until NUM_COLORS) {
            val idx = i
            val btn = View(ctx).apply {
                isClickable = true
                isFocusable = true
                val bg = GradientDrawable().apply {
                    setColor(palette[idx])
                    setStroke(UiKit.dp(ctx, 2), Color.WHITE)
                    cornerRadius = 0f
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(ctx, 52), 1f)
                setOnClickListener {
                    if (gameRunning && idx != lastColor) doColor(idx)
                }
            }
            val margin = LinearLayout.LayoutParams(0, UiKit.dp(ctx, 52), 1f)
            margin.setMargins(UiKit.dp(ctx, 3), 0, UiKit.dp(ctx, 3), 0)
            btn.layoutParams = margin
            bar.addView(btn)
        }
        return bar
    }

    private fun startGame() {
        val r = Random(System.currentTimeMillis())
        for (y in 0 until BOARD) for (x in 0 until BOARD) board[y][x] = r.nextInt(NUM_COLORS)
        steps = 0
        lastColor = board[0][0]
        score = 0
        gameRunning = true
        scoreText?.text = "Punti: 0"
        stepsText?.text = "Mosse: 0 / $MAX_STEPS"
        gameView?.invalidate()
    }

    private fun doColor(color: Int) {
        flood(color)
        lastColor = color
        scoreText?.text = "Punti: $score"
        stepsText?.text = "Mosse: $steps / $MAX_STEPS"
        gameView?.invalidate()
        if (checkWin() || steps >= MAX_STEPS) {
            val won = checkWin()
            endGame(won)
        }
    }

    private fun flood(replacement: Int) {
        val target = board[0][0]
        if (target == replacement) return
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.addLast(0 to 0)
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (board[y][x] == target) {
                board[y][x] = replacement
                if (x != 0) queue.addLast(x - 1 to y)
                if (x != BOARD - 1) queue.addLast(x + 1 to y)
                if (y != 0) queue.addLast(x to y - 1)
                if (y != BOARD - 1) queue.addLast(x to y + 1)
            }
        }
        steps++
        // Punti: più mosse risparmi, più punti.
        score = (MAX_STEPS - steps) * 10 + if (checkWin()) 100 else 0
    }

    private fun checkWin(): Boolean {
        val c = board[0][0]
        for (y in 0 until BOARD) for (x in 0 until BOARD) if (board[y][x] != c) return false
        return true
    }

    private fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)

        val mvc = if (won) (score * 0.2f).toInt().coerceAtLeast(10) else kotlin.math.max(score / 5, 3)
        val xp = if (won) (score * 0.1f).toInt().coerceAtLeast(5) else kotlin.math.max(score / 8, 2)

        val result = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_FLOOD, score,
                mvc = mvc, xp = xp,
                label = "Flood: ${if (won) "vittoria!" else "sconfitta"}",
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
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        endLayout.addView(TextView(ctx).apply {
            text = "🌊"; textSize = 48f; gravity = Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Hai Vinto!" else "Mosse Esaurite!"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $score  •  Mosse: $steps"
            textSize = 18f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        result?.let { endLayout.addView(levelResultView(it)) }
        endLayout.addView(TextView(ctx).apply {
            text = "+${result?.mvc ?: mvc} MVC  •  +${result?.xp ?: xp} XP"
            textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
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
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    inner class FloodView(context: android.content.Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gap = 1

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val dim = minOf(w, h)
            val cell = dim / BOARD
            val xOff = (w - dim) / 2
            val yOff = (h - dim) / 2
            c.drawColor(Color.parseColor("#0D0620"))
            for (y in 0 until BOARD) {
                for (x in 0 until BOARD) {
                    paint.color = palette[board[y][x].coerceIn(0, NUM_COLORS - 1)]
                    c.drawRect(
                        xOff + x * cell + gap, yOff + y * cell + gap,
                        xOff + (x + 1) * cell - gap, yOff + (y + 1) * cell - gap,
                        paint
                    )
                }
            }
        }
    }
}
