package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.math.log2
import kotlin.random.Random

/**
 * 🧩 2048 Uova — unisci le uova dello stesso livello scorrendo!
 *
 * Il classico 2048 con unova. Ogni uovo ha una "giara" (tier):
 * bianca → verde → blu → viola → oro → arcobaleno.
 * Scorri in una direzione per far scivolare tutte le uova:
 * due uova dello stesso livello si fondono in una più potente!
 *
 * Controlla:
 * - Scorri ← → ↑ ↓ per muovere tutte le uova nella direzione
 * - Uova + Uova = Uova superiore (2+2=4, 4+4=8, ...)
 * - Arriva all'Uovo Arcobaleno (2048) per vincere!
 */
class Game2048Activity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val board = IntArray(16)
    private var score = 0
    private var gameRunning = false
    private var reached2048 = false
    private var scoreText: TextView? = null
    private var boardView: BoardView? = null
    private var overlayContainer: FrameLayout? = null

    companion object {
        private val TIER_COLORS = listOf(
            0xFFEAD7A1.toInt(), 0xFFE4B978.toInt(), 0xFFE88E5A.toInt(),
            0xFFF2B179.toInt(), 0xFFF59563.toInt(), 0xFFCE5FA8.toInt(),
            0xFF8E7CE8.toInt(), 0xFF5F9EE9.toInt(), 0xFF57D6D9.toInt(),
            0xFF66E07A.toInt(), 0xFFE6C84D.toInt(), 0xFFFFD700.toInt()
        )
        private val TIER_EMOJIS = listOf(
            "🥚", "🥚", "🥚", "🥚", "🥚", "🥚", "🥚", "🥚", "🥚", "🥚", "✨", "🌈"
        )
    }

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "2048 Uova", "🧩"))
        root.addView(TextView(ctx).apply {
            text = "⇦⇨⇩⇧ Scorri per fondere le uova dello stesso livello!\n2+2=4, 4+4=8, ... fino all'Uovo Arcobaleno 🈯"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_2048))

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 18f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(scoreText!!)
        header.addView(TextView(ctx).apply {
            text = "🌈 2048"; textSize = 18f; setTextColor(Color.parseColor(UiKit.ACCENT))
        })
        root.addView(header)

        boardView = BoardView(ctx)
        root.addView(boardView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        board.fill(0)
        score = 0
        reached2048 = false
        gameRunning = true
        scoreText?.text = "Punti: 0"
        addRandomTile()
        addRandomTile()
        boardView?.invalidate()
    }

    private fun addRandomTile() {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return
        board[empty[Random.nextInt(empty.size)]] = if (Random.nextFloat() < 0.9f) 2 else 4
    }

    /** Unisce una riga verso sinistra. Ritorna la nuova riga e i punti guadagnati. */
    private fun mergeLeft(line: IntArray): Pair<IntArray, Int> {
        val nz = line.filter { it != 0 }
        val out = mutableListOf<Int>()
        var pts = 0
        var i = 0
        while (i < nz.size) {
            if (i + 1 < nz.size && nz[i] == nz[i + 1]) {
                val v = nz[i] * 2
                out.add(v); pts += v; i += 2
            } else {
                out.add(nz[i]); i += 1
            }
        }
        while (out.size < 4) out.add(0)
        return out.toIntArray() to pts
    }

    private fun move(direction: Int): Boolean {
        val before = board.copyOf()
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                val line = when (direction) {
                    0 -> intArrayOf(board[r * 4 + 0], board[r * 4 + 1], board[r * 4 + 2], board[r * 4 + 3])
                    1 -> intArrayOf(board[r * 4 + 3], board[r * 4 + 2], board[r * 4 + 1], board[r * 4 + 0])
                    2 -> intArrayOf(board[0 * 4 + c], board[1 * 4 + c], board[2 * 4 + c], board[3 * 4 + c])
                    else -> intArrayOf(board[3 * 4 + c], board[2 * 4 + c], board[1 * 4 + c], board[0 * 4 + c])
                }
                val (merged, pts) = mergeLeft(line)
                when (direction) {
                    0 -> for (k in 0 until 4) board[r * 4 + k] = merged[k]
                    1 -> for (k in 0 until 4) board[r * 4 + k] = merged[3 - k]
                    2 -> for (k in 0 until 4) board[k * 4 + c] = merged[k]
                    else -> for (k in 0 until 4) board[k * 4 + c] = merged[3 - k]
                }
                score += pts
            }
        }
        if (!board.contentEquals(before)) {
            addRandomTile()
            scoreText?.text = "Punti: $score"
            if (board.any { it >= 2048 }) reached2048 = true
            if (!hasMoves()) endGame()
            return true
        }
        return false
    }

    private fun hasMoves(): Boolean {
        if (board.any { it == 0 }) return true
        for (r in 0 until 4) for (c in 0 until 4) {
            val v = board[r * 4 + c]
            if (c < 3 && board[r * 4 + c + 1] == v) return true
            if (r < 3 && board[(r + 1) * 4 + c] == v) return true
        }
        return false
    }

    private fun endGame() {
        if (!gameRunning) return
        gameRunning = false
        val won = reached2048
        val maxEgg = board.maxOrNull() ?: 2
        val mvc = if (won) 80 else (maxEgg / 2).coerceAtLeast(10).coerceAtMost(200)
        val xp = if (won) 30 else (maxEgg / 4).coerceAtLeast(3).coerceAtMost(80)
        val result = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_2048, score,
                mvc = mvc, xp = xp,
                giftEggRarityId = if (won) "uncommon" else null,
                label = if (won) "2048 Uova: Uovo Arcobaleno!" else "2048 Uova: max $maxEgg",
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
            text = if (won) "🌈" else "🥚"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (won) "Hai creato l'Uovo Arcobaleno!" else "Nessuna mossa disponibile"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $score  •  Max: $maxEgg"; textSize = 18f
            setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        result?.let { endLayout.addView(levelResultView(it)) }
        endLayout.addView(TextView(ctx).apply {
            text = "+${result?.mvc ?: mvc} MVC  •  +${result?.xp ?: xp} XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
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
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    inner class BoardView(context: android.content.Context) : View(context) {

        private val textDark = Color.parseColor("#776E65")
        private val textLight = Color.WHITE

        private var startX = 0f
        private var startY = 0f

        private fun valueToTier(v: Int): Int = if (v <= 2) 0 else log2(v.toFloat()).toInt() - 1

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val gap = w * 0.02f
            val size = (w - gap * 5) / 4
            val cell = RectF()

            val cellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1030") }
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2C55") }

            for (r in 0 until 4) for (col in 0 until 4) {
                val left = gap + col * (size + gap)
                val top = gap + r * (size + gap)
                cell.set(left, top, left + size, top + size)
                c.drawRoundRect(cell, size * 0.04f, size * 0.04f, cellBg)
            }

            for (r in 0 until 4) for (col in 0 until 4) {
                val v = board[r * 4 + col]
                if (v == 0) continue
                val left = gap + col * (size + gap)
                val top = gap + r * (size + gap)
                val cx = left + size / 2f
                val cy = top + size / 2f
                val tier = valueToTier(v)
                val color = if (tier < TIER_COLORS.size) TIER_COLORS[tier] else TIER_COLORS.last()
                val emoji = if (tier < TIER_EMOJIS.size) TIER_EMOJIS[tier] else TIER_EMOJIS.last()

                val eggRadius = size * 0.25f + tier * size * 0.015f
                val eggPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                val rf = RectF(cx - eggRadius, cy - eggRadius * 1.2f, cx + eggRadius, cy + eggRadius * 1.2f)
                c.drawOval(rf, eggPaint)

                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.argb(100, Color.red(color), Color.green(color), Color.blue(color))
                }
                c.drawOval(
                    RectF(cx - eggRadius - 2f, cy - eggRadius * 1.2f - 2f, cx + eggRadius + 2f, cy + eggRadius * 1.2f + 2f),
                    glowPaint
                )

                val label = if (tier >= 8) emoji else ""
                val valueText = v.toString()
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                    this.color = if (tier <= 1) textDark else textLight
                    textSize = size * 0.22f
                }
                val baseline = cy + (size - textPaint.textSize) / 2f - textPaint.ascent() / 2f
                if (label.isNotEmpty()) {
                    textPaint.textSize = size * 0.20f
                    c.drawText(label, cx, cy - eggRadius - 4f, textPaint)
                }
                c.drawText(valueText, cx, baseline + size * 0.15f, textPaint)
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x; startY = ev.y
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!gameRunning) return true
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    if (Math.abs(dx) < 30 && Math.abs(dy) < 30) return true
                    val direction = if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0) 1 else 0
                    } else {
                        if (dy > 0) 3 else 2
                    }
                    move(direction)
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}
