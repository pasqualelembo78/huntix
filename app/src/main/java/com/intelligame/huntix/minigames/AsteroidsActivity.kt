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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 🚀 Asteroids — distruggi gli asteroidi e sopravvivi!
 *
 * Originale: haxpor/asteroids (MIT)
 * Adattamento: meccanica preservata, rendering nel pattern Canvas di Huntix.
 */
class AsteroidsActivity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private val FRAME_MS = 16L

    private var score = 0
    private var lives = 3
    private var level = 1
    private var gameRunning = false
    private var scoreText: TextView? = null
    private var gameView: AsteroidsView? = null
    private var overlayContainer: FrameLayout? = null

    // Player
    private var px = 0f
    private var py = 0f
    private var pdx = 0f
    private var pdy = 0f
    private var pRadians = 0f
    private var turningLeft = false
    private var turningRight = false
    private var accelerating = false
    private var fireCooldown = 0

    private val bullets = mutableListOf<Bullet>()
    private val asteroids = mutableListOf<Asteroid>()

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            update(FRAME_MS / 1000f)
            gameView?.invalidate()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "Asteroids", "🚀"))
        root.addView(TextView(ctx).apply {
            text = "Tieni premuto a sx/dx per ruotare, su per accellerare, tappa per sparare."
            textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_ASTEROIDS))
        scoreText = TextView(ctx).apply {
            text = "Punti: 0  •  Vite: 3  •  Livello 1"
            textSize = 16f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        gameView = AsteroidsView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        val v = gameView ?: return
        if (v.width == 0 || v.height == 0) {
            v.post { startGame() }
            return
        }
        px = v.width / 2f
        py = v.height / 2f
        pdx = 0f; pdy = 0f
        pRadians = -Math.PI.toFloat() / 2
        turningLeft = false; turningRight = false; accelerating = false
        fireCooldown = 0
        bullets.clear()
        asteroids.clear()
        score = 0; lives = 3; level = 1
        spawnAsteroids()
        gameRunning = true
        scoreText?.text = "Punti: 0  •  Vite: 3  •  Livello 1"
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, FRAME_MS)
    }

    private fun spawnAsteroids() {
        val v = gameView ?: return
        for (i in 0 until level + 3) {
            val safe = 100f
            var x = 0f; var y = 0f
            do {
                x = Random.nextFloat() * v.width
                y = Random.nextFloat() * v.height
            } while (sqrt((x - px) * (x - px) + (y - py) * (y - py)) < safe)
            asteroids.add(Asteroid(x, y, AsteroidType.LARGE, v.width.toFloat(), v.height.toFloat()))
        }
    }

    private fun update(dt: Float) {
        val v = gameView ?: return

        // Rotazione
        val rot = 3f * dt
        if (turningLeft) pRadians += rot
        if (turningRight) pRadians -= rot

        // Accelerazione
        val acc = 200f * dt
        val maxSpeed = 300f
        val dec = 10f
        if (accelerating) {
            pdx += cos(pRadians) * acc
            pdy += sin(pRadians) * acc
        }
        val vec = sqrt(pdx * pdx + pdy * pdy)
        if (vec > 0f) {
            pdx -= (pdx / vec) * dec * dt
            pdy -= (pdy / vec) * dec * dt
        }
        if (vec > maxSpeed) {
            pdx = (pdx / vec) * maxSpeed
            pdy = (pdy / vec) * maxSpeed
        }
        px += pdx * dt
        py += pdy * dt
        px = wrapX(px, v.width)
        py = wrapY(py, v.height)

        // Proiettili
        fireCooldown--
        if (fireCooldown < 0) fireCooldown = 0
        for (i in bullets.indices.reversed()) {
            bullets[i].update(dt, v.width.toFloat(), v.height.toFloat())
            if (bullets[i].outOfBounds(v.width, v.height)) bullets.removeAt(i)
        }

        // Asteroidi
        for (i in asteroids.indices.reversed()) {
            asteroids[i].update(dt, v.width.toFloat(), v.height.toFloat())
        }

        checkCollisions(v.width, v.height)
        scoreText?.text = "Punti: $score  •  Vite: $lives  •  Livello $level"
    }

    private fun wrapX(x: Float, w: Int): Float = when {
        x < -20f -> w + 20f
        x > w + 20f -> -20f
        else -> x
    }

    private fun wrapY(y: Float, h: Int): Float = when {
        y < -20f -> h + 20f
        y > h + 20f -> -20f
        else -> y
    }

    private fun shoot() {
        if (!gameRunning) return
        if (fireCooldown > 0) return
        bullets.add(Bullet(px, py, pRadians))
        fireCooldown = 12
    }

    private fun checkCollisions(w: Int, h: Int) {
        // Proiettile vs asteroide
        for (i in bullets.indices.reversed()) {
            val b = bullets[i]
            for (j in asteroids.indices.reversed()) {
                val a = asteroids[j]
                if (a.contains(b.x, b.y)) {
                    bullets.removeAt(i)
                    score += a.score
                    splitAsteroid(j, a.type)
                    break
                }
            }
        }

        // Navicella vs asteroide
        if (asteroids.isEmpty()) {
            level++
            spawnAsteroids()
        }
        for (j in asteroids.indices.reversed()) {
            val a = asteroids[j]
            val dist = sqrt((a.x - px) * (a.x - px) + (a.y - py) * (a.y - py))
            if (dist < a.radius + 10f) {
                asteroids.removeAt(j)
                playerHit()
                break
            }
        }
    }

    private fun splitAsteroid(index: Int, type: AsteroidType) {
        val a = asteroids[index]
        asteroids.removeAt(index)
        when (type) {
            AsteroidType.LARGE -> for (n in 0 until 2) asteroids.add(Asteroid(a.x, a.y, AsteroidType.MEDIUM, a.w, a.h))
            AsteroidType.MEDIUM -> for (n in 0 until 2) asteroids.add(Asteroid(a.x, a.y, AsteroidType.SMALL, a.w, a.h))
            AsteroidType.SMALL -> {}
        }
    }

    private fun playerHit() {
        lives--
        if (lives <= 0) {
            endGame()
        } else {
            px = (gameView?.width ?: 0) / 2f
            py = (gameView?.height ?: 0) / 2f
            pdx = 0f; pdy = 0f
            pRadians = -Math.PI.toFloat() / 2
        }
    }

    private fun endGame() {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)

        val mvc = (score / 5).coerceAtLeast(5)
        val xp = (score / 10).coerceAtLeast(2)

        val result = try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_ASTEROIDS, score,
                mvc = mvc, xp = xp,
                label = "Asteroids: $score punti",
                isWin = score >= 200
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
            text = "💥"; textSize = 48f; gravity = Gravity.CENTER
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Game Over!"
            textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 6))
        })
        endLayout.addView(TextView(ctx).apply {
            text = "Punteggio: $score  •  Livello: $level"
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

    inner class AsteroidsView(context: android.content.Context) : View(context) {

        private val bgPaint = Paint().apply { color = Color.parseColor("#0D0620") }
        private val shipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private val astPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B39DDB"); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FF88") }

        private var touchId = -1
        private var touchMode = 0 // 0=nessuno, 1=ruota sx, 2=ruota dx, 3=su+spara

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            c.drawRect(0f, 0f, w, h, bgPaint)

            // Proiettili
            for (b in bullets) {
                c.drawRect(b.x - 2, b.y - 2, b.x + 2, b.y + 2, bulletPaint)
            }

            // Asteroidi (poligoni irregolari)
            for (a in asteroids) {
                val n = a.shapeX.size
                val path = android.graphics.Path()
                for (i in 0 until n) {
                    val x1 = a.shapeX[i]; val y1 = a.shapeY[i]
                    if (i == 0) path.moveTo(x1, y1) else path.lineTo(x1, y1)
                }
                path.close()
                c.drawPath(path, astPaint)
            }

            // Navicella (triangolo)
            val r = 8f
            val nx = px + cos(pRadians) * r
            val ny = py + sin(pRadians) * r
            val bx = px + cos(pRadians + Math.PI.toFloat()) * 5f
            val by = py + sin(pRadians + Math.PI.toFloat()) * 5f
            val lx = px + cos(pRadians - 4f * Math.PI.toFloat() / 5f) * r
            val ly = py + sin(pRadians - 4f * Math.PI.toFloat() / 5f) * r
            val rx = px + cos(pRadians + 4f * Math.PI.toFloat() / 5f) * r
            val ry = py + sin(pRadians + 4f * Math.PI.toFloat() / 5f) * r
            val ship = android.graphics.Path()
            ship.moveTo(nx, ny); ship.lineTo(lx, ly); ship.lineTo(bx, by); ship.lineTo(rx, ry)
            ship.close()
            c.drawPath(ship, shipPaint)

            // Fiamma
            if (accelerating) {
                val fx = px + cos(pRadians + Math.PI.toFloat()) * 9f
                val fy = py + sin(pRadians + Math.PI.toFloat()) * 9f
                val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFCA28") }
                c.drawCircle(fx, fy, 3f, fp)
            }
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchId = ev.getPointerId(0)
                    touchMode = when {
                        ev.x < width / 3f -> 1
                        ev.x > width * 2f / 3f -> 2
                        else -> 3
                    }
                    turningLeft = touchMode == 1
                    turningRight = touchMode == 2
                    accelerating = touchMode == 3
                    if (touchMode == 3) shoot()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ev.getPointerId(0) == touchId) {
                        val mode = when {
                            ev.x < width / 3f -> 1
                            ev.x > width * 2f / 3f -> 2
                            else -> 3
                        }
                        if (mode != touchMode) {
                            touchMode = mode
                            turningLeft = mode == 1
                            turningRight = mode == 2
                            accelerating = mode == 3
                            if (mode == 3) shoot()
                        }
                        if (touchMode == 3) {
                            // autofire mentre si tiene premuto il centro
                            fireCooldown = 0
                            shoot()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchId = -1
                    turningLeft = false; turningRight = false; accelerating = false
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    private class Bullet(
        var x: Float,
        var y: Float,
        radians: Float
    ) {
        private val dx = cos(radians) * 400f
        private val dy = sin(radians) * 400f

        fun update(dt: Float, w: Float, h: Float) {
            x += dx * dt
            y += dy * dt
        }

        fun outOfBounds(w: Int, h: Int): Boolean =
            x < -10f || x > w + 10f || y < -10f || y > h + 10f
    }

    private enum class AsteroidType { SMALL, MEDIUM, LARGE }

    private inner class Asteroid(
        var x: Float,
        var y: Float,
        val type: AsteroidType,
        val w: Float,
        val h: Float
    ) {
        val radius: Float
        val score: Int
        private val dx: Float
        private val dy: Float
        private val rotationSpeed: Float
        private var radians: Float
        val shapeX: FloatArray
        val shapeY: FloatArray

        init {
            val r = Random(System.currentTimeMillis() + x.toInt() + y.toInt())
            when (type) {
                AsteroidType.SMALL -> {
                    radius = 12f
                    score = 100
                    val sp = r.nextFloat() * 30f + 70f
                    dx = cos(r.nextFloat() * 2f * Math.PI.toFloat()) * sp
                    dy = sin(r.nextFloat() * 2f * Math.PI.toFloat()) * sp
                }
                AsteroidType.MEDIUM -> {
                    radius = 20f
                    score = 50
                    val sp = r.nextFloat() * 10f + 50f
                    dx = cos(r.nextFloat() * 2f * Math.PI.toFloat()) * sp
                    dy = sin(r.nextFloat() * 2f * Math.PI.toFloat()) * sp
                }
                AsteroidType.LARGE -> {
                    radius = 40f
                    score = 20
                    val sp = r.nextFloat() * 10f + 20f
                    dx = cos(r.nextFloat() * 2f * Math.PI.toFloat()) * sp
                    dy = sin(r.nextFloat() * 2f * Math.PI.toFloat()) * sp
                }
            }
            rotationSpeed = r.nextFloat() * 2f - 1f
            radians = r.nextFloat() * 2f * Math.PI.toFloat()
            val numPoints = when (type) {
                AsteroidType.SMALL -> 8
                AsteroidType.MEDIUM -> 10
                AsteroidType.LARGE -> 12
            }
            shapeX = FloatArray(numPoints)
            shapeY = FloatArray(numPoints)
            for (i in 0 until numPoints) {
                val dist = r.nextFloat() * radius / 2f + radius / 2f
                val angle = i * 2f * Math.PI.toFloat() / numPoints
                shapeX[i] = x + cos(angle + radians) * dist
                shapeY[i] = y + sin(angle + radians) * dist
            }
        }

        fun update(dt: Float, w: Float, h: Float) {
            x += dx * dt
            y += dy * dt
            radians += rotationSpeed * dt
            x = wrapX(x, w.toInt())
            y = wrapY(y, h.toInt())
            for (i in shapeX.indices) {
                val angle = i * 2f * Math.PI.toFloat() / shapeX.size
                val dist = sqrt((shapeX[i] - x) * (shapeX[i] - x) + (shapeY[i] - y) * (shapeY[i] - y))
                shapeX[i] = x + cos(angle + radians) * dist
                shapeY[i] = y + sin(angle + radians) * dist
            }
        }

        fun contains(bx: Float, by: Float): Boolean =
            sqrt((bx - x) * (bx - x) + (by - y) * (by - y)) < radius * 0.8f
    }
}
