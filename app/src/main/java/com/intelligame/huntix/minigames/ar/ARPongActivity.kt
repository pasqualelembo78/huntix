package com.intelligame.huntix.minigames.ar

import android.view.MotionEvent
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.github.sceneview.ar.node.AnchorNode
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.math.Position
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import kotlin.math.abs
import kotlin.math.max

/**
 * 🏓 AR Breakout Eggs — brick-breaker in Realtà Aumentata.
 *
 * Un muro di uova 3D è ancorato su una superficie reale: rompi le uova
 * con una palla che rimbalza. Trascina lo schermo per muovere la palettiera.
 * Raccogli tutte le uova del muro per vincere!
 */
class ARPongActivity : ARGameActivity() {

    companion object {
        private const val WALL_W = 0.60f
        private const val WALL_H = 0.40f
        private const val BRICK_COLS = 8
        private const val BRICK_ROWS = 4
        private const val PADDLE_W = 0.18f
        private const val PADDLE_H = 0.03f
        private const val BALL_R = 0.015f
        private val BRICK_COLORS = intArrayOf(
            0xFFFF6B6B.toInt(), 0xFFFFD166.toInt(), 0xFF00FF88.toInt(),
            0xFF6AD7FF.toInt(), 0xFFA78BFA.toInt()
        )
    }

    private var arena: AnchorNode? = null
    private var paddleNode: io.github.sceneview.node.Node? = null
    private var ballNode: SphereNode? = null
    private val brickNodes = mutableListOf<SphereNode>()

    private var paddleX = 0f
    private var ballX = 0f
    private var ballY = 0f
    private var ballVX = 0f
    private var ballVY = 0f
    private var destroyed = 0
    private var totalBricks = 0
    private var lives = 3
    private var gameRunning = false
    private var score = 0

    private var tickCb: Runnable? = null

    init {
        showsModeDialog = true
        usesSurfaceArena = true
    }

    override fun onGameCreate() {
        statusText.text = "🔍 Inquadra una superficie piana…"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🏓 Breakout AR"
        timerText.text = "Uova: 0 / 0"
        scoreText.text = "Vite: 3"
        updateLevelHud(MiniGameManager.GAME_PONG)

        initMatch()
        whenReady { placeArena { buildArena(it) } }
    }

    private fun initMatch() {
        val diff = MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_PONG)
        paddleX = 0f
        ballX = 0f
        ballY = -WALL_H / 2f + 0.08f
        ballVX = (2f + diff * 1.5f) * (if (Math.random() > 0.5) 1f else -1f)
        ballVY = 1.5f + diff * 0.8f
        destroyed = 0
        score = 0
        lives = 3
        gameRunning = true
        timerText.text = "Uova: 0 / $totalBricks  •  ${score}/${MiniGameManager.getLevelTarget(this, MiniGameManager.GAME_PONG)}"
        scoreText.text = "Vite: $lives"
    }

    private fun buildArena(anchor: AnchorNode) {
        arena = anchor
        buildBricks(anchor)
        buildPaddle(anchor)
        buildBall(anchor)
        startGameLoop()
    }

    private fun buildBricks(anchor: AnchorNode) {
        brickNodes.clear()
        val cols = BRICK_COLS
        val rows = BRICK_ROWS
        val brickW = WALL_W / cols
        val brickH = WALL_H * 0.20f
        val startY = WALL_H / 2f - brickH / 2f - 0.03f
        val startX = -WALL_W / 2f + brickW / 2f

        for (row in 0 until rows) {
            val color = BRICK_COLORS[row % BRICK_COLORS.size]
            for (col in 0 until cols) {
                val bx = startX + col * brickW
                val by = startY - row * (brickH + 0.005f)
                val brick = eggNode(color, 0.035f).apply {
                    position = Position(bx, by, 0f)
                }
                anchor.addChildNode(brick)
                brickNodes.add(brick)
            }
        }
        totalBricks = brickNodes.size
        timerText.text = "Uova: 0 / $totalBricks"
    }

    private fun buildPaddle(anchor: AnchorNode) {
        paddleNode = cubeNode(0xFF00FF88.toInt(), Float3(PADDLE_W, PADDLE_H, PADDLE_H))
        anchor.addChildNode(paddleNode!!)
        paddleNode?.position = Position(0f, -WALL_H / 2f + PADDLE_H, 0f)
    }

    private fun buildBall(anchor: AnchorNode) {
        ballNode = eggNode(android.graphics.Color.WHITE, BALL_R)
        anchor.addChildNode(ballNode!!)
        ballNode?.position = Position(ballX, ballY, 0f)
    }

    private fun startGameLoop() {
        val diff = MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_PONG)
        val tickMs = (16L - 4L * diff).toLong().coerceAtLeast(8L)

        val runnable = object : Runnable {
            override fun run() {
                if (!gameRunning) return
                update()
                postDelayed(tickMs, this)
            }
        }
        tickCb = runnable
        postDelayed(tickMs, runnable)
    }

    private fun update() {
        val diff = MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_PONG)
        val dt = 0.016f * (1f + diff * 0.3f)

        ballX += ballVX * dt
        ballY += ballVY * dt

        val halfW = WALL_W / 2f - BALL_R
        if (ballX < -halfW || ballX > halfW) {
            ballVX = -ballVX
            ballX = ballX.coerceIn(-halfW, halfW)
        }

        val top = WALL_H / 2f - BALL_R
        if (ballY > top) {
            ballVY = -ballVY
            ballY = top
        }

        val paddleTop = -WALL_H / 2f + PADDLE_H + BALL_R
        val paddleBot = -WALL_H / 2f + PADDLE_H
        if (ballY <= paddleTop && ballY >= paddleBot - BALL_R &&
            abs(ballX - paddleX) < PADDLE_W / 2f + BALL_R) {
            ballVY = abs(ballVY) * 1.1f
            val offset = (ballX - paddleX) / (PADDLE_W / 2f)
            ballVX += offset * 1f * (1f + diff * 0.1f)
            ballVX = ballVX.coerceIn(-8f * (1f + diff), 8f * (1f + diff))
            ballY = paddleTop
            haptic(true)
        }

        val cols = BRICK_COLS
        val rows = BRICK_ROWS
        val brickW = WALL_W / cols
        val brickH = WALL_H * 0.20f
        val startY = WALL_H / 2f - brickH / 2f - 0.03f
        val startX = -WALL_W / 2f + brickW / 2f

        for (i in brickNodes.indices) {
            if (i >= brickNodes.size) break
            val node = brickNodes[i]
            val col = i % cols
            val row = i / cols
            if (row >= rows) continue
            val bx = startX + col * brickW
            val by = startY - row * (brickH + 0.005f)
            val margin = BALL_R + max(brickW, brickH) / 2f
            if (ballX in (bx - margin)..(bx + margin) &&
                ballY in (by - margin)..(by + margin)) {
                brickNodes.removeAt(i)
                node.parent?.removeChildNode(node)
                removeNode(node)
                destroyed++
                score += 10
                ballVY = -ballVY
                haptic()
                timerText.text = "Uova: $destroyed / $totalBricks  •  ${score}/${MiniGameManager.getLevelTarget(this, MiniGameManager.GAME_PONG)}"
                if (destroyed >= totalBricks) {
                    endGame(true)
                }
                break
            }
        }

        ballNode?.position = Position(ballX, ballY, 0f)

        if (ballY < -WALL_H / 2f + BALL_R) {
            lives--
            scoreText.text = "Vite: $lives"
            if (lives <= 0) {
                endGame(false)
            } else {
                resetBall()
            }
        }
    }

    private fun resetBall() {
        paddleX = 0f
        ballX = 0f
        ballY = -WALL_H / 2f + 0.08f
        ballVX = 3f * (if (Math.random() > 0.5) 1f else -1f)
        ballVY = 2f
        ballNode?.position = Position(ballX, ballY, 0f)
        paddleNode?.position = Position(0f, -WALL_H / 2f + PADDLE_H, 0f)
    }

    override fun onBackgroundTapped(event: MotionEvent) {
        if (!gameRunning) return
        paddleX = ((event.x / resources.displayMetrics.widthPixels.toFloat()) - 0.5f) * WALL_W * 1.5f
        paddleX = paddleX.coerceIn(-WALL_W / 2f + PADDLE_W / 2f, WALL_W / 2f - PADDLE_W / 2f)
        paddleNode?.position = Position(paddleX, -WALL_H / 2f + PADDLE_H, 0f)
    }

    private fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        removeCallback(tickCb)
        val reward = score.coerceAtLeast(20)
        try {
            finishGame(
                reward = reward,
                label = "AR Breakout: $destroyed uova",
                isWin = won,
                gameId = MiniGameManager.GAME_PONG,
                giftEggRarityId = if (won) "common" else null
            )
        } catch (e: Exception) { Sentry.captureException(e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeCallback(tickCb)
    }
}
