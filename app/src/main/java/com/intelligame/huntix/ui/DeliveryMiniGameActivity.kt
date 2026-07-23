package com.intelligame.huntix.ui

import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.reallife.MoneyManager
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * DeliveryMiniGameActivity — mini-game consegna pacchi.
 *
 * Il giocatore muove un punto verso la destinazione (freccia indicatrice).
 * Ha N secondi per arrivare. Guadagna cash proporzionale al tempo rimasto.
 */
class DeliveryMiniGameActivity : AppCompatActivity() {

    private lateinit var gameView: DeliveryGameView
    private lateinit var timerText: TextView
    private lateinit var scoreText: TextView
    private var running = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            timeLeft -= 0.05f
            if (timeLeft <= 0f) {
                timeLeft = 0f; running = false; gameOver(false)
                return
            }
            timerText.text = "⏱ ${String.format("%.1f", timeLeft)}s"
            gameView.movePlayer()
            gameView.invalidate()
            if (gameView.reached()) {
                running = false; gameOver(true)
                return
            }
            handler.postDelayed(this, 50)
        }
    }

    private var timeLeft = 15f
    private var totalTime = 15f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
        }

        // Top bar
        val topBar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0A0618"))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
        }
        topBar.addView(TextView(c).apply {
            text = "← "; textSize = 20f; setTextColor(Color.parseColor(UiKit.ACCENT))
            isClickable = true; setOnClickListener { finish() }
        })
        topBar.addView(TextView(c).apply {
            text = "📦  Consegna"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        timerText = TextView(c).apply {
            text = "⏱ 15.0s"; textSize = 14f; setTextColor(Color.parseColor("#FF5252"))
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(timerText)
        root.addView(topBar)

        // Score
        scoreText = TextView(c).apply {
            text = "💰 \$0"; textSize = 13f; setTextColor(Color.parseColor("#FFD86B"))
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 6), 0, UiKit.dp(c, 2))
        }
        root.addView(scoreText)

        // Instructions
        root.addView(TextView(c).apply {
            text = "Muovi il joystick per raggiungere 📍 prima del tempo!"; textSize = 11f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 14), 0, UiKit.dp(c, 14), UiKit.dp(c, 6))
        })

        // Game view
        gameView = DeliveryGameView(c)
        root.addView(gameView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        running = true
        timeLeft = totalTime
        gameView.reset()
        handler.postDelayed(tick, 50)
    }

    override fun onPause() {
        super.onPause()
        running = false
        handler.removeCallbacks(tick)
    }

    private fun gameOver(success: Boolean) {
        handler.removeCallbacks(tick)
        if (success) {
            val ratio = (timeLeft / totalTime).coerceIn(0f, 1f)
            val pay = (80 + (120 * ratio)).toInt()
            MoneyManager.addCash(this, pay)
            MoneyManager.incrementJobsDone(this)
            Toast.makeText(this, "✅ Consegna completata! +\$${pay}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "⏰ Tempo scaduto!", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    /**
     * DeliveryGameView — vista 2D del mini-game consegna.
     * Mostra strade, edifici, punto giocatore (blu), destinazione (rosso), freccia indicatrice.
     */
    private class DeliveryGameView(ctx: android.content.Context) : View(ctx) {

        private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF42A5F5.toInt() }
        private val destPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
        private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF555565.toInt() }
        private val buildingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF808080.toInt() }
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFCA28.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
        }
        private val arrowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFCA28.toInt() }

        private var px = 0.5f; private var py = 0.5f  // normalized [0,1]
        private var dx = 0f; private var dy = 0f        // destination normalized
        private var playerScreenX = 0f; private var playerScreenY = 0f

        private val buildings = mutableListOf<Pair<RectF, Int>>()

        fun reset() {
            px = 0.1f + Random.nextFloat() * 0.3f
            py = 0.1f + Random.nextFloat() * 0.8f
            dx = 0.6f + Random.nextFloat() * 0.3f
            dy = 0.1f + Random.nextFloat() * 0.8f
            buildings.clear()
            // Generate some buildings
            for (i in 0 until 12) {
                val bx = Random.nextFloat() * 0.9f
                val by = Random.nextFloat() * 0.9f
                val bw = 0.05f + Random.nextFloat() * 0.08f
                val bh = 0.05f + Random.nextFloat() * 0.08f
                val color = intArrayOf(0xFF8B8B8B.toInt(), 0xFFA09070.toInt(), 0xFF708090.toInt(), 0xFF906050.toInt()).random()
                buildings.add(Pair(RectF(bx, by, bx + bw, by + bh), color))
            }
        }

        fun movePlayer() {
            // Auto-move toward destination (simulates joystick input)
            val ddx = dx - px; val ddy = dy - py
            val dist = sqrt(ddx * ddx + ddy * ddy)
            if (dist > 0.01f) {
                px += (ddx / dist) * 0.008f
                py += (ddy / dist) * 0.008f
            }
            px = px.coerceIn(0.02f, 0.98f)
            py = py.coerceIn(0.02f, 0.98f)
        }

        fun reached(): Boolean {
            val ddx = dx - px; val ddy = dy - py
            return sqrt(ddx * ddx + ddy * ddy) < 0.04f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()

            // Background
            canvas.drawColor(0xFF1A1A2E.toInt())

            // Roads
            val gridStep = w / 8f
            var i = 0f
            while (i <= w) {
                canvas.drawRect(i - 4f, 0f, i + 4f, h, roadPaint)
                i += gridStep
            }
            var j = 0f
            while (j <= h) {
                canvas.drawRect(0f, j - 4f, w, j + 4f, roadPaint)
                j += gridStep
            }

            // Buildings
            for ((rect, color) in buildings) {
                buildingPaint.color = color
                canvas.drawRoundRect(rect.left * w, rect.top * h, rect.right * w, rect.bottom * h, 4f, 4f, buildingPaint)
            }

            // Destination
            val destCx = dx * w; val destCy = dy * h
            canvas.drawCircle(destCx, destCy, 14f, destPaint)
            canvas.drawCircle(destCx, destCy, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

            // Arrow to destination
            val pcx = px * w; val pcy = py * h
            val adx = destCx - pcx; val ady = destCy - pcy
            val adist = sqrt(adx * adx + ady * ady)
            if (adist > 30f) {
                // Draw arrow shaft
                canvas.drawLine(pcx, pcy, pcx + adx * 0.7f, pcy + ady * 0.7f, arrowPaint)
                // Arrowhead
                val angle = Math.atan2(ady.toDouble(), adx.toDouble()).toFloat()
                val headLen = 15f
                canvas.drawLine(
                    pcx + adx * 0.7f, pcy + ady * 0.7f,
                    pcx + adx * 0.7f - headLen * Math.cos((angle - 0.5).toDouble()).toFloat(),
                    pcy + ady * 0.7f - headLen * Math.sin((angle - 0.5).toDouble()).toFloat(),
                    arrowPaint
                )
                canvas.drawLine(
                    pcx + adx * 0.7f, pcy + ady * 0.7f,
                    pcx + adx * 0.7f - headLen * Math.cos((angle + 0.5).toDouble()).toFloat(),
                    pcy + ady * 0.7f - headLen * Math.sin((angle + 0.5).toDouble()).toFloat(),
                    arrowPaint
                )
            }

            // Player
            canvas.drawCircle(pcx, pcy, 10f, playerPaint)
            canvas.drawCircle(pcx, pcy, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

            playerScreenX = pcx; playerScreenY = pcy
        }
    }
}
