package com.intelligame.huntix.minigames.ar

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.minigames.MiniGameBase

class AREggShooterActivity : MiniGameBase() {

    private fun ClosedRange<Float>.randomFloat() =
        (Math.random() * (endInclusive - start) + start).toFloat()

    private fun ClosedRange<Long>.randomLong() =
        (Math.random() * (endInclusive - start) + start).toLong()

    private data class Egg(
        var x: Float, var y: Float,
        var radius: Float, var type: Int,
        var life: Float = 1f, var maxLife: Float = 3f,
        var spawnTime: Long = System.currentTimeMillis(),
        var dead: Boolean = false, var popping: Boolean = false, var popAlpha: Float = 1f
    )

    private val eggs = mutableListOf<Egg>()
    private var lives = 3
    private var score = 0
    private var timeLeft = 30
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var shooterView: ShooterCanvasView
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var timerText: TextView
    private lateinit var livesText: TextView
    private var spawnRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null

    override fun onGameCreate() {
        setupUI()
        startGame()
    }

    private fun setupUI() {
        val c = this
        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(c, 8), UiKit.dp(c, 4), UiKit.dp(c, 8), UiKit.dp(c, 8))
        }

        val topRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(c, 8), 0, UiKit.dp(c, 8), UiKit.dp(c, 4))
        }

        livesText = TextView(c).apply {
            text = "❤️❤️❤️"; textSize = 14f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(livesText)

        timerText = TextView(c).apply {
            text = "⏱ 30s"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(timerText)

        scoreText = TextView(c).apply {
            text = "0 pt"; textSize = 14f; setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(scoreText)
        container.addView(topRow)

        statusText = TextView(c).apply {
            text = "Tocca le uova per colpirle! 🎯"
            textSize = 13f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 4))
        }
        container.addView(statusText)

        shooterView = ShooterCanvasView(c)
        shooterView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 400)
        )
        container.addView(shooterView)

        val legend = TextView(c).apply {
            text = "🥚=10pt  ✨=30pt  💣=-1 vita"
            textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM)); gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 4), 0, 0)
        }
        container.addView(legend)

        val scroll = UiKit.scroll(c, UiKit.title(c, "AR Egg Shooter", "🔫"), container)
        setContentView(scroll)
    }

    private fun startGame() {
        eggs.clear(); lives = 3; score = 0; timeLeft = 30; isRunning = true
        updateUI()
        timerText.setTextColor(Color.WHITE)

        timerRunnable?.let { handler.removeCallbacks(it) }
        spawnRunnable?.let { handler.removeCallbacks(it) }

        val timerTick = object : Runnable {
            override fun run() {
                if (!isRunning) return
                timeLeft--
                timerText.text = "⏱ ${timeLeft}s"
                if (timeLeft <= 10) timerText.setTextColor(Color.parseColor("#FF4444"))
                if (timeLeft <= 0) { endGame(); return }
                handler.postDelayed(this, 1000)
            }
        }
        timerRunnable = timerTick
        handler.postDelayed(timerTick, 1000)

        scheduleSpawn()
    }

    private fun scheduleSpawn() {
        if (!isRunning) return
        val spawner = object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (eggs.count { !it.dead } < 6) {
                    val type = when {
                        Math.random() < 0.15 -> 2
                        Math.random() < 0.3 -> 1
                        else -> 0
                    }
                    val sz = shooterView.width.toFloat()
                    val sy = shooterView.height.toFloat()
                    eggs.add(Egg(
                        x = (80f..(sz - 80f)).randomFloat(),
                        y = (80f..(sy - 80f)).randomFloat(),
                        radius = UiKit.dp(this@AREggShooterActivity, 24).toFloat(),
                        type = type,
                        maxLife = (2f..4f).randomFloat()
                    ))
                    shooterView.invalidate()
                }
                handler.postDelayed(this, (600L..1000L).randomLong())
            }
        }
        spawnRunnable = spawner
        handler.postDelayed(spawner, 300)
    }

    private fun updateUI() {
        livesText.text = "❤️".repeat(lives)
        scoreText.text = "$score pt"
    }

    private fun endGame() {
        isRunning = false
        timerRunnable?.let { handler.removeCallbacks(it) }
        spawnRunnable?.let { handler.removeCallbacks(it) }

        val reward = (score * 0.5).toInt().coerceAtMost(300)
        MiniGameManager.applyReward(this, MiniGameManager.GameReward(
            mvcCoins = reward,
            label = "AR Egg Shooter ($score pt)",
            isWin = score > 30
        ), MiniGameManager.GAME_AR_SHOOTER)

        statusText.text = "🎮 Fine! $score pt → +$reward MVC"
        statusText.setTextColor(Color.parseColor(UiKit.GREEN))
        val area = (statusText.parent as? LinearLayout)
        area?.addView(UiKit.button(this, "🔄 Gioca Ancora", UiKit.ACCENT) { startGame() })
    }

    inner class ShooterCanvasView(ctx: android.content.Context) : View(ctx) {

        private val bgPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val gridPaint = Paint().apply { color = Color.parseColor("#1A1030"); strokeWidth = 1f }
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(bgPaint.color)
            val step = UiKit.dp(context, 30).toFloat()
            var x = 0f; while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
            var y = 0f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }

            val now = System.currentTimeMillis()
            val iter = eggs.iterator()
            while (iter.hasNext()) {
                val egg = iter.next()
                val elapsed = (now - egg.spawnTime) / 1000f
                egg.life = (1f - elapsed / egg.maxLife).coerceAtLeast(0f)

                if (egg.life <= 0f && !egg.popping) { egg.dead = true; iter.remove(); continue }
                if (egg.popping) {
                    egg.popAlpha -= 0.08f
                    if (egg.popAlpha <= 0f) { iter.remove(); continue }
                }

                val alpha = ((if (egg.popping) egg.popAlpha else egg.life) * 255).toInt().coerceIn(0, 255)
                val emoji = when (egg.type) {
                    0 -> "🥚"; 1 -> "✨"; 2 -> "💣"; else -> "🥚"
                }
                textP.textSize = egg.radius * 1.6f
                val savedAlpha = textP.alpha
                textP.alpha = alpha
                canvas.drawText(emoji, egg.x, egg.y + egg.radius * 0.5f, textP)
                textP.alpha = savedAlpha
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN || !isRunning) return true
            val tx = event.x; val ty = event.y

            for (egg in eggs) {
                if (egg.dead || egg.popping) continue
                val dx = tx - egg.x; val dy = ty - egg.y
                if (dx * dx + dy * dy < egg.radius * egg.radius * 2f) {
                    when (egg.type) {
                        0 -> { score += 10; egg.popping = true }
                        1 -> { score += 30; egg.popping = true }
                        2 -> { lives = (lives - 1).coerceAtLeast(0); egg.popping = true }
                    }
                    updateUI()
                    invalidate()
                    if (lives <= 0) { endGame() }
                    break
                }
            }
            return true
        }
    }
}
