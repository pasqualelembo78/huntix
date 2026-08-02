package com.intelligame.huntix.minigames.ar

import android.view.MotionEvent
import com.google.arcore.Frame
import com.google.arcore.Session
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Float3
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🦖 AR Dino Runner — il classico dinosauro Chrome in Realtà Aumentata.
 *
 * Il dinosauro è ancorato al pavimento davanti a te: corre in posizione
 * fissa mentre gli ostacoli (cespugli spinosi, uova-uccello) scorrono
 * da destra a sinistra nella stanza reale. Tocca per saltare!
 */
class ARDinoActivity : ARGameActivity() {

    companion object {
        private const val DINO_COLOR = 0xFF00FF88.toInt()
        private const val CACTUS_COLOR = 0xFF00C86A.toInt()
        private const val BIRD_COLOR = 0xFFFF5252.toInt()
        private const val GROUND_Y = -0.05f
        private const val DINO_Z = 0.3f
        private const val OBSTACLE_SPAWN_Z = 1.2f
        private const val SPEED_START = 0.6f
        private const val SPEED_MAX = 2.0f
    }

    private var arena: AnchorNode? = null
    private var dinoBody: Node? = null
    private var dinoHead: Node? = null
    private val obstacles = mutableListOf<Obstacle>()

    private var dinoY = GROUND_Y
    private val dinoHeight = 0.12f
    private var dinoVel = 0f
    private val jumpStrength = -0.35f
    private val gravity = 0.018f
    private var isJumping = false

    private var gameSpeed = SPEED_START
    private var lastFrameTime = 0L
    private var score = 0
    private var lastScoreTime = 0L

    private var spawnCb: Runnable? = null

    data class Obstacle(
        val node: Node,
        var z: Float,
        val width: Float,
        val height: Float,
        val type: Int
    )

    init {
        showsModeDialog = true
    }

    override fun onGameCreate() {
        score = 0
        gameSpeed = SPEED_START
        dinoY = GROUND_Y + dinoHeight / 2
        dinoVel = 0f
        isJumping = false
        obstacles.clear()
        lastFrameTime = System.currentTimeMillis()
        lastScoreTime = System.currentTimeMillis()

        statusText.text = "🔍 Inquadra il pavimento…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🦖 Dino Runner AR"
        timerText.text = "Distanza: 0m"
        scoreText.text = "0 pt"
        startGame()
        placeArena { anchor -> buildArena(anchor) }
    }

    private fun buildArena(anchor: AnchorNode) {
        arena = anchor

        dinoBody = cubeNode(DINO_COLOR, Float3(dinoHeight * 1.2f, dinoHeight, dinoHeight * 0.5f)).apply {
            position = Position(0f, dinoY, DINO_Z)
        }
        dinoHead = eggNode(DINO_COLOR, dinoHeight * 0.4f).apply {
            scale = io.github.sceneview.math.Scale(0.8f, 1.2f, 0.8f)
            position = Position(dinoHeight * 0.6f, dinoY + dinoHeight * 0.2f, DINO_Z)
        }
        anchor.addChildNode(dinoBody!!)
        anchor.addChildNode(dinoHead!!)

        startSpawning()
    }

    private fun startSpawning() {
        removeCallback(spawnCb)
        val delayMs = ((800L..1400L).random() / (gameSpeed / SPEED_START)).toLong().coerceAtLeast(400)
        spawnCb = postDelayed(delayMs) {
            if (!running || arena == null) return@postDelayed
            spawnObstacle()
            startSpawning()
        }
    }

    private fun spawnObstacle() {
        val anchor = arena ?: return
        val r = Random.nextFloat()

        if (r < 0.7f) {
            // Cactus
            val w = (0.04f..0.07f).random()
            val h = (0.1f..0.25f).random()
            val node = cubeNode(CACTUS_COLOR, Float3(w, h, w)).apply {
                position = Position(0f, GROUND_Y + h / 2, OBSTACLE_SPAWN_Z)
            }
            anchor.addChildNode(node)
            obstacles.add(Obstacle(node, OBSTACLE_SPAWN_Z, w, h, 0))
        } else {
            // Flying bird
            val w = 0.06f
            val h = 0.04f
            val node = cubeNode(BIRD_COLOR, Float3(w, h, w)).apply {
                position = Position(0f, GROUND_Y + 0.15f, OBSTACLE_SPAWN_Z)
            }
            anchor.addChildNode(node)
            obstacles.add(Obstacle(node, OBSTACLE_SPAWN_Z, w, h, 1))
        }
    }

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running || arena == null) return

        val now = System.currentTimeMillis()
        val dt = (now - lastFrameTime).coerceIn(0, 50)
        lastFrameTime = now
        val factor = dt / 16f

        // Speed up over time
        if (gameSpeed < SPEED_MAX) {
            gameSpeed += 0.0005f * factor
        }

        // Score by distance
        if (now - lastScoreTime > 100) {
            score += (gameSpeed * 0.5f * (now - lastScoreTime) / 100f).toInt()
            timerText.text = "Distanza: ${score / 10}m"
            scoreText.text = "$score pt"
            lastScoreTime = now
        }

        // Move obstacles toward dinosaur
        val iter = obstacles.iterator()
        while (iter.hasNext()) {
            val obs = iter.next()
            obs.z -= gameSpeed * 0.015f * factor
            obs.node.position = Position(0f, obs.node.position.y, obs.z)
            if (obs.z < -0.1f) {
                iter.remove()
                removeNode(obs.node)
            }
        }

        // Dino jump physics
        if (isJumping) {
            dinoVel += gravity
            dinoY += dinoVel
            if (dinoY >= GROUND_Y + dinoHeight / 2) {
                dinoY = GROUND_Y + dinoHeight / 2
                dinoVel = 0f
                isJumping = false
            }
            val headY = dinoY + dinoHeight * 0.2f
            dinoBody?.position = Position(0f, dinoY, DINO_Z)
            dinoHead?.position = Position(dinoHeight * 0.6f, headY, DINO_Z)
        }

        // Collision check
        val dinoZFront = DINO_Z - 0.1f
        val dinoZBack = DINO_Z + 0.1f
        for (obs in obstacles) {
            if (obs.z > dinoZFront && obs.z < dinoZBack) {
                val dinoTop = dinoY - dinoHeight / 2
                val dinoBottom = dinoY + dinoHeight / 2
                val obsTop = GROUND_Y + obs.height / 2 - obs.height
                val obsBottom = GROUND_Y + obs.height / 2

                if (obsTop < dinoBottom && obsBottom > dinoTop) {
                    endGame()
                    return
                }
            }
        }
    }

    override fun onBackgroundTapped(event: MotionEvent) {
        jump()
    }

    private fun jump() {
        if (!isJumping) {
            isJumping = true
            dinoVel = jumpStrength
            haptic(heavy = true)
        }
    }

    private fun endGame() {
        if (!running) return
        stopGame()
        removeCallback(spawnCb)
        val reward = (score / 5).coerceAtLeast(10).coerceAtMost(400)
        try {
            finishGame(reward, "AR Dino ($score pt)", score >= 200, MiniGameManager.GAME_DINO)
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeCallback(spawnCb)
    }
}
