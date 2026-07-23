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
 * TaxiMiniGameActivity — mini-game taxi.
 * Il giocatore guida un taxi verso la destinazione del passeggero.
 * Il contachilometri sale col tempo: più veloce = più cash.
 */
class TaxiMiniGameActivity : AppCompatActivity() {

    private lateinit var gameView: TaxiGameView
    private lateinit var fareText: TextView
    private lateinit var timerText: TextView
    private var running = false
    private var fare = 0
    private var timeLeft = 20f
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            timeLeft -= 0.05f
            fare = ((20f - timeLeft) * 15).toInt().coerceIn(0, 300)
            fareText.text = "💵 \$$fare"
            timerText.text = "⏱ ${String.format("%.1f", timeLeft)}s"
            gameView.movePlayer()
            gameView.invalidate()
            if (timeLeft <= 0f) { running = false; gameOver(false); return }
            if (gameView.reached()) { running = false; gameOver(true); return }
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
        }

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
            text = "🚕  Taxi"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        timerText = TextView(c).apply {
            text = "⏱ 20.0s"; textSize = 14f; setTextColor(Color.parseColor("#FF5252"))
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(timerText)
        root.addView(topBar)

        fareText = TextView(c).apply {
            text = "💵 \$0"; textSize = 14f; setTextColor(Color.parseColor("#FFD86B"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 6), 0, UiKit.dp(c, 2))
        }
        root.addView(fareText)

        root.addView(TextView(c).apply {
            text = "Conduci il passeggero a 📍 — più veloce guadagni di più!"; textSize = 11f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(UiKit.dp(c, 14), 0, UiKit.dp(c, 14), UiKit.dp(c, 6))
        })

        gameView = TaxiGameView(c)
        root.addView(gameView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        running = true; timeLeft = 20f; fare = 0
        gameView.reset()
        handler.postDelayed(tick, 50)
    }

    override fun onPause() {
        super.onPause()
        running = false; handler.removeCallbacks(tick)
    }

    private fun gameOver(success: Boolean) {
        handler.removeCallbacks(tick)
        if (success && fare > 0) {
            MoneyManager.addCash(this, fare)
            MoneyManager.incrementJobsDone(this)
            Toast.makeText(this, "🚕 Arrivato! +\$$fare", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "⏰ Tempo scaduto!", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private class TaxiGameView(ctx: android.content.Context) : View(ctx) {
        private val taxiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFCA28.toInt() }
        private val destPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4CAF50.toInt() }
        private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF555565.toInt() }
        private val buildingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF707070.toInt() }
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFCA28.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
        }

        private var px = 0.5f; private var py = 0.5f
        private var dx = 0f; private var dy = 0f
        private val buildings = mutableListOf<Pair<RectF, Int>>()

        fun reset() {
            px = 0.1f + Random.nextFloat() * 0.3f
            py = 0.1f + Random.nextFloat() * 0.8f
            dx = 0.6f + Random.nextFloat() * 0.3f
            dy = 0.1f + Random.nextFloat() * 0.8f
            buildings.clear()
            for (i in 0 until 15) {
                val bx = Random.nextFloat() * 0.9f
                val by = Random.nextFloat() * 0.9f
                val bw = 0.04f + Random.nextFloat() * 0.07f
                val bh = 0.04f + Random.nextFloat() * 0.07f
                buildings.add(Pair(RectF(bx, by, bx + bw, by + bh),
                    intArrayOf(0xFF8B8B8B.toInt(), 0xFFA09070.toInt(), 0xFF708090.toInt()).random()))
            }
        }

        fun movePlayer() {
            val ddx = dx - px; val ddy = dy - py
            val dist = sqrt(ddx * ddx + ddy * ddy)
            if (dist > 0.01f) {
                px += (ddx / dist) * 0.007f
                py += (ddy / dist) * 0.007f
            }
            px = px.coerceIn(0.02f, 0.98f); py = py.coerceIn(0.02f, 0.98f)
        }

        fun reached(): Boolean {
            val ddx = dx - px; val ddy = dy - py
            return sqrt(ddx * ddx + ddy * ddy) < 0.04f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawColor(0xFF1A1A2E.toInt())

            val gridStep = w / 8f
            var i = 0f; while (i <= w) { canvas.drawRect(i - 4f, 0f, i + 4f, h, roadPaint); i += gridStep }
            var j = 0f; while (j <= h) { canvas.drawRect(0f, j - 4f, w, j + 4f, roadPaint); j += gridStep }

            for ((rect, color) in buildings) {
                buildingPaint.color = color
                canvas.drawRoundRect(rect.left * w, rect.top * h, rect.right * w, rect.bottom * h, 4f, 4f, buildingPaint)
            }

            // Destination
            val destCx = dx * w; val destCy = dy * h
            canvas.drawCircle(destCx, destCy, 14f, destPaint)
            canvas.drawCircle(destCx, destCy, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

            // Arrow
            val pcx = px * w; val pcy = py * h
            val adx = destCx - pcx; val ady = destCy - pcy
            val adist = sqrt(adx * adx + ady * ady)
            if (adist > 30f) {
                canvas.drawLine(pcx, pcy, pcx + adx * 0.7f, pcy + ady * 0.7f, arrowPaint)
            }

            // Taxi (yellow square with "TAXI" text)
            canvas.drawRoundRect(pcx - 12f, pcy - 8f, pcx + 12f, pcy + 8f, 4f, 4f, taxiPaint)
            val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 10f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
            }
            canvas.drawText("TAXI", pcx, pcy + 4f, txtPaint)
        }
    }
}
