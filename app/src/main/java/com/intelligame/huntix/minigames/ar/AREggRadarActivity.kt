package com.intelligame.huntix.minigames.ar

import com.intelligame.huntix.managers.MiniGameManager

class AREggRadarActivity : ARGameActivity() {

    private var timeLeft = 45
    private var total = 0
    private var timerCb: Runnable? = null

    override fun onGameCreate() {
        usesSurfaceArena = false
        val diff = MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_AR_RADAR)
        timeLeft = (45 - (15 * diff).toInt()).coerceAtLeast(20)
        statusText.text = "Radar AR: cattura tutte le uova sospese! 🛰️"
        updateLevelHud(MiniGameManager.GAME_AR_RADAR)
        startGame(); startTimer(); whenReady { spawnWave() }
    }

    private fun spawnWave() {
        val extra = (3 * MiniGameManager.levelDifficulty(this, MiniGameManager.GAME_AR_RADAR)).toInt()
        val positions = mutableListOf(
            Triple(0.8f, -0.4f, 0.2f), Triple(1.1f, 0.35f, -0.1f),
            Triple(0.9f, 0.0f, 0.35f), Triple(1.3f, -0.3f, -0.25f),
            Triple(1.0f, 0.45f, 0.15f)
        )
        repeat(extra) { i ->
            positions.add(Triple(0.7f + Math.random().toFloat() * 0.6f,
                -0.4f + Math.random().toFloat() * 0.8f,
                -0.3f + Math.random().toFloat() * 0.6f))
        }
        positions.forEachIndexed { i, (f, r, u) ->
            spawnEgg(i % 5, f, r, u, radius = 0.09f)
        }
        total = positions.size
        updateHud()
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

    override fun onEggTapped(egg: AREgg) {
        if (!running || !egg.alive) return
        removeEgg(egg)
        updateHud()
        if (aliveCount() == 0) endGame()
    }

    private fun updateHud() {
        val found = total - aliveCount()
        livesText.text = "🔍 ${aliveCount()}"
        scoreText.text = "$found/$total"
        if (timerText.text.isEmpty()) timerText.text = "⏱ ${timeLeft}s"
        statusText.text = if (aliveCount() == 0) "Tutte catturate!"
        else "Uova rilevate: ${aliveCount()} — toccale!"
    }

    private fun endGame() {
        stopGame(); removeCallback(timerCb)
        val found = total - aliveCount()
        val reward = found * 60
        finishGame(reward, "AR Egg Radar ($found/$total)", found == total,
            MiniGameManager.GAME_AR_RADAR, score = found)
    }
}
