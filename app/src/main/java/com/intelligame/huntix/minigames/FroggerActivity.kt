package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.math.abs

/**
 * 🐸 Frogger — porta la rana dall'altra parte del fiume!
 *
 * Originale: dianjiaogit/Frogger_Android (MIT)
 * Adattamento: logica di gioco preservata, rendering nel pattern Canvas di Huntix.
 */
class FroggerActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val STEP_MS = 100L

    private var score = 0
    private var time = 0f
    private var gameRunning = false
    private var won = false
    private var dead = false
    private var scoreText: TextView? = null
    private var gameView: FroggerView? = null
    private var overlayContainer: FrameLayout? = null

    // Grid (base HEIGHT/12 = UNIT)
    private var unit = 0f
    private var logRows = 0f
    private var truckRows = 0f
    private var startY = 0f
    private var endY = 0f

    private var frogX = 0f
    private var frogY = 0f
    private val radius = 10f

    private val logs = mutableListOf<MovingRect>()
    private val trucks = mutableListOf<MovingRect>()
    private var speed = 0

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            step()
            gameView?.invalidate()
            handler.postDelayed(this, STEP_MS)
        }
    }

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Frogger", "🐸"))
        root.addView(TextView(ctx).apply {
            text = "Swipe per muovere la rana. Evita i camion e attraversa il fiume sui tronchi!"
            textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_FROGGER))
        scoreText = TextView(ctx).apply {
            text = "Tempo: 0.0s"
            textSize = 16f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        gameView = FroggerView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        val v = gameView ?: return
        v.post {
            val w = v.width.coerceAtLeast(1)
            val h = v.height.coerceAtLeast(1)
            startGameWithSize(w, h)
        }
    }

    private fun startGameWithSize(w: Int, h: Int) {
        unit = h / 12f
        endY = unit
        logRows = unit * 5
        truckRows = unit * 9
        startY = unit * 10
        frogX = w / 2f
        frogY = (startY - truckRows) / 2f + truckRows
        speed = 2 + (2f * levelDifficulty(MiniGameManager.GAME_FROGGER)).toInt()

        // Tronchi: 4 righe tra endY e logRows
        logs.clear()
        var iy = endY + unit / 6f
        var toLeft = true
        for (i in 0 until 4) {
            var ix = 0f
            for (j in 0 until 3) {
                logs.add(MovingRect(ix, iy, toLeft, w * 3 / 12f, unit * 2 / 3))
                ix += w / 3f
            }
            iy += unit
            toLeft = !toLeft
        }
        // Camion: 4 righe tra logRows e truckRows
        trucks.clear()
        iy = logRows + unit / 6f
        toLeft = true
        for (i in 0 until 4) {
            var ix = 0f
            for (j in 0 until 2) {
                trucks.add(MovingRect(ix, iy, toLeft, w / 6f, unit * 2 / 3))
                ix += w / 2f
            }
            iy += unit
            toLeft = !toLeft
        }

        score = 0
        time = 0f
        won = false
        dead = false
        gameRunning = true
        scoreText?.text = "Tempo: 0.0s"
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, STEP_MS)
    }

    private fun step() {
        val v = gameView ?: return
        val w = v.width.coerceAtLeast(1)

        val logSpeed = speed + 2
        for (m in logs) m.step(w, logSpeed)
        for (m in trucks) m.step(w, speed)

        // Rana sui tronchi
        for (m in logs) {
            if (frogY > m.y && frogY < m.y + m.h && frogX >= m.x && frogX <= m.x + m.width) {
                frogX += if (m.toLeft) -speed else speed
            }
        }
        // Collisione camion
        for (m in trucks) {
            if (frogY > m.y && frogY < m.y + m.h && frogX - m.x <= m.width && m.x - frogX <= radius) {
                dead = true
            }
        }
        // Caduta in acqua
        if (!dead && frogInWater(v)) dead = true

        time += 0.1f
        scoreText?.text = "Tempo: ${"%.1f".format(time)}s"

        if (frogY < endY) won = true
        if (dead || won) endGame(won)
    }

    private fun frogInWater(v: View): Boolean {
        if (frogY <= logRows && frogY >= endY) {
            var onLog = false
            for (m in logs) {
                if (frogY > m.y && frogY < m.y + m.h && frogX >= m.x && frogX <= m.x + m.width) {
                    onLog = true
                }
            }
            if (!onLog) return true
        }
        return false
    }

    private fun endGame(win: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)

        val finalScore = if (win) (60f / time * 100).toInt().coerceAtLeast(100) else (time * 10).toInt().coerceAtLeast(1)
        val mvc = if (win) 30 else 5
        val xp = if (win) 15 else 3

        val result = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_FROGGER, finalScore,
                mvc = mvc, xp = xp,
                label = "Frogger: ${if (win) "attraversato!" else "perso"}",
                isWin = win
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
            text = if (win) "🐸🏆" else "💀"; textSize = 48f; gravity = Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = if (win) "Rana Salva!" else "Game Over!"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $finalScore  •  Tempo: ${"%.1f".format(time)}s"
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

    inner class FroggerView(context: android.content.Context) : View(context) {

        private val bgPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val waterPaint = Paint().apply { color = Color.parseColor("#0A3D5C") }
        private val roadPaint = Paint().apply { color = Color.parseColor("#1A1030") }
        private val logPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8D6E63") }
        private val truckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350") }
        private val frogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66BB6A") }
        private var startX = 0f
        private var startY = 0f

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            c.drawRect(0f, 0f, w, h, bgPaint)

            // Acqua (fiume)
            c.drawRect(0f, endY, w, logRows, waterPaint)
            // Strada
            c.drawRect(0f, logRows, w, truckRows, roadPaint)

            for (m in logs) c.drawRoundRect(
                android.graphics.RectF(m.x, m.y, m.x + m.width, m.y + m.h),
                6f, 6f, logPaint
            )
            for (m in trucks) c.drawRoundRect(
                android.graphics.RectF(m.x, m.y, m.x + m.width, m.y + m.h),
                4f, 4f, truckPaint
            )

            c.drawCircle(frogX, frogY, radius, frogPaint)

            // Meta
            val metaPaint = Paint().apply { color = Color.parseColor("#FFCA28"); textAlign = Paint.Align.CENTER; textSize = 14f }
            c.drawText("🏁", w / 2, endY + 18, metaPaint)
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
                    val v = gameView ?: return true
                    if (abs(dx) < 30 && abs(dy) < 30) return true
                    when {
                        abs(dx) > abs(dy) ->
                            if (dx > 0) frogX = (frogX + 30).coerceAtMost(v.width - radius)
                            else frogX = (frogX - 30).coerceAtLeast(radius)
                        else ->
                            if (dy > 0) frogY = (frogY + unit).coerceAtMost(startY)
                            else frogY = (frogY - unit).coerceAtLeast(0f)
                    }
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    private class MovingRect(
        var x: Float,
        val y: Float,
        val toLeft: Boolean,
        val width: Float,
        val h: Float
    ) {
        fun step(w: Int, spd: Int) {
            x = if (toLeft) {
                val nx = x - spd
                if (nx + width < 0) w.toFloat() else nx
            } else {
                val nx = x + spd
                if (nx > w) -width else nx
            }
        }
    }
}
