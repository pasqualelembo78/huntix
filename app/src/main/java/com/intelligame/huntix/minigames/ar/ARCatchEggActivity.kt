package com.intelligame.huntix.minigames.ar

import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import java.util.Collections

class ARCatchEggActivity : ARGameActivity() {

    private var lives = 3
    private var score = 0
    private var timeLeft = 40
    private var speedMult = 1f
    private val eggsActive = Collections.synchronizedList(mutableListOf<AREgg>())
    private var timerCb: Runnable? = null
    private var spawnCb: Runnable? = null

    override fun onGameCreate() {
        usesSurfaceArena = false
        val diff = MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_CATCH_EGG)
        lives = 3; score = 0; timeLeft = (40 - (10 * diff).toInt()).coerceAtLeast(20); speedMult = 1f
        eggsActive.clear()
        statusText.text = "🎯  Cattura le uova dorate! Evita i💣  Nero (perdono una vita)"
        statusText.setTextColor(android.graphics.Color.parseColor(UiKit.ACCENT))
        livesText.text = "❤️".repeat(lives)
        timerText.text = "⏱ ${timeLeft}s"
        scoreText.text = "0 pt"
        updateLevelHud(MiniGameManager.GAME_CATCH_EGG)
        startGame()
        startTimer()
        scheduleSpawn()
    }

    private fun startTimer() {
        removeCallback(timerCb)
        timerCb = postDelayed(1000) {
            if (!running) return@postDelayed
            timeLeft--
            val elapsed = 40 - timeLeft
            speedMult = 1f + elapsed * 0.035f
            val spawnRate = (300L..Math.max(150L, 900L - elapsed * 30L)).random()
            timerText.text = "⏱ ${timeLeft}s  🔥 ${String.format("%.1f", speedMult)}x"
            if (timeLeft <= 10) timerText.setTextColor(android.graphics.Color.parseColor("#FF4444"))
            if (timeLeft <= 0) { endGame(); return@postDelayed }
            removeCallback(spawnCb)
            spawnCb = postDelayed(spawnRate) {
                if (!running) return@postDelayed
                synchronized(eggsActive) {
                    val maxEggs = (8 + (elapsed / 5)).coerceAtMost(12)
                    if (eggsActive.count { it.alive } < maxEggs) spawnEgg()
                }
                scheduleSpawn()
            }
            startTimer()
        }
    }

    private fun scheduleSpawn() {
        removeCallback(spawnCb)
        val elapsed = 40 - timeLeft
        val interval = (300L..Math.max(150L, 900L - elapsed * 30L)).random()
        spawnCb = postDelayed(interval) {
            if (!running) return@postDelayed
            synchronized(eggsActive) {
                val maxEggs = (8 + (elapsed / 5)).coerceAtMost(12)
                if (eggsActive.count { it.alive } < maxEggs) spawnEgg()
            }
            scheduleSpawn()
        }
    }

    private fun spawnEgg() {
        val elapsed = 40 - timeLeft
        val bombChance = (elapsed * 0.008f).coerceIn(0.10f, 0.35f)
        val rnd = Math.random()
        val type = when {
            rnd < 0.05 -> 3      // 🟡 Rara — 100pt
            rnd < 0.05 + bombChance -> 6      // ⚫ Bomba — perde vita (aumenta col tempo)
            rnd < 0.25 -> 1      // 🟢 Verde — 25pt
            else -> 0            // ⚪ Normale — 10pt
        }
        val forward = (0.6f..1.2f).random()
        val right = (-0.45f..0.45f).random()
        val up = (-0.25f..0.30f).random()
        val egg = spawnEgg(type, forward, right, up, radius = 0.08f) ?: return
        egg.phase = (forward * 6.28f)
        synchronized(eggsActive) { eggsActive.add(egg) }
    }

    override fun onArFrame(session: Session, frame: Frame) {
        if (!running) return
        synchronized(eggsActive) {
            val iter = eggsActive.iterator()
            while (iter.hasNext()) {
                val egg = iter.next()
                if (!egg.alive) { iter.remove(); continue }
                egg.phase += 0.03f * speedMult
                val y = kotlin.math.sin(egg.phase.toDouble()).toFloat() * 0.12f
                val x = kotlin.math.sin(egg.phase.toDouble() * 0.5f).toFloat() * 0.1f
                moveEggLocal(egg, x, y, 0f)
            }
        }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || !egg.alive) return
        when (egg.type) {
            0 -> { score += 10; removeEgg(egg) }
            1 -> { score += 25; removeEgg(egg) }
            3 -> { score += 100; removeEgg(egg) }
            6 -> { lives = (lives - 1).coerceAtLeast(0); removeEgg(egg) }
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
        val reward = (score * 0.6).toInt().coerceAtLeast(10).coerceAtMost(350)
        finishGame(
            reward = reward,
            label = "AR Cattura Uova ($score pt)",
            isWin = score > 40,
            gameId = MiniGameManager.GAME_CATCH_EGG,
            score = score
        )
    }
}
