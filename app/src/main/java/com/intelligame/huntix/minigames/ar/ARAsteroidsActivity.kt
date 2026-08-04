package com.intelligame.huntix.minigames.ar

import android.os.SystemClock
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 🚀 AR Asteroids — un'arena sospesa nella stanza REALE.
 * Ruota con lo swipe sinistra/destra, accelera con lo swipe in alto,
 * tappa per sparare. Distruggi gli asteroidi prima che ti distruggano.
 */
class ARAsteroidsActivity : ARGameActivity() {

    companion object {
        private const val HALF_W = 0.85f
        private const val TOP = 1.45f
        private const val BOTTOM = 0.15f

        private const val C_SHIP   = 0xFFF8F8FF.toInt()
        private const val C_BULLET = 0xFF00FF88.toInt()
        private const val C_FLAME  = 0xFFFFCA28.toInt()
        private const val C_AST1   = 0xFFB39DDB.toInt()
        private const val C_AST2   = 0xFF9575CD.toInt()
        private const val C_AST3   = 0xFF7E57C2.toInt()

        private enum class Type(val radius: Float, val score: Int, val color: Int) {
            LARGE(0.17f, 20, C_AST1), MEDIUM(0.11f, 50, C_AST2), SMALL(0.07f, 100, C_AST3)
        }
    }

    private class Asteroid(
        var x: Float, var y: Float, val type: Type,
        var dx: Float, var dy: Float, var spin: Float, val node: SphereNode
    )

    private class Bullet(var x: Float, var y: Float, val dx: Float, val dy: Float, val node: SphereNode)

    private var arena: AnchorNode? = null
    private var yawCos = 1f
    private var yawSin = 0f

    private var ship: SphereNode? = null
    private var flame: SphereNode? = null
    private val bullets = mutableListOf<Bullet>()
    private val asteroids = mutableListOf<Asteroid>()

    private var px = 0f
    private var py = 0.8f
    private var pdx = 0f
    private var pdy = 0f
    private var pRadians = -Math.PI.toFloat() / 2f
    private var turningLeft = false
    private var turningRight = false
    private var accelerating = false
    private var fireCooldown = 0

    private var score = 0
    private var lives = 3
    private var level = 1
    private var gameOver = false
    private var lastNow = 0L

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        bullets.clear(); asteroids.clear()
        px = 0f; py = 0.8f; pdx = 0f; pdy = 0f
        pRadians = -Math.PI.toFloat() / 2f
        turningLeft = false; turningRight = false; accelerating = false
        fireCooldown = 0
        score = 0; lives = 3; level = 1
        gameOver = false
        lastNow = SystemClock.elapsedRealtime()
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "Vite: 3"
        timerText.text = "Livello 1"
        scoreText.text = "Punti: 0"
        updateLevelHud(MiniGameManager.GAME_ASTEROIDS)
        startGame()
        whenReady { placeArena { build(it) } }
    }

    private fun build(a: AnchorNode) {
        arena = a
        val yaw = yawToFaceCamera(a)
        yawCos = kotlin.math.cos(yaw)
        yawSin = kotlin.math.sin(yaw)

        ship = eggNode(C_SHIP, 0.085f).apply { position = Position(0f, 0f, 0f) }
        a.addChildNode(ship!!)
        flame = eggNode(C_FLAME, 0.04f)
        a.addChildNode(flame!!)
        flame!!.isVisible = false

        spawnAsteroids()
        placeShip()
        statusText.text = "🚀 Swipe per ruotare, tappa per sparare"
        installInputCapture(
            onStart = {
                turningLeft = false; turningRight = false; accelerating = false
            },
            onDrag = { dx, dy ->
                turningLeft = dx < -25f
                turningRight = dx > 25f
                accelerating = dy < -25f
            },
            onEnd = { _, _, isTap ->
                turningLeft = false; turningRight = false; accelerating = false
                if (isTap) shoot()
            }
        )
    }

    // ── geometria ────────────────────────────────────────────────

    private fun worldPos(x: Float, y: Float): Triple<Float, Float, Float> {
        val (rx, rz) = rotOffset(x, 0f)
        return Triple(rx, y, rz)
    }

    private fun rotOffset(x: Float, z: Float): Pair<Float, Float> =
        (x * yawCos + z * yawSin) to (-x * yawSin + z * yawCos)

    private fun placeShip() {
        val (x, y, z) = worldPos(px, py)
        ship?.position = Position(x, y, z)
        val (fx, fy, fz) = worldPos(
            px + cos(pRadians + Math.PI.toFloat()) * 0.16f,
            py + sin(pRadians + Math.PI.toFloat()) * 0.16f
        )
        flame?.position = Position(fx, fy, fz)
        flame?.isVisible = accelerating
    }

    private fun wrapX(x: Float): Float = when {
        x < -HALF_W - 0.25f -> HALF_W + 0.25f
        x > HALF_W + 0.25f -> -HALF_W - 0.25f
        else -> x
    }

    private fun wrapY(y: Float): Float = when {
        y < BOTTOM - 0.25f -> TOP + 0.25f
        y > TOP + 0.25f -> BOTTOM - 0.25f
        else -> y
    }

    private fun spawnAsteroids() {
        val arenaRef = arena ?: return
        repeat(level + 2) {
            // Asteroidi attorno alla navicella (dentro il campo visivo), non
            // sparsi su un muro di 3.6 m dove quasi nessuno risulterebbe inquadrato.
            val ang = Random.nextFloat() * 2f * Math.PI.toFloat()
            val dist = 0.45f + Random.nextFloat() * 0.85f
            val x = (px + cos(ang) * dist).coerceIn(-HALF_W * 0.9f, HALF_W * 0.9f)
            val y = (py + sin(ang) * dist).coerceIn(BOTTOM + 0.25f, TOP - 0.25f)
            val t = Type.LARGE
            val sp = 0.15f + Random.nextFloat() * 0.35f
            val aAng = Random.nextFloat() * 2f * Math.PI.toFloat()
            val node = eggNode(t.color, t.radius)
            val (nx, ny, nz) = worldPos(x, y)
            node.position = Position(nx, ny, nz)
            arenaRef.addChildNode(node)
            asteroids.add(Asteroid(x, y, t, cos(aAng) * sp, sin(aAng) * sp, Random.nextFloat() * 1.5f - 0.75f, node))
        }
    }

    // ── loop ─────────────────────────────────────────────────────

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running || arena == null || gameOver) return
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastNow).coerceAtLeast(0L) / 1000f).coerceAtMost(0.05f)
        lastNow = now
        if (dt <= 0f) return

        if (turningLeft) pRadians += 3.4f * dt
        if (turningRight) pRadians -= 3.4f * dt

        val acc = 2.2f * dt
        if (accelerating) {
            pdx += cos(pRadians) * acc
            pdy += sin(pRadians) * acc
        }
        val vec = sqrt(pdx * pdx + pdy * pdy)
        if (vec > 0f) {
            pdx -= (pdx / vec) * 1.2f * dt
            pdy -= (pdy / vec) * 1.2f * dt
        }
        val maxSpeed = 2.0f
        if (vec > maxSpeed) {
            pdx = (pdx / vec) * maxSpeed
            pdy = (pdy / vec) * maxSpeed
        }
        px = wrapX(px + pdx * dt)
        py = wrapY(py + pdy * dt)
        placeShip()

        fireCooldown--
        if (fireCooldown < 0) fireCooldown = 0
        for (i in bullets.indices.reversed()) {
            val b = bullets[i]
            b.x += b.dx * dt
            b.y += b.dy * dt
            if (b.x < -HALF_W - 0.3f || b.x > HALF_W + 0.3f || b.y < BOTTOM - 0.3f || b.y > TOP + 0.3f) {
                removeNode(b.node)
                bullets.removeAt(i)
            } else {
                val (nx, ny, nz) = worldPos(b.x, b.y)
                b.node.position = Position(nx, ny, nz)
            }
        }

        for (i in asteroids.indices.reversed()) {
            val a = asteroids[i]
            a.x = wrapX(a.x + a.dx * dt)
            a.y = wrapY(a.y + a.dy * dt)
            a.node.rotation = io.github.sceneview.math.Rotation(0f, a.spin * 30f, 0f)
            val (nx, ny, nz) = worldPos(a.x, a.y)
            a.node.position = Position(nx, ny, nz)
        }

        checkCollisions()
    }

    private fun shoot() {
        if (!running || gameOver) return
        if (fireCooldown > 0) return
        val arenaRef = arena ?: return
        val b = Bullet(
            px + cos(pRadians) * 0.2f,
            py + sin(pRadians) * 0.2f,
            cos(pRadians) * 5.5f,
            sin(pRadians) * 5.5f,
            eggNode(C_BULLET, 0.032f)
        )
        val (bx, by, bz) = worldPos(b.x, b.y)
        b.node.position = Position(bx, by, bz)
        arenaRef.addChildNode(b.node)
        bullets.add(b)
        fireCooldown = 14
        haptic()
    }

    private fun checkCollisions() {
        for (i in bullets.indices.reversed()) {
            val b = bullets[i]
            for (j in asteroids.indices.reversed()) {
                val a = asteroids[j]
                if (sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)) < a.type.radius) {
                    removeNode(b.node)
                    bullets.removeAt(i)
                    score += a.type.score
                    splitAsteroid(j, a)
                    scoreText.text = "Punti: $score"
                    haptic(true)
                    break
                }
            }
        }

        if (asteroids.isEmpty()) {
            level++
            timerText.text = "Livello $level"
            spawnAsteroids()
        }

        for (j in asteroids.indices.reversed()) {
            val a = asteroids[j]
            val dist = sqrt((a.x - px) * (a.x - px) + (a.y - py) * (a.y - py))
            if (dist < a.type.radius + 0.09f) {
                asteroids.removeAt(j)
                removeNode(a.node)
                playerHit()
                break
            }
        }
    }

    private fun splitAsteroid(index: Int, a: Asteroid) {
        asteroids.removeAt(index)
        removeNode(a.node)
        if (a.type == Type.LARGE) {
            for (n in 0 until 2) spawnChild(a.x, a.y, Type.MEDIUM)
        } else if (a.type == Type.MEDIUM) {
            for (n in 0 until 2) spawnChild(a.x, a.y, Type.SMALL)
        }
    }

    private fun spawnChild(x: Float, y: Float, type: Type) {
        val arenaRef = arena ?: return
        val sp = 0.3f + Random.nextFloat() * 0.4f
        val ang = Random.nextFloat() * 2f * Math.PI.toFloat()
        val node = eggNode(type.color, type.radius)
        val (nx, ny, nz) = worldPos(x, y)
        node.position = Position(nx, ny, nz)
        arenaRef.addChildNode(node)
        asteroids.add(Asteroid(x, y, type, cos(ang) * sp, sin(ang) * sp, Random.nextFloat() * 2f - 1f, node))
    }

    private fun playerHit() {
        lives--
        livesText.text = "Vite: $lives"
        burst(worldPos(px, py).let { (x, y, z) -> dev.romainguy.kotlin.math.Float3(x, y, z) }, C_FLAME, 18)
        if (lives <= 0) {
            endGame()
        } else {
            px = 0f; py = 0.8f; pdx = 0f; pdy = 0f
            pRadians = -Math.PI.toFloat() / 2f
            placeShip()
            // breve invulnerabilità per non morire subito
            for (a in asteroids.toList()) {
                if (sqrt((a.x - px) * (a.x - px) + (a.y - py) * (a.y - py)) < 0.6f) {
                    a.dx = -a.dx; a.dy = -a.dy
                }
            }
        }
    }

    // ── fine ─────────────────────────────────────────────────────

    private fun endGame() {
        if (gameOver) return
        gameOver = true
        stopGame()
        val target = MiniGameManager.getLevelTarget(this, MiniGameManager.GAME_ASTEROIDS)
        val won = score >= target
        val reward = (score / 3).coerceAtLeast(10).coerceAtMost(400)
        try {
            finishGame(
                reward,
                if (won) "AR Asteroids: asteroide spazzato!" else "AR Asteroids ($score pt)",
                won,
                MiniGameManager.GAME_ASTEROIDS,
                score = score
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}
