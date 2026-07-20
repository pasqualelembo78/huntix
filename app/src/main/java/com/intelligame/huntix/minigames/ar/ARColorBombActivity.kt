package com.intelligame.huntix.minigames.ar

import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.intelligame.huntix.managers.MiniGameManager

class ARColorBombActivity : ARGameActivity() {

    private val EMOJI = arrayOf("🥚", "🟣", "🟢", "🌸", "🟡", "🔵")
    private var lives = 3
    private var score = 0
    private var timeLeft = 30
    private var target = 0
    private val eggsActive = mutableListOf<AREgg>()
    private var timerCb: Runnable? = null
    private var spawnCb: Runnable? = null
    private var targetCb: Runnable? = null

    override fun onGameCreate() {
        lives = 3; score = 0; timeLeft = 30
        eggsActive.clear()
        target = (0..5).random()
        statusText.text = "Esplodi le uova del colore bersaglio! 💥"
        updateHud()
        startGame(); startTimer(); scheduleSpawn(); scheduleTarget()
    }

    private fun startTimer() {
        removeCallback(timerCb)
        timerCb = postDelayed(1000) {
            if (!running) return@postDelayed
            timeLeft--
            timerText.text = "⏱ ${timeLeft}s"
            if (timeLeft <= 0) endGame() else startTimer()
        }
    }

    private fun scheduleTarget() {
        removeCallback(targetCb)
        targetCb = postDelayed(5000) {
            if (!running) return@postDelayed
            target = (0..5).random()
            updateHud()
            scheduleTarget()
        }
    }

    private fun scheduleSpawn() {
        removeCallback(spawnCb)
        spawnCb = postDelayed((500L..900L).random()) {
            if (!running) return@postDelayed
            if (eggsActive.count { it.alive } < 7) {
                val type = (0..5).random()
                val egg = spawnEgg(type, (0.6f..1.2f).random(),
                    (-0.45f..0.45f).random(), (-0.25f..0.30f).random(), radius = 0.08f)
                egg?.let { eggsActive.add(it) }
            }
            scheduleSpawn()
        }
    }

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running) return
        for (egg in eggsActive) {
            if (!egg.alive) continue
            egg.phase += 0.02f
            moveEggLocal(egg, sin(egg.phase.toDouble()).toFloat() * 0.12f,
                cos(egg.phase.toDouble()).toFloat() * 0.1f, 0f)
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || !egg.alive) return
        if (egg.type == target) { score += 25; removeEgg(egg); eggsActive.remove(egg) }
        else { lives = (lives - 1).coerceAtLeast(0); removeEgg(egg); eggsActive.remove(egg) }
        updateHud()
        if (lives <= 0) endGame()
    }

    private fun updateHud() {
        livesText.text = "❤️".repeat(lives)
        scoreText.text = "$score pt"
        if (timerText.text.isEmpty()) timerText.text = "⏱ ${timeLeft}s"
        statusText.text = "Bersaglio: ${EMOJI[target]}"
    }

    private fun endGame() {
        stopGame()
        removeCallback(timerCb); removeCallback(spawnCb); removeCallback(targetCb)
        finishGame((score * 0.8).toInt().coerceAtMost(350),
            "AR Color Bomb ($score pt)", score > 60, MiniGameManager.GAME_AR_BOMB)
    }

    private fun cos(v: Double) = kotlin.math.cos(v).toFloat()
    private fun sin(v: Double) = kotlin.math.sin(v).toFloat()
}
