package com.intelligame.huntix.minigames.ar

import com.intelligame.huntix.managers.MiniGameManager

class ARNumberPickActivity : ARGameActivity() {

    private var lives = 3
    private var score = 0
    private var round = 0
    private var target = 0
    private val slots = mutableListOf<AREgg>()

    override fun onGameCreate() {
        lives = 3; score = 0; round = 0
        statusText.text = "Number Pick AR: scegli l'uovo giusto! 🔢"
        startGame()
        whenReady { nextRound() }
    }

    private fun nextRound() {
        slots.forEach { removeEgg(it) }
        slots.clear()
        round++
        if (round > 5) { endGame(); return }
        val nums = (1..9).shuffled().take(3)
        target = nums.maxOrNull() ?: 0
        val offsets = listOf(Triple(0.9f, -0.35f, 0f), Triple(0.9f, 0f, 0.1f), Triple(0.9f, 0.35f, -0.05f))
        nums.forEachIndexed { i, n ->
            val (f, r, u) = offsets[i]
            val egg = spawnEgg((i + 1) % 5, f, r, u, radius = 0.09f)
            egg?.phase = n.toFloat()
            egg?.let { slots.add(it) }
        }
        updateHud(nums)
    }

    private fun updateHud(nums: List<Int>) {
        livesText.text = "❤️".repeat(lives)
        scoreText.text = "$score pt"
        timerText.text = "R$round/5"
        statusText.text = "Tocca l'uovo con il numero PIÙ ALTO\n" +
            nums.mapIndexed { i, n -> "${i + 1}️⃣=$n" }.joinToString("  ")
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || !egg.alive) return
        val n = egg.phase.toInt()
        if (n == target) { score += 30; statusText.text = "✅ $n era il più alto!" }
        else { lives = (lives - 1).coerceAtLeast(0); statusText.text = "❌ $n non è il più alto" }
        updateHud(slots.map { it.phase.toInt() })
        if (lives <= 0) endGame() else postDelayed(900) { if (running) nextRound() }
    }

    private fun endGame() {
        stopGame()
        finishGame((score * 0.7).toInt().coerceAtMost(300),
            "AR Number Pick ($score pt)", score > 60, MiniGameManager.GAME_NUMBER_PICK)
    }
}
