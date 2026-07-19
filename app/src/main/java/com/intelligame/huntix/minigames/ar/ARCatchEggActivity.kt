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

class ARCatchEggActivity : MiniGameBase() {

    private fun ClosedRange<Float>.randomFloat() =
        (Math.random() * (endInclusive - start) + start).toFloat()

    private fun ClosedRange<Long>.randomLong() =
        (Math.random() * (endInclusive - start) + start).toLong()

    private data class FloatingEgg(
        var x: Float, var y: Float,
        var vx: Float,
        var amplitude: Float, var frequency: Float,
        var phase: Float, var baseY: Float,
        var type: Int, var radius: Float,
        var dead: Boolean = false,
        var popping: Boolean = false, var popAlpha: Float = 1f
    )

    private val eggs = mutableListOf<FloatingEgg>()
    private var lives = 3
    private var score = 0
    private var timeLeft = 40
    private var isRunning = false
    private var speedMult = 1f
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var catchView: CatchCanvasView
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var timerText: TextView
    private lateinit var livesText: TextView
    private var timerRunnable: Runnable? = null
    private var spawnRunnable: Runnable? = null
    private var frameRunnable: Runnable? = null

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
            text = "⏱ 40s"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
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
            text = "Tocca le uova in movimento! 🎯"
            textSize = 13f; setTextColor(Color.parseColor(UiKit.ACCENT)); gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiKit.dp(c, 4))
        }
        container.addView(statusText)

        catchView = CatchCanvasView(c)
        catchView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 400)
        )
        container.addView(catchView)

        val legend = TextView(c).apply {
            text = "🥚=10  ✨=25  👑=100(5%)  💣=-1 vita"
            textSize = 11f; setTextColor(Color.parseColor(UiKit.TEXT_DIM)); gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 4), 0, 0)
        }
        container.addView(legend)

        val scroll = UiKit.scroll(c, UiKit.title(c, "AR Prendi Uovo", "🥚"), container)
        setContentView(scroll)
    }

    private fun startGame() {
        eggs.clear(); lives = 3; score = 0; timeLeft = 40; isRunning = true; speedMult = 1f
        updateUI()
        timerText.setTextColor(Color.WHITE)

        timerRunnable?.let { handler.removeCallbacks(it) }
        spawnRunnable?.let { handler.removeCallbacks(it) }
        frameRunnable?.let { handler.removeCallbacks(it) }

        val timerTick = object : Runnable {
            override fun run() {
                if (!isRunning) return
                timeLeft--
                timerText.text = "⏱ ${timeLeft}s"
                if (timeLeft <= 10) timerText.setTextColor(Color.parseColor("#FF4444"))
                speedMult = 1f + (40 - timeLeft) * 0.02f
                if (timeLeft <= 0) { endGame(); return }
                handler.postDelayed(this, 1000)
            }
        }
        timerRunnable = timerTick
        handler.postDelayed(timerTick, 1000)

        scheduleSpawn()

        val frameTick = object : Runnable {
            override fun run() {
                if (!isRunning) return
                updateEggs()
                catchView.invalidate()
                handler.postDelayed(this, 33)
            }
        }
        frameRunnable = frameTick
        handler.postDelayed(frameTick, 33)
    }

    private fun scheduleSpawn() {
        if (!isRunning) return
        val spawner = object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (eggs.count { !it.dead } < 8) {
                    spawnEgg()
                }
                handler.postDelayed(this, (500L..900L).randomLong())
            }
        }
        spawnRunnable = spawner
        handler.postDelayed(spawner, 200)
    }

    private fun spawnEgg() {
        val w = catchView.width.toFloat().coerceAtLeast(1f)
        val h = catchView.height.toFloat().coerceAtLeast(1f)
        val type = when {
            Math.random() < 0.05 -> 3
            Math.random() < 0.15 -> 2
            Math.random() < 0.35 -> 1
            else -> 0
        }
        val dirX = if (Math.random() < 0.5) 1f else -1f
        val baseSpeed = (1.5f..3f).randomFloat() * speedMult
        val eggBaseY = (h * 0.2f..h * 0.8f).randomFloat()

        eggs.add(FloatingEgg(
            x = if (dirX > 0) -40f else w + 40f,
            y = eggBaseY,
            vx = baseSpeed * dirX,
            amplitude = (20f..50f).randomFloat(),
            frequency = (0.02f..0.05f).randomFloat(),
            phase = (0f..6.28f).randomFloat(),
            baseY = eggBaseY,
            type = type,
            radius = UiKit.dp(this, 22).toFloat()
        ))
    }

    private fun updateEggs() {
        val iter = eggs.iterator()
        while (iter.hasNext()) {
            val egg = iter.next()
            if (egg.popping) {
                egg.popAlpha -= 0.06f
                if (egg.popAlpha <= 0f) { iter.remove(); continue }
                continue
            }
            egg.x += egg.vx
            egg.phase += egg.frequency
            egg.y = egg.baseY + (Math.sin(egg.phase.toDouble()) * egg.amplitude).toFloat()

            if (egg.x < -80f || egg.x > catchView.width + 80f) {
                egg.dead = true; iter.remove()
            }
        }
    }

    private fun updateUI() {
        livesText.text = "❤️".repeat(lives)
        scoreText.text = "$score pt"
    }

    private fun endGame() {
        isRunning = false
        timerRunnable?.let { handler.removeCallbacks(it) }
        spawnRunnable?.let { handler.removeCallbacks(it) }
        frameRunnable?.let { handler.removeCallbacks(it) }

        val reward = (score * 0.6).toInt().coerceAtMost(350)
        MiniGameManager.applyReward(this, MiniGameManager.GameReward(
            mvcCoins = reward,
            label = "AR Catch Egg ($score pt)",
            isWin = score > 40
        ), MiniGameManager.GAME_CATCH_EGG)

        statusText.text = "🎮 Fine! $score pt → +$reward MVC"
        statusText.setTextColor(Color.parseColor(UiKit.GREEN))
        val area = (statusText.parent as? LinearLayout)
        area?.addView(UiKit.button(this, "🔄 Gioca Ancora", UiKit.ACCENT) { startGame() })
    }

    inner class CatchCanvasView(ctx: android.content.Context) : View(ctx) {
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

            for (egg in eggs) {
                val alpha = ((if (egg.popping) egg.popAlpha else 1f) * 255).toInt().coerceIn(0, 255)
                val emoji = when (egg.type) {
                    0 -> "🥚"; 1 -> "✨"; 2 -> "💣"; 3 -> "👑"; else -> "🥚"
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
                val hitRadius = egg.radius * 2f
                if (dx * dx + dy * dy < hitRadius * hitRadius) {
                    when (egg.type) {
                        0 -> { score += 10; egg.popping = true }
                        1 -> { score += 25; egg.popping = true }
                        2 -> { lives = (lives - 1).coerceAtLeast(0); egg.popping = true }
                        3 -> { score += 100; egg.popping = true }
                    }
                    updateUI()
                    if (lives <= 0) endGame()
                    break
                }
            }
            return true
        }
    }
}
