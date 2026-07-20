package com.intelligame.huntix.minigames.ar

import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.intelligame.huntix.managers.MiniGameManager

class AREggShooterActivity : ARGameActivity() {

    private var lives = 3
    private var score = 0
    private var timeLeft = 40
    private val eggsActive = mutableListOf<AREgg>()
    private var timerCb: Runnable? = null
    private var spawnCb: Runnable? = null

    override fun onGameCreate() {
        lives = 3; score = 0; timeLeft = 40
        eggsActive.clear()
        statusText.text = "Spara alle uova che salgono! ✨"
        updateHud()
        startGame(); startTimer(); scheduleSpawn()
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

    private fun scheduleSpawn() {
        removeCallback(spawnCb)
        spawnCb = postDelayed((450L..800L).random()) {
            if (!running) return@postDelayed
            if (eggsActive.count { it.alive } < 7) spawnEggUp()
            scheduleSpawn()
        }
    }

    private fun spawnEggUp() {
        val type = if (Math.random() < 0.2) 3 else 0
        val egg = spawnEgg(type, forward = 1.0f, right = (-0.4f..0.4f).random(), up = -0.45f,
            radius = 0.08f) ?: return
        egg.phase = -0.45f
        eggsActive.add(egg)
    }

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running) return
        val iter = eggsActive.iterator()
        while (iter.hasNext()) {
            val egg = iter.next()
            if (!egg.alive) { iter.remove(); continue }
            egg.phase += 0.006f
            moveEggLocal(egg, 0f, egg.phase, 0f)
            if (egg.phase > 0.55f) {
                eggsActive.remove(egg); removeEgg(egg)
                lives = (lives - 1).coerceAtLeast(0)
                updateHud()
                if (lives <= 0) endGame()
            }
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || !egg.alive) return
        score += if (egg.type == 3) 100 else 10
        removeEgg(egg); eggsActive.remove(egg); updateHud()
    }

    private fun updateHud() {
        livesText.text = "❤️".repeat(lives)
        scoreText.text = "$score pt"
        if (timerText.text.isEmpty()) timerText.text = "⏱ ${timeLeft}s"
    }

    private fun endGame() {
        stopGame(); removeCallback(timerCb); removeCallback(spawnCb)
        finishGame((score * 0.7).toInt().coerceAtMost(400),
            "AR Egg Shooter ($score pt)", score > 50, MiniGameManager.GAME_AR_SHOOTER)
    }
}
