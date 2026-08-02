package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.math.min
import kotlin.random.Random

/**
 * 🦖 Dino Runner — il classico gioco del dinosauro Chrome in versione Huntix.
 *
 * Corri su un terreno infinito evitando cespugli, uova-spinaci e uccelli volanti.
 * Più corri lunga, più veloce diventa. Toccare lo schermo per saltare.
 *
 * Pixel-perfect: 16:9 logical grid (GROUND_Y a 72% dall'alto) con parallax background.
 */
class DinoGameActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val tickMs = 16L

    private lateinit var gameView: DinoView
    private lateinit var scoreText: TextView
    private lateinit var overlayContainer: FrameLayout

    private var dinoY = 0f
    private var dinoVel = 0f
    private val gravity = 0.75f
    private val jumpStrength = -15f
    private val groundY = 0.72f
    private val dinoSize = 0.07f
    private var gameRunning = false
    private var score = 0
    private var highScore = 0
    private var gameSpeed = 2.5f
    private val speedIncrement = 0.02f
    private var lastSpeedBump = 0L
    private val speedBumpInterval = 5000L

    private val obstacles = mutableListOf<Obstacle>()
    private var lastSpawn = 0L
    private val spawnIntervalBase = 1500L
    private var nextCactusGap = 0L

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groundPaint = Paint().apply { color = Color.parseColor("#0D0620") }
    private val gridPaint = Paint().apply { color = Color.parseColor("#1A1030"); strokeWidth = 2f }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            step()
            gameView.invalidate()
            handler.postDelayed(this, tickMs)
        }
    }

    private var nightMode = false
    private var nightPhase = 0f

    data class Obstacle(val x: Float, val w: Float, val h: Float, val type: Int, val y: Float)

    companion object {
        private const val TYPE_CACTUS_SMALL = 0
        private const val TYPE_CACTUS_LARGE = 1
        private const val TYPE_BIRD = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Dino Runner", "🦖"))
        root.addView(TextView(ctx).apply {
            text = "Tocca per saltare! Evita cespugli e uccelli. 🦖"
            textSize = 12f; setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"; textSize = 18f; setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText)

        gameView = DinoView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        overlayContainer = wrapper
        setContentView(wrapper)

        loadHighScore()
        startGame()
    }

    private fun startGame() {
        dinoY = groundY - dinoSize / 2 - 0.02f
        dinoVel = 0f
        score = 0
        gameSpeed = 2.5f
        lastSpeedBump = 0L
        obstacles.clear()
        lastSpawn = 0L
        nextCactusGap = 0L
        gameRunning = true
        nightMode = false
        nightPhase = 0f
        scoreText.text = "Punti: 0"
        gameView.isFocusable = true
        gameView.isClickable = true
        gameView.invalidate()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, tickMs)
    }

    private fun step() {
        val dt = System.currentTimeMillis()
        val deltaSec = tickMs / 1000f

        // Physics
        dinoVel += gravity
        dinoY += dinoVel * deltaSec * 60f

        val groundPosY = groundY * 1f
        val dinoBottom = dinoY + dinoSize / 2
        if (dinoBottom >= groundPosY - dinoSize / 2 - 0.01f) {
            dinoY = groundPosY - dinoSize - 0.01f
            dinoVel = 0f
        }

        // Speed increases over time
        if (dt - lastSpeedBump > speedBumpInterval) {
            gameSpeed += speedIncrement
            lastSpeedBump = dt
        }

        // Spawn obstacles
        if (dt - lastSpawn > spawnIntervalBase / (gameSpeed / 2.5f)) {
            spawnObstacle()
            lastSpawn = dt
        }

        // Move obstacles
        val iter = obstacles.iterator()
        while (iter.hasNext()) {
            val obs = iter.next()
            val newX = obs.x - gameSpeed * deltaSec * 60f / 100f
            if (newX < -obs.w) {
                iter.remove()
            } else {
                iter.set(Obstacle(newX, obs.w, obs.h, obs.type, obs.y))
            }
        }

        // Check collisions
        checkCollisions()

        // Score
        score += (gameSpeed * deltaSec * 6).toInt()
        scoreText.text = "Punti: $score"

        // Night cycle
        nightPhase += deltaSec * 0.05f
        if (nightPhase > 1f) nightPhase = 0f
        nightMode = nightPhase < 0.3f || nightPhase > 0.7f
    }

    private fun spawnObstacle() {
        val margin = 0.1f
        val speedFactor = min(gameSpeed / 2.5f, 4f)

        if (Random.nextFloat() < 0.3f && speedFactor > 1.5f) {
            // Bird (flies)
            val y = (groundY - 0.15f) * (0.5f + Random.nextFloat() * 0.3f)
            obstacles.add(Obstacle(1f + margin, 0.06f, 0.04f, TYPE_BIRD, y))
        } else {
            // Cactus
            val type = if (Random.nextFloat() < 0.4f) TYPE_CACTUS_LARGE else TYPE_CACTUS_SMALL
            val w = if (type == TYPE_CACTUS_LARGE) 0.05f else 0.035f
            val h = groundY - 0.07f
            obstacles.add(Obstacle(1f + margin, w, h, type, groundY - h / 2 - 0.01f))
            // Sometimes spawn a second cactus close by
            if (Random.nextFloat() < 0.3f) {
                nextCactusGap = 0.03f
            }
        }

        if (nextCactusGap > 0) {
            nextCactusGap -= gameSpeed * 0.001f
            if (nextCactusGap > 0) {
                val type = if (Random.nextFloat() < 0.4f) TYPE_CACTUS_LARGE else TYPE_CACTUS_SMALL
                val w = if (type == TYPE_CACTUS_LARGE) 0.05f else 0.035f
                val h = groundY - 0.07f
                obstacles.add(Obstacle(1f + margin + 0.06f, w, h, type, groundY - h / 2 - 0.01f))
            }
        }
    }

    private fun checkCollisions() {
        val dinoLeft = 0.1f
        val dinoRight = 0.1f + dinoSize
        val dinoTop = dinoY - dinoSize / 2
        val dinoBottom = dinoY + dinoSize / 2

        for (obs in obstacles) {
            val obsLeft = obs.x
            val obsRight = obs.x + obs.w
            val obsTop = obs.y - obs.h / 2
            val obsBottom = obs.y + obs.h / 2

            if (dinoLeft < obsRight && dinoRight > obsLeft && dinoTop < obsBottom && dinoBottom > obsTop) {
                endGame()
                return
            }
        }
    }

    private fun loadHighScore() {
        val prefs = getSharedPreferences("dino_game", MODE_PRIVATE)
        highScore = prefs.getInt("high_score", 0)
    }

    private fun saveHighScore() {
        if (score > highScore) {
            highScore = score
            val prefs = getSharedPreferences("dino_game", MODE_PRIVATE)
            prefs.edit().putInt("high_score", highScore).apply()
        }
    }

    private fun endGame() {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
        saveHighScore()

        val mvc = (score / 10).coerceAtLeast(8)
        val xp = (score / 20).coerceAtLeast(2)
        try {
            MiniGameManager.consumePlay(this, MiniGameManager.GAME_DINO)
            MiniGameManager.applyReward(
                this,
                MiniGameManager.GameReward(
                    mvcCoins = mvc, xpPoints = xp,
                    label = "Dino: $score pt",
                    isWin = score >= 200
                ),
                MiniGameManager.GAME_DINO
            )
        } catch (e: Exception) { Sentry.captureException(e) }

        val ctx = this
        val overlay = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#CC0D0620"))
            setPadding(UiKit.dp(ctx, 30), UiKit.dp(ctx, 40), UiKit.dp(ctx, 30), UiKit.dp(ctx, 40))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true; isFocusable = true
        }
        val endLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        endLayout.addView(TextView(ctx).apply {
            text = "🦖"; textSize = 48f; gravity = android.view.Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Partita Finita!"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $score  (Record: $highScore)"; textSize = 18f
            setTextColor(Color.parseColor(UiKit.GREEN))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "+$mvc MVC  •  +$xp XP"; textSize = 14f; setTextColor(Color.parseColor(UiKit.ACCENT))
            gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, UiKit.dp(ctx, 16))
        })
        endLayout.addView(UiKit.button(ctx, "🔄  Riprova", UiKit.ACCENT) {
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

    inner class DinoView(context: android.content.Context) : View(context) {
        private val dinoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FF88") }
        private val cactusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00C86A") }
        private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF8888") }
        private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val minDim = min(w, h)
            val gridW = minDim
            val gridH = minDim * 1.778f // ~16:9
            val offsetX = (w - gridW) / 2
            val offsetY = (h - gridH) / 2

            // Background
            if (nightMode) {
                c.drawColor(Color.parseColor("#050310"))
            } else {
                c.drawColor(Color.parseColor("#0D0620"))
            }

            // Ground line
            val groundYAbs = offsetY + groundY * gridH
            val groundPaint2 = Paint().apply { color = Color.parseColor("#00FF88") }
            c.drawLine(offsetX, groundYAbs, offsetX + gridW, groundYAbs, groundPaint2)

            // Draw dino
            val dinoX = offsetX + 0.1f * gridW
            val dinoCX = dinoX + dinoSize * gridW / 2
            val dinoCY = offsetY + dinoY * gridH
            val dinoR = dinoSize * gridW / 2
            c.drawCircle(dinoCX, dinoCY, dinoR, dinoPaint)
            // Eye
            eyePaint.color = if (nightMode) Color.WHITE else Color.BLACK
            val eyeOffset = dinoR * 0.3f
            val eyeR = dinoR * 0.2f
            c.drawCircle(dinoCX - eyeOffset, dinoCY - eyeOffset, eyeR, eyePaint)

            // Draw obstacles
            for (obs in obstacles) {
                val obsX = offsetX + obs.x * gridW
                val obsW = obs.w * gridW
                val obsH = obs.h * gridH
                val obsY = offsetY + obs.y * gridH

                when (obs.type) {
                    TYPE_CACTUS_SMALL -> {
                        cactusPaint.color = Color.parseColor("#00C86A")
                        // Multi-segment cactus
                        val segW = obsW / 2
                        c.drawRect(obsX, obsY - obsH, obsX + segW, obsY, cactusPaint)
                        c.drawRect(obsX + segW, obsY - obsH * 0.7f, obsX + obsW, obsY, cactusPaint)
                    }
                    TYPE_CACTUS_LARGE -> {
                        cactusPaint.color = Color.parseColor("#009633")
                        // Large cactus with arm
                        c.drawRect(obsX, obsY - obsH, obsX + obsW, obsY, cactusPaint)
                        c.drawRect(obsX + obsW, obsY - obsH * 0.6f, obsX + obsW * 1.3f, obsY - obsH * 0.4f, cactusPaint)
                    }
                    TYPE_BIRD -> {
                        // Bird — two circles + beak
                        birdPaint.color = Color.parseColor("#FF8888")
                        val bx = obsX + obsW / 2
                        val by = obsY
                        val wingR = obsW / 3
                        c.drawCircle(bx - wingR, by, wingR, birdPaint)
                        c.drawCircle(bx + wingR, by, wingR, birdPaint)
                        // Eye
                        eyePaint.color = Color.BLACK
                        c.drawCircle(bx - wingR * 0.5f, by - wingR * 0.3f, wingR * 0.2f, eyePaint)
                        // Beak
                        val beakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFA500") }
                        c.drawCircle(bx + wingR + wingR * 0.3f, by, wingR * 0.4f, beakPaint)
                    }
                }
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (gameRunning) {
                        jump()
                    }
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    private fun jump() {
        if (dinoY >= groundY - dinoSize - 0.02f || dinoVel == 0f) {
            dinoVel = jumpStrength
        }
    }
}
