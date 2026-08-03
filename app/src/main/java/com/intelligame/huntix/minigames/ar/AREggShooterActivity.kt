package com.intelligame.huntix.minigames.ar

import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import java.util.Collections

class AREggShooterActivity : ARGameActivity() {

    private var lives = 3
    private var score = 0
    private var timeLeft = 40
    private val eggsActive = Collections.synchronizedList(mutableListOf<AREgg>())
    private var timerCb: Runnable? = null
    private var spawnCb: Runnable? = null

    override fun onGameCreate() {
        usesSurfaceArena = false
        val diff = MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_AR_SHOOTER)
        lives = 3; score = 0
        timeLeft = (40 - (10 * diff).toInt()).coerceAtLeast(20)
        eggsActive.clear()
        statusText.text = "🎯  Spara alle uova dorate che salgono! ⚪=10pt  🟡=100pt  ⚫=perdi vita"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "❤️".repeat(lives)
        timerText.text = "⏱ ${timeLeft}s"
        scoreText.text = "0 pt"
        updateLevelHud(MiniGameManager.GAME_AR_SHOOTER)
        startGame(); startTimer(); scheduleSpawn()
    }

    private fun startTimer() {
        removeCallback(timerCb)
        timerCb = postDelayed(1000) {
            if (!running) return@postDelayed
            timeLeft--
            val elapsed = 40 - timeLeft
            timerText.text = "⏱ ${timeLeft}s  🔥 ${String.format("%.1f", 1f + elapsed * 0.03f)}x"
            if (timeLeft <= 0) endGame() else startTimer()
        }
    }

    private fun scheduleSpawn() {
        removeCallback(spawnCb)
        val elapsed = 40 - timeLeft
        val interval = (Math.max(200L, 450L - elapsed * 10L)..Math.max(150L, 800L - elapsed * 15L)).random()
        spawnCb = postDelayed(interval) {
            if (!running) return@postDelayed
            synchronized(eggsActive) {
                val maxEggs = (7 + (elapsed / 5)).coerceAtMost(12)
                if (eggsActive.count { it.alive } < maxEggs) spawnEggUp()
            }
            scheduleSpawn()
        }
    }

    private fun spawnEggUp() {
        val elapsed = 40 - timeLeft
        val bombChance = (elapsed * 0.006f).coerceIn(0f, 0.25f)
        val rnd = Math.random()
        val type = when {
            rnd < 0.12 -> 3      // 🟡 Rara — 100pt
            rnd < 0.12 + bombChance -> 6      // ⚫ Bomba — perde vita
            else -> 0            // ⚪ Normale — 10pt
        }
        val egg = spawnEgg(type, forward = 1.0f, right = (-0.4f..0.4f).random(), up = -0.45f,
            radius = 0.08f) ?: return
        egg.phase = -0.45f
        synchronized(eggsActive) { eggsActive.add(egg) }
    }

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running) return
        synchronized(eggsActive) {
            val iter = eggsActive.iterator()
            while (iter.hasNext()) {
                val egg = iter.next()
                if (!egg.alive) { iter.remove(); continue }
                egg.phase += 0.006f
                moveEggLocal(egg, 0f, egg.phase, 0f)
                if (egg.phase > 0.55f) {
                    iter.remove(); removeEgg(egg)
                    lives = (lives - 1).coerceAtLeast(0)
                    updateHud()
                    if (lives <= 0) endGame()
                }
            }
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || !egg.alive) return
        score += if (egg.type == 3) 100 else 10
        synchronized(eggsActive) {
            removeEgg(egg); eggsActive.remove(egg)
        }
        updateHud()
    }

    private fun updateHud() {
        livesText.text = "❤️".repeat(lives)
        scoreText.text = "$score pt"
        if (timerText.text.isEmpty()) timerText.text = "⏱ ${timeLeft}s"
    }

    private fun endGame() {
        stopGame()
        removeCallback(timerCb); removeCallback(spawnCb)
        val reward = (score * 0.7).toInt().coerceAtLeast(10).coerceAtMost(400)
        finishGame(
            reward = reward,
            label = "AR Egg Shooter ($score pt)",
            isWin = score > 50,
            gameId = MiniGameManager.GAME_AR_SHOOTER,
            score = score
        )
    }
}
