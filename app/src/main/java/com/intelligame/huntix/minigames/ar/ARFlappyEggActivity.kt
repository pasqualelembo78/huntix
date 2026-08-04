package com.intelligame.huntix.minigames.ar

import android.os.SystemClock
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.SphereNode
import io.sentry.Sentry
import java.util.Collections
import kotlin.math.abs
import kotlin.random.Random

/**
 * 🐣 AR Flappy Egg — un'uovo batte le ali dentro la stanza REALE e deve
 * attraversare le colonne di tubi che gli vengono incontro. Tocca per
 * battere le ali; ogni colonna superata = punti.
 */
class ARFlappyEggActivity : ARGameActivity() {

    companion object {
        private const val GRAVITY = 3.2f
        private const val FLAP = 1.15f
        private const val PIPE_SPEED = 0.75f
        private const val SPAWN_INTERVAL = 1500f
        private const val TOP = 1.15f
        private const val BOTTOM = -1.15f
        private const val PIPE_W = 0.30f
        private const val PIPE_D = 0.30f
        private val C_EGG = 0xFFF8F8FF.toInt()
        private val C_PIPE = 0xFF3DDC84.toInt()
        private val C_PIPE_EDGE = 0xFF2FA96B.toInt()
    }

    init {
        showsModeDialog = true
    }

    private class PipePair(
        val top: CubeNode, val topCap: CubeNode,
        val bottom: CubeNode, val bottomCap: CubeNode,
        val gapY: Float, var z: Float
    ) {
        var scored = false
    }

    private var arena: AnchorNode? = null
    private var eggNode: SphereNode? = null
    private val pipes = Collections.synchronizedList(mutableListOf<PipePair>())
    private var y = 0f
    private var vy = 0f
    private var score = 0
    private var lastNow = 0L
    private var spawnAcc = 0f
    private var flapAnim = 0f
    private var gameOver = false

    override fun onGameCreate() {
        synchronized(pipes) {
            pipes.forEach { removeNode(it.top); removeNode(it.topCap); removeNode(it.bottom); removeNode(it.bottomCap) }
            pipes.clear()
        }
        y = 0f
        vy = 0f
        score = 0
        spawnAcc = SPAWN_INTERVAL * 0.6f
        flapAnim = 0f
        gameOver = false
        lastNow = SystemClock.elapsedRealtime()
        statusText.text = "Tocca per battere le ali! 🐣"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "🥚 Flappy Egg"
        timerText.text = ""
        scoreText.text = "0 pt"
        updateLevelHud(MiniGameManager.GAME_FLAPPY)
        startGame()
        whenReady { build() }
    }

    private fun build() {
        // In modalità superficie l'arena viene sollevata di 1.2 m sopra il tavolo
        // così il campo di gioco verticale resta all'altezza degli occhi.
        val a = tryArenaByMode(elevation = 1.2f)
        if (a == null) {
            if (running) postDelayed(400) { build() }
            return
        }
        arena = a
        // Non chiamare persistArena: l'arena è temporanea per il gioco flappy
        val egg = eggNode(C_EGG, 0.09f)
        egg.position = Position(0f, 0f, 0f)
        a.addChildNode(egg)
        eggNode = egg
        installInputCapture(onStart = { flap() }, onEnd = { _, _, _ -> flap() })
    }

    private fun flap() {
        if (gameOver || !running) return
        vy = FLAP
        flapAnim = 1f
    }

    override fun onArFrame(session: com.google.ar.core.Session, frame: com.google.ar.core.Frame) {
        if (gameOver || !running || arena == null) return
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastNow).coerceAtLeast(0L)) / 1000f
        lastNow = now
        if (dt <= 0f) return

        vy -= GRAVITY * dt
        y += vy * dt
        y = y.coerceIn(BOTTOM + 0.1f, TOP - 0.1f)
        if (y <= BOTTOM + 0.11f || y >= TOP - 0.11f) {
            endGame()
            return
        }
        eggNode?.position = Position(0f, y, 0f)

        spawnAcc += dt * 1000f
        if (spawnAcc >= spawnInterval()) {
            spawnAcc = 0f
            spawnPipe()
        }

        val gapTolerance = GAP_H / 2f
        synchronized(pipes) {
            val iter = pipes.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.z -= PIPE_SPEED * dt
                p.top.position = Position(0f, p.top.position.y, p.z)
                p.topCap.position = Position(0f, p.topCap.position.y, p.z)
                p.bottom.position = Position(0f, p.bottom.position.y, p.z)
                p.bottomCap.position = Position(0f, p.bottomCap.position.y, p.z)
                if (!p.scored && p.z <= 0f) {
                    p.scored = true
                    score++
                    scoreText.text = "$score pt"
                }
                if (p.z < -1.1f) {
                    iter.remove()
                    removeNode(p.top); removeNode(p.topCap); removeNode(p.bottom); removeNode(p.bottomCap)
                    continue
                }
                if (abs(p.z) < 0.24f) {
                    if (y > p.gapY + gapTolerance || y < p.gapY - gapTolerance) {
                        endGame()
                        return
                    }
                }
            }
        }

        if (flapAnim > 0f) {
            flapAnim = (flapAnim - dt * 3.5f).coerceAtLeast(0f)
            eggNode?.scale = io.github.sceneview.math.Scale(1f, 1.35f + flapAnim * 0.65f, 1f)
        }
    }

    private fun spawnPipe() {
        val a = arena ?: return
        val gapY = Random.nextFloat() * 1.1f - 0.25f
        val z = 1.7f

        val topH = TOP - gapY - 0.30f
        val bottomH = gapY - 0.30f - BOTTOM
        val top = cubeNode(C_PIPE, Float3(PIPE_W, topH, PIPE_D))
        top.position = Position(0f, gapY + 0.30f + topH / 2f, z)
        val topCap = cubeNode(C_PIPE_EDGE, Float3(PIPE_W + 0.10f, 0.18f, PIPE_D + 0.10f))
        topCap.position = Position(0f, gapY + 0.39f, z)
        a.addChildNode(top)
        a.addChildNode(topCap)
        val bottom = cubeNode(C_PIPE, Float3(PIPE_W, bottomH, PIPE_D))
        bottom.position = Position(0f, BOTTOM + bottomH / 2f - 0.30f, z)
        val bottomCap = cubeNode(C_PIPE_EDGE, Float3(PIPE_W + 0.10f, 0.18f, PIPE_D + 0.10f))
        bottomCap.position = Position(0f, gapY - 0.39f, z)
        a.addChildNode(bottom)
        a.addChildNode(bottomCap)

        val pair = PipePair(top, topCap, bottom, bottomCap, gapY, z)
        synchronized(pipes) { pipes.add(pair) }
    }

    private fun spawnInterval(): Float =
        SPAWN_INTERVAL - 400f * MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_FLAPPY)

    private fun endGame() {
        if (gameOver) return
        gameOver = true
        stopGame()
        val reward = (score * 2).coerceAtLeast(5).coerceAtMost(350)
        try {
            finishGame(reward, "AR Flappy ($score pt)", score >= 10, MiniGameManager.GAME_FLAPPY, score = score)
        } catch (e: Exception) { Sentry.captureException(e) }
    }
}
