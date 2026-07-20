package com.intelligame.huntix.minigames.ar

import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.intelligame.huntix.managers.MiniGameManager
import kotlin.math.sin

class ARCatchEggActivity : ARGameActivity() {

    private var lives = 3
    private var score = 0
    private var timeLeft = 40
    private var speedMult = 1f
    private val eggsActive = mutableListOf<AREgg>()
    private var timerCb: Runnable? = null
    private var spawnCb: Runnable? = null

    override fun onGameCreate() {
        lives = 3; score = 0; timeLeft = 40; speedMult = 1f
        eggsActive.clear()
        statusText.text = "Tocca le uova fluttuanti nell'aria! 🎯"
        updateHud()
        startGame()
        startTimer()
        scheduleSpawn()
    }

    private fun startTimer() {
        removeCallback(timerCb)
        timerCb = postDelayed(1000) {
            if (!running) return@postDelayed
            timeLeft--
            timerText.text = "⏱ ${timeLeft}s"
            if (timeLeft <= 10) timerText.setTextColor(
                android.graphics.Color.parseColor("#FF4444"))
            speedMult = 1f + (40 - timeLeft) * 0.02f
            if (timeLeft <= 0) { endGame(); return@postDelayed }
            startTimer()
        }
    }

    private fun scheduleSpawn() {
        removeCallback(spawnCb)
        spawnCb = postDelayed((500L..900L).random()) {
            if (!running) return@postDelayed
            if (eggsActive.count { it.alive } < 8) spawnEgg()
            scheduleSpawn()
        }
    }

    private fun spawnEgg() {
        val type = when {
            Math.random() < 0.05 -> 3
            Math.random() < 0.15 -> 1
            Math.random() < 0.35 -> 6
            else -> 0
        }
        val forward = (0.6f..1.2f).random()
        val right = (-0.45f..0.45f).random()
        val up = (-0.25f..0.30f).random()
        val egg = spawnEgg(type, forward, right, up, radius = 0.08f) ?: return
        egg.phase = (forward * 6.28f)
        eggsActive.add(egg)
    }

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running) return
        val iter = eggsActive.iterator()
        while (iter.hasNext()) {
            val egg = iter.next()
            if (!egg.alive) { iter.remove(); continue }
            egg.phase += 0.03f * speedMult
            val y = sin(egg.phase.toDouble()).toFloat() * 0.12f
            val x = sin(egg.phase.toDouble() * 0.5f).toFloat() * 0.1f
            moveEggLocal(egg, x, y, 0f)
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || !egg.alive) return
        when (egg.type) {
            0 -> { score += 10; removeEgg(egg) }
            1 -> { score += 25; removeEgg(egg) }
            6 -> { lives = (lives - 1).coerceAtLeast(0); removeEgg(egg) }
            3 -> { score += 100; removeEgg(egg) }
        }
        updateHud()
        if (lives <= 0) endGame()
    }

    private fun updateHud() {
        livesText.text = "❤️".repeat(lives)
        scoreText.text = "$score pt"
        if (timerText.text.isEmpty()) timerText.text = "⏱ ${timeLeft}s"
    }

    private fun endGame() {
        stopGame()
        removeCallback(timerCb); removeCallback(spawnCb)
        val reward = (score * 0.6).toInt().coerceAtMost(350)
        finishGame(reward, "AR Catch Egg ($score pt)", score > 40, MiniGameManager.GAME_CATCH_EGG)
    }
}
