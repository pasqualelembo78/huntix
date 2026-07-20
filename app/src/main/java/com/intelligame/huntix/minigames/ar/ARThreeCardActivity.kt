package com.intelligame.huntix.minigames.ar

import com.intelligame.huntix.managers.MiniGameManager

class ARThreeCardActivity : ARGameActivity() {

    private var wins = 0
    private var round = 0
    private var awaitingPick = false
    private var prizeEgg: AREgg? = null
    private val cups = mutableListOf<AREgg>()
    private val rightPos = floatArrayOf(-0.35f, 0f, 0.35f)

    override fun onGameCreate() {
        wins = 0; round = 0
        cups.forEach { removeEgg(it) }; cups.clear()
        statusText.text = "Shell Game AR: indovina il premio! 🥚"
        startGame()
        whenReady { setupRound() }
    }

    private fun setupRound() {
        round++
        if (round > 5) { endGame(); return }
        cups.clear()
        for (i in 0..2) {
            val egg = spawnEgg(0, 0.95f, rightPos[i], 0f, radius = 0.1f)
            egg?.let { cups.add(it) }
        }
        prizeEgg = cups.random()
        prizeEgg?.let { recolorEgg(it, 6) }
        updateHud()
        awaitingPick = false
        postDelayed(600) { shuffle(6) }
    }

    private fun shuffle(remaining: Int) {
        if (remaining <= 0) { awaitingPick = true; statusText.text = "Dove si è nascosto il premio? 👆"; return }
        val a = (0..2).random(); var b = (0..2).random(); while (b == a) b = (0..2).random()
        val tmp = rightPos[a]; rightPos[a] = rightPos[b]; rightPos[b] = tmp
        cups.getOrNull(a)?.let { moveEggLocal(it, 0f, rightPos[a], 0f) }
        cups.getOrNull(b)?.let { moveEggLocal(it, 0f, rightPos[b], 0f) }
        postDelayed(280) { shuffle(remaining - 1) }
    }

    override fun onEggTapped(egg: AREgg) {
        if (!running || !awaitingPick || !egg.alive) return
        awaitingPick = false
        if (egg == prizeEgg) {
            wins++; statusText.text = "🎉 Bravo! Era il premio!"
        } else {
            statusText.text = "😅 Il premio era l'altro!"
            prizeEgg?.let { recolorEgg(it, 6) }
        }
        updateHud()
        postDelayed(1100) { if (running) setupRound() }
    }

    private fun updateHud() {
        livesText.text = "🏆 $wins"
        scoreText.text = "Round $round/5"
        timerText.text = ""
    }

    private fun endGame() {
        stopGame()
        finishGame(wins * 80, "AR Three Card ($wins/5)", wins >= 3, MiniGameManager.GAME_THREE_CARD)
    }
}
